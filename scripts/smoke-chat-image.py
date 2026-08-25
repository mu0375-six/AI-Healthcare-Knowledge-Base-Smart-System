import json
import urllib.request

def login():
    req = urllib.request.Request(
        "http://localhost:8080/api/auth/login",
        data=json.dumps({"username": "user", "password": "User123!"}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read())["data"]["token"]


token = login()
boundary = "----GrokBoundary7"
png = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de"
    "0000000c49444154789c6360f8cf000000020001e221bc330000000049454e44ae426082"
)
head = (
    f"--{boundary}\r\n"
    'Content-Disposition: form-data; name="file"; filename="lab.png"\r\n'
    "Content-Type: image/png\r\n\r\n"
).encode()
tail = f"\r\n--{boundary}--\r\n".encode()
req = urllib.request.Request(
    "http://localhost:8080/api/chat/images",
    data=head + png + tail,
    method="POST",
    headers={
        "Authorization": "Bearer " + token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    },
)
with urllib.request.urlopen(req, timeout=20) as r:
    up = json.loads(r.read())
print("UPLOAD", up)

ask = json.dumps({"question": "这张图是体检单吗，帮我看看", "imageIds": [up["data"]["id"]]}).encode()
req = urllib.request.Request(
    "http://localhost:8080/api/chat/ask",
    data=ask,
    method="POST",
    headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    },
)
with urllib.request.urlopen(req, timeout=90) as r:
    text = r.read().decode("utf-8", errors="replace")
print("ASK ok", ("event:done" in text or "event:delta" in text), "len", len(text))
print(text[-500:] if len(text) > 500 else text)
