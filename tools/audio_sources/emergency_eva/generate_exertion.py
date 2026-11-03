#!/usr/bin/env python3
"""Pitch-preserving faster CC0-derived breathing variant; never another vocal layer."""
from pathlib import Path
import hashlib
import subprocess

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'src/main/resources/assets/frozendawn/sounds/ambient/eva_emergency_breathing.ogg'
EXPECTED = 'cf9d3fdb1b64c7cae557f042edcf94d9f6eff2caed8b5fb89c0ef47063f33784'

def main():
    if hashlib.sha256(SOURCE.read_bytes()).hexdigest() != EXPECTED:
        raise SystemExit('Base breathing changed; review its source ledger before deriving the exertion variant.')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(SOURCE), '-map_metadata', '-1',
                    '-af', 'atempo=1.25,afade=t=in:d=0.04,areverse,afade=t=in:d=0.04,areverse',
                    '-ar', '48000', '-ac', '2', '-c:a', 'vorbis', '-strict', 'experimental', '-q:a', '4',
                    str(SOURCE.with_name('eva_emergency_breathing_fast.ogg'))], check=True)
    print('Created pitch-preserving faster emergency breathing variant.')

if __name__ == '__main__': main()
