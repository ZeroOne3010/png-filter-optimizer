import importlib.util
import sys
from pathlib import Path
import tempfile
import unittest

_SPEC = importlib.util.spec_from_file_location("publish_benchmark_images", Path(__file__).with_name("publish-benchmark-images.py"))
_MODULE = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _MODULE
_SPEC.loader.exec_module(_MODULE)
Report = _MODULE.Report
rewrite_markdown = _MODULE.rewrite_markdown
stage_report = _MODULE.stage_report


class PublishBenchmarkReportTest(unittest.TestCase):
    def test_stages_images_and_renders_structured_html_report(self):
        markdown = "## Benchmark summary\n\n| Image | Best |\n|---|---|\n| icon.png | adaptive |\n\n### icon.png\n\n- adaptive: 3 ms\n\n![](filter-visualizations/icon.filters.png)\n"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            image = root / "reports/filter-visualizations/icon.filters.png"
            image.parent.mkdir(parents=True)
            image.write_bytes(b"png")
            copied = stage_report(markdown, root / "reports", root / "pages", "123")
            report = (root / "pages/123/index.html").read_text()
            self.assertEqual(["filter-visualizations/icon.filters.png"], copied)
            self.assertIn("<h2>Benchmark summary</h2>", report)
            self.assertIn("<table>", report)
            self.assertTrue((root / "pages/123/report.md").is_file())
            self.assertIn("<li>adaptive: 3 ms</li>", report)
            self.assertTrue((root / "pages/123/filter-visualizations/icon.filters.png").is_file())

    def test_rewrite_adds_report_link_and_remote_image_urls(self):
        markdown = "![](filter-visualizations/icon filters.png)\n"
        rewritten = rewrite_markdown(markdown, "https://example.test/site/", "run 1")
        self.assertIn("[View this benchmark report on GitHub Pages](https://example.test/site/run%201/)", rewritten)
        self.assertIn("![](https://example.test/site/run%201/filter-visualizations/icon%20filters.png)", rewritten)

    def test_report_parser_keeps_paragraphs_structured(self):
        report = Report.parse("## Heading\n\nOne line\ncontinued.\n")
        self.assertEqual(("heading", "paragraph"), tuple(block.kind for block in report.blocks))
        self.assertEqual("## Heading\n\nOne line\ncontinued.\n", report.render_markdown())


if __name__ == "__main__":
    unittest.main()
