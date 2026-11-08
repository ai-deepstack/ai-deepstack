#!/usr/bin/env bash
# 把公版古籍导入服务器 ZHOUYI 知识库。
#
# 用法:
#   bash deploy-import.sh              # 导入 defaults.txt 里的全部文件
#   bash deploy-import.sh <文件>...     # 导入指定文件
#
# 依赖: 本地 node + ssh + scp；服务器 curl + python3

set -euo pipefail

SERVER="ubuntu@140.143.233.246"
KB_ID="2112421438227316737"
REMOTE_DIR="/home/ubuntu/kb-import"
SSH="ssh -o StrictHostKeyChecking=no"
SCP="scp -o StrictHostKeyChecking=no -q"

if [[ $# -gt 0 ]]; then
  FILES=("$@")
else
  mapfile -t FILES < <(grep -v '^#' defaults.txt | grep -v '^$')
fi

echo "待导入 ${#FILES[@]} 个文件"

$SSH "$SERVER" "mkdir -p $REMOTE_DIR"

# 一次登录，token 缓存到服务器
$SSH "$SERVER" 'curl -s -X POST http://127.0.0.1:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"token\"])"' \
  > /tmp/kb_token

if [[ ! -s /tmp/kb_token ]]; then
  echo "登录失败" >&2
  exit 1
fi
echo "已登录"

# 上传 token
$SCP /tmp/kb_token "$SERVER:$REMOTE_DIR/token"

ok=0
skip=0
fail=0

for file in "${FILES[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "  跳过（本地不存在）: $file" >&2
    continue
  fi

  name=$(basename "$file" .md)
  $SCP "$file" "$SERVER:$REMOTE_DIR/current.md"

  result=$($SSH "$SERVER" "cd $REMOTE_DIR && python3 - <<'PY'
import json, os, urllib.request

KB_ID = '2112421438227316737'
NAME = '$name'
TOKEN = open('token').read().strip()

# 已存在则跳过
req = urllib.request.Request(
    'http://127.0.0.1:8088/api/kb/docs/page?pageNum=1&pageSize=200&knowledgeBaseId=' + KB_ID,
    headers={'Authorization': 'Bearer ' + TOKEN})
existing = json.load(urllib.request.urlopen(req))['data']['list']
if any(d.get('title') == NAME for d in existing):
    print('SKIP')
    raise SystemExit

content = open('current.md', encoding='utf-8').read()
payload = json.dumps({
    'knowledgeBaseId': int(KB_ID),
    'title': NAME,
    'sourceType': 0,
    'rawContent': content,
    'tags': '周易,古籍,公有领域',
}, ensure_ascii=False).encode('utf-8')

req = urllib.request.Request(
    'http://127.0.0.1:8088/api/kb/docs', data=payload, method='POST',
    headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'})
resp = json.load(urllib.request.urlopen(req))
print('OK' if resp.get('code') == 200 else 'FAIL:' + str(resp.get('message')))
PY")

  size=$(stat -c%s "$file")
  case "$result" in
    SKIP) echo "  跳过（已存在）: $name"; skip=$((skip+1)) ;;
    OK)   echo "  已导入: $name（$size 字符）"; ok=$((ok+1)) ;;
    *)    echo "  失败: $name -> $result" >&2; fail=$((fail+1)) ;;
  esac
done

echo
echo "完成：导入 $ok，跳过 $skip，失败 $fail"
