# syntax=docker/dockerfile:1
# ============================================================
# NitroWater 后端 —— 单 Dockerfile 多目标（aligned with WaterFun）
#
# 一次构建全部模块，每个运行时 target 只提取自己的 jar（同名 app.jar）。
#
# 用法：
#   docker build --target account  -t nitrowater-account:local  .
#   docker build --target resource -t nitrowater-resource:local .
#   docker build --target server   -t nitrowater-server:local   .
#   docker build --target bff      -t nitrowater-bff:local      .
# CI（docker/build-push-action）用 target 分别构建各镜像。
# ============================================================
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY nitrowater-lib/ nitrowater-lib/
COPY nitrowater-core/ nitrowater-core/
COPY nitrowater-account/ nitrowater-account/
COPY nitrowater-bff/ nitrowater-bff/
COPY nitrowater-server/ nitrowater-server/
COPY nitrowater-resource/ nitrowater-resource/

RUN chmod +x gradlew && ./gradlew --no-daemon -x test \
      :nitrowater-account:bootJar :nitrowater-bff:bootJar \
      :nitrowater-server:bootJar :nitrowater-resource:bootJar

# ==================== Runtime: account (SSO) ====================
FROM eclipse-temurin:25-jre AS account
WORKDIR /app
COPY --from=build /app/nitrowater-account/build/libs/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: resource ====================
FROM eclipse-temurin:25-jre AS resource
WORKDIR /app
COPY --from=build /app/nitrowater-resource/build/libs/app.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: server (business API) ====================
FROM eclipse-temurin:25-jre AS server
WORKDIR /app
COPY --from=build /app/nitrowater-server/build/libs/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: bff ====================
FROM eclipse-temurin:25-jre AS bff
WORKDIR /app
COPY --from=build /app/nitrowater-bff/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
