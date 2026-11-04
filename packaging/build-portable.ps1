param(
    [Parameter(Mandatory=$true)][string]$PreparedJar,
    [string]$RuntimePath,
    [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (-not $OutputPath) { $OutputPath = Join-Path (Split-Path -Parent (Split-Path -Parent $project)) '发布/本草中药材管理平台.exe' }
$build = Join-Path $project ('work/exe-' + [Guid]::NewGuid().ToString('N'))
$payload = Join-Path $build 'payload'
New-Item -ItemType Directory -Force -Path $payload | Out-Null
Copy-Item -LiteralPath $PreparedJar -Destination (Join-Path $payload 'app.jar')
[IO.File]::WriteAllText((Join-Path $payload 'config.properties'), "server.url=https://lzhan.xyz/herb-api`n", [Text.UTF8Encoding]::new($false))
if ($RuntimePath) {
    Copy-Item -LiteralPath $RuntimePath -Destination (Join-Path $payload 'runtime') -Recurse
} else {
    & jlink --add-modules java.base,java.desktop,java.net.http,java.sql,java.naming,java.management,java.security.jgss,jdk.crypto.ec,jdk.unsupported,jdk.localedata,jdk.httpserver --strip-debug --no-man-pages --no-header-files --compress=2 --output (Join-Path $payload 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = Join-Path $build 'payload.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($payload, $archive, [IO.Compression.CompressionLevel]::Optimal, $false)
$buildId = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.Substring(0,16).ToLowerInvariant()
$source = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'PortableLauncher.cs')).Replace('__BUILD_ID__', $buildId)
$generated = Join-Path $build 'PortableLauncher.cs'
[IO.File]::WriteAllText($generated, $source, [Text.UTF8Encoding]::new($false))
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll ("/win32icon:" + (Join-Path $PSScriptRoot 'herb.ico')) ("/resource:" + $archive + ',payload.zip') ("/out:" + $OutputPath) $generated
if ($LASTEXITCODE -ne 0) { throw 'Portable launcher compilation failed' }
Write-Output ('PORTABLE_EXE=' + $OutputPath)
Write-Output ('PAYLOAD_BUILD=' + $buildId)
Write-Output ('PAYLOAD_PATH=' + $payload)
