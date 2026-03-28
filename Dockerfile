FROM maven:3.8-openjdk-17-slim AS builder

# 设置工作目录
WORKDIR /app

# 复制Maven配置文件
COPY pom.xml .
COPY src ./src

# 构建应用(跳过测试以加快构建)
RUN mvn clean package -DskipTests

# 运行时镜像
FROM openjdk:17-slim

# 安装FFmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg curl && \
    rm -rf /var/lib/apt/lists/*

# 验证FFmpeg安装
RUN ffmpeg -version

# 创建应用目录
WORKDIR /app

# 复制JAR文件
COPY --from=builder /app/target/*.jar app.jar

# 创建临时目录
RUN mkdir -p /tmp/uploads && \
    mkdir -p /app/logs

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
