@echo off
set /p TUNNEL_KEY="请输入 Tunnel Key (tk_xxxxxx): "
set TARGET_URL=http://httpbin.org/post

echo ======================================================================
echo   Tunnel Agent 正在连接网关...
echo   密钥: %TUNNEL_KEY%
echo   转发目标: %TARGET_URL%
echo ======================================================================

java -jar target\tunnel-agent.jar --server=ws://localhost:8080/tunnel/connect --key=%TUNNEL_KEY% --target=%TARGET_URL%

pause
