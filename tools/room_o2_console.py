#!/usr/bin/env python3
"""Readable, read-only view of owner-requested O2 diagnostics in the client log."""
import argparse
import re
import sys
import time
from collections import defaultdict
from pathlib import Path

from lab_client import ROOT, read_status

HEADER = re.compile(r'\[[^\]]*?(\d{2}:\d{2}:\d{2})(?:\.\d+)?\].*(?:\[CHAT\]|\[RoomO2\]) Room O2 accounting: (\d+) cached rooms;')
ROW = re.compile(r'(?:\[CHAT\]|\[RoomO2\])\s+Room (\d+): pool (\d+) \| (\w+) \| supply ([0-9.]+) / demand ([0-9.]+) \| local ([0-9.]+) \| (\d+) sources / (\d+) rooms')
LABELS = {'SUPPLIED': 'SUPPORTED', 'LOW': 'NEAR CAPACITY', 'STRAINED': 'STRAINED',
          'TRAPPED_AIR': 'TRAPPED AIR / NO SUPPLY', 'NO_AIR': 'NO AIR',
          'BREATHING_SHORTAGE': 'O2 SHORTAGE / RESTORE SUPPLY', 'NO_OXYGEN': 'NO O2 / GAS RETAINED', 'NO_SEAL': 'NO SEAL', 'OPEN_AIR': 'OPEN AIR', 'WAITING': 'WAITING'}


def latest_budget(text):
    """Keep the last complete batch; never combine rows from separate reads."""
    complete = None
    pending = None
    for line in text.splitlines():
        if 'Starting integrated minecraft server version' in line:
            complete = pending = None
        head = HEADER.search(line)
        if head:
            pending = {'time': head[1], 'count': int(head[2]), 'rows': []}
            if pending['count'] == 0:
                complete = pending
                pending = None
            continue
        if pending is None:
            continue
        match = ROW.search(line)
        if match:
            pending['rows'].append(dict(room=int(match[1]), pool=int(match[2]), mode=match[3],
                supply=float(match[4]), demand=float(match[5]), local=float(match[6]),
                sources=int(match[7]), rooms=int(match[8])))
            if len(pending['rows']) == min(pending['count'], 32):
                complete = pending
                pending = None
    return complete


def number(value):
    text = f'{value:.2f}'
    return '0' if text in ('0.00', '-0.00') else text.rstrip('0').rstrip('.')


def render(sample, world):
    lines = [world.upper(), 'Last logged budget: ' + sample['time'],
             '1 unit = one player breathing. Flames shed after 10s overload. Furnaces auto-restart; other flames need relighting.', '']
    pools = defaultdict(list)
    for row in sample['rows']:
        pools[row['pool']].append(row)
    for pool, rows in sorted(pools.items()):
        r = rows[0]
        shortfall = r['demand'] - r['supply']
        balance = (f'Shortfall {number(shortfall)}' if shortfall > 0
                   else f'Spare {number(-shortfall)}')
        lines.append(f'Pool {pool}   Supply {number(r["supply"])} | Demand {number(r["demand"])} | {balance}')
        lines.append(f'  {r["sources"]} source(s), shared across {r["rooms"]} room(s)')
        for row in sorted(rows, key=lambda r: r['room']):
            label = LABELS.get(row['mode'], row['mode'])
            lines.append(f'  Room {row["room"]:<4} {label:<25} Local demand {number(row["local"])}')
        lines.append('')
    if not sample['rows']:
        lines.append('No cached room budgets yet.')
    if sample['count'] > 32:
        lines.append(f'{sample["count"] - 32} additional rooms omitted by the game diagnostic.')
    lines.append('Refresh values: click Read in Minecraft.')
    return '\n'.join(lines)


def read_log(path):
    # Bound reads; an incomplete batch at the beginning/end is ignored.
    with path.open('rb') as stream:
        stream.seek(0, 2)
        size = stream.tell()
        stream.seek(max(0, size - 2 * 1024 * 1024))
        return stream.read().decode('utf-8', errors='replace')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', required=True, help='Expected active world; never inferred')
    parser.add_argument('--log', type=Path, default=ROOT / 'run-lab/logs/latest.log')
    parser.add_argument('--watch', action='store_true', help='Refresh when a new complete budget is logged')
    args = parser.parse_args()
    last = None
    session = None
    try:
        while True:
            status = read_status(ROOT / 'run-lab/lab-bridge')
            if not status.get('worldLoaded') or args.world not in (status.get('worldName'), status.get('world')):
                raise RuntimeError('The requested world is not active; stopped rather than displaying another world.')
            if session is not None and session != status['session']:
                raise RuntimeError('World session changed. Start this view again after a fresh Read budgets click.')
            session = status['session']
            sample = latest_budget(read_log(args.log))
            output = render(sample, status['worldName']) if sample else (
                status['worldName'].upper() + '\nWaiting for a budget sample. Click Read in Minecraft.')
            if output != last:
                if args.watch and sys.stdout.isatty():
                    print('\033[2J\033[H', end='')
                print(output, flush=True)
                last = output
            if not args.watch:
                return 0
            time.sleep(1)
    except KeyboardInterrupt:
        return 0
    except (OSError, ValueError, RuntimeError) as error:
        print(f'O2 console: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
