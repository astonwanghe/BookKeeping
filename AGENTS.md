# Pixel Ledger 协作规范

## 项目范围

Pixel Ledger 是仅供两人使用的私有记账系统，由 SwiftUI iOS 客户端和自建 Spring Boot API 组成。不考虑 App Store 发布，不提供公开注册、共享账本、资金账户、转账、报销、资产负债等功能。

新增功能前先确认它是否属于上述范围。不要为了假设中的公开用户、多租户或平台化需求引入额外抽象。

## 技术选型

- 后端：Java 25、Spring Boot 4、Spring Security、MyBatis、Flyway。
- 存储：MySQL 9.7、Redis 8.8。
- 客户端：SwiftUI、Swift 6，保持温馨复古的像素风格。
- 金额必须使用 Java `BigDecimal`、Swift `Decimal` 和 MySQL `DECIMAL`，禁止使用浮点类型。
- 不引入 JPA 或 Spring Data JPA；关系型数据库访问只使用 MyBatis。
- 不重新引入 Docker Compose。API、MySQL、Redis 在本地和生产环境中均独立运行。
- 不重新引入 Testcontainers。集成测试连接 `.env.test` 配置的独立本地 MySQL、Redis。

## 后端分层

- `domain/`：供 MyBatis 使用的可变 Java Bean DO。
- `dto/`：请求和响应 DTO；API 契约优先使用不可变 `record`。
- `mapper/`：数据库访问；只使用明确的 DO、DTO 或基础类型作为入参与返回值，禁止使用 `Map<String, Object>`。
- `service/`：业务流程、跨 Mapper 操作和外部服务协作。
- `api/`：输入校验、鉴权以及 DTO 与 DO 的转换。
- `security/`、`config/`：认证过滤器和应用配置，避免把业务逻辑放入配置类。

Mapper 使用接口上的 `@Mapper` 注册，不要同时添加重复的全局 `@MapperScan`。配置文件只保留项目主动选择的行为，不重复声明 Spring Boot/Flyway 默认值。

## API 与安全

- 除非明确说明并同步客户端，否则保持既有 API 响应字段兼容。
- 所有业务查询和写入必须在服务端基于已认证用户 ID 限制范围，不能依赖客户端传入用户 ID。
- 不提供创建用户 API。两个用户直接预置到 `t_user`。
- 密码必须使用与 `SecurityConfig` 一致的 BCrypt 强度，目前为 12。
- 刷新令牌只存储 SHA-256 哈希；邮箱验证和密码重置的一次性令牌存入 Redis，并设置有限 TTL。
- Redis 限流、一次性令牌等键必须带清晰命名空间，测试不能清理无关业务键。
- Redis 无密码只适用于私有网络，不得映射到生产宿主机或公网。

## 数据库与 Flyway

- 业务表统一使用 `t_` 前缀；新增表和新增字段必须添加中文 `COMMENT`。
- 用户不允许物理删除；关联 `t_user` 的外键禁止使用 `ON DELETE CASCADE`。
- V1–V3 已成功执行并成为不可修改历史。禁止编辑、重命名或删除现有迁移；结构变更从新的有序版本开始。
- 修改迁移 SQL 时，先在空的临时数据库验证，再应用到共享环境。
- 外键与唯一约束必须显式定义。
- 分类为空的总预算使用 `IFNULL(category_id, 0)` 函数唯一索引，不增加对业务无意义的辅助字段。

## iOS 工程

- `ios/project.yml` 是唯一的 Xcode 工程配置源。
- `ios/PixelLedger.xcodeproj` 是生成物，禁止提交、手工编辑或在评审中保留其差异。
- 首次检出或修改 `project.yml` 后，在 `ios/` 目录运行 `xcodegen generate`。
- API 响应模型优先声明为 `Decodable`；只需编码的请求载荷声明为 `Encodable`，不要无差别使用 `Codable`。
- 客户端模型可以只声明当前实际读取的响应字段；服务端仍需遵守 API 字段兼容要求。
- Token 只保存在 Keychain；内存令牌与 Keychain 状态必须同步清理。
- Debug 和 Release 的 API 地址统一在 `project.yml` 管理，不在 Swift 源码中散落环境判断。

## 本地开发与测试

- IDEA 运行 `PixelLedgerApplication` 时使用 `local` Profile，工作目录为仓库根目录。
- 本地 Docker 容器名称为 `mysql`、`redis`；API 连接 `localhost:3306` 与 `localhost:6379`。
- `.env.test` 只允许使用本地、可替换的测试凭据，不能复用生产密钥。
- `.env.test` 与 `.env.production` 当前按项目决定维持现有 Git 跟踪方式；除非用户明确要求，不要擅自取消跟踪或改成其他环境文件体系。
- 仓库中的 `.env.production` 只能保留占位或非敏感值；真实生产值只填写在服务器工作副本中，不得提交。
- 后端改动后运行 `cd backend && mvn test`。
- `ApiIntegrationTest` 使用真实本地 MySQL/Redis，会重建固定测试用户 `1001`、`1002` 并清理其关联数据；必须使用专用测试数据库。
- `PasswordHashGeneratorTest` 是项目明确保留的 BCrypt 辅助工具。除非用户明确要求，不要将其当作无效测试删除。
- 修改 iOS 源码或 `project.yml` 后，通过 XcodeGen 生成临时工程并执行至少一次 Debug 构建验证；验证后不要把生成工程加入 Git。

## 脚本与部署

- Shell 脚本使用 `set -euo pipefail`，修改后运行 `bash -n`。
- 容器只接收实际需要的环境变量；不要把包含数据库、JWT、SMTP 凭据的完整环境文件传给无需这些配置的 Redis。
- 本地 MySQL、Redis 可映射到 `127.0.0.1`；生产 MySQL、Redis 禁止映射宿主机端口。
- 生产 API 通过 Caddy 或 Nginx 以 HTTPS 对外提供服务。
- 使用不可变 API 镜像标签；每日备份 MySQL 数据卷。

## 仓库整洁

- 不提交 `.DS_Store`、`.idea/`、`.vscode/`、`backend/target/`、Swift 构建目录或 Xcode 用户状态。
- 删除代码前先确认没有框架隐式引用、API 契约影响或 Flyway 历史约束。
- 不为薄封装而直接破坏现有分层；只有在职责确实重复时才合并类或目录。
- 保持改动聚焦，保留用户已有且与当前任务无关的工作区变更。
