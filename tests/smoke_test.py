# -*- coding: utf-8 -*-
"""
Java 版图书管理系统 · 冒烟测试
用法：先启动系统，然后执行
    python tests/smoke_test.py http://127.0.0.1:8080 管理密码 admin
覆盖核心链路：登录、工作台、图书、读者、借阅、申请、罚金、书评、设置、
报表、分析、导出、公开页，以及 REST API 的鉴权与主要端点。
"""
import sys

import requests

PASS = 0
FAIL = 0


def ok(name, condition, detail=""):
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"  ✓ {name}")
    else:
        FAIL += 1
        print(f"  ✗ {name}  {detail}")


def main(base, password="123456", account="admin"):
    base = base.rstrip("/")
    s = requests.Session()

    print("== 公开页 ==")
    r = s.get(f"{base}/health", timeout=10)
    ok("健康检查 /health", r.status_code == 200 and r.text == "ok", r.status_code)
    r = s.get(f"{base}/login", timeout=10)
    ok("登录页", r.status_code == 200 and "登录" in r.text, r.status_code)
    r = s.get(f"{base}/notice", timeout=10)
    ok("公告与规则页", r.status_code == 200, r.status_code)
    r = s.get(f"{base}/reader/signup", timeout=10)
    ok("读者办卡页", r.status_code == 200, r.status_code)
    r = s.get(f"{base}/reader/forgot", timeout=10)
    ok("忘记密码页", r.status_code == 200, r.status_code)
    r = s.get(f"{base}/books", timeout=10, allow_redirects=False)
    ok("未登录访问管理端被拦截", r.status_code in (301, 302), r.status_code)

    print("== 管理员登录 ==")
    r = s.get(f"{base}/login", timeout=10)
    csrf = s.cookies.get_dict()
    r = s.post(f"{base}/login", data={
        "account": account, "password": password, "csrf_token": get_csrf(r.text)},
        timeout=10, allow_redirects=False)
    ok("登录成功（重定向）", r.status_code in (301, 302), f"{r.status_code} {r.text[:100]}")

    print("== 管理端页面 ==")
    for path, name, keyword in [
        ("/", "工作台", "工作台"),
        ("/books", "图书列表", "图书"),
        ("/books/new", "新增图书", "书名"),
        ("/readers", "读者列表", "读者"),
        ("/readers/new", "新增读者", "卡号"),
        ("/borrows", "借阅记录", "借阅"),
        ("/borrow/new", "办理借书", "读者"),
        ("/requests", "借阅申请", "申请"),
        ("/fines", "罚金台账", "罚金"),
        ("/reviews", "书评管理", "书评"),
        ("/reports", "统计报表", "统计"),
        ("/analytics", "数据分析", "分析"),
        ("/settings", "系统设置", "设置"),
        ("/logs", "操作日志", "日志"),
        ("/reminders", "提醒记录", "提醒"),
        ("/books/import", "批量导入", "导入"),
        ("/print/overdue", "催还单打印页", "催还"),
        ("/print/labels", "书标打印页", "书标"),
    ]:
        r = s.get(base + path, timeout=10)
        ok(name, r.status_code == 200 and keyword in r.text, f"{r.status_code}")

    print("== 导出 ==")
    r = s.get(f"{base}/export/books.csv", timeout=10)
    ok("导出图书 CSV", r.status_code == 200 and r.content[:3] == b"\xef\xbb\xbf", r.status_code)
    r = s.get(f"{base}/export/backup.sql", timeout=10)
    ok("导出完整备份", r.status_code == 200 and "INSERT INTO" in r.text, r.status_code)

    print("== 写操作（带 CSRF）==")
    r = s.get(f"{base}/books/new", timeout=10)
    token = get_csrf(r.text)
    r = s.post(f"{base}/books/new", data={
        "title": "冒烟测试之书", "author": "测试", "category": "测试",
        "total_copies": "2", "csrf_token": token}, timeout=10, allow_redirects=False)
    ok("新增图书", r.status_code in (301, 302), r.status_code)
    r = s.get(f"{base}/books?q=冒烟测试", timeout=10)
    ok("能搜到新书", "冒烟测试之书" in r.text)

    r = s.post(f"{base}/books/new", data={"title": "无CSRF", "total_copies": "1"},
               timeout=10, allow_redirects=False)
    ok("无 CSRF 的 POST 被拒绝", r.status_code == 400, r.status_code)

    print("== REST API (JWT) ==")
    api = f"{base}/api/v1"
    r = s.get(f"{api}/books")
    ok("API 未带 token 被拒（401）", r.status_code == 401, r.status_code)
    r = s.post(f"{api}/auth/login", json={"account": account, "password": "wrong-password"})
    ok("API 错误密码被拒（401）", r.status_code == 401, r.status_code)
    r = s.post(f"{api}/auth/login", json={"account": account, "password": password})
    ok("API 登录换取 token", r.status_code == 200 and r.json().get("token"), r.status_code)
    token = r.json().get("token", "")
    auth = {"Authorization": f"Bearer {token}"}
    r = s.get(f"{api}/me", headers=auth)
    ok("API /me 返回身份", r.status_code == 200 and r.json().get("role") == "admin", r.status_code)
    r = s.get(f"{api}/books", headers=auth)
    ok("API 图书列表", r.status_code == 200 and "items" in r.json(), r.status_code)
    r = s.get(f"{api}/readers", headers=auth)
    ok("API 读者列表（admin）", r.status_code == 200 and "items" in r.json(), r.status_code)
    r = s.get(f"{api}/borrows", headers=auth)
    ok("API 借阅列表（admin）", r.status_code == 200 and "items" in r.json(), r.status_code)
    r = s.get(f"{api}/stats/overview", headers=auth)
    ok("API 统计总览（admin）", r.status_code == 200 and "copies" in r.json(), r.status_code)
    r = s.get(f"{base}/v3/api-docs", headers={"Accept": "application/json"})
    ok("OpenAPI 文档可访问", r.status_code == 200 and "openapi" in r.text, r.status_code)

    print()
    print(f"通过 {PASS} 项，失败 {FAIL} 项")
    sys.exit(1 if FAIL else 0)


def get_csrf(html):
    marker = 'name="csrf_token" value="'
    idx = html.find(marker)
    if idx < 0:
        marker = "name=\"csrf_token\" th:value=\""
        idx = html.find(marker)
    if idx < 0:
        return ""
    start = idx + len(marker)
    end = html.find('"', start)
    return html[start:end]


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1],
         sys.argv[2] if len(sys.argv) > 2 else "123456",
         sys.argv[3] if len(sys.argv) > 3 else "admin")
