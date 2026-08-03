# Pixel Ledger

Private, two-user bookkeeping app with a SwiftUI iOS client and a self-hosted Spring Boot API.

## Layout

- `backend/` — Java 25 / Spring Boot 4 / MyBatis / Flyway API
- `ios/` — SwiftUI iOS client sources and an XcodeGen `project.yml`; run `xcodegen generate` from `ios/` after installing Xcode/XcodeGen.

## Local IDEA debugging

1. Install and start Docker Desktop.
2. Fill in test-only values in `backend/.env.test`.
3. Start MySQL and Redis once: `cd backend && ./scripts/start-local-datastores.sh`.
4. In IDEA, run `PixelLedgerApplication` directly with working directory set to the repository root and active profile `local` (or use `--spring.profiles.active=local`). The API connects to data containers at `127.0.0.1`; Docker Compose is not used.
5. Create test users once with `API_URL=http://localhost:8080 ADMIN_KEY='…' sh scripts/provision-user.sh 手机号 初始密码`.

Flyway runs schema migrations when the API starts. Do not edit an already-applied migration; add a new `V<n>__description.sql` file instead.

## Production

1. Place a filled-in `.env.production` on the server. Never commit production secrets.
2. Run `./scripts/start-production-datastores.sh` once to create the private Docker network and persistent MySQL/Redis containers.
3. Build and push an immutable API image to a private registry, then deploy it: `./scripts/deploy-production-api.sh .env.production registry.example.com/pixel-ledger-api:<git-sha>`.
4. Put Caddy or Nginx in front of `127.0.0.1:8080` for HTTPS.

MySQL and Redis are independent persistent containers. Replacing or scaling API containers never recreates them; neither production data service exposes a host port. Back up the MySQL volume daily and never expose Redis publicly.

Both environments use the container names `mysql` and `redis`. Redis intentionally has no password in this private-network design, and runs with AOF persistence enabled.
