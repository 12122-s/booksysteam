#!/bin/bash
# ============================================================
# Java 版图书管理系统 · 并行试用启动脚本
#   - 用独立新库 library_java，和 Python 版的 library_system 互不影响
#   - 端口 8081（Python 版占 8080）
#   - 首次运行自动建库建表，管理员 admin / 初始密码 123456
# 用法：bash start.sh    停止：bash start.sh stop    看日志：tail -f app.log
# ============================================================
cd "$(dirname "$0")" || exit 1

# ===== 按你的虚拟机改这里 =====
DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_USER="root"
DB_PASSWORD="你的MySQL密码"     # <-- 改成虚拟机上 MySQL 的 root 密码
# ==============================

JAR="booksysteam-1.0.0.jar"

if [ "$1" = "stop" ]; then
    pkill -f "$JAR" && echo "已停止" || echo "没有在运行"
    exit 0
fi

if [ "$DB_PASSWORD" = "你的MySQL密码" ]; then
    echo "请先编辑本文件，把 DB_PASSWORD 改成虚拟机上 MySQL 的真实密码。"
    exit 1
fi

pkill -f "$JAR" 2>/dev/null && sleep 2

export LIB_DB_HOST="$DB_HOST"
export LIB_DB_PORT="$DB_PORT"
export LIB_DB_USER="$DB_USER"
export LIB_DB_PASSWORD="$DB_PASSWORD"
export LIB_DB_NAME=library_java
export SERVER_PORT=8081

nohup java -jar "$JAR" > app.log 2>&1 &
sleep 3
if pgrep -f "$JAR" > /dev/null; then
    IP=$(hostname -I 2>/dev/null | awk '{print $1}')
    echo "已启动：http://${IP:-127.0.0.1}:8081"
    echo "管理员账号 admin，初始密码 123456（首次启动自动建库 library_java）"
    echo "看日志：tail -f $(pwd)/app.log"
    echo "停止：  bash $(pwd)/start.sh stop"
else
    echo "启动失败，最后几行日志："
    tail -20 app.log
fi
