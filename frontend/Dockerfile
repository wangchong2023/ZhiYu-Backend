# ============================================================
# 前端 Nginx 运行时镜像
# 构建上下文: frontend/ 目录
# 前置条件: frontend/dist/ 已存在（由本地 npm run build 或 CI 生成）
# ============================================================
FROM nginx:1.27-alpine

# 静态文件
COPY dist /usr/share/nginx/html
# Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
