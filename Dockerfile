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

# ==================== web SPA (nitrowater-web) ====================
# The BFF serves the built SPA from classpath:/static/ (see WebConfig + the processResources
# hook in nitrowater-bff/build.gradle). Build it here and overlay the output into the gradle
# stage, so the bff image is self-contained — no host-side `npm run build`, no bind mount.
FROM node:24-alpine AS web-ui
WORKDIR /build/nitrowater-web
COPY nitrowater-web/package.json nitrowater-web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY nitrowater-web/ ./
RUN npm run build

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
# SPA for the BFF: processResources maps nitrowater-web/dist → classpath:/static/.
# Without this the image ships a jar with no index.html and the catch-all returns 404.
COPY --from=web-ui /build/nitrowater-web/dist nitrowater-web/dist

RUN chmod +x gradlew && ./gradlew --no-daemon -x test -PskipAdminUi -PskipAuthUi \
      :nitrowater-account:bootJar :nitrowater-bff:bootJar \
      :nitrowater-server:bootJar :nitrowater-resource:bootJar

# ==================== Runtime base ====================
FROM eclipse-temurin:25-jre AS runtime-base
WORKDIR /app

# ==================== Runtime: account (SSO) ====================
FROM runtime-base AS account
COPY --from=build /app/nitrowater-account/build/libs/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: resource ====================
FROM runtime-base AS resource
COPY --from=build /app/nitrowater-resource/build/libs/app.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: server (business API) ====================
FROM runtime-base AS server
COPY --from=build /app/nitrowater-server/build/libs/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

# ==================== Runtime: bff ====================
# Only the bff carries a healthcheck, so only it needs curl (the JRE image ships without it).
FROM runtime-base AS bff
RUN apt-get update \
      && apt-get install -y --no-install-recommends curl \
      && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/nitrowater-bff/build/libs/app.jar app.jar
EXPOSE 8080 9080
ENTRYPOINT ["java", "-jar", "app.jar"]
