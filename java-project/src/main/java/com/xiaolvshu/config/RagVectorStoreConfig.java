package com.xiaolvshu.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Objects;

@Configuration
public class RagVectorStoreConfig {

    /**
     * 配置RAG模块使用的JdbcTemplate，连接到PostgreSQL数据库。
     * 连接参数从application-dev.yaml中读取，支持通过环境变量覆盖。
     */
    @Bean(name = "ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(
            @Value("${app.rag.vector-db.url}") String url,
            @Value("${app.rag.vector-db.username}") String username,
            @Value("${app.rag.vector-db.password}") String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(Objects.requireNonNull(url));
        dataSource.setUsername(Objects.requireNonNull(username));
        dataSource.setPassword(Objects.requireNonNull(password));
        return new JdbcTemplate(dataSource);
    }

    /**
     * 配置RAG模块使用的VectorStore，基于pgvector实现。
     * 依赖注入EmbeddingModel和JdbcTemplate，连接参数从application-dev.yaml中读取，支持通过环境变量覆盖。
     */
    @Bean
    public VectorStore ragVectorStore(
            EmbeddingModel embeddingModel,
            @Qualifier("ragJdbcTemplate") JdbcTemplate ragJdbcTemplate,
            @Value("${app.rag.vector-db.schema:public}") String schemaName,
            @Value("${app.rag.vector-db.table:vector_store}") String tableName,
            @Value("${app.rag.vector-db.dimensions:1024}") int dimensions) {
        return PgVectorStore.builder(Objects.requireNonNull(ragJdbcTemplate), Objects.requireNonNull(embeddingModel))
                .schemaName(Objects.requireNonNull(schemaName))
                .vectorTableName(Objects.requireNonNull(tableName))
                .dimensions(dimensions)
                .initializeSchema(true)
                .build();
    }
}
