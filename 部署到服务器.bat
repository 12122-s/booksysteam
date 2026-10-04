@echo off
rem ============================================================
rem Java 版图书管理系统 · 打包并上传到虚拟机服务器（并行试用模式）
rem 用法：双击运行，按提示输入服务器地址和用户名
rem 上传后到服务器上：cd ~/library-java → 改 start.sh 里的密码 → bash start.sh
rem 详细说明见 docs/虚拟机部署说明.md
rem ============================================================
chcp 65001 >nul
title 图书管理系统 - 打包上传

if "%JAVA_HOME%"=="" set JAVA_HOME=C:\Program Files\Zulu\zulu-17

echo [1/3] 正在打包（第一次约 1-3 分钟）...
call "%~dp0mvnw.cmd" package -DskipTests -q
if errorlevel 1 (
  echo [错误] 打包失败，请检查上面的输出。
  pause
  exit /b 1
)
echo      产物：%~dp0target\booksysteam-1.0.0.jar

set /p SERVER=[2/3] 输入服务器地址（如 192.168.56.10）：
if "%SERVER%"=="" ( echo 未输入地址，退出。 & pause & exit /b 1 )
set /p SUSER=      输入服务器用户名（如 root）：
if "%SUSER%"=="" set SUSER=root

echo [3/3] 正在上传到 %SUSER%@%SERVER%:~/library-java/ ...
ssh %SUSER%@%SERVER% "mkdir -p ~/library-java/target"
scp "%~dp0target\booksysteam-1.0.0.jar" %SUSER%@%SERVER%:~/library-java/target/
scp "%~dp0deploy\start.sh" "%~dp0Dockerfile" "%~dp0docker-compose.yml" %SUSER%@%SERVER%:~/library-java/
if errorlevel 1 (
  echo [错误] 上传失败，请确认 ssh 能连通、用户名密码正确。
  pause
  exit /b 1
)

echo.
echo 完成！上传了 jar + start.sh + Dockerfile + docker-compose.yml。
echo.
echo 方式 A（Docker，推荐）—— 到服务器上执行：
echo   ssh %SUSER%@%SERVER%
echo   cd ~/library-java
echo   nano docker-compose.yml   （把 LIB_DB_PASSWORD 改成服务器 MySQL 密码）
echo   docker compose up -d --build
echo   浏览器打开 http://服务器IP:8081   管理员 admin / 初始密码 123456
echo   日志：docker logs -f library-java    停止：docker compose down
echo.
echo 方式 B（直接 java 跑）—— cd ~/library-java 后改 start.sh 里的密码，
echo   然后 bash start.sh（停止：bash start.sh stop）
pause
