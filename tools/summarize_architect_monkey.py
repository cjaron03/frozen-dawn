#!/usr/bin/env python3
"""Keep a readable stress matrix and gameplay XML alongside immutable per-case traces.
This is evidence collection, not a gate; architectMonkey still rejects failures.
"""
import argparse
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--required', type=Path, required=True)
    parser.add_argument('--artifacts', type=Path, required=True)
    args = parser.parse_args()
    args.artifacts.mkdir(parents=True, exist_ok=True)
    names = [line.split()[0] for line in args.required.read_text().splitlines() if line.strip() and not line.startswith('#')]
    cases = {}
    if args.report.is_file():
        shutil.copy2(args.report, args.artifacts / 'gametest-report.xml')
        try:
            cases = {case.get('name'): case for case in ET.parse(args.report).getroot().iter('testcase')}
        except ET.ParseError:
            pass
    summaries = {}
    for path in args.artifacts.glob('run-*/summary.json'):
        data = json.loads(path.read_text())
        summaries[data.get('context', {}).get('testName')] = (path, data)
    rows = []
    for name in names:
        case = cases.get(name)
        path, data = summaries.get(name, (None, {}))
        problem = None if case is None else next((case.find(tag) for tag in ('failure', 'error', 'skipped') if case.find(tag) is not None), None)
        result = 'MISSING' if case is None or not data else 'FAILED' if problem is not None or data.get('outcome') != 'PASSED' else 'PASSED'
        rows.append({'test': name, 'result': result, 'ticks': data.get('elapsedTicks'), 'destroyed': data.get('destroyedBlocks'),
                     'reason': problem.get('message', '') if problem is not None else data.get('reason', 'Missing test or trace'),
                     'report': None if path is None else str(path.relative_to(args.artifacts)),
                     'sourceSha256': data.get('build', {}).get('sourceSha256')})
    counts = {status: sum(row['result'] == status for row in rows) for status in ('PASSED', 'FAILED', 'MISSING')}
    lines = ['# Architect stress results', '', f"{counts['PASSED']} passed; {counts['FAILED']} failed; {counts['MISSING']} missing.", '',
             'Failures are findings, not expected passes. See each immutable report for the scenario seed, terrain hash, scripted events, and decision trace.', '',
             '| Test | Result | Ticks | Destroyed |', '| --- | --- | ---: | ---: |']
    for row in rows:
        label = f"[{row['test']}]({row['report']})" if row['report'] else row['test']
        lines.append(f"| {label} | {row['result']} | {row['ticks']} | {row['destroyed']} |")
    lines += ['', '## Findings', '']
    for row in rows:
        if row['result'] != 'PASSED': lines.append(f"- **{row['test']}**: {row['reason']}")
    (args.artifacts / 'matrix.json').write_text(json.dumps(rows, indent=2) + '\n')
    (args.artifacts / 'README.md').write_text('\n'.join(lines) + '\n')
    print(f"Architect stress: {counts['PASSED']} passed, {counts['FAILED']} failed, {counts['MISSING']} missing. Evidence: {args.artifacts / 'README.md'}")


if __name__ == '__main__': main()
