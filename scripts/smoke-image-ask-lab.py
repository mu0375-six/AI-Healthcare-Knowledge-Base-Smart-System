# -*- coding: utf-8 -*-
"""图片问诊留痕：以 user 身份上传真实化验单照片并发起一次带图提问。"""
import json
import urllib.request

BASE = "http://localhost:8080"
IMG = r"D:\中软国际实习\小组项目\samples\real-lab-cmp.jpg"


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

boundary = "----HealthKbImgBoundary9"
file_bytes = open(IMG, "rb").read()
parts = [
    f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"real-lab-cmp.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".encode(),
    file_bytes,
    f"\r\n--{boundary}--\r\n".encode(),
]
req = urllib.request.Request(
    BASE + "/api/chat/images",
    data=b"".join(parts),
    method="POST",
    headers={
        "Authorization": "Bearer " + token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    },
)
with urllib.request.urlopen(req, timeout=30) as r:
    up = json.loads(r.read())
print("[ok] upload image id=", up["data"]["id"], "bytes=", len(file_bytes))

ask = json.dumps({
    "question": "这是一张血常规+微量元素的化验单照片，帮我看一下哪些指标异常、需要注意什么",
    "imageIds": [up["data"]["id"]],
}).encode()
req = urllib.request.Request(
    BASE + "/api/chat/ask",
    data=ask,
    method="POST",
    headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    },
)
with urllib.request.urlopen(req, timeout=120) as r:
    text = r.read().decode("utf-8", errors="replace")
has_done = "event:done" in text
print("[ok] ask done=", has_done, "sse_len=", len(text))
tail = text[-600:]
print(tail)
