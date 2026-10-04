# 图书管理系统（Java 版）

这是一个面向小型图书室的**双端管理系统**：管理员端负责图书、读者、借还、罚金、报表；读者端可以自己查书、申请借阅、预约、写书评。

> 本项目是从同功能的 Python/Flask 版用 **Java 17 + Spring Boot 3** 重写的，路由、页面、数据库表结构、密码哈希全部与 Python 版兼容，可以直连原来那套 MySQL 数据库无缝升级。

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
| 图书推荐 | 读者端"猜你可能想看"：按分类偏好推荐；新读者走热门兜底 |
| 数据安全 | 每日自动备份、导出 CSV、导出完整 .sql、从备份恢复（与 Python 版备份文件互通） |
| 其他 | 操作日志、打印借书单/催还单/书标、公告与规则页 |

---

## 技术栈

| 层次 | 用了什么 |
| --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.3（Spring MVC + Thymeleaf 服务端渲染） |
| 数据访问 | Spring JDBC（JdbcTemplate）+ 手写 SQL，不用 ORM，便于控制查询和索引 |
| 数据库 | MySQL 8（utf8mb4），启动时自动建库建表、补字段、补索引 |
| 缓存 | 进程内 TTL 缓存（报表 60s / 分析 120s / 推荐 300s） |
| 密码哈希 | PBKDF2/Scrypt，**完全兼容 Python werkzeug 的哈希格式** |
| Excel 导入 | Apache POI |
| 邮件 | Spring Mail（SMTP 参数存数据库，页面上可改） |
| 构建 | Maven（`mvnw` 或本机 Maven 均可） |
| 单元测试 | JUnit 5（不需要数据库即可运行） |

---

## 系统架构

```
浏览器 → 内嵌 Tomcat (8080) → Spring MVC Controller → Service → DAO(JdbcTemplate) → MySQL
                                                    ↓
                                          报表/分析/推荐走 TTL 缓存
```

标准三层架构：

```
src/main/java/com/library/
├── LibraryApplication.java   启动入口（@SpringBootApplication + @EnableScheduling）
├── config/                   数据库初始化器（自动建库建表）
├── controller/               Web 控制器（一个业务模块一个类）
├── service/                  业务逻辑（借阅规则、罚金计算、提醒、备份、导入…）
├── dao/                      数据访问（JdbcTemplate + 手写 SQL + BeanPropertyRowMapper）
├── entity/                   实体类（与数据库表一一对应）
├── web/                      登录拦截器、CSRF、flash 消息、全局模板变量、错误页
└── util/                     日期/密码/CSV/SQL 转义/TTL 缓存工具
src/main/resources/
├── application.yml           配置（数据库连接用环境变量 LIB_DB_* 覆盖）
├── templates/                Thymeleaf 模板（fragments/ 里是布局片段）
└── static/                   样式、脚本、上传文件
```

---

## 快速开始

### 方式一：本地直接跑（Windows）

1. 装好 JDK 17+ 和 MySQL 8，确保 MySQL 服务已启动
2. 修改数据库连接（可选）：`src/main/resources/application.yml`，或设置环境变量 `LIB_DB_HOST / LIB_DB_PORT / LIB_DB_USER / LIB_DB_PASSWORD / LIB_DB_NAME`
3. 构建并启动：

```bash
mvn.cmd spring-boot:run
# 或打包后运行：
mvn.cmd package -DskipTests
java -jar target/booksysteam-1.0.0.jar
```

4. 浏览器打开 http://localhost:8080
5. 管理员账号 `admin`，初始密码 `123456`

> 首次启动自动建库（`library_system`）、建表、写入默认设置，不需要手动执行任何 SQL。

### 方式二：部署到 Linux 服务器

```bash
mvn package -DskipTests
scp target/booksysteam-1.0.0.jar 用户名@服务器IP:~/library/
ssh 用户名@服务器IP
LIB_DB_HOST=127.0.0.1 LIB_DB_PASSWORD=你的密码 java -jar booksysteam-1.0.0.jar
```

建议用 systemd 托管进程、nginx 做反向代理（静态资源 + 超时控制），配置方法与 Python 版部署文档中的 nginx 部分相同。

---

## 与 Python 版的兼容性

这一版刻意做到了"数据层完全互通"，方便平滑升级：

- **数据库表结构一致**：books / readers / borrow_records / settings / password_resets / reminder_logs / operation_logs / book_reviews / borrow_requests，九张表一模一样；启动时自动补齐 Python 版后来加的列和索引。
- **密码哈希互通**：Python 版存的 `scrypt:...` / `pbkdf2:sha256:...` 哈希，Java 版能直接校验（有单元测试锁定这个行为）；Java 新写的哈希 Python 版也能校验。
- **备份文件互通**：Java 版导出的 .sql 备份转义规则与 Python 版逐字一致，两边互相能恢复。
- **上传目录约定一致**：封面在 `uploads/covers/`，收款码在 `uploads/pay/`。

**有一处刻意的小改动**：Python 版的缓存支持 Redis（单实例下其实很少用上），Java 版改为进程内 TTL 缓存，行为效果一致（报表容忍 60 秒旧数据，换取大量重复计算）。将来要多实例部署时再引入 Redis。

---

## 测试

```bash
# 单元测试：不需要数据库，覆盖密码兼容、CSV/GBK 解码、日期容错、SQL 转义
mvn test
```

---

## 几个实现上的取舍

**1. 为什么继续手写 SQL，不用 ORM（MyBatis/JPA）？**
和 Python 版同一取向：查询以"多表 JOIN + 分组统计"为主，SQL 直写、好优化、好索引。Java 侧用 `JdbcTemplate` + `BeanPropertyRowMapper` 把样板代码降到最低，实体类用 Plain POJO。

**2. 为什么不用 Spring Security？**
登录模型是"管理员 + 读者"双身份共用一个会话（还能同浏览器双登录、系统重启全员下线），用 Spring Security 的 Filter 链反而绕。所以按 Python 版的逻辑自写了 `AuthInterceptor`：路径前缀分流 + CSRF 校验 + 启动戳失效，逻辑一目了然。

**3. 为什么邮件发送参数存数据库而不是 application.yml？**
发件邮箱要在管理页面上随时改（换授权码不用改配置文件重启），所以每次发送时按当前设置动态构建 `JavaMailSender`。

**4. 为什么自动任务用 @Scheduled？**
到期提醒（每 30 分钟）和每日备份（每 30 分钟检查当天是否已备）与 Python 版的线程方案一致；单实例部署下 `@Scheduled` 最简单可靠。要多实例部署时需先把这两个任务拆出去（与 Python 版同样的约束）。

**5. 日期为什么存字符串？**
与 Python 版一致：`YYYY-MM-DD` 存库，和表单、Excel 导入导出格式完全一致，不用来回转换。`DateUtil.parseDate()` 对脏数据容错返回 null。

---

## 后续计划

- [ ] 抽取 RESTful API（`/api/v1/...`）+ JWT 认证 + OpenAPI 文档
- [ ] 打包 Dockerfile + docker-compose（数据库/应用/nginx 三容器）
- [x] 与 Python 版数据库/密码/备份互通
- [x] 到期提醒 + 每日自动备份定时任务

---

## 注意事项

- `.env` / `application.yml` 里不要提交真实数据库密码
- 首次部署后请立刻修改管理员密码，并检查「系统设置」里的借期、罚金等规则
