#!/usr/bin/env python3
"""Regenerates the checksum block of a pinned consumer-contract manifest.

Run this ONLY as part of a reviewed contract-version update — the point of the recorded checksums
is that an unreviewed local edit to a fixture makes `ContractBundleIntegrityTest` fail.

    python3 tools/update-contract-manifest.py app/src/test/resources/contract/v1
"""
import hashlib
import json
import pathlib
import sys

BUNDLE = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/test/resources/contract/v1")
MANIFEST = BUNDLE / "manifest.json"

manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
resources = {}
for path in sorted(BUNDLE.rglob("*.json")):
    if path == MANIFEST:
        continue
    relative = path.relative_to(BUNDLE).as_posix()
    resources[relative] = "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()

manifest["resources"] = resources
MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n")
print(f"recorded {len(resources)} checksums in {MANIFEST}")
