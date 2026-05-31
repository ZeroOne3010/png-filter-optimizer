#!/usr/bin/env python3
"""Publish benchmark reports and their image references to GitHub Pages."""

from __future__ import annotations

import argparse
import html
import re
import shutil
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

IMAGE_PATTERN = re.compile(r"!\[([^\]]*)\]\((?!data:|https?://)([^)]+\.png)\)")
INLINE_PATTERN = re.compile(r"(!?\[[^\]]*\]\([^)]+\)|`[^`]+`|\*\*[^*]+\*\*|</?strong>)")


@dataclass(frozen=True)
class Block:
    kind: str
    lines: tuple[str, ...]


@dataclass(frozen=True)
class Report:
    """A small Markdown document model that can also render a standalone HTML page."""

    blocks: tuple[Block, ...]

    @classmethod
    def parse(cls, markdown: str) -> "Report":
        lines = markdown.splitlines()
        blocks: list[Block] = []
        index = 0
        while index < len(lines):
            line = lines[index]
            if not line.strip():
                index += 1
                continue
            if line.startswith("#"):
                blocks.append(Block("heading", (line,)))
                index += 1
                continue
            if _is_supported_raw_html(line):
                blocks.append(Block("raw_html", (line,)))
                index += 1
                continue
            if line.startswith("- "):
                index = _collect(lines, index, lambda value: value.startswith("- "), "list", blocks)
                continue
            if line.startswith("|") and index + 1 < len(lines) and _is_table_separator(lines[index + 1]):
                index = _collect(lines, index, lambda value: value.startswith("|"), "table", blocks)
                continue
            if IMAGE_PATTERN.fullmatch(line):
                blocks.append(Block("image", (line,)))
                index += 1
                continue
            index = _collect(lines, index, lambda value: bool(value.strip()) and not _starts_block(value), "paragraph", blocks)
        return cls(tuple(blocks))

    def render_markdown(self) -> str:
        """Render the document back to Markdown for summaries and PR comments."""
        return "\n\n".join("\n".join(block.lines) for block in self.blocks) + "\n"

    def render_html(self, title: str) -> str:
        body = "\n".join(_render_block(block) for block in self.blocks)
        escaped_title = html.escape(title)
        return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escaped_title}</title>
