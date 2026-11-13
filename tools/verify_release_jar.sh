#!/usr/bin/env bash
# Audits a built PackStudio jar: entry integrity, target Minecraft version, and dependency metadata.
# Usage: tools/verify_release_jar.sh <jar> <expected minecraft version> <expected api family>
#
# Deliberately avoids `unzip | grep -q` pipelines: with `set -o pipefail` the early-exiting grep can
# SIGPIPE unzip and turn a successful check into a spurious failure.
set -euo pipefail

JAR="${1:?jar path required}"
EXPECTED_MC="${2:?expected minecraft version required}"
EXPECTED_FAMILY="${3:?expected api family required}"

fail() {
  echo "JAR AUDIT FAILED: $*"
  exit 1
}

[ -f "$JAR" ] || fail "jar not found: $JAR"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

unzip -l "$JAR" > "$WORKDIR/listing.txt" 2>&1 || fail "jar is not readable: $JAR"
unzip -p "$JAR" fabric.mod.json > "$WORKDIR/fabric.mod.json" 2>/dev/null || fail "fabric.mod.json missing from jar"
unzip -p "$JAR" packstudio-build.properties > "$WORKDIR/build.properties" 2>/dev/null \
  || fail "packstudio-build.properties missing from jar"

# 1. Mod metadata must target exactly this Minecraft version and remain client-only on Java 25.
python3 - "$WORKDIR/fabric.mod.json" "$EXPECTED_MC" <<'PY'
import json, sys
meta_path, expected = sys.argv[1], sys.argv[2]
meta = json.load(open(meta_path))
version = meta['version']
if f'mc{expected}' not in version:
    sys.exit(f"jar version '{version}' does not name Minecraft {expected}")
range_expr = meta['depends']['minecraft']
if expected not in range_expr:
    sys.exit(f"minecraft dependency '{range_expr}' does not target {expected}")
if meta.get('environment') != 'client':
    sys.exit('mod is not client-only')
if meta['depends'].get('java') != '>=25':
    sys.exit('java requirement is not >=25')
if meta['depends'].get('fabric-api', '*') == '*':
    sys.exit('fabric-api requirement has no lower bound')
print(f"fabric.mod.json OK: version={version} minecraft={range_expr} "
      f"fabric-api={meta['depends']['fabric-api']} java={meta['depends']['java']}")
PY

# 2. The packaged classes must include the entrypoint and exactly this build's compatibility layer.
grep -F 'com/packstudio/client/PackStudioClient.class' "$WORKDIR/listing.txt" >/dev/null \
  || fail "missing client entrypoint class"
grep -F 'com/packstudio/compat/impl/MinecraftCompatImpl.class' "$WORKDIR/listing.txt" >/dev/null \
  || fail "missing compatibility implementation class"
grep -F 'assets/packstudio/lang/en_us.json' "$WORKDIR/listing.txt" >/dev/null \
  || fail "missing bundled translations"

# 3. The packaged build identity must describe this target.
grep -Fx "minecraft_version=${EXPECTED_MC}" "$WORKDIR/build.properties" >/dev/null \
  || fail "build identity does not declare minecraft_version=${EXPECTED_MC}"
grep -Fx "minecraft_family=${EXPECTED_FAMILY}" "$WORKDIR/build.properties" >/dev/null \
  || fail "build identity does not declare minecraft_family=${EXPECTED_FAMILY}"

echo "JAR AUDIT PASSED: ${JAR} -> Minecraft ${EXPECTED_MC} (family ${EXPECTED_FAMILY})"
cat "$WORKDIR/build.properties"
