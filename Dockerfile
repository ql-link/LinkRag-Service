# syntax=docker/dockerfile:1

# ---- 构建阶段：Maven 多模块打包 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY . .
# BuildKit 缓存 .m2，避免每次全量下载依赖
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package

# ---- 运行阶段：仅 JRE ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# link-api 是唯一可执行模块；spring-boot-maven-plugin 重打包后原始包为 *.jar.original
COPY --from=build /build/link-api/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1g"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
