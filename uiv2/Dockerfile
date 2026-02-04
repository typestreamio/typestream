FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
COPY uiv2/package.json uiv2/pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY uiv2/ ./
COPY protos/ /protos/
RUN pnpm proto && pnpm build

FROM caddy:2-alpine
COPY --from=build /app/dist /srv
COPY uiv2/Caddyfile.prod /etc/caddy/Caddyfile
EXPOSE 5173
