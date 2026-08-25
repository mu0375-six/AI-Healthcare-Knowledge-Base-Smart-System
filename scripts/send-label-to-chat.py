import json
import pathlib
import urllib.request

IMG = pathlib.Path("frontend/public/demo/metformin-real.jpg")


def login():
    req = urllib.request.Request(
        "http://localhost:8080/api/auth/login",
        data=json.dumps({"username": "user", "password": "User123!"}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read())["data"]["token"]


def upload(token: str):
    boundary = "----KangshiLabel"
    raw = IMG.read_bytes()
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{IMG.name}"\r\n'
        "Content-Type: image/jpeg\r\n\r\n"
    ).encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        "http://localhost:8080/api/chat/images",
        data=head + raw + tail,
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())["data"]["id"]


def ask(token: str, image_id: int):
    body = {
        "question": "这是药盒照片。请根据图上的信息告诉我这是什么药、用来干什么、有哪些使用注意。不要写具体剂量。",
        "imageIds": [image_id],
    }
    req = urllib.request.Request(
        "http://localhost:8080/api/chat/ask",
        data=json.dumps(body).encode(),
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        },
    )
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read().decode("utf-8", errors="replace")


token = login()
image_id = upload(token)
print("IMAGE_ID", image_id)
text = ask(token, image_id)
answer = ""
for block in text.split("\n\n"):
    if "event:done" in block:
        for line in block.splitlines():
            if line.startswith("data:"):
                payload = json.loads(line[5:])
                answer = payload.get("fullContent", "")
print("----- ANSWER -----")
print(answer)
