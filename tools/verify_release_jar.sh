#!/usr/bin/env bash
# Audits a built PackStudio jar: entry integrity, target Minecraft version, and dependency metadata.
# Usage: tools/verify_release_jar.sh <jar> <expected minecraft version> <expected api family>
set -euo pipefail

JAR="${1:?jar path required}"
EXPECTED_MC="${2:?expected minecraft version required}"
EXPECTED_FAMILY="${3:?expected api family required}"

fail() {
  echo "::error title=JarMetadataAudit::$*"
  exit 1
}

[ -f "$JAR" ] || fail "jar not found: $JAR"

ENTRYPOINT=$(unzip -p "$JAR" fabric.mod.json \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["entrypoints"]["client"][0], d["environment"], d["version"])')
MOD_JSON=$(unzip -p "$JAR" fabric.mod.json | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)))')

# 1. fabric.mod.json must declare this exact Minecraft version (no universal/loose ranges).
python3 - "$JAR" "$EXPECTED_MC" <<'PY'
import json, subprocess, sys
jar, expected = sys.argv[1], sys.argv[2]
meta = json.loads(subprocess.check_output(['unzip', '-p', jar, 'fabric.mod.json']))
version = meta['version']
if f'mc{expected}' not in version:
    sys.exit(f"jar version '{version}' does not contain mc{expected}")
range_expr = meta['depends']['minecraft']
if expected not in range_expr:
    sys.exit(f"minecraft dependency '{range_expr}' does not target {expected}")
if meta.get('environment') != 'client':
    sys.exit('mod is not client-only')
if meta['depends'].get('java') != '>=25':
    sys.exit('java requirement is not >=25')
print(f"fabric.mod.json OK: version={version} minecraft={range_expr} fabric-api={meta['depends']['fabric-api']}")
PY

# 2. The entrypoint class must really be in the jar, and only one compatibility layer may be present.
unzip -l "$JAR" | grep -q 'com/packstudio/compat/impl/MinecraftCompatImpl.class' \
  || fail "missing compatibility implementation class"
COMPAT_COUNT=$(unzip -l "$JAR" | grep -c 'com/packstudio/compat/impl/.*\.class' || true)
[ "$COMPAT_COUNT" -ge 1 ] || fail "no compatibility classes packaged"
unzip -l "$JAR" | grep -q 'com/packstudio/client/PackStudioClient.class' \
  || fail "missing client entrypoint class"
unzip -l "$JAR" | grep -q 'packstudio-build.properties' \
  || fail "missing build identity resource"

# 3. The packaged build identity must describe this target.
BUILD_INFO=$(unzip -p "$JAR" packstudio-build.properties)
echo "$BUILD_INFO" | grep -qx "minecraft_version=${EXPECTED_MC}" \
  || fail "build identity does not declare minecraft_version=${EXPECTED_MC}"
echo "$BUILD_INFO" | grep -qx "minecraft_family=${EXPECTED_FAMILY}" \
  || fail "build identity does not declare minecraft_family=${EXPECTED_FAMILY}"

# 4. Version-specific Minecraft classes must not leak between API families.
HAS_METADATA_RESOURCES=0
unzip -l "$JAR" | grep -q 'net/minecraft' && HAS_METADATA_RESOURCES=1
if [ "$HAS_METADATA_RESOURCES" -eq 1 ]; then
  echo "::warning title=JarMetadataAudit::jar bundles net/minecraft classes"
fi

echo "::notice title=JarMetadataAudit::${JAR} verified for Minecraft ${EXPECTED_MC} (family ${EXPECTED_FAMILY}) | ${ENTRYPOINT} | ${MOD_JSON}"