<style>
body {{ color: #1f2328; font: 16px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0 auto; max-width: 1400px; padding: 2rem; }}
a {{ color: #0969da; }} table {{ border-collapse: collapse; display: block; max-width: 100%; overflow: auto; }}
th, td {{ border: 1px solid #d0d7de; padding: .35rem .7rem; }} th {{ background: #f6f8fa; }}
img {{ border: 1px solid #d0d7de; image-rendering: pixelated; max-width: 100%; }} code {{ background: #eff1f3; padding: .1rem .25rem; }}
</style>
</head>
<body>
{body}
</body>
</html>
"""


def _collect(lines: list[str], index: int, predicate, kind: str, blocks: list[Block]) -> int:
    collected: list[str] = []
    while index < len(lines) and predicate(lines[index]):
        collected.append(lines[index])
        index += 1
    blocks.append(Block(kind, tuple(collected)))
    return index


def _starts_block(line: str) -> bool:
    return line.startswith("#") or line.startswith("- ") or line.startswith("|") or _is_supported_raw_html(line) or bool(IMAGE_PATTERN.fullmatch(line))


def _is_supported_raw_html(line: str) -> bool:
    return line in {"<details>", "</details>"} or bool(re.fullmatch(r'<summary>[^<]+</summary>|<a id="[A-Za-z0-9._-]+"></a>', line))


def _is_table_separator(line: str) -> bool:
    return line.startswith("|") and all(re.fullmatch(r":?-{3,}:?", cell.strip()) for cell in line.strip("|").split("|"))


def _render_inline(text: str) -> str:
    rendered: list[str] = []
    position = 0
    for match in INLINE_PATTERN.finditer(text):
        rendered.append(html.escape(text[position:match.start()]))
        token = match.group(0)
        if token.startswith("!["):
            alt, src = re.fullmatch(r"!\[([^\]]*)\]\(([^)]+)\)", token).groups()
            rendered.append(f'<img alt="{html.escape(alt, quote=True)}" src="{html.escape(src, quote=True)}">')
        elif token.startswith("["):
            label, href = re.fullmatch(r"\[([^\]]*)\]\(([^)]+)\)", token).groups()
            rendered.append(f'<a href="{html.escape(href, quote=True)}">{html.escape(label)}</a>')
        elif token.startswith("`"):
            rendered.append(f"<code>{html.escape(token[1:-1])}</code>")
        elif token in {"<strong>", "</strong>"}:
            rendered.append(token)
        else:
            rendered.append(f"<strong>{html.escape(token[2:-2])}</strong>")
        position = match.end()
    rendered.append(html.escape(text[position:]))
    return "".join(rendered)


def _render_block(block: Block) -> str:
    if block.kind == "heading":
        marker, text = block.lines[0].split(" ", 1)
        level = min(len(marker), 6)
        heading_id = html.escape(text, quote=True)
        return f'<h{level} id="{heading_id}">{_render_inline(text)}</h{level}>'
    if block.kind == "raw_html":
        return block.lines[0]
    if block.kind == "list":
        return "<ul>\n" + "\n".join(f"<li>{_render_inline(line[2:])}</li>" for line in block.lines) + "\n</ul>"
    if block.kind == "table":
        rows = [[cell.strip() for cell in line.strip("|").split("|")] for line in block.lines]
        header = "".join(f"<th>{_render_inline(cell)}</th>" for cell in rows[0])
        body = "\n".join("<tr>" + "".join(f"<td>{_render_inline(cell)}</td>" for cell in row) + "</tr>" for row in rows[2:])
        return f"<table>\n<thead><tr>{header}</tr></thead>\n<tbody>\n{body}\n</tbody>\n</table>"
    if block.kind == "image":
        return f"<p>{_render_inline(block.lines[0])}</p>"
    return f"<p>{_render_inline(' '.join(block.lines))}</p>"


def safe_relative_path(raw_src: str) -> Path:
    normalized = raw_src.replace("\\", "/")
    path = Path(normalized)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Unsafe image path in markdown: {raw_src}")
    return path


def image_refs(markdown: str) -> list[str]:
    return list(dict.fromkeys(match.group(2) for match in IMAGE_PATTERN.finditer(markdown)))


def stage_report(markdown: str, base_dir: Path, pages_dir: Path, run_id: str) -> list[str]:
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

    title = f"PNG corpus benchmark report for run {run_id}"
    report = Report.parse(markdown)
    run_dir.joinpath("report.md").write_text(report.render_markdown(), encoding="utf-8")
    run_dir.joinpath("index.html").write_text(report.render_html(title), encoding="utf-8")
    pages_dir.joinpath("index.html").write_text(
        "<!doctype html>\n<meta charset=\"utf-8\">\n"
        "<title>Latest PNG corpus benchmark report</title>\n"
        f'<p>Latest PNG corpus benchmark report: <a href="{html.escape(run_id)}/">run {html.escape(run_id)}</a>.</p>\n',
        encoding="utf-8",
    )
    return copied


def rewrite_markdown(markdown: str, page_url: str, run_id: str) -> str:
    base_url = page_url.rstrip("/") + "/" + quote(run_id, safe="-._~") + "/"

    def replace(match: re.Match[str]) -> str:
        alt = match.group(1)
        src = safe_relative_path(match.group(2)).as_posix()
        return f"![{alt}]({base_url}{quote(src, safe='/-._~')})"

    report_link = f"[View this benchmark report on GitHub Pages]({base_url})"
    rewritten = IMAGE_PATTERN.sub(replace, Report.parse(markdown).render_markdown())
    return f"{report_link}\n\n{rewritten}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--markdown", type=Path, required=True)
    parser.add_argument("--base-dir", type=Path, required=True)
    parser.add_argument("--pages-dir", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--page-url", help="Rewrite markdown image references and add the report URL for summaries and comments.")
    args = parser.parse_args()

    markdown = args.markdown.read_text(encoding="utf-8")
    copied = stage_report(markdown, args.base_dir, args.pages_dir, args.run_id)
    if args.page_url:
        args.markdown.write_text(rewrite_markdown(markdown, args.page_url, args.run_id), encoding="utf-8")
    action = "staged and rewrote" if args.page_url else "staged"
    print(f"{action} benchmark report and {len(copied)} image(s) for GitHub Pages.")


if __name__ == "__main__":
    main()
