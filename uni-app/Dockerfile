# ============================================================
# uni-app H5 Dockerfile — Vue 3 / uni-app / Vite
# Multi-stage: Node build → Nginx static serve
# ============================================================

# ---------- Build Stage ----------
FROM node:22-alpine AS builder

# esbuild needs extra memory for large dependency trees (mermaid, highlight.js, etc.)
ENV NODE_OPTIONS="--max-old-space-size=4096"

WORKDIR /build

# Copy dependency manifests first for layer caching
COPY package.json package-lock.json ./

RUN npm ci

# Copy source and build H5 production bundle
COPY . .
RUN npm run build:h5

# ---------- Runtime Stage ----------
FROM nginx:alpine

LABEL maintainer="webchat-team"
LABEL description="WebChat uni-app H5 — user-facing web app"

# Copy built H5 assets (uni-app outputs to dist/build/h5/)
COPY --from=builder /build/dist/build/h5 /usr/share/nginx/html

# Nginx config with SPA fallback + API/WebSocket reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
