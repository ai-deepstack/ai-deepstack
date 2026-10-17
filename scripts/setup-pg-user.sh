#!/usr/bin/env bash
# ====================================================================
# ai-deepstack PostgreSQL 角色初始化
# ====================================================================
# 创建角色 ai_deepstack，把 ai_deepstack 库的所有权交给它，使它对库内
# 所有对象（表 / 序列 / 视图 / 函数 / 触发器）拥有完整权限，无需逐对象
# GRANT。触发器没有独立所有权，跟随其所属的表。
#
# 用法（Ubuntu 服务器上，仓库根目录执行）：
#   DEEPSTACK_DB_PASSWORD='...' bash scripts/setup-pg-user.sh
#   不传环境变量则交互输入密码。
#
# 可覆盖的环境变量：
#   DB_NAME                数据库名，默认 ai_deepstack
#   DB_USER                角色名，默认 ai_deepstack
#   PG_SUPERUSER           超级用户名，默认 postgres
#   DEEPSTACK_DB_PASSWORD  角色密码
#
# 幂等：可重复执行。
# ====================================================================
set -euo pipefail

DB_NAME="${DB_NAME:-ai_deepstack}"
DB_USER="${DB_USER:-ai_deepstack}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP="$REPO_ROOT/sql/00_deepstack_bootstrap_pg.sql"

die() { echo "错误：$*" >&2; exit 1; }

# ---------- 前置检查 ----------
command -v psql >/dev/null || die "未找到 psql，请先安装 PostgreSQL 客户端"
[[ -f "$BOOTSTRAP" ]] || die "找不到建表脚本：$BOOTSTRAP"
sudo -n -u "$PG_SUPERUSER" true 2>/dev/null \
  || die "无法以 $PG_SUPERUSER 身份执行，请用 sudo 运行本脚本"

# ---------- 密码 ----------
if [[ -z "${DEEPSTACK_DB_PASSWORD:-}" ]]; then
  read -r -s -p "为角色 ${DB_USER} 设置密码: " DEEPSTACK_DB_PASSWORD
  echo
  read -r -s -p "再输入一次: " _confirm
  echo
  [[ "$DEEPSTACK_DB_PASSWORD" == "$_confirm" ]] || die "两次输入不一致"
fi
[[ -n "$DEEPSTACK_DB_PASSWORD" ]] || die "密码不能为空"

# SQL 字符串字面量里转义单引号
PW_SQL="${DEEPSTACK_DB_PASSWORD//\'/\'\'}"

# 以超级用户执行 SQL（走 unix socket + peer 认证）
psu() { sudo -u "$PG_SUPERUSER" psql -v ON_ERROR_STOP=1 "$@"; }

# ---------- 0. 版本检查 ----------
PG_MAJOR="$(psu -tAc 'SHOW server_version_num' | cut -c1-2)"
echo "==> PostgreSQL 服务端版本号前缀：${PG_MAJOR}（建表脚本要求 16+）"
[[ "$PG_MAJOR" -ge 16 ]] || die "PostgreSQL 版本过低，建表脚本要求 16+"

# ---------- 1. 角色 ----------
echo "==> 创建/更新角色 ${DB_USER}"
psu <<SQL
DO \$\$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${PW_SQL}';
    RAISE NOTICE '角色已存在，已更新密码';
  ELSE
    CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${PW_SQL}';
    RAISE NOTICE '已创建角色';
  END IF;
END
\$\$;
SQL

# ---------- 2. 数据库 ----------
echo "==> 创建数据库 ${DB_NAME}（OWNER ${DB_USER}）"
if psu -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -qx 1; then
  echo "    已存在，跳过创建"
else
  psu -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"
fi

# 库已存在时属主可能仍是超级用户，统一改过来
psu -d postgres -c "ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};"

# ---------- 3. 扩展（需要超级用户）----------
# pgvector / AGE 的 control 文件都没有 trusted = true，非超级用户建不了。
echo "==> 创建扩展（需要超级用户）"
psu -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS vector;"
psu -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

if psu -d "$DB_NAME" -tAc "SELECT 1 FROM pg_available_extensions WHERE name = 'age'" | grep -qx 1; then
  psu -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS age;"
