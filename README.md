# 图书管理系统（Java 版）

[![CI](https://github.com/12122-s/booksysteam/actions/workflows/ci.yml/badge.svg)](https://github.com/12122-s/booksysteam/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

一个面向小型图书室（学校、社区、企业阅览室）的**双端管理系统**：

- **管理员端** —— 图书、读者、借还、预约排队、罚金台账、统计报表、数据分析、批量导入导出、数据备份、操作日志；
- **读者端** —— 自助办卡、卡号/手机号登录、找书、申请借阅、续借、写书评、在线缴费申请、找回密码。

后端 Java 17 + Spring Boot 3，服务端渲染用 Thymeleaf，数据访问用 JdbcTemplate 直写 SQL，
另有一套 JWT 认证的 REST API；一条 `docker compose up` 即可完整跑起来。

<p align="center">
  <img src="src/main/resources/static/img/login-illustration.svg" alt="登录页插图" width="420">
</p>

---

## 目录

- [功能一览](#功能一览)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [环境变量](#环境变量)
- [项目结构](#项目结构)
- [系统架构](#系统架构)
- [REST API（v1）](#rest-apiv1)
- [测试](#测试)
- [几个实现上的取舍](#几个实现上的取舍)
- [部署](#部署)
- [注意事项](#注意事项)

---

## 功能一览

| 模块 | 主要能力 |
| --- | --- |
| 图书管理 | 增删改查、分类筛选、搜索、上下架、封面图片、批量导入 Excel/CSV、借阅历史 |
| 读者管理 | 增删改查、办卡审核、状态管理、重置密码、借阅历史 |
| 借阅管理 | 借书、还书、续借（可自选天数）、逾期自动判罚 |
| 借阅申请 | 读者在线申请 → 管理员办理借出；无库存时转为排队预约并自动通知 |
| 罚金 | 自动计算、应收/已收/未收三项分开统计、减免、读者端在线缴费申请 |
| 读者端 | 自助办卡、卡号或手机号登录、找书、借书、续借、书评、改密码 |
| 找回密码 | 邮箱验证码（配置 SMTP 后自动发送）或管理员人工转告 |
| 消息通知 | 到期提醒、逾期提醒、预约到馆通知（邮件，可开关） |
| 统计报表 | 分类分布、借阅排行、6 个月借阅趋势、库存概览、罚金台账 |
| 数据分析 | 借阅趋势、分类热度、读者活跃度、按时归还率、借阅时长分布、图书周转率 |
| 图书推荐 | 读者端「猜你可能想看」：按分类偏好推荐；新读者走热门兜底 |
| 数据安全 | 每日自动备份、导出 CSV、导出完整 .sql、从备份恢复 |
| 其他 | 操作日志、打印借书单/催还单/书标、公告与规则页 |

规模：约 **7400 行** Java 主代码、**530 行**单元测试、**35 个** Thymeleaf 页面、**9 张**数据表、**23 个**控制器。

---

## 技术栈

| 层次 | 用了什么 |
| --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.3（Spring MVC + Thymeleaf 服务端渲染） |
| 数据访问 | Spring JDBC（JdbcTemplate）+ 手写 SQL，不用 ORM，便于控制查询和索引 |
| 数据库 | MySQL 8（utf8mb4），启动时自动建库建表、补字段、补索引 |
| 缓存 | 进程内 TTL 缓存（报表 60s / 分析 120s / 推荐 300s） |
| 密码哈希 | PBKDF2-SHA256 与 scrypt 双算法（BouncyCastle）；哈希串自带算法名、盐与参数，可平滑升级算法 |
| 认证 | 自写 `HandlerInterceptor`（会话 + CSRF）；REST 侧用 JWT（jjwt HS384） |
| 接口文档 | springdoc-openapi + Swagger UI |
| Excel 导入 | Apache POI |
| 邮件 | Spring Mail（SMTP 参数存数据库，页面上可改） |
| 构建 | Maven（`mvnw` 或本机 Maven 均可） |
| 测试 | JUnit 5（单元测试不依赖数据库）+ requests 端到端检查脚本 |
| 部署 | Docker Compose（应用 + MySQL）、systemd + nginx |
| CI | GitHub Actions |

---

## 快速开始

### 方式一：Docker Compose（推荐，一条命令）

`docker-compose.yml` 自带一个独立的 MySQL 容器，数据落在宿主机的 `./dbdata` 目录里，
看得见、好备份，也不会碰到你机器上已有的数据库。

```bash
# 需要先装好 Docker
docker compose up -d --build

# 看启动日志（等 MySQL 健康检查通过后应用才会启动）
docker compose logs -f library-java
```

浏览器打开 **http://localhost:8081**，管理员账号 `admin` / 初始密码 `123456`。

停止：`docker compose down`（数据仍保留在 `./dbdata`；加 `-v` 才会连数据卷一起删）。

> 想改数据库密码：复制 `.env.example` 为 `.env`，在里面设置 `MYSQL_ROOT_PASSWORD`、
> `LIB_DB_PASSWORD`。注意 MySQL 的账号密码**只在数据目录第一次初始化时生效**，
> 对已经存在的 `./dbdata` 改密码不会生效。

### 方式二：本地直接跑（开发用）

1. 装好 **JDK 17+** 和 **MySQL 8**，确保 MySQL 服务已启动；
2. 按需修改数据库连接：改 `src/main/resources/application.yml`，或设置环境变量
   `LIB_DB_HOST / LIB_DB_PORT / LIB_DB_USER / LIB_DB_PASSWORD / LIB_DB_NAME`；
3. 构建并启动：

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

浏览器打开 **http://localhost:8080**，管理员账号 `admin` / 初始密码 `123456`。

> 首次启动会**自动建库（默认 `library_system`）、建表、写入默认设置**，
> 不需要手动执行任何 SQL。

打包成可执行 jar（约 50 MB，自带 Tomcat，不需要另装）：

```bash
mvnw.cmd package -DskipTests          # Windows
./mvnw package -DskipTests            # Linux / macOS
java -jar target/booksysteam-1.0.0.jar
```

### 方式三：部署到 Linux 服务器

见 [docs/虚拟机部署说明.md](docs/虚拟机部署说明.md)，覆盖裸机 systemd、Docker Compose、
nginx 反向代理三种方式。

---

## 环境变量

所有配置都有可用的开发默认值，生产环境请用环境变量覆盖。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `LIB_DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `LIB_DB_PORT` | `3306` | MySQL 端口 |
| `LIB_DB_USER` | `root` | 数据库账号 |
| `LIB_DB_PASSWORD` | `123456` | 数据库密码 —— **生产环境必改** |
| `LIB_DB_NAME` | `library_system` | 库名（Docker 部署用的是 `library_java`） |
| `SERVER_PORT` | `8080` | HTTP 端口（Docker 部署用的是 `8081`） |
| `LIB_JWT_SECRET` | 内置开发值 | JWT 签名密钥，**至少 32 字符，生产环境必改** |
| `LIB_JWT_TTL_MINUTES` | `120` | JWT 有效期（分钟） |

模板见 [.env.example](.env.example)。另外封面/收款码上传目录（`uploads/`）和自动备份目录
（`backups/`）固定建在**进程工作目录**下，用 systemd 部署时注意 `WorkingDirectory`。

---

## 项目结构

```
booksysteam/
├── src/main/java/com/library/
│   ├── LibraryApplication.java     启动入口（@SpringBootApplication + @EnableScheduling）
│   ├── config/                     数据库初始化器（自动建库建表）、OpenAPI 配置
│   ├── controller/                 页面控制器，一个业务模块一个类
│   │   └── api/                    REST API v1 控制器 + 统一异常处理
│   ├── service/                    业务逻辑（借阅规则、罚金、提醒、备份、导入…）
│   ├── dao/                        数据访问（JdbcTemplate + 手写 SQL）
│   ├── entity/                     实体类，与数据库表一一对应
│   ├── dto/                        表单对象（@Valid 校验）
│   ├── web/                        拦截器、CSRF、flash 消息、全局模板变量、错误页
│   └── util/                       日期/密码/JWT/CSV/SQL 转义/TTL 缓存工具
├── src/main/resources/
│   ├── application.yml             配置（数据库连接用 LIB_DB_* 覆盖）
│   ├── templates/                  Thymeleaf 模板（fragments/ 里是布局片段）
│   └── static/                     样式、脚本、插图
├── src/test/java/                  单元测试（43 个，不需要数据库）
├── tests/                          端到端检查脚本（requests，CI 里跑）
│   ├── smoke_test.py               冒烟：公开页 / 管理端 / 导出 / 写操作 / REST API
│   └── reader_e2e.py               读者端全流程：办卡 → 审核 → 借还 → 续借 → 书评
├── docs/虚拟机部署说明.md            服务器部署指南
├── deploy/start.sh                 服务器裸机启动脚本（不用 Docker 时）
├── .github/workflows/ci.yml        GitHub Actions
├── Dockerfile                      jar → 运行镜像
├── docker-compose.yml              应用 + MySQL 一键编排
├── .env.example / .dockerignore
└── pom.xml
```

---

## 系统架构

```
浏览器 → 内嵌 Tomcat (8080) → Spring MVC Controller → Service → DAO(JdbcTemplate) → MySQL
                                                    ↓
                                          报表/分析/推荐走 TTL 缓存
```

标准三层架构，`Controller → Service → DAO` 单向依赖。两个入口共用同一套 Service：

- **服务端渲染**：Thymeleaf 模板 + 会话登录（`AuthInterceptor` 做路径分流与 CSRF 校验）；
- **REST API**：`/api/v1/...` + JWT（`ApiAuthInterceptor` 做令牌校验与角色鉴权）。

---

## REST API（v1）

JWT 认证的 REST 接口，与服务端渲染页面共用同一套 Service 层：

| 方法 | 路径 | 身份 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 公开 | 登录换 token（管理员账号或读者卡号/手机号） |
| GET | `/api/v1/me` | 登录 | 当前身份（role/name/readerId） |
| GET | `/api/v1/books` | 登录 | 图书列表（q/category/available/page） |
| GET | `/api/v1/books/{id}` | 登录 | 图书详情 |
| GET | `/api/v1/readers` | admin | 读者列表 |
| GET | `/api/v1/borrows` | admin | 借阅记录（status=borrowing/overdue/returned） |
| POST | `/api/v1/borrows` | admin | 办理借出 |
| POST | `/api/v1/borrows/{id}/return` | admin | 办理还书 |
| POST | `/api/v1/borrows/{id}/renew` | 登录 | 续借（读者只能续自己的） |
| GET | `/api/v1/stats/overview` | admin | 库存与罚金总览 |

用法：

```bash
# 登录换 token
curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"account":"admin","password":"123456"}'

# 带 token 调用
curl http://localhost:8080/api/v1/books -H "Authorization: Bearer <token>"
```

- 交互式文档：**http://localhost:8080/swagger-ui.html**（点右上角 Authorize 填 token）
- 错误统一返回 `{"code":401,"message":"..."}`：401 = 未登录/token 失效，403 = 角色权限不足
- 密钥：`LIB_JWT_SECRET`（至少 32 字符，生产环境务必覆盖默认值）；有效期默认 120 分钟
- 登录接口与页面共用同一套限流：15 分钟内失败 5 次锁定

---

## 测试

```bash
# 单元测试：43 项，不需要数据库
# 覆盖密码哈希、CSV/GBK 解码、日期容错、SQL 转义、JWT、登录限流，
# 以及借阅业务规则（含并发扣减回归：借出必须走原子扣减，防止超卖）
mvnw.cmd test        # Windows
./mvnw test          # Linux / macOS
```

每次 push / 提 PR 都会由 GitHub Actions 跑两层检查（见 [.github/workflows/ci.yml](.github/workflows/ci.yml)）：

1. **单元测试**：`./mvnw -B test`，43 项，不连数据库；
2. **端到端检查**：GitHub 起一个真实 MySQL 容器 → 打包 → 启动应用 → 跑
   `tests/smoke_test.py`（39 项冒烟）与 `tests/reader_e2e.py`（22 项读者端全流程）。
   部分检查随「办卡是否需审核」「借阅是否需申请」等系统设置走不同分支。

端到端脚本也可以本地手动跑（需要应用已启动）：

```bash
pip install requests
python tests/smoke_test.py http://127.0.0.1:8080 123456 admin
python tests/reader_e2e.py
```

---

## 几个实现上的取舍

**1. 为什么手写 SQL，不用 ORM（MyBatis/JPA）？**
这个系统的查询以「多表 JOIN + 分组聚合」为主，SQL 直写更好控制和优化，
报表场景也不容易生成低效语句。`JdbcTemplate` + `BeanPropertyRowMapper`
已经把样板代码降得很低，实体类用 Plain POJO。

**2. 为什么不用 Spring Security？**
登录模型是「管理员 + 读者」双身份共用一个会话（还能同浏览器双登录、系统重启全员下线），
套 Spring Security 的 Filter 链反而绕。所以自写了 `AuthInterceptor`：
路径前缀分流 + CSRF 校验 + 启动戳失效，逻辑一目了然。
REST 侧同理，用 `ApiAuthInterceptor` 做令牌校验与角色鉴权。

**3. 为什么邮件发送参数存数据库而不是 application.yml？**
发件邮箱要在管理页面上随时改（换授权码不用改配置文件重启），
所以每次发送时按当前设置动态构建 `JavaMailSender`。

**4. 为什么自动任务用 `@Scheduled`？**
到期提醒（每 30 分钟）和每日备份（每 30 分钟检查当天是否已备）都是单实例任务；
单实例部署下 `@Scheduled` 最简单可靠。要多实例部署时需先把这两个任务拆出去，
否则同一封提醒邮件会重复发。

**5. 日期为什么存字符串？**
`YYYY-MM-DD` 存库，和表单、Excel 导入导出格式完全一致，不用来回转换。
`DateUtil.parseDate()` 对脏数据容错返回 `null`。

**6. 借出为什么必须是「条件 UPDATE + 判断影响行数」？**
原来的写法是「先查库存、再扣减」，这在并发借最后一册时会 check-then-act 竞态：
两个请求都读到库存 1、都通过校验，`available_copies` 被扣成负数并多写一条借阅记录。
改成 `UPDATE ... SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`
后，扣减本身是原子的，影响行数为 0 就直接返回失败，配合事务保证「扣了库存就一定写出借阅记录」。
并发回归测试锁定了这个行为。

---

## 部署

| 场景 | 做法 |
| --- | --- |
| 本地试用 / 演示 | `docker compose up -d --build` → http://localhost:8081 |
| 服务器部署（推荐） | 同上；数据在 `./dbdata`，上传和备份挂到宿主机目录 |
| 服务器裸机（不用 Docker） | 见 [docs/虚拟机部署说明.md](docs/虚拟机部署说明.md)，用 `deploy/start.sh` 或 systemd |
| 对外用 80 端口 | nginx 反代到应用端口，配置模板见部署文档 |

**让它常驻**：建议用 systemd 托管（开机自启 + 崩溃自动拉起），
配置模板见 [docs/虚拟机部署说明.md](docs/虚拟机部署说明.md)。

---

## 注意事项

- **不要提交真实密码**：`.env` 已被 `.gitignore` 忽略，提交前请确认没把真实凭据写进
  `application.yml` / `docker-compose.yml`；
- **首次部署后立刻修改管理员密码**（`admin` / `123456` 是公开的初始值），
  并检查「系统设置」里的借期、罚金等规则；
- **生产环境务必覆盖 `LIB_JWT_SECRET`**，并确认 SMTP 授权码只存在数据库设置里；
- 定时任务与 `@Scheduled` 和进程绑定，**同一套数据库不要同时跑多个实例**，
  否则会重复发提醒邮件、重复备份。
