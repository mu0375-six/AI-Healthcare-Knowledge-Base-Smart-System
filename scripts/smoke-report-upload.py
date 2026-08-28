# -*- coding: utf-8 -*-
"""报告解读留痕：以 user 身份上传 demo-report.txt 并触发解析。"""
import json
import urllib.request
from pathlib import Path

BASE = "http://localhost:8080"
REPORT = Path(r"D:\中软国际实习\小组项目\samples\demo-report.txt")


def login():
    req = urllib.request.Request(
        BASE + "/api/auth/login",
        data=json.dumps({"username": "user", "password": "User123!"}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read())["data"]["token"]


token = login()
print("[ok] login user")

boundary = "----HealthKbReportBoundary5"
parts = [
    f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"demo-report.txt\"\r\nContent-Type: text/plain\r\n\r\n".encode("utf-8"),
    REPORT.read_bytes(),
    f"\r\n--{boundary}--\r\n".encode(),
]
req = urllib.request.Request(
    BASE + "/api/reports/upload",
    data=b"".join(parts),
    method="POST",
    headers={
        "Authorization": "Bearer " + token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Accept": "application/json",
    },
)
with urllib.request.urlopen(req, timeout=120) as r:
    payload = json.loads(r.read())
if payload.get("code") != 0:
    raise SystemExit("upload failed: " + json.dumps(payload, ensure_ascii=False)[:400])
d = payload["data"]
print("[ok] report id=", d.get("id"), "filename=", d.get("filename"))
items = d.get("items") or []
print("[ok] parsed items:", len(items))
for it in items[:12]:
    print("   ", it.get("name"), it.get("value"), it.get("unit"), "flag=", it.get("flag"))
