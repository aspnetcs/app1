# ============================================================
# admin-lite Dockerfile — React 18 / Vite 6 / TypeScript
# Multi-stage: Node build → Nginx static serve
# ============================================================

# ---------- Build Stage ----------
FROM node:22-alpine AS builder

WORKDIR /build

# Copy dependency manifests first for layer caching
COPY package.json package-lock.json ./

RUN npm ci

# Copy source and build production bundle
COPY . .
RUN npm run build

# ---------- Runtime Stage ----------
FROM nginx:alpine

LABEL maintainer="webchat-team"
LABEL description="WebChat admin-lite — React admin panel"

# Copy built static assets
COPY --from=builder /build/dist /usr/share/nginx/html

# Nginx config with SPA fallback + API reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
