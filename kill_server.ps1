# 終止所有 Java 進程（包括可能占用端口的 GameServer）
Write-Host "正在終止所有 Java 進程..." -ForegroundColor Yellow

Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "終止進程 ID: $($_.Id) - $($_.ProcessName)" -ForegroundColor Cyan
    Stop-Process -Id $_.Id -Force
}

Write-Host "完成！所有 Java 進程已終止。" -ForegroundColor Green
Write-Host "現在可以重新啟動 GameServer。" -ForegroundColor Green
