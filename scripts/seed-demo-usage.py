# -*- coding: utf-8 -*-
"""Walk the demo flow so the website has visible usage records."""
from __future__ import annotations

import json
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "http://localhost:8080"
REPORT = Path(r"D:\中软国际实习\小组项目\samples\demo-report.txt")
CTX = ssl.create_default_context()


def request(method: str, path: str, token: str | None = None, body: dict | None = None, accept: str = "application/json"):
    data = None
    headers = {"Accept": accept}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=90) as resp:
            raw = resp.read()
            return resp.status, raw.decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"{method} {path} -> HTTP {e.code}\n{raw}") from e


def json_req(method: str, path: str, token: str | None = None, body: dict | None = None):
    status, text = request(method, path, token, body)
    payload = json.loads(text) if text else {}
    if isinstance(payload, dict) and payload.get("code") not in (0, None):
        raise SystemExit(f"{method} {path} -> business code {payload.get('code')}: {payload.get('message')}\n{text}")
    return payload


def login(username: str, password: str) -> str:
    payload = json_req("POST", "/api/auth/login", body={"username": username, "password": password})
    token = payload["data"]["token"]
    print(f"[ok] login {username}")
    return token


def ask(token: str, question: str, session_id: int | None) -> tuple[int, int]:
    body = {"question": question}
    if session_id is not None:
        body["sessionId"] = session_id
    status, text = request("POST", "/api/chat/ask", token, body, accept="text/event-stream")
    sid = session_id
    mid = None
    for block in text.split("\n\n"):
        event = "message"
        data_lines = []
        for line in block.splitlines():
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].strip())
        if not data_lines:
            continue
        try:
            data = json.loads("\n".join(data_lines))
        except json.JSONDecodeError:
            continue
        if event == "meta":
            sid = data.get("sessionId", sid)
            mid = data.get("messageId", mid)
        elif event == "done":
            sid = data.get("sessionId", sid)
            mid = data.get("messageId", mid)
    if sid is None or mid is None:
        raise SystemExit(f"ask failed to parse SSE\n{text[:800]}")
    print(f"[ok] ask session={sid} message={mid} q={question}")
    return int(sid), int(mid)


def upload_report(token: str, path: Path):
    boundary = "----HealthKbBoundary7MA4YWxkTrZu0gW"
    file_bytes = path.read_bytes()
    parts = []
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{path.name}\"\r\nContent-Type: text/plain\r\n\r\n".encode("utf-8"))
    parts.append(file_bytes)
    parts.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    data = b"".join(parts)
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Accept": "application/json",
    }
    req = urllib.request.Request(BASE + "/api/reports/upload", data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, context=CTX, timeout=90) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if payload.get("code") != 0:
        raise SystemExit(f"upload report failed: {payload}")
    print(f"[ok] report id={payload['data'].get('id')} file={path.name}")
    return payload


def main():
    user = login("user", "User123!")

    sid, mid1 = ask(user, "二甲双胍有什么注意事项", None)
    sid, mid2 = ask(user, "高血压饮食要注意什么", sid)
    json_req("POST", "/api/favorites", user, {"messageId": mid1})
    print(f"[ok] favorite message={mid1}")

    triage = json_req("POST", "/api/triage", user, {"symptoms": "胸痛 呼吸困难", "age": 46, "sex": "男"})
    top = (triage.get("data") or {}).get("departments") or triage.get("data") or []
    print(f"[ok] triage -> {json.dumps(top, ensure_ascii=False)[:240]}")

    json_req("PUT", "/api/health/profile", user, {
        "age": 46,
        "sex": "男",
        "heightCm": 172.0,
        "weightKg": 78.5,
        "allergies": "青霉素过敏",
        "sharedToAdmin": False,
    })
    json_req("POST", "/api/health/metrics", user, {
        "metricType": "空腹血糖",
        "value": 7.2,
        "unit": "mmol/L",
        "recordedAt": "2026-08-16T08:00:00",
        "note": "空腹",
    })
    json_req("POST", "/api/health/metrics", user, {
        "metricType": "收缩压",
        "value": 148.0,
        "unit": "mmHg",
        "recordedAt": "2026-08-16T08:10:00",
        "note": "晨起",
    })
    json_req("POST", "/api/health/histories", user, {
        "disease": "2型糖尿病",
        "diagnosedAt": "2023-05-12",
        "status": "随访中",
        "note": "口服二甲双胍",
    })
    advice = json_req("POST", "/api/health/advice", user)
    print("[ok] health profile + metrics + history + advice")
    print("    advice preview:", (advice.get("data") or {}).get("advice", "")[:80].replace("\n", " "))

    upload_report(user, REPORT)

    admin = login("admin", "Admin123!")
    docs = json_req("GET", "/api/admin/knowledge", admin)
    print(f"[ok] admin knowledge count={len(docs.get('data') or [])}")
    added = json_req("POST", "/api/admin/knowledge/text", admin, {
        "title": "胸痛呼吸困难急诊识别要点",
        "category": "科室导诊",
        "source": "演示补录·急诊共识摘要",
        "content": (
            "突发胸痛合并呼吸困难属于高危症状，需优先考虑急性冠脉综合征、肺栓塞、张力性气胸、主动脉夹层等。"
            "伴随大汗、放射至左肩或下颌、意识改变、血氧下降时应立即急诊，不要自行服药观察。"
            "到达医院后通常先做心电图、血氧、血压评估，再根据情况完善肌钙蛋白、D-二聚体、胸部影像。"
            "本条为演示补录，不能替代现场急救与执业医师判断。"
        ),
    })
    print(f"[ok] admin added knowledge id={added['data'].get('id')} title={added['data'].get('title')}")

    sessions = json_req("GET", "/api/chat/sessions", user)
    reports = json_req("GET", "/api/reports", user)
    favs = json_req("GET", "/api/favorites", user)
    print("--- summary ---")
    print(f"user sessions: {len(sessions.get('data') or [])}")
    print(f"user reports: {len(reports.get('data') or [])}")
    print(f"user favorites: {len(favs.get('data') or [])}")
    print("done. Login as user / User123! or admin / Admin123! to view.")


if __name__ == "__main__":
    sys.exit(main())
