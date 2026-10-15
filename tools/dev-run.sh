#!/usr/bin/env bash
# Development harness: runs Minecraft (offline/dev account) with the freshly built Lock agent.
# Requires `gradle :agent:jar` (which also prepares .mc-dev via :mapping:setupMinecraft).
# Usage: tools/dev-run.sh [extra Minecraft args...]   e.g. --quickPlaySingleplayer "Test World"
# Environment: LOCK_HOME (default .mc-dev/lock-home), MC_RUN_DIR (default .mc-dev/run)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${MC_VERSION:-1.21.11}"
MC="$ROOT/.mc-dev/$VER"
AGENT="$(ls "$ROOT"/agent/build/libs/lock-agent-*.jar | head -n1)"
RUN="${MC_RUN_DIR:-$ROOT/.mc-dev/run}"
export LOCK_HOME="${LOCK_HOME:-$ROOT/.mc-dev/lock-home}"
mkdir -p "$RUN" "$LOCK_HOME" "$MC/natives"
CP="$(paste -sd: "$MC/libraries.txt"):$MC/client.jar"
ASSET_INDEX="$(basename "$(ls "$MC"/assets/indexes/*.json | head -n1)" .json)"
exec java -Xmx2G -Dlock.home="$LOCK_HOME" \
  -Dorg.lwjgl.system.SharedLibraryExtractPath="$MC/natives" \
  -javaagent:"$AGENT" -cp "$CP" net.minecraft.client.main.Main \
  --username LockDev --version "$VER" --gameDir "$RUN" --assetsDir "$MC/assets" --assetIndex "$ASSET_INDEX" \
  --accessToken 0 --userType legacy --versionType release --width "${MC_WIDTH:-1280}" --height "${MC_HEIGHT:-720}" "$@"
