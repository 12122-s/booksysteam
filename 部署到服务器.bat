@echo off
rem ============================================================
rem Java 版图书管理系统 · 打包并上传到虚拟机服务器
rem 用法：双击运行，按提示输入服务器地址和用户名
rem 上传后到服务器上执行：
rem   ~/library-java/重启.sh   （见 docs/虚拟机部署说明.md）
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
ssh %SUSER%@%SERVER% "mkdir -p ~/library-java"
scp "%~dp0target\booksysteam-1.0.0.jar" %SUSER%@%SERVER%:~/library-java/
if errorlevel 1 (
  echo [错误] 上传失败，请确认 ssh 能连通、用户名密码正确。
  pause
  exit /b 1
)

echo.
echo 完成！接下来到服务器上执行：
echo   ssh %SUSER%@%SERVER%
echo   然后按 docs/虚拟机部署说明.md 第四章启动（或重启 systemd 服务）：
echo   sudo systemctl restart library-java
pause
