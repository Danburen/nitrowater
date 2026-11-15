# syntax=docker/dockerfile:1
# ============================================================
# NitroWater 后端 —— 单 Dockerfile 多目标（aligned with WaterFun）
#
# 一次构建全部模块；运行时 target 只取自己的 jar（同名 app.jar），并按 Spring Boot
# 的 layers.idx 把它拆成「依赖 / loader / 快照 / 应用」四层，各占一个镜像层：
# 发版只有 ~2MB 的 application 层变化，几十到上百 MB 的 dependencies 层复用宿主缓存。
#
# 用法：
#   docker build --target account  -t nitrowater-account:local  .
#   docker build --target resource -t nitrowater-resource:local .
#   docker build --target server   -t nitrowater-server:local   .
#   docker build --target bff      -t nitrowater-bff:local      .
# CI（docker/build-push-action）用 target 分别构建各镜像。
#
# Base image tags are the newest patch of the same major/minor line as the local
# toolchain, so a host build and an image build behave the same:
#   local Node 24.18.0 / pnpm 11.18.0  -> image node:24.22.3-alpine
#   local Temurin 25.0.4.1+1           -> image eclipse-temurin:25.0.5_8
# Bump them together with the toolchain (java { toolchain } / packageManager).
# ============================================================
# ==================== account UIs (pnpm workspace) ====================
# Both SPAs and the shared packages live in one pnpm workspace at nitrowater-account/.
# Build them in a node stage; vite emits straight to
# nitrowater-account/src/main/resources/static/{admin,auth} (same relative layout), which
# the gradle stage overlays. Gradle then runs with -PskipAdminUi -PskipAuthUi so it never
# shells out to node/pnpm.
FROM node:24.22.3-alpine AS ui-base
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
FROM node:24.22.3-alpine AS web-ui
WORKDIR /build/nitrowater-web
COPY nitrowater-web/package.json nitrowater-web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY nitrowater-web/ ./
RUN npm run build

# ==================== build (gradle, all modules) ====================
FROM eclipse-temurin:25.0.5_8-jdk AS build
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

# Explode every boot jar into its Spring Boot layers (BOOT-INF/layers.idx ships inside the jar),
# so the runtime targets can copy one image layer per boot layer.
# `--launcher` is required: without it the tool emits the "thin jar + lib/" layout, which only
# runs as one directory and therefore yields a single fat image layer.
RUN for svc in account bff server resource; do \
      java -Djarmode=tools -jar nitrowater-${svc}/build/libs/app.jar extract \
        --layers --launcher --destination /app/layers/${svc}; \
    done

# ==================== Runtime base ====================
FROM eclipse-temurin:25.0.5_8-jre AS runtime-base
WORKDIR /app

# Each target copies the extracted layers in dependency order, one image layer each: application
# last so its BOOT-INF/classes wins over anything a sibling layer ships at the same path.
# The on-disk layout is exactly what a boot jar unpacks to at runtime; only the start command
# changes — `java -jar app.jar` becomes invoking the launcher class the manifest points at.

# ==================== Runtime: account (SSO) ====================
FROM runtime-base AS account
COPY --from=build /app/layers/account/dependencies/ ./
COPY --from=build /app/layers/account/spring-boot-loader/ ./
COPY --from=build /app/layers/account/snapshot-dependencies/ ./
COPY --from=build /app/layers/account/application/ ./
EXPOSE 8090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ==================== Runtime: resource ====================
FROM runtime-base AS resource
COPY --from=build /app/layers/resource/dependencies/ ./
COPY --from=build /app/layers/resource/spring-boot-loader/ ./
COPY --from=build /app/layers/resource/snapshot-dependencies/ ./
COPY --from=build /app/layers/resource/application/ ./
EXPOSE 8093
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ==================== Runtime: server (business API) ====================
FROM runtime-base AS server
COPY --from=build /app/layers/server/dependencies/ ./
COPY --from=build /app/layers/server/spring-boot-loader/ ./
COPY --from=build /app/layers/server/snapshot-dependencies/ ./
COPY --from=build /app/layers/server/application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ==================== Runtime: bff ====================
# Only the bff carries a healthcheck, so only it needs curl (the JRE image ships without it).
FROM runtime-base AS bff
RUN apt-get update \
      && apt-get install -y --no-install-recommends curl \
      && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/layers/bff/dependencies/ ./
COPY --from=build /app/layers/bff/spring-boot-loader/ ./
COPY --from=build /app/layers/bff/snapshot-dependencies/ ./
COPY --from=build /app/layers/bff/application/ ./
EXPOSE 8080 9080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
