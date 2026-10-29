#!/usr/bin/env python3
"""Validate gameplay results and immutable Architect evidence, not just a process exit code."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def read_report(report):
    if not report.is_file(): raise ValueError(f'No completed GameTest report: {report}')
    root = ET.parse(report).getroot()
    cases = list(root.iter('testcase'))
    if not cases: raise ValueError(f'GameTest report contains zero tests: {report}')
    for problem in ('failure', 'error', 'skipped'):
        found = list(root.iter(problem))
        if found: raise ValueError(f'{len(found)} {problem} result(s): ' + '; '.join(e.get('message', '') for e in found))
    for element in root.iter():
        for attr in ('failures', 'errors', 'skipped'):
            if int(element.get(attr, '0')): raise ValueError(f'Report declares {attr}={element.get(attr)}')
    return cases


def check_shards(reports, cases_by_report):
    """Shards must together run every test of one test list, each exactly once (GameTestPacing#keepShard)."""
    shards = []
    for report, cases in zip(reports, cases_by_report):
        manifest = report.with_name(report.name + '.shard')
        if not manifest.is_file(): raise ValueError(f'No shard manifest beside {report}')
        shard = dict(line.split('=', 1) for line in manifest.read_text().splitlines() if '=' in line)
        if len({case.get('name') for case in cases}) != int(shard['kept']):
            raise ValueError(f'Shard {shard["index"]} kept {shard["kept"]} tests but reported a different set')
        shards.append(shard)
    if len({(s['count'], s['total'], s['all']) for s in shards}) != 1: raise ValueError('Shards split different test lists')
    if sorted(int(s['index']) for s in shards) != list(range(int(shards[0]['count']))) or len(shards) != int(shards[0]['count']):
        raise ValueError('Shard reports are missing or repeated')
    names = [case.get('name') for cases in cases_by_report for case in cases]
    if sum(int(s['kept']) for s in shards) != int(shards[0]['total']) or len(set(names)) != int(shards[0]['total']):
        raise ValueError('Shards do not cover every test exactly once')


def verify(reports, requirements, artifacts, fingerprint):
    reports = [reports] if isinstance(reports, Path) else list(reports)
    cases_by_report = [read_report(report) for report in reports]
    if len(reports) > 1 or any(report.with_name(report.name + '.shard').exists() for report in reports):
        check_shards(reports, cases_by_report)
    cases = [case for found in cases_by_report for case in found]
    required = {}
    for line in requirements.read_text().splitlines():
        if not line.strip() or line.startswith('#'): continue
        name, evidence = line.split()
        if name in required or evidence not in ('trace', 'xml'): raise ValueError('Invalid required-test manifest')
        required[name] = evidence
    if not required: raise ValueError('Required Architect test manifest is empty')
    names = [case.get('name') for case in cases]
    missing = set(required) - set(names)
    if missing: raise ValueError('Required Architect tests did not run: ' + ', '.join(sorted(missing)))
    for name in required:
        if names.count(name) != 1: raise ValueError(f'Expected exactly one completed result for {name}')
    summaries = {}
    for path in artifacts.rglob('summary.json'):
        data = json.loads(path.read_text())
        name = data.get('context', {}).get('testName')
        if name not in required: continue
        if data.get('outcome') != 'PASSED': raise ValueError(f'{name}: report outcome is not PASSED')
        if data.get('build', {}).get('sourceSha256') != fingerprint: raise ValueError(f'{name}: stale build fingerprint')
        trace = path.parent / 'decisions.tsv'
        if not trace.is_file() or hashlib.sha256(trace.read_bytes()).hexdigest() != data.get('traceSha256'):
            raise ValueError(f'{name}: missing or mismatched trace')
        if data.get('retained', 0) <= 0: raise ValueError(f'{name}: empty decision trace')
        if not data.get('context', {}).get('initialTerrainSha256'): raise ValueError(f'{name}: missing starting terrain fingerprint')
        summaries[name] = data
    for name, evidence in required.items():
        if evidence == 'trace' and name not in summaries: raise ValueError(f'{name}: missing run summary')
    return len(cases), len(required)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, action='append', required=True, help='repeat once per shard')
    parser.add_argument('--required', type=Path, required=True)
    parser.add_argument('--artifacts', type=Path, required=True)
    parser.add_argument('--build-info', type=Path, required=True)
    args = parser.parse_args()
    props = dict(line.split('=', 1) for line in args.build_info.read_text().splitlines() if '=' in line)
    try:
        count, required = verify(args.report, args.required, args.artifacts, props['sourceSha256'])
    except (ValueError, OSError, ET.ParseError, KeyError) as error:
        raise SystemExit(f'Architect gate FAILED: {error}')
    print(f'Architect gate passed: {count} GameTests; all {required} required Architect cases and reports verified.')
    print(f'Artifacts: {args.artifacts}')


if __name__ == '__main__': main()
