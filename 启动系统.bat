@echo off
rem 图书管理系统（Java 版）· Windows 一键启动
rem 双击后自动构建并启动，浏览器打开 http://localhost:8080
rem 管理员账号 admin，初始密码 123456

chcp 65001 >nul
title 图书管理系统 - Java 版

where java >nul 2>nul
if errorlevel 1 (
  echo [错误] 没有找到 Java。请先安装 JDK 17 或更高版本：
  echo        https://www.azul.com/downloads/?version=java-17-lts^&os=windows^&architecture=x86-64-bit^&package=jdk
  pause
  exit /b 1
)

echo 正在启动系统，首次运行需要下载依赖（大约 1-3 分钟），请稍候...
start "" http://localhost:8080
call "%~dp0mvnw.cmd" spring-boot:run -q
pause
