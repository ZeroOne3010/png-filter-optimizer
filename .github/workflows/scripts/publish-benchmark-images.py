#!/usr/bin/env python3
"""Stage benchmark image references for GitHub Pages and optionally rewrite markdown URLs."""

from __future__ import annotations

import argparse
import html
import re
import shutil
from pathlib import Path
from urllib.parse import quote

IMAGE_PATTERN = re.compile(r"!\[([^\]]*)\]\((?!data:|https?://)([^)]+\.png)\)")


def safe_relative_path(raw_src: str) -> Path:
    normalized = raw_src.replace("\\", "/")
    path = Path(normalized)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Unsafe image path in markdown: {raw_src}")
    return path


def image_refs(markdown: str) -> list[str]:
    refs: list[str] = []
    seen: set[str] = set()
    for match in IMAGE_PATTERN.finditer(markdown):
        src = match.group(2)
        if src not in seen:
            refs.append(src)
            seen.add(src)
    return refs


def stage_images(markdown: str, base_dir: Path, pages_dir: Path, run_id: str) -> list[str]:
    run_dir = pages_dir / run_id
    run_dir.mkdir(parents=True, exist_ok=True)
    copied: list[str] = []

    for src in image_refs(markdown):
        relative_path = safe_relative_path(src)
        source = (base_dir / relative_path).resolve()
        if not source.is_file():
            raise FileNotFoundError(f"Markdown image reference does not exist: {source}")
        destination = run_dir / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        copied.append(relative_path.as_posix())

    write_indexes(pages_dir, run_id, copied)
    return copied


def write_indexes(pages_dir: Path, run_id: str, copied: list[str]) -> None:
    pages_dir.mkdir(parents=True, exist_ok=True)
    (pages_dir / "index.html").write_text(
        "<!doctype html>\n"
        "<meta charset=\"utf-8\">\n"
        "<title>PNG corpus benchmark artifacts</title>\n"
        f"<p>Benchmark image artifacts for run <a href=\"{html.escape(run_id)}/\">{html.escape(run_id)}</a>.</p>\n",
        encoding="utf-8",
    )

    run_dir = pages_dir / run_id
    links = "\n".join(
        f'<li><a href="{quote(path, safe="/-._~")}">{html.escape(path)}</a></li>' for path in copied
    )
    run_dir.mkdir(parents=True, exist_ok=True)
    run_dir.joinpath("index.html").write_text(
        "<!doctype html>\n"
        "<meta charset=\"utf-8\">\n"
        f"<title>PNG corpus benchmark artifacts for run {html.escape(run_id)}</title>\n"
        f"<h1>PNG corpus benchmark artifacts for run {html.escape(run_id)}</h1>\n"
        f"<ul>{links}</ul>\n",
        encoding="utf-8",
    )


def rewrite_markdown(markdown: str, page_url: str, run_id: str) -> str:
    base_url = page_url.rstrip("/") + "/" + quote(run_id, safe="-._~") + "/"

    def replace(match: re.Match[str]) -> str:
        alt = match.group(1)
        src = safe_relative_path(match.group(2)).as_posix()
        return f"![{alt}]({base_url}{quote(src, safe='/-._~')})"

    return IMAGE_PATTERN.sub(replace, markdown)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--markdown", type=Path, required=True)
    parser.add_argument("--base-dir", type=Path, required=True)
    parser.add_argument("--pages-dir", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--page-url", help="Rewrite markdown image references to this deployed Pages base URL.")
    args = parser.parse_args()

    markdown_path = args.markdown
    markdown = markdown_path.read_text(encoding="utf-8")
    copied = stage_images(markdown, args.base_dir, args.pages_dir, args.run_id)

    if args.page_url:
        markdown_path.write_text(rewrite_markdown(markdown, args.page_url, args.run_id), encoding="utf-8")

    action = "staged and rewrote" if args.page_url else "staged"
    print(f"{action} {len(copied)} benchmark image(s) for GitHub Pages.")


if __name__ == "__main__":
    main()
