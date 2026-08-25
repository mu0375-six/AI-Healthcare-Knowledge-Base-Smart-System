# 多阶段构建：编译产物与运行环境分离，最终镜像不含 Maven 与源码。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷 pom 拉依赖，源码变动时这一层仍能命中缓存
COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY src ./src
# 镜像构建阶段跳过测试：测试在 CI 里单独跑，这里重复跑只会拖慢构建
RUN mvn -B --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# 用非 root 运行，容器内写入只限于 /app/data
RUN useradd -r -u 1001 -m healthkb && mkdir -p /app/data && chown -R healthkb:healthkb /app
COPY --from=build /build/target/*.jar app.jar
RUN chown healthkb:healthkb /app/app.jar
USER healthkb
EXPOSE 8080
# 敏感配置一律由 compose 从 .env 注入，镜像里不含任何默认密钥
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
