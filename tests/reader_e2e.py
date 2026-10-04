# -*- coding: utf-8 -*-
"""读者端全流程 e2e：办卡 → 审核 → 登录 → 书城 → 申请/借书 → 续借 → 书评 → 管理员还书。
requests 默认自动跟随重定向；flash 消息只在第一次 GET 出现，不要重复请求同一地址。"""
import random
import re
import sys

import requests

BASE = "http://127.0.0.1:8080"
PASS = 0
FAIL = 0


def ok(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  ✓ {name}")
    else:
        FAIL += 1
        print(f"  ✗ {name}  {detail}")


def csrf(html):
    m = re.search(r'name="csrf_token" value="([^"]+)"', html)
    return m.group(1) if m else ""


def main():
    phone = f"139{random.randint(10000000, 99999999)}"
    admin = requests.Session()
    r = admin.post(f"{BASE}/login", data={
        "account": "admin", "password": "123456",
        "csrf_token": csrf(admin.get(f"{BASE}/login").text)})
    ok("管理员登录", "工作台" in r.text)

    reader = requests.Session()
    r = reader.post(f"{BASE}/reader/signup", data={
        "name": "测试读者甲", "phone": phone, "reader_type": "学生",
        "department": "高一(9)班", "email": "t@example.com", "password": "abc12345",
        "confirm_password": "abc12345", "agree": "1",
        "csrf_token": csrf(reader.get(f"{BASE}/reader/signup").text)})
    ok("读者自助办卡", "办卡成功" in r.text)
    m = re.search(r"借书卡号是 ([A-Za-z0-9]+)", r.text)
    card = m.group(1) if m else ""
    ok("返回了卡号", card != "")

    # 若办卡需审核：管理员到读者列表通过审核
    r = admin.get(f"{BASE}/readers?q={card}")
    m = re.search(r"/readers/(\d+)/approve", r.text)
    if m:
        r = admin.post(f"{BASE}/readers/{m.group(1)}/approve",
                       data={"csrf_token": csrf(admin.get(f"{BASE}/readers").text)})
        ok("管理员通过办卡审核", "已确认" in r.text)

    # 退出，用卡号重新登录
    reader.get(f"{BASE}/reader/logout")
    r = reader.post(f"{BASE}/login", data={
        "account": card, "password": "abc12345",
        "csrf_token": csrf(reader.get(f"{BASE}/login").text)})
    ok("读者卡号登录", "找书" in r.text)

    # 书城
    r = reader.get(f"{BASE}/reader")
    ok("书城页面", "猜你可能想看" in r.text)
    book_ids = re.findall(r"/reader/(?:borrow|request)/(\d+)", r.text)
    ok("书城列出图书", len(book_ids) > 0)
    bid = book_ids[0]

    # 申请模式或自助借书（按设置走哪个分支）
    if "申请借阅" in r.text:
        r = reader.post(f"{BASE}/reader/request/{bid}", data={"csrf_token": csrf(r.text)})
        ok("提交借阅申请", "申请" in r.text)
        r = reader.get(f"{BASE}/reader/my")
        ok("我的借阅显示申请", "我申请的借阅" in r.text)
    else:
        r = reader.post(f"{BASE}/reader/borrow/{bid}", data={"csrf_token": csrf(r.text)})
        ok("读者自助借书", "借书成功" in r.text)

    # 管理员办理借出：从申请列表的「办理借出」链接里拿到读者/图书 id
    # （列表按 id 升序，本轮注册读者的申请排在最后，取最后一处匹配）
    r = admin.get(f"{BASE}/requests")
    ok("管理端看到申请", "借阅申请" in r.text)
    readers_pending = re.findall(r"pick_reader=(\d+)", r.text)
    books_pending = re.findall(r"pick_book=(\d+)", r.text)
    if readers_pending and books_pending:
        rid, bid2 = readers_pending[-1], books_pending[-1]
        r = admin.get(f"{BASE}/borrow/new?reader_id={rid}&book_id={bid2}")
        ok("办理借书页带出读者和书", "确认借出" in r.text)
        r = admin.post(f"{BASE}/borrow/new", data={
            "reader_id": rid, "book_id": bid2,
            "csrf_token": csrf(admin.get(f"{BASE}/borrow/new").text)})
        ok("管理员借出成功", "借出成功" in r.text)

    # 读者续借
    r = reader.get(f"{BASE}/reader/my")
    ok("我的借阅-正在借", "正在借的书" in r.text)
    m = re.search(r"/reader/renew/(\d+)", r.text)
    if m:
        r = reader.post(f"{BASE}/reader/renew/{m.group(1)}",
                        data={"days": "7", "csrf_token": csrf(r.text)})
        ok("读者续借", "续借成功" in r.text)

    # 管理员还书
    r = admin.get(f"{BASE}/borrows?status=borrowing")
    m = re.search(r"/borrows/(\d+)/return", r.text)
    if m:
        r = admin.post(f"{BASE}/borrows/{m.group(1)}/return",
                       data={"csrf_token": csrf(admin.get(f"{BASE}/borrows").text)})
        ok("管理员还书", "还书成功" in r.text)

    # 还书后在“以前借过的书”里写书评
    r = reader.get(f"{BASE}/reader/my")
    m = re.search(r"/reader/review/(\d+)", r.text)
    if m:
        r = reader.post(f"{BASE}/reader/review/{m.group(1)}", data={
            "rating": "5", "content": "写得非常好，冒烟测试书评。",
            "csrf_token": csrf(reader.get(f"{BASE}/reader/review/{m.group(1)}").text)})
        ok("读者写书评", "评价已保存" in r.text)
        r = admin.get(f"{BASE}/reviews")
        ok("管理端看到书评", "冒烟测试书评" in r.text)

    # 读者退出
    r = reader.get(f"{BASE}/reader/logout")
    ok("读者退出", r.status_code in (200, 301, 302), r.status_code)

    print()
    print(f"读者端流程：通过 {PASS} 项，失败 {FAIL} 项")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
