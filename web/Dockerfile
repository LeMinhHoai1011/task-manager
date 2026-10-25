# Build the SPA and serve it with Caddy (automatic HTTPS + reverse proxy).
FROM node:22-alpine AS build
WORKDIR /src
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .

# Vite inlines VITE_* at build time. Defaults keep the same-origin /api/v1
# setup; set VITE_API_BASE only when hosting the SPA on another origin.
ARG VITE_API_BASE=/api/v1
ARG VITE_ENABLE_AI=true
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_ENABLE_AI=$VITE_ENABLE_AI

RUN pnpm build

FROM caddy:2-alpine
COPY --from=build /src/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
EXPOSE 80 443 443/udp
