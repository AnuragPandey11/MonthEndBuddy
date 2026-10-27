# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Non-root nginx listening on 8080. Its config comes from infra/nginx/default.conf through the "infra" build
# context (compose.yaml: additional_contexts).
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY --from=infra nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
