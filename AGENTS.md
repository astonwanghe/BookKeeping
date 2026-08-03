# Pixel Ledger 协作规范

## 项目范围

Pixel Ledger 是仅供两人使用的私有记账系统，由 SwiftUI iOS 客户端和自建 Spring Boot API 组成。不考虑 App Store 发布，不提供公开注册、共享账本、资金账户、转账、报销、资产负债等功能。

## 技术选型

- 后端：Java 25、Spring Boot 4、Spring Security、MyBatis、Flyway。
- 存储：MySQL 9.7、Redis 8.8；金额必须使用 `DECIMAL`，禁止使用浮点类型。
- 客户端：SwiftUI，保持温馨复古的像素风格。
- 不引入 JPA 或 Spring Data JPA；关系型数据库访问只使用 MyBatis。
- 不重新引入 Docker Compose。本地 API 通过 IDEA 直接运行；MySQL、Redis 为独立 Docker 容器。生产环境中 API、MySQL、Redis 同样独立部署。

## 后端代码规范

- 分层必须明确：
  - `domain/`：供 MyBatis 使用的可变 Java Bean DO。
  - `dto/`：请求和响应 DTO；API 契约优先使用不可变的 `record`。
  - `mapper/`：只使用明确的 DO、DTO 或基础类型作为入参与返回值；禁止使用 `Map<String, Object>`。
  - `api/`：负责输入校验、鉴权以及 DTO 与 DO 的转换。
- 除非明确说明并同步客户端，否则保持既有 API 响应字段兼容。
- 所有数据查询和写入必须在服务端基于已认证用户 ID 限制范围。
- 不提供创建用户 API。两个用户通过数据库直接预置到 `t_user`；密码字段必须存储与应用配置强度一致的 BCrypt 哈希值。

## 数据库与 Flyway

- 业务表统一使用 `t_` 前缀；新增表和新增字段必须添加中文 `COMMENT`。
- 用户不允许物理删除；关联 `t_user` 的外键禁止使用 `ON DELETE CASCADE`。
- 不得修改已经成功执行的 Flyway 迁移；结构变更必须新增有序的 `V<n>__description.sql` 文件。
- 在 V1 尚未成功执行前可以修正 V1；一旦 Flyway 记录为成功，只能新增后续迁移。
- 外键与唯一约束必须显式定义。分类为空的总预算使用 `IFNULL(category_id, 0)` 函数唯一索引，不增加对业务无意义的辅助字段。

## 本地开发

- IDEA 运行 `PixelLedgerApplication` 时使用 `local` Profile，工作目录为仓库根目录。
- 本地 Docker 容器名称为 `mysql`、`redis`；API 连接 `localhost:3306` 与 `localhost:6379`。
- `.env.test` 仅用于本地；不要将真实生产密钥提交到代码库。`.env.production` 只在生产服务器填写。
- 后端改动后运行 `mvn test`。修改迁移 SQL 时，先在空的临时数据库验证，再应用到共享环境。

## 生产安全

- 生产 MySQL、Redis 不得映射宿主机端口；仅 API 通过 Caddy 或 Nginx 以 HTTPS 对外提供服务。
- Redis 设计为无密码，因此必须始终位于私有 Docker 网络，不能暴露到公网。
- 使用不可变 API 镜像标签；密钥只放在服务器环境文件；每日备份 MySQL 数据卷。
