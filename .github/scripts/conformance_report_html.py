"""Renders a backend job's Failsafe JUnit XML reports as one self-contained HTML page next to them.

Usage: conformance_report_html.py <failsafe-reports directory> <title> <output html>

Every test case becomes a row; failures and errors keep their message and stack trace, folded, so a reviewer can read
the result from the artifact without a JUnit viewer. A missing or empty directory still yields a page saying so.
"""

import html
import pathlib
import sys
import xml.etree.ElementTree as ElementTree

STYLE = """
body { font-family: system-ui, sans-serif; margin: 2rem; color: #1f2328; }
table { border-collapse: collapse; width: 100%; }
th, td { border: 1px solid #d0d7de; padding: 0.35rem 0.6rem; text-align: left; vertical-align: top; }
th { background: #f6f8fa; }
.passed { color: #1a7f37; } .failed, .error { color: #cf222e; font-weight: bold; } .skipped { color: #9a6700; }
pre { white-space: pre-wrap; font-size: 0.8rem; background: #f6f8fa; padding: 0.5rem; }
"""


def outcome(case):
    for status in ("failure", "error", "skipped"):
        element = case.find(status)
        if element is not None:
            label = "failed" if status == "failure" else status
            detail = (element.get("message") or "") + "\n" + (element.text or "")
            return label, detail.strip()
    return "passed", ""


def row(case):
    label, detail = outcome(case)
    body = f"<details><summary>details</summary><pre>{html.escape(detail)}</pre></details>" if detail else ""
    return label, (f"<tr><td>{html.escape(case.get('name', ''))}</td><td>{html.escape(case.get('classname', ''))}</td>"
                   f"<td>{html.escape(case.get('time', ''))}</td><td class='{label}'>{label}{body}</td></tr>")


def main():
    reports, title, output = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
    rows, failed = [], 0
    for report in sorted(reports.glob("TEST-*.xml")):
        for case in ElementTree.parse(report).getroot().iter("testcase"):
            label, markup = row(case)
            failed += label in ("failed", "error")
            rows.append(markup)
    if rows:
        content = (f"<p>{len(rows)} tests, {failed} failed or in error.</p>"
                   "<table><tr><th>Test case</th><th>Class</th><th>Time (s)</th><th>Result</th></tr>"
                   + "".join(rows) + "</table>")
    else:
        content = "<p>No JUnit XML report was produced.</p>"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        f"<!DOCTYPE html><html><head><meta charset='utf-8'><title>{html.escape(title)}</title>"
        f"<style>{STYLE}</style></head><body><h1>{html.escape(title)}</h1>{content}</body></html>",
        encoding="utf-8")


if __name__ == "__main__":
    main()
