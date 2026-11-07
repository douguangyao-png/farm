#!/usr/bin/env bash
# 在服务器上编译真后端（芋道 + yudao-module-farm）。不在本机编译（本机内存紧），也不影响线上服务：
# - 源码同步到 /opt/farmer-saas/backend-src（不含 target），产物 jar 在 backend-src/yudao-server/target/
# - 用独立的 Maven 仓库 /opt/farmer-saas/backend/m2（不动系统的 ~/.m2），阿里云镜像
# - nice 降优先级，Maven 堆限制 1.5G，避免挤占 training / ziheng
# 用法：bash deploy/backend-build.sh [模块参数，默认整个 yudao-server 及依赖]
set -euo pipefail

HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }

tar -czf "$TMP/backend.tgz" -C "$ROOT" --exclude=target --exclude=node_modules backend
$SCP "$TMP/backend.tgz" "$HOST:/tmp/farm-backend-src.tgz" 2>&1 | quiet
rm -rf "$TMP"

$SSH "$HOST" 'bash -s' <<'REMOTE' 2>&1 | quiet
set -e
B=/opt/farmer-saas
mkdir -p $B/backend/m2 $B/backend-src
# 保留 target（增量编译），只替换源码
cd $B/backend-src
find . -mindepth 1 -maxdepth 1 ! -name backend -exec rm -rf {} + 2>/dev/null || true
tar -xzf /tmp/farm-backend-src.tgz && rm -f /tmp/farm-backend-src.tgz
cd backend
cat > $B/backend/settings.xml <<'XML'
<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>
XML
export MAVEN_OPTS="-Xmx1536m"
echo "== mvn package（跳过测试），$(java -version 2>&1 | head -1)；Maven 实际用：$(mvn -v | grep 'Java version')"
nice -n 10 mvn -B -q -s $B/backend/settings.xml -Dmaven.repo.local=$B/backend/m2 -DskipTests -pl yudao-server -am package 2>&1 | tail -40
ls -l yudao-server/target/*.jar 2>/dev/null || echo "没有产出 jar"
REMOTE
