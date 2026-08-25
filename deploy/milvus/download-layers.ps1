# Resume-friendly download of milvusdb/milvus:v2.4.9 layers, then docker load.
# Re-run the same command after any failure; completed files are skipped.

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent (Split-Path -Parent $Here)
$Out = Join-Path $Here "blobs"
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$Files = @(
    @{ Name = "2eb12b8537088637687f7f30f39a89743b43ecd70fb67073c08fb153df3e9dcc"; Size = 7158 },
    @{ Name = "7646c8da332499ae416b15479ce832db32e39a501c662e24324f595509a0d3db"; Size = 29533754 },
    @{ Name = "e071a2bda6d58eed0cc7ef7ee1d6189fdfca64c67a123dd1439aa744078ec8ef"; Size = 31036738 },
    @{ Name = "653cde9beea196094ab6385810ee2033ffe07faa7a08ee2bc55777c0f85f309a"; Size = 78164775 },
    @{ Name = "865722af4c96a77ef45b7c7005f5b8807d5ca1a578013b5b5813eaf22751f7b5"; Size = 28585 },
    @{ Name = "79099f9f1d08356c84e415fea1a02804202a9922ca91bb814ac43d1a414d81f4"; Size = 350454941 },
    @{ Name = "535376b75c84412fcbf99a8ce19c14e91f8a980d5eb1af34ceba2b3507a23af4"; Size = 9463 },
    @{ Name = "eb2f8229d37517248fc22b3a1fa7f22df0e1f35c02191a8ca61a3856e074fc9a"; Size = 9464 },
    @{ Name = "4f4fb700ef54461cfa02571ae0db9a0dc1e0cdb5577484a6d75e68dc38e8acc1"; Size = 32 }
)

foreach ($f in $Files) {
    $dest = Join-Path $Out $f.Name
    $have = 0
    if (Test-Path $dest) {
        $have = (Get-Item $dest).Length
    }
    if ($have -eq $f.Size) {
        Write-Host ("skip {0} ({1})" -f $f.Name.Substring(0, 12), $have)
        continue
    }
    $url = "https://dockerproxy.net/v2/milvusdb/milvus/blobs/sha256:$($f.Name)"
    Write-Host ("get {0} {1}/{2}" -f $f.Name.Substring(0, 12), $have, $f.Size)
    & curl.exe -L --retry 30 --retry-all-errors --retry-delay 3 -C - -o $dest $url
    if ($LASTEXITCODE -ne 0) {
        Write-Host ("curl exit {0}, re-run this script to resume" -f $LASTEXITCODE)
        exit $LASTEXITCODE
    }
    $got = (Get-Item $dest).Length
    if ($got -ne $f.Size) {
        Write-Host ("size mismatch {0} != {1}, re-run this script to resume" -f $got, $f.Size)
        exit 1
    }
}

Write-Host "all blobs ok, packing..."
$py = Join-Path $Root "scripts\load-milvus-image.py"
python $py
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host "done. next: docker image ls milvusdb/milvus"
