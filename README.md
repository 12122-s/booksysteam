# 图书管理系统（Java 版）

面向小型图书室（学校、社区、企业阅览室）的双端管理系统：**管理员端**管图书、读者、借还与统计，**读者端**支持自助办卡、找书、借阅与书评。

[![CI](https://github.com/12122-s/booksysteam/actions/workflows/ci.yml/badge.svg)](https://github.com/12122-s/booksysteam/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)

<p align="center">
  <img src="src/main/resources/static/img/login-illustration.svg" alt="登录页插图" width="380">
</p>

## 功能

| 端 | 主要能力 |
| --- | --- |
| 管理员端 | 图书 / 读者管理、借书还书续借、借阅申请与预约排队、罚金台账、统计报表、数据分析、批量导入导出、数据备份与恢复、操作日志、打印单据 |
| 读者端 | 自助办卡、卡号或手机号登录、找书、申请借阅、续借、写书评、在线缴费申请、找回密码 |

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.3（Spring MVC + Thymeleaf 服务端渲染） |
| 数据访问 | Spring JDBC（JdbcTemplate）+ 手写 SQL，不用 ORM |
| 数据库 | MySQL 8（utf8mb4），启动时自动建库建表补字段，不需要手动执行 SQL |
| 认证 | 页面用会话 + CSRF；REST API 用 JWT（HS384） |
| 其他 | Apache POI（Excel 导入）、Spring Mail（SMTP 参数页面可改）、springdoc-openapi（Swagger UI）、进程内 TTL 缓存 |
| 部署 | 可执行 jar（内嵌 Tomcat，服务器不用另装），systemd 常驻，nginx 反代 |

## 快速开始

前置只要两样：**JDK 17** 和 **MySQL 8**。

```bash
# 启动（默认连本机 MySQL：root / 123456，库不存在会自动创建）
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # Linux / macOS
```

浏览器打开 http://localhost:8080，管理员账号 `admin`，初始密码 `123456`。

打包成可执行 jar：

```bash
mvnw.cmd package -DskipTests    # Windows
./mvnw package -DskipTests      # Linux / macOS
java -jar target/booksysteam-1.0.0.jar
```

部署到 Linux 服务器：见 [docs/服务器部署说明.md](docs/服务器部署说明.md)。

## 配置

所有配置都有可用的开发默认值，生产环境请用环境变量覆盖：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `LIB_DB_HOST` / `LIB_DB_PORT` | `127.0.0.1` / `3306` | MySQL 地址 |
| `LIB_DB_USER` / `LIB_DB_PASSWORD` | `root` / `123456` | 数据库账号（**生产必改**） |
| `LIB_DB_NAME` | `library_system` | 库名 |
| `SERVER_PORT` | `8080` | HTTP 端口 |
| `LIB_JWT_SECRET` | 内置开发值 | JWT 签名密钥，至少 32 字符（**生产必改**） |
| `LIB_JWT_TTL_MINUTES` | `120` | token 有效期（分钟） |

封面上传目录 `uploads/` 和自动备份目录 `backups/` 固定建在**进程工作目录**下，用 systemd 部署时注意 `WorkingDirectory`。

## 项目结构

```
src/main/java/com/library/
├── config/        启动初始化（自动建库建表）、OpenAPI 配置
├── controller/    页面控制器；api/ 下是 REST API v1
├── service/       业务逻辑（借阅规则、罚金、提醒、备份、导入…）
├── dao/           数据访问（JdbcTemplate + 手写 SQL）
├── entity/ dto/   实体类与表单对象
├── web/           拦截器、CSRF、全局模板变量、错误页
└── util/          日期、密码、JWT、CSV、SQL 转义、TTL 缓存
src/main/resources/    application.yml、Thymeleaf 模板、静态资源
src/test/java/         单元测试（43 项，不依赖数据库）
tests/                 端到端检查脚本（requests，本地或 CI 里跑）
docs/                  服务器部署说明
deploy/start.sh        服务器启动 / 停止脚本
```

## REST API

JWT 认证，与服务端页面共用同一套 Service 层：

| 方法 | 路径 | 身份 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 公开 | 登录换 token（管理员或读者卡号 / 手机号） |
| GET | `/api/v1/me` | 登录 | 当前身份 |
| GET | `/api/v1/books`、`/api/v1/books/{id}` | 登录 | 图书列表 / 详情 |
| GET | `/api/v1/readers` | admin | 读者列表 |
| GET | `/api/v1/borrows` | admin | 借阅记录 |
| POST | `/api/v1/borrows`、`/api/v1/borrows/{id}/return`、`/api/v1/borrows/{id}/renew` | admin | 借出 / 归还 / 续借 |
| GET | `/api/v1/stats/overview` | admin | 库存与罚金总览 |

交互式文档：`http://localhost:8080/swagger-ui.html`（点 Authorize 填 token）。

## 测试

```bash
mvnw.cmd test        # Windows
./mvnw test          # Linux / macOS
```

43 项单元测试不依赖数据库，覆盖密码哈希、日期容错、SQL 转义、JWT、登录限流，以及借阅业务规则（含并发扣减回归）。

GitHub Actions 每次 push 自动跑两层：单元测试，以及起一个真实 MySQL 后的端到端检查（`tests/smoke_test.py` 39 项冒烟 + `tests/reader_e2e.py` 22 项读者端全流程）。

## 注意事项

- 初始管理员 `admin / 123456` 是公开值，**首次部署后立刻修改**，并检查借期、罚金等规则；
- 生产环境务必覆盖 `LIB_DB_PASSWORD` 和 `LIB_JWT_SECRET`；
- 定时任务（到期提醒、每日备份）跟着进程走，**同一套数据库只跑一个实例**，否则会重复发邮件、重复备份；
- 不要提交真实密码：配置文件和部署脚本里只留占位值。
