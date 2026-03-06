# 基础镜像，改用 eclipse-temurin
FROM eclipse-temurin:21-jre-alpine

# 设置工作目录
WORKDIR /app

# 复制编译好的 Jar 包到容器中
COPY app.jar app.jar

# 暴露端口
EXPOSE 8080

# 设置时区
ENV TZ=Asia/Shanghai
# 设置默认 Java 参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=release"]
