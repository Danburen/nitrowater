# syntax=docker/dockerfile:1
# Standalone offline tools site: build the Vue SPA, serve it with nginx.
# Fully self-contained — no backend, no portal callback, no build args.
#   docker build -t nitrowater-tools:local .
#   docker run --rm -p 8080:80 nitrowater-tools:local
FROM node:24.22.3-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
