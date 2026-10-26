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
# ==================== account UIs (pnpm workspace) ====================
# Both SPAs and the shared packages live in one pnpm workspace at nitrowater-account/.
# Build them in a node stage; vite emits straight to
# nitrowater-account/src/main/resources/static/{admin,auth} (same relative layout), which
# the gradle stage overlays. Gradle then runs with -PskipAdminUi -PskipAuthUi so it never
# shells out to node/pnpm.
FROM node:24-alpine AS ui-base
WORKDIR /build/nitrowater-account
# pnpm 11 requires Node >= 22.13; node:24-alpine also matches local dev.
RUN npm install -g pnpm@11.18.0
COPY nitrowater-account/package.json nitrowater-account/pnpm-workspace.yaml nitrowater-account/pnpm-lock.yaml ./
COPY nitrowater-account/packages ./packages
COPY nitrowater-account/admin-ui/package.json ./admin-ui/package.json
COPY nitrowater-account/auth-ui/package.json ./auth-ui/package.json
RUN pnpm install --frozen-lockfile
COPY nitrowater-account/admin-ui ./admin-ui
COPY nitrowater-account/auth-ui ./auth-ui

FROM ui-base AS admin-ui
RUN pnpm --filter admin-ui build

# ==================== auth-ui (Vue SPA) ====================
# SSO login / register surface. Same workspace and output contract as admin-ui.
FROM ui-base AS auth-ui
RUN pnpm --filter auth-ui build

# ==================== build (gradle, all modules) ====================
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

# Overlay the freshly built SPAs (context excludes any stale local copies via .dockerignore).
COPY --from=admin-ui /build/nitrowater-account/src/main/resources/static/admin nitrowater-account/src/main/resources/static/admin
COPY --from=auth-ui /build/nitrowater-account/src/main/resources/static/auth nitrowater-account/src/main/resources/static/auth

RUN chmod +x gradlew && ./gradlew --no-daemon -x test -PskipAdminUi -PskipAuthUi \
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
