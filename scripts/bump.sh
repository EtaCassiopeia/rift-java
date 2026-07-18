#!/usr/bin/env bash
#
# The rift-java-specific edit half of the dependency-bump loop, invoked by the reusable
# .github/workflows/dep-bump.yml. Keeps ALL knowledge of which files pin the engine version in one
# place, so the reusable workflow stays generic.
#
#   scripts/bump.sh --current        print the currently pinned engine version (bare, no leading v)
#   scripts/bump.sh <new-version>    rewrite every pin literal to <new-version> and self-verify
#
# <rift.engine.version> in pom.xml is the source of truth (natives fetch, embedded spawn default,
# version preflight). A few files hard-code the pinned version as drift-catchers — a testcontainers
# test asserts the resolved version + proxy image tag, and the conformance README shows the corpus
# path — so they are bumped together and the whole change is verified, keeping the auto-PR green.

set -euo pipefail

PIN_FILE="pom.xml"
RIFT_CONTAINER_TEST="rift-java-testcontainers/src/test/java/io/github/etacassiopeia/rift/testcontainers/RiftContainerTest.java"
CONFORMANCE_README="rift-java-conformance/README.md"

current() {
  # The value inside <rift.engine.version>...</rift.engine.version>; every other <...version> is left
  # alone. Portable sed (GNU + BSD) so the script runs locally too, not just on GNU-grep CI.
  sed -n 's|.*<rift\.engine\.version>\([^<]*\)</rift\.engine\.version>.*|\1|p' "${PIN_FILE}" | head -n1
}

if [ "${1:-}" = "--current" ]; then
  current
  exit 0
fi

NEW="${1:?usage: bump.sh --current | bump.sh <new-version>}"
CURRENT="$(current)"
if [ -z "${CURRENT}" ]; then
  echo "Could not read <rift.engine.version> from ${PIN_FILE}." >&2
  exit 1
fi

# -i.bak is portable across GNU (CI) and BSD (local) sed; drop the backups afterwards.
sed -i.bak "s|<rift.engine.version>${CURRENT}</rift.engine.version>|<rift.engine.version>${NEW}</rift.engine.version>|" "${PIN_FILE}"
# The ENGINE_VERSION assertion and the proxy image tag (anchored so unrelated strings can't match).
sed -i.bak "s|assertEquals(\"${CURRENT}\", RiftContainer.ENGINE_VERSION|assertEquals(\"${NEW}\", RiftContainer.ENGINE_VERSION|" "${RIFT_CONTAINER_TEST}"
sed -i.bak "s|zainalpour/rift-proxy:v${CURRENT}|zainalpour/rift-proxy:v${NEW}|g" "${RIFT_CONTAINER_TEST}"
# The version-locked conformance corpus path (all occurrences).
sed -i.bak "s|sdk-conformance-v${CURRENT}|sdk-conformance-v${NEW}|g" "${CONFORMANCE_README}"
rm -f "${PIN_FILE}.bak" "${RIFT_CONTAINER_TEST}.bak" "${CONFORMANCE_README}.bak"

# Fail loudly if any target still carries the old version (a rename/format drift would otherwise ship a
# half-bumped, red PR).
if grep -q "<rift.engine.version>${CURRENT}</rift.engine.version>" "${PIN_FILE}" \
   || grep -q "\"${CURRENT}\", RiftContainer.ENGINE_VERSION" "${RIFT_CONTAINER_TEST}" \
   || grep -q "rift-proxy:v${CURRENT}" "${RIFT_CONTAINER_TEST}" \
   || grep -q "sdk-conformance-v${CURRENT}" "${CONFORMANCE_README}"; then
  echo "Failed to fully bump ${CURRENT} -> ${NEW}; some version-tracking literal was not updated." >&2
  exit 1
fi

echo "Bumped rift engine ${CURRENT} -> ${NEW}"
