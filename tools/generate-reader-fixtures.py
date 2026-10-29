#!/usr/bin/env python3
"""Regenerates the PDF fixtures the in-app reader is tested against.

    python3 tools/generate-reader-fixtures.py app/src/test/resources/reader

Needs PyMuPDF (`pip install pymupdf`). The fixtures are checked in, so this only has to run when a
case is added or changed. They are small real documents rather than byte strings written in a test,
because the encrypted cases only mean anything if a real PDF engine produced them: a hand-written
`/Encrypt` entry would test the test.

The same directory is on the instrumentation classpath (see `androidTest`'s resource dirs in
app/build.gradle.kts), so the on-device tests open exactly these files with the platform renderer.
"""

import pathlib
import sys

import fitz  # PyMuPDF

PAGE_COUNT = 3


def build_document() -> fitz.Document:
    doc = fitz.open()
    for index in range(PAGE_COUNT):
        # Letter-sized, each page saying which page it is, so a test that renders page N can tell
        # it is not looking at page 0.
        page = doc.new_page(width=612, height=792)
        page.insert_text((72, 96), f"Reader fixture page {index + 1} of {PAGE_COUNT}", fontsize=18)
    return doc


def main(out_dir: pathlib.Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)

    # The plain readable case.
    build_document().save(out_dir / "three-pages.pdf", garbage=4, deflate=True)

    # Opening needs a password: the platform renderer throws SecurityException for it.
    build_document().save(
        out_dir / "encrypted-user-password.pdf",
        encryption=fitz.PDF_ENCRYPT_AES_256,
        user_pw="reader-fixture",
        owner_pw="reader-fixture-owner",
    )

    # Encrypted with an owner password only, which restricts printing and copying but opens without
    # one. Common in published papers, and it must stay readable: classifying every `/Encrypt`
    # dictionary as "encrypted" would refuse documents the renderer opens fine.
    build_document().save(
        out_dir / "encrypted-owner-only.pdf",
        encryption=fitz.PDF_ENCRYPT_AES_256,
        owner_pw="reader-fixture-owner",
        permissions=fitz.PDF_PERM_ACCESSIBILITY,
    )

    # A transfer cut short: the signature is there, the end of the file is not.
    whole = (out_dir / "three-pages.pdf").read_bytes()
    (out_dir / "truncated.pdf").write_bytes(whole[: len(whole) // 2])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(pathlib.Path(sys.argv[1]))