else
  echo "    未检测到 AGE，跳过（需要知识图谱请先编译安装 AGE）"
fi

# ---------- 4. 以新角色跑建表脚本 ----------
# 用 TCP 连 127.0.0.1，走 pg_hba 的 scram-sha-256 规则。
# 这样建出来的对象属主天然是 ${DB_USER}，无需事后转移。
echo "==> 以 ${DB_USER} 身份执行建表脚本"
PGPASSWORD="$DEEPSTACK_DB_PASSWORD" psql \
  -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 -q -f "$BOOTSTRAP"

# ---------- 5. 转移已存在对象的所有权 ----------
# 仅当建表脚本此前被超级用户跑过、库里已有 postgres 名下的对象时才需要。
# 只处理 deepstack schema，不碰 public 下的扩展对象。
if psu -d "$DB_NAME" -tAc "SELECT 1 FROM pg_namespace WHERE nspname = 'deepstack'" | grep -qx 1; then
  echo "==> 转移 deepstack schema 及已有对象的所有权"
  psu -d "$DB_NAME" <<SQL
ALTER SCHEMA deepstack OWNER TO ${DB_USER};

DO \$\$
DECLARE
  r record;
BEGIN
  -- 表 / 分区表 / 序列 / 视图 / 物化视图；索引和触发器跟随其表
  FOR r IN
    SELECT c.relkind, c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'deepstack'
       AND c.relkind IN ('r', 'p', 'S', 'v', 'm')
  LOOP
    EXECUTE format('ALTER %s deepstack.%I OWNER TO %I',
      CASE r.relkind
        WHEN 'S' THEN 'SEQUENCE'
        WHEN 'v' THEN 'VIEW'
        WHEN 'm' THEN 'MATERIALIZED VIEW'
        ELSE 'TABLE'
      END, r.relname, '${DB_USER}');
  END LOOP;

  -- 触发器函数（update_time 之类）
  FOR r IN
    SELECT p.oid::regprocedure AS sig
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'deepstack'
  LOOP
    EXECUTE format('ALTER FUNCTION %s OWNER TO %I', r.sig, '${DB_USER}');
  END LOOP;
END
\$\$;
SQL
fi

# ---------- 6. 未来对象自动授权 ----------
# 默认权限按「创建者角色」记录：以后 ${PG_SUPERUSER} 在 deepstack 里新建的
# 对象会自动授权给 ${DB_USER}。不追溯已存在的对象（那些已由第 5 步接管）。
echo "==> 配置默认权限（超管将来新建的对象自动授权）"
psu -d "$DB_NAME" <<SQL
ALTER DEFAULT PRIVILEGES FOR ROLE ${PG_SUPERUSER} IN SCHEMA deepstack
  GRANT ALL ON TABLES TO ${DB_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${PG_SUPERUSER} IN SCHEMA deepstack
  GRANT ALL ON SEQUENCES TO ${DB_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${PG_SUPERUSER} IN SCHEMA deepstack
  GRANT ALL ON FUNCTIONS TO ${DB_USER};
SQL

# ---------- 7. 验证 ----------
echo
echo "==> 验证"
psu -d "$DB_NAME" -c \
  "SELECT tableowner, count(*) AS tables FROM pg_tables WHERE schemaname = 'deepstack' GROUP BY tableowner;"

psu -d "$DB_NAME" -c \
  "SELECT nspname, nspowner::regrole AS owner FROM pg_namespace WHERE nspname IN ('deepstack', 'public');"

echo "==> 用 ${DB_USER} 连接测试"
PGPASSWORD="$DEEPSTACK_DB_PASSWORD" psql \
  -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT current_user || ' 已连接，可见表数：' || count(*) FROM pg_tables WHERE schemaname = 'deepstack';"

echo
echo "完成。应用侧配置："
echo "  DEEPSTACK_DB_USER=${DB_USER}"
echo "  DEEPSTACK_DB_PASSWORD=<你设置的密码>"
echo "  DEEPSTACK_DB_NAME=${DB_NAME}"
echo
echo "后续按 sql/README.md 顺序补跑 01-06（同样以 ${DB_USER} 身份执行）。"
