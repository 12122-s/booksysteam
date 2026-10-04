# 图书管理系统（Java 版）· 生产镜像
# 构建前先打好 jar：mvnw.cmd package -DskipTests（产物 target/booksysteam-1.0.0.jar）
# 镜像只含 JRE + jar，自带 Tomcat，不需要额外装任何东西
FROM eclipse-temurin:17-jre

WORKDIR /app
ENV TZ=Asia/Shanghai

COPY target/booksysteam-1.0.0.jar app.jar

# 封面/收款码上传目录、自动备份目录建议用 docker compose 的 volumes 挂出来
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
