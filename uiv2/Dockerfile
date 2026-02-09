FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY uiv2/package.json uiv2/package.json
RUN corepack enable && pnpm install --frozen-lockfile --filter uiv2
COPY uiv2/ uiv2/
COPY protos/ /app/protos/
WORKDIR /app/uiv2
RUN pnpm proto && pnpm build

FROM caddy:2-alpine
COPY --from=build /app/uiv2/dist /srv
COPY uiv2/Caddyfile.prod /etc/caddy/Caddyfile
EXPOSE 5173
