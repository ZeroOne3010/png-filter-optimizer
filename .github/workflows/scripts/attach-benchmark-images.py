#!/usr/bin/env python3
"""Upload local benchmark image references as GitHub comment attachments."""

from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

IMAGE_PATTERN = re.compile(r"!\[([^\]]*)\]\((?!data:|https?://)([^)]+\.png)\)")
UPLOADED_PATTERN = re.compile(r"!\[[^\]]*\]\((https://github\.com/user-attachments/assets/[^)]+)\)")


def upload_image(path: Path, repo: str) -> str:
    result = subprocess.run(
        ["gh", "image", str(path), "--repo", repo],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    match = UPLOADED_PATTERN.search(result.stdout)
    if not match:
        raise RuntimeError(f"Could not parse gh-image output for {path}: {result.stdout!r}")
    return match.group(1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--markdown", type=Path, required=True)
    parser.add_argument("--base-dir", type=Path, required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()

    markdown_path = args.markdown
    base_dir = args.base_dir
    markdown = markdown_path.read_text(encoding="utf-8")
    replacements: dict[str, str] = {}

    for match in IMAGE_PATTERN.finditer(markdown):
        relative_src = match.group(2)
        if relative_src not in replacements:
            local_path = (base_dir / relative_src).resolve()
            if not local_path.is_file():
                raise FileNotFoundError(f"Markdown image reference does not exist: {local_path}")
            replacements[relative_src] = upload_image(local_path, args.repo)

    for relative_src, uploaded_url in replacements.items():
        markdown = markdown.replace(f"]({relative_src})", f"]({uploaded_url})")

    markdown_path.write_text(markdown, encoding="utf-8")
    print(f"Uploaded {len(replacements)} benchmark image attachment(s).")


if __name__ == "__main__":
    main()
