# syntax=docker/dockerfile:1.7

# 构建与运行阶段严格分离：生产镜像不包含 Maven、源码和本地依赖缓存，
# 服务器只需从 TCR 拉取最终 JRE 镜像，不需要访问 Maven Central。
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
# Spring Boot 4 的 tools jarmode 会按依赖、Loader、快照依赖和业务代码拆层。
# 日常 Java 修改通常只改变 application 层，避免向 TCR 重传整个 fat JAR。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests \
    && cp target/*.jar /workspace/app.jar \
    && java -Djarmode=tools -jar /workspace/app.jar extract \
        --layers \
        --launcher \
        --destination /workspace/extracted

FROM eclipse-temurin:21-jre-noble AS runtime

# curl 只用于容器健康检查。安装发生在 GitHub Runner 的镜像构建阶段，
# 线上服务器不会执行 apt，也不会依赖境外软件源。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 xiaolvshu \
    && useradd --system --uid 10001 --gid xiaolvshu --home-dir /app --shell /usr/sbin/nologin xiaolvshu

WORKDIR /app

# 复制顺序从最稳定到最常变化，使 BuildKit、TCR 和生产服务器都能复用大体积依赖层。
COPY --from=build --chown=xiaolvshu:xiaolvshu /workspace/extracted/dependencies/ ./
COPY --from=build --chown=xiaolvshu:xiaolvshu /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=xiaolvshu:xiaolvshu /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=xiaolvshu:xiaolvshu /workspace/extracted/application/ ./

USER xiaolvshu

EXPOSE 8080

# 2 核 4G 单机还要同时容纳 Elasticsearch、MySQL、RabbitMQ 和 Redis。
# 堆上限默认 512 MiB，并保留约 256 MiB 给线程栈、Netty、类元数据等堆外内存。
ENV JAVA_OPTS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/api/auth/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
