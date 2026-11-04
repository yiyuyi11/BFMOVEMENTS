param(
    [string]$Mods = "D:\USTBL\.minecraft\versions\Mechanomania-test-new\mods",
    [string]$Jar = $PSScriptRoot
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Mods)) {
    throw "找不到 mods 目录：$Mods"
}

$movement = Join-Path $Jar "battlefield-movement-1.5.1-neoforge-1.21.1.jar"
$bfops = Join-Path $Jar "battlefield-ops-0.8.30-handoff-neoforge-1.21.1.jar"
foreach ($file in @($movement, $bfops)) {
    if (-not (Test-Path $file)) {
        throw "缺少交付文件：$file"
    }
}

$backup = Join-Path $Mods ("bfops-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force $backup | Out-Null
Get-ChildItem $Mods -Filter "battlefield-ops-*.jar" -File | ForEach-Object {
    Copy-Item $_.FullName $backup -Force
    Remove-Item $_.FullName -Force
}
Get-ChildItem $Mods -Filter "battlefield-movement-*.jar" -File | Remove-Item -Force

Copy-Item $movement $Mods -Force
Copy-Item $bfops $Mods -Force

Write-Host "已安装："
Write-Host "  $movement"
Write-Host "  $bfops"
Write-Host "旧 BattlefieldOps 备份在：$backup"
Write-Host "Z = 趴下/起身（冲刺时飞扑），左 Alt = 滑铲，冲刺+潜行 = 滑铲。"
