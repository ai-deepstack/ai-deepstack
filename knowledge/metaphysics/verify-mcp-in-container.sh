#!/bin/bash
# 在 ai-deepstack-app 容器内验证 metaphysics-mcp 可用
set -e

echo "=== node ==="
node -v
echo "=== 全局包 ==="
npm ls -g --depth=0 2>/dev/null | grep metaphysics || echo "(未全局安装)"
echo "=== MCP tools/list ==="

REQ='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

printf '%s\n' "$REQ" | timeout 25 npx -y metaphysics-mcp 2>/dev/null | head -c 400
echo
echo "=== 调用 bazi_chart ==="

CALL='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"bazi_chart","arguments":{"birthDate":"1990-05-12","birthTime":"14:30","gender":"male"}}}'

printf '%s\n' "$CALL" | timeout 25 npx -y metaphysics-mcp 2>/dev/null | tail -1 | head -c 300
