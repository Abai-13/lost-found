# ============================================
# 阶段 1：构建阶段（用 Maven 编译打包）
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# 设置 Maven 工作目录
WORKDIR /app

# 先复制 pom.xml（利用 Docker 缓存层 — 依赖不常变）
COPY pom.xml .

# 下载依赖（这一层会被缓存，除非 pom.xml 变了）
RUN mvn dependency:go-offline -B

# 复制源码
COPY src ./src

# 打包（跳过测试，加快构建速度）
RUN mvn clean package -DskipTests -q

# ============================================
# 阶段 2：运行阶段（只用 JRE，镜像更小）
# ============================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# 创建应用目录
WORKDIR /app

# 从构建阶段复制 JAR 包
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口（文档作用，实际端口映射在 docker-compose 里配）
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java","-Xms256m", "-Xmx512m", "-jar", "app.jar"]
