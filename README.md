# Pixel Ledger

Private, two-user bookkeeping app with a SwiftUI iOS client and a self-hosted Spring Boot API.

## Layout

- `backend/` — Java 25 / Spring Boot 4 / MyBatis / Flyway API
- `ios/` — SwiftUI iOS client sources and an XcodeGen `project.yml`; run `xcodegen generate` from `ios/` after installing Xcode/XcodeGen.

## IDEA 本地调试

1. 安装 Docker Desktop 并保持 Docker Engine 运行。
2. 填写 `backend/.env.test` 中的占位符；这是本地测试数据，不能使用生产密钥。
3. 在 IDEA 打开 `backend/pom.xml`，将 Run Configuration 的 **Working directory** 设置为 `backend/`，**Active profiles** 设置为 `local`。
4. 直接点击 Debug。Spring Boot 会自动启动 `compose.dev.yaml` 中的 MySQL 和 Redis、等待健康检查通过、创建连接并启动 API；基础设施会保留运行，后续 Debug 会复用它们。
5. 首次启动后，通过 `API_URL=http://localhost:8080 ADMIN_KEY='…' sh scripts/provision-user.sh 手机号 初始密码` 创建两位测试账户。

The first startup runs Flyway migrations automatically. Do not edit an already-applied migration; add a new `V<n>__description.sql` file instead.

## Production

Edit `backend/.env.production`, replace every placeholder with a production secret, then start with `docker compose --env-file .env.production -f compose.production.yaml up -d --build`.

Put the API behind a TLS reverse proxy, keep MySQL/Redis on the private Docker network, configure a real SMTP account, and back up the MySQL volume daily before exposing the API to iOS devices. Redis requires `REDIS_PASSWORD` and is intentionally not published to a host port.
