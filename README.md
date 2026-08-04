# Pixel Ledger

Pixel Ledger（像素账本）是一个仅供两人使用的私有记账系统，由 SwiftUI iOS 客户端和自建 Spring Boot API 组成。项目不面向 App Store 或公开用户，不提供注册、共享账本、资金账户、转账、报销和资产负债等功能。

## 技术栈

- iOS：SwiftUI、Swift 6、XcodeGen
- API：Java 25、Spring Boot 4、Spring Security、MyBatis、Flyway
- 存储：MySQL 9.7、Redis 8.8
- 部署：API、MySQL、Redis 独立运行，不使用 Docker Compose

## 目录结构

```text
PixelLedger/
├── backend/          # Spring Boot API、迁移、测试与部署脚本
├── ios/
│   ├── PixelLedger/  # SwiftUI 源码与 Info.plist
│   └── project.yml   # 唯一的 Xcode 工程配置源
├── AGENTS.md         # 项目协作与实现约束
└── README.md
```

`ios/PixelLedger.xcodeproj` 是 XcodeGen 生成物，不纳入版本控制，也不要手工维护其中的配置。

## iOS 开发

需要安装 Xcode 和 XcodeGen。首次检出或修改 `ios/project.yml` 后重新生成工程：

```bash
cd ios
xcodegen generate
open PixelLedger.xcodeproj
```

Debug 构建默认连接 `http://127.0.0.1:8080`；Release 地址在 `ios/project.yml` 中配置，部署前必须替换示例域名。

## 本地后端开发

1. 安装并启动 Docker Desktop。
2. 确认 `backend/.env.test` 中是本地测试配置，不能填入生产凭据。
3. 启动独立的 MySQL、Redis 容器：

   ```bash
   cd backend
   ./scripts/start-local-datastores.sh
   ```

4. 在 IDEA 中直接运行 `PixelLedgerApplication`：

   - Working directory：仓库根目录
   - Active profile：`local`

本地 API 连接 `127.0.0.1:3306` 和 `127.0.0.1:6379`。容器名称固定为 `mysql`、`redis`，无需也不得新增 Docker Compose 配置。

## 测试

修改后端后运行：

```bash
cd backend
mvn test
```

`ApiIntegrationTest` 会启动完整 Spring Boot 应用，并连接 `.env.test` 指向的 MySQL 和 Redis，而不是创建 Testcontainers。测试会重建 ID 为 `1001`、`1002` 的固定测试用户并清理相关业务数据，因此 `.env.test` 必须指向专用的本地测试数据库。

`PasswordHashGeneratorTest` 是有意保留的 BCrypt 辅助工具，会在测试输出中打印一个强度为 12 的哈希。它不属于业务断言；需要单独运行时使用：

```bash
cd backend
mvn -Dtest=PasswordHashGeneratorTest test
```

## 数据库迁移

应用启动时由 Flyway 自动读取 `backend/src/main/resources/db/migration`。V1–V3 已是迁移历史，禁止修改或删除；任何结构调整都必须新增后续版本的迁移文件。

金额在 Java 和 MySQL 中必须使用 `BigDecimal`/`DECIMAL`，禁止使用浮点类型。

## 生产部署

生产服务器上的 `.env.production` 必须填入真实配置，但真实生产密钥不能提交到 Git。部署步骤：

1. 创建生产私有网络并启动持久化数据服务：

   ```bash
   cd backend
   ./scripts/start-production-datastores.sh .env.production
   ```

2. 构建并推送带不可变标签的 API 镜像，例如 Git SHA。
3. 部署 API：

   ```bash
   ./scripts/deploy-production-api.sh .env.production registry.example.com/pixel-ledger-api:<git-sha>
   ```

4. 使用 Caddy 或 Nginx 将 HTTPS 请求转发到 `127.0.0.1:8080`。

生产 MySQL、Redis 不映射宿主机端口，只允许 API 通过私有 Docker 网络访问。Redis 有意采用无密码设计，因此绝不能暴露到公网。MySQL 数据卷需要每日备份。

## 仓库约定

- `.env.test` 与 `.env.production` 当前按项目决定保留现有跟踪方式；不要擅自调整该策略。
- `.env.test` 只能保存可替换的本地凭据；生产服务器中的真实 `.env.production` 内容不得提交。
- 不提交 `.DS_Store`、IDE 私有配置、`backend/target` 或生成的 Xcode 工程。
- 更完整的实现约束见 `AGENTS.md`。
