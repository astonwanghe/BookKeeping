# Pixel Ledger

Private, two-user bookkeeping app with a SwiftUI iOS client and a self-hosted Spring Boot API.

## Layout

- `backend/` — Java 25 / Spring Boot 4 / MyBatis / Flyway API
- `ios/` — SwiftUI iOS client sources and an XcodeGen `project.yml`; run `xcodegen generate` from `ios/` after installing Xcode/XcodeGen.

## Local backend

1. Copy `backend/.env.example` to `backend/.env` and replace all placeholder secrets, including `REDIS_PASSWORD`.
2. Run `docker compose --env-file .env up --build` from `backend/`.
3. Create the two allowed accounts with `API_URL=http://localhost:8080 ADMIN_KEY='…' sh scripts/provision-user.sh 手机号 初始密码`. The endpoint is protected by `X-Admin-Key`; remove that environment variable after onboarding.

The first startup runs Flyway migrations automatically. Do not edit an already-applied migration; add a new `V<n>__description.sql` file instead.

## Production

Put the API behind a TLS reverse proxy, keep MySQL/Redis on the private Docker network, configure a real SMTP account, and back up the MySQL volume daily before exposing the API to iOS devices. Redis requires `REDIS_PASSWORD` and is intentionally not published to a host port.
