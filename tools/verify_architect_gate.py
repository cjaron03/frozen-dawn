#!/usr/bin/env python3
"""Validate gameplay results and immutable Architect evidence, not just a process exit code."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def verify(report, requirements, artifacts, fingerprint):
    if not report.is_file(): raise ValueError(f'No completed GameTest report: {report}')
    root = ET.parse(report).getroot()
    cases = list(root.iter('testcase'))
    if not cases: raise ValueError('GameTest report contains zero tests')
    for problem in ('failure', 'error', 'skipped'):
        found = list(root.iter(problem))
        if found: raise ValueError(f'{len(found)} {problem} result(s): ' + '; '.join(e.get('message', '') for e in found))
    for element in root.iter():
        for attr in ('failures', 'errors', 'skipped'):
            if int(element.get(attr, '0')): raise ValueError(f'Report declares {attr}={element.get(attr)}')
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
    parser.add_argument('--report', type=Path, required=True)
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
