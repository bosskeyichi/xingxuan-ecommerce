#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
部署验收脚本：从任意一台机器对「网关入口」做端到端检查。

用法：
    python deploy/verify_deployment.py                       # 默认 http://127.0.0.1:8080
    python deploy/verify_deployment.py http://HOST_IP:8080
    python deploy/verify_deployment.py http://VM_IP:8080

检查项：
    1. 网关 /health 与后端 /api/health（探针链路）
    2. 用户端 / 与管理端 /admin/ 静态资源
    3. /admin 缺省尾斜杠的 301 跳转（端口是否被保留）
    4. 公开接口：商品列表、分类列表
    5. 登录链路：alice 登录 → /auth/me → 购物车 → 订单列表
    6. 权限边界：普通用户访问管理端接口应 403
    7. 管理员：用户分页、订单分页、看板
    8. 单设备登录互斥：再次登录后旧 token 应 401
    9. 公开列表不返回下架商品（status 参数不生效）
"""
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")
results = []


def request(path, method="GET", body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "ignore")
    except Exception as e:
        return 0, str(e)


def check(name, ok, detail=""):
    results.append((name, ok))
    print(("PASS  " if ok else "FAIL  ") + name + ("  | " + detail if detail else ""))


def main():
    print("验收目标：%s\n" % BASE)

    # 1. 健康检查链路
    status, raw = request("/health")
    check("网关 /health", status == 200 and "gateway ok" in raw, "HTTP %s %s" % (status, raw.strip()[:40]))

    status, raw = request("/api/health")
    try:
        body = json.loads(raw)
        ok = status == 200 and body.get("code") == 200 and body["data"]["status"] == "UP"
        detail = "%s / 实例 %s" % (body["data"]["service"], body["data"]["instance"])
    except Exception:
        ok, detail = False, raw[:120]
    check("后端 /api/health", ok, detail)

    # 2. 前端静态资源
    status, raw = request("/")
    check("用户端首页 /", status == 200 and 'id="app"' in raw, "HTTP %s，%d 字节" % (status, len(raw)))
    status, raw = request("/admin/")
    check("管理端 /admin/", status == 200 and "/admin/assets/" in raw, "HTTP %s，%d 字节" % (status, len(raw)))

    # 3. /admin 301 跳转（Location 必须保留端口）
    try:
        req = urllib.request.Request(BASE + "/admin")
        with urllib.request.build_opener(urllib.request.HTTPRedirectHandler).open(req, timeout=20) as resp:
            final = resp.geturl()
        check("/admin 重定向", final.endswith("/admin/") and resp.status == 200, "最终地址 " + final)
    except Exception as e:
        check("/admin 重定向", False, str(e))

    # 4. 公开接口
    status, raw = request("/api/product/list?current=1&size=5")
    try:
        body = json.loads(raw)
        rows = body["data"].get("records") or []
        check("商品列表", status == 200 and len(rows) == 5,
              "共 %s 件，返回 %d 条，首个=%s" % (body["data"].get("total"), len(rows), rows[0]["name"] if rows else "-"))
    except Exception:
        check("商品列表", False, raw[:160])

    status, raw = request("/api/category/list")
    try:
        cats = json.loads(raw)["data"]
        check("分类列表", status == 200 and len(cats) >= 5, "%d 个分类" % len(cats))
    except Exception:
        check("分类列表", False, raw[:160])

    # 5. 登录链路
    status, raw = request("/api/auth/login", "POST", {"username": "alice", "password": "123456"})
    token = None
    try:
        token = json.loads(raw)["data"]["token"]
        check("用户登录 alice", status == 200, "token 长度 %d" % len(token))
    except Exception:
        check("用户登录 alice", False, raw[:160])

    if token:
        status, raw = request("/api/auth/me", token=token)
        check("登录态 /api/auth/me", status == 200 and "alice" in raw, "HTTP %s" % status)

        status, raw = request("/api/cart/list", token=token)
        check("购物车 /api/cart/list", status == 200, "HTTP %s" % status)

        status, raw = request("/api/order/list?current=1&size=5", token=token)
        check("订单列表 /api/order/list", status == 200, "HTTP %s" % status)

        status, raw = request("/api/auth/admin/users/page?current=1&size=5", token=token)
        check("越权拦截（普通用户访问管理端接口）", status == 403, "HTTP %s" % status)

    # 6. 管理员
    status, raw = request("/api/auth/login", "POST", {"username": "admin", "password": "123456"})
    admin_token = None
    try:
        admin_token = json.loads(raw)["data"]["token"]
        check("管理员登录 admin", status == 200, "token 长度 %d" % len(admin_token))
    except Exception:
        check("管理员登录 admin", False, raw[:160])

    if admin_token:
        status, raw = request("/api/auth/admin/users/page?current=1&size=5", token=admin_token)
        check("管理端用户分页", status == 200, "HTTP %s" % status)
        status, raw = request("/api/order/admin/list?current=1&size=5", token=admin_token)
        check("管理端订单分页", status == 200, "HTTP %s" % status)
        status, raw = request("/api/admin/stats/overview", token=admin_token)
        check("管理端看板 /api/admin/stats/overview", status == 200, "HTTP %s" % status)

    # 7. 单设备登录互斥
    if token:
        request("/api/auth/login", "POST", {"username": "alice", "password": "123456"})
        status, _ = request("/api/auth/me", token=token)
        check("单设备登录互斥（旧 token 失效）", status == 401, "旧 token HTTP %s" % status)

    # 8. 公开列表不接受 status 参数（防止匿名枚举下架商品）
    status, raw = request("/api/product/list?current=1&size=50&status=0")
    try:
        rows = json.loads(raw)["data"].get("records") or []
        ok = status == 200 and all(p.get("status") == 1 for p in rows)
        check("公开列表不返回下架商品", ok, "返回 %d 条，均上架=%s" % (len(rows), ok))
    except Exception:
        check("公开列表不返回下架商品", False, raw[:160])

    passed = sum(1 for _, ok in results if ok)
    print("\n结果：%d/%d 通过" % (passed, len(results)))
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
