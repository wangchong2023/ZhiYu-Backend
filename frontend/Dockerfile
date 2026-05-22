# ============================================================
# Stage 1: Node 编译（与运行镜像解耦）
# ============================================================
FROM node:20-alpine AS builder
WORKDIR /app

# 利用 Docker layer cache：先只拷贝依赖文件
COPY frontend/package*.json ./
RUN npm ci --prefer-offline

# 再拷贝源码 + 编译
COPY frontend/ ./
RUN npm run build

# ============================================================
# Stage 2: Nginx 运行时（最终镜像仅 ~8MB 增量）
# ============================================================
FROM nginx:1.27-alpine

# Nginx 基础镜像已包含 /usr/share/nginx/html
COPY --from=builder /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# 健康检查
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
