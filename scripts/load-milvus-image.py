"""Assemble milvusdb/milvus:v2.4.9 from deploy/milvus/blobs and docker load it."""
from __future__ import annotations

import gzip
import json
import shutil
import subprocess
import sys
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLOBS = ROOT / "deploy" / "milvus" / "blobs"
UNPACK = ROOT / "deploy" / "milvus" / "unpack"
ARCHIVE = ROOT / "deploy" / "milvus" / "milvus-v2.4.9-load.tar"
LOCAL_NAME = "milvusdb/milvus:v2.4.9"

CONFIG = {
    "digest": "sha256:2eb12b8537088637687f7f30f39a89743b43ecd70fb67073c08fb153df3e9dcc",
    "size": 7158,
}
LAYERS = [
    {"digest": "sha256:7646c8da332499ae416b15479ce832db32e39a501c662e24324f595509a0d3db", "size": 29533754},
    {"digest": "sha256:e071a2bda6d58eed0cc7ef7ee1d6189fdfca64c67a123dd1439aa744078ec8ef", "size": 31036738},
    {"digest": "sha256:653cde9beea196094ab6385810ee2033ffe07faa7a08ee2bc55777c0f85f309a", "size": 78164775},
    {"digest": "sha256:865722af4c96a77ef45b7c7005f5b8807d5ca1a578013b5b5813eaf22751f7b5", "size": 28585},
    {"digest": "sha256:79099f9f1d08356c84e415fea1a02804202a9922ca91bb814ac43d1a414d81f4", "size": 350454941},
    {"digest": "sha256:535376b75c84412fcbf99a8ce19c14e91f8a980d5eb1af34ceba2b3507a23af4", "size": 9463},
    {"digest": "sha256:eb2f8229d37517248fc22b3a1fa7f22df0e1f35c02191a8ca61a3856e074fc9a", "size": 9464},
    {"digest": "sha256:4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1", "size": 32},
]


def sha(item: dict) -> str:
    return item["digest"].split(":", 1)[1]


def require(item: dict) -> Path:
    path = BLOBS / sha(item)
    if not path.exists() or path.stat().st_size != item["size"]:
        raise SystemExit(f"missing or incomplete blob: {path.name}")
    return path


def gunzip(src: Path, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"gunzip {src.name}", flush=True)
    with gzip.open(src, "rb") as fin, dest.open("wb") as fout:
        shutil.copyfileobj(fin, fout)


def main() -> int:
    require(CONFIG)
    for layer in LAYERS:
        require(layer)

    UNPACK.mkdir(parents=True, exist_ok=True)
    config_sha = sha(CONFIG)
    config_name = f"{config_sha}.json"
    shutil.copyfile(BLOBS / config_sha, UNPACK / config_name)

    layer_paths: list[str] = []
    for layer in LAYERS:
        layer_sha = sha(layer)
        layer_dir = UNPACK / layer_sha
        gunzip(BLOBS / layer_sha, layer_dir / "layer.tar")
        layer_paths.append(f"{layer_sha}/layer.tar")

    (UNPACK / "manifest.json").write_text(
        json.dumps([{"Config": config_name, "RepoTags": [LOCAL_NAME], "Layers": layer_paths}]),
        encoding="utf-8",
    )

    print(f"packing {ARCHIVE}", flush=True)
    with tarfile.open(ARCHIVE, "w") as tar:
        tar.add(UNPACK / "manifest.json", arcname="manifest.json")
        tar.add(UNPACK / config_name, arcname=config_name)
        for layer in LAYERS:
            layer_sha = sha(layer)
            tar.add(UNPACK / layer_sha / "layer.tar", arcname=f"{layer_sha}/layer.tar")

    print("docker load ...", flush=True)
    subprocess.check_call(["docker", "load", "-i", str(ARCHIVE)])
    print("loaded", LOCAL_NAME, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
