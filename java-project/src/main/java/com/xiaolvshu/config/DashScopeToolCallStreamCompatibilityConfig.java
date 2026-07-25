package com.xiaolvshu.config;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 修正 DashScope OpenAI 兼容接口的流式工具调用片段。
 *
 * <p>DashScope 会在第一个 {@code tool_calls} 片段返回真实调用 ID 和函数名，在后续参数片段中
 * 返回 {@code id: ""} 和空函数名。Spring AI 2.0.0 的 ChunkMerger 使用 Optional 是否存在来
 * 判断这是“新调用”还是“上一调用的续片”；OpenAI Java SDK 会把空字符串包装成非空 Optional，
 * 导致续片被误判为新调用，最终读取缺失函数名时抛出 NoSuchElementException。</p>
 *
 * <p>OpenAI 流式协议中，续片的调用 ID 本来就应当缺省。这里在 SDK 反序列化前把空 ID 规范化
 * 为 JSON null，使 ChunkMerger 进入原有的参数拼接分支。处理器逐行转发 SSE，不缓存完整响应，
 * 因此不会把旅行 Agent 的真实流式输出退化为整段输出。</p>
 */
@Configuration(proxyBeanMethods = false)
public class DashScopeToolCallStreamCompatibilityConfig {

    private static final Pattern EMPTY_ID =
            Pattern.compile("\"id\"\\s*:\\s*\"\"");

    @Bean
    OpenAiHttpClientBuilderCustomizer dashScopeToolCallStreamCustomizer() {
        return builder -> builder.interceptor(new EmptyToolCallIdInterceptor());
    }

    static String normalizeSseLine(String line) {
        /*
         * 只处理 SSE data 行，避免改写普通 JSON 响应、错误正文或响应头。自然语言 content 中
         * 出现的 JSON 引号会被转义为 \", 不会命中这个未转义的字段模式。
         */
        if (!line.startsWith("data:") || line.equals("data: [DONE]")) {
            return line;
        }
        return EMPTY_ID.matcher(line).replaceAll("\"id\":null");
    }

    private static final class EmptyToolCallIdInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            ResponseBody body = response.body();
            String contentType = response.header("Content-Type", "");

            /*
             * OpenAI SDK 的同步接口还会承载 embedding 等请求。补丁严格限定到 Chat Completions
             * 的 event-stream，防止改变向量化、非流式聊天和第三方错误响应的语义。
             */
            if (body == null
                    || !chain.request().url().encodedPath().endsWith("/chat/completions")
                    || !contentType.toLowerCase().contains("text/event-stream")) {
                return response;
            }

            return response.newBuilder()
                    .body(new NormalizingSseResponseBody(body))
                    .build();
        }
    }

    private static final class NormalizingSseResponseBody extends ResponseBody {

        private final ResponseBody delegate;
        private BufferedSource normalizedSource;

        private NormalizingSseResponseBody(ResponseBody delegate) {
            this.delegate = delegate;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            /*
             * 替换后的字节数可能变化，不能继续声明上游 Content-Length。SSE 通常本来就是未知
             * 长度；返回 -1 也能确保客户端以流结束信号而不是旧长度判断响应边界。
             */
            return -1L;
        }

        @Override
        public BufferedSource source() {
            if (normalizedSource == null) {
                normalizedSource = Okio.buffer(new NormalizingSseSource(delegate.source()));
            }
            return normalizedSource;
        }
    }

    private static final class NormalizingSseSource implements Source {

        private final BufferedSource upstream;
        private final Buffer pending = new Buffer();

        private NormalizingSseSource(BufferedSource upstream) {
            this.upstream = upstream;
        }

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            if (byteCount == 0L) {
                return 0L;
            }

            /*
             * 每次最多等待一行 SSE，然后立即交给 OpenAI SDK。参数片段仍按服务端到达节奏输出，
             * 不会等待 [DONE]，也不会把整个模型响应聚合到内存。
             */
            while (pending.size() == 0L) {
                String line = upstream.readUtf8Line();
                if (line == null) {
                    return -1L;
                }
                pending.writeUtf8(normalizeSseLine(line));
                pending.writeByte('\n');
            }

            return pending.read(sink, Math.min(byteCount, pending.size()));
        }

        @Override
        public Timeout timeout() {
            return upstream.timeout();
        }

        @Override
        public void close() throws IOException {
            upstream.close();
        }
    }
}
