import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from tools.verify_architect_gate import verify


class ArchitectGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.report = self.root / 'report.xml'
        self.required = self.root / 'required.txt'
        self.required.write_text('architectlab.case trace\n')
        self.report.write_text('<testsuite><testcase name="architectlab.case"/></testsuite>')
        trace = self.root / 'decisions.tsv'
        trace.write_text('tick\tevent\n0\tRUN_START\n')
        self.summary = self.root / 'summary.json'
        self.data = {'outcome': 'PASSED', 'context': {'testName': 'architectlab.case', 'initialTerrainSha256': 'terrain'},
                     'build': {'sourceSha256': 'build'}, 'retained': 1,
                     'traceSha256': hashlib.sha256(trace.read_bytes()).hexdigest()}
        self.summary.write_text(json.dumps(self.data))

    def check(self): return verify(self.report, self.required, self.root, 'build')
    def test_pass_requires_named_case_and_current_trace(self): self.assertEqual((1, 1), self.check())
    def test_missing_report_fails(self):
        self.report.unlink()
        with self.assertRaises(ValueError): self.check()
    def test_unrelated_green_test_is_not_sufficient(self):
        self.report.write_text('<testsuite><testcase name="unrelated"/></testsuite>')
        with self.assertRaises(ValueError): self.check()
    def test_errors_skips_and_failures_fail(self):
        for kind in ('failure', 'error', 'skipped'):
            with self.subTest(kind=kind):
                self.report.write_text(f'<testsuite><testcase name="architectlab.case"><{kind}/></testcase></testsuite>')
                with self.assertRaises(ValueError): self.check()
    def test_stale_summary_fails(self):
        self.data['build']['sourceSha256'] = 'old'
        self.summary.write_text(json.dumps(self.data))
        with self.assertRaises(ValueError): self.check()
    def test_missing_or_tampered_trace_fails(self):
        (self.root / 'decisions.tsv').write_text('another run')
        with self.assertRaises(ValueError): self.check()
    def test_missing_summary_fails(self):
        self.summary.unlink()
        with self.assertRaises(ValueError): self.check()
    def test_empty_report_fails(self):
        self.report.write_text('<testsuite/>')
        with self.assertRaises(ValueError): self.check()


if __name__ == '__main__': unittest.main()
