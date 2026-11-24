FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --registry https://registry.npmmirror.com
COPY . .
# 管理端由网关以 /admin/ 前缀对外提供，构建时同步设置 base
RUN npm run build -- --base=/admin/

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
