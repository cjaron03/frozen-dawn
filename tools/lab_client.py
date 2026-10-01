#!/usr/bin/env python3
"""Local runClientLab automation. No network access, automatic retries, or arbitrary commands."""
import argparse
import json
import os
from pathlib import Path
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


def read_status(root):
    try:
        status = json.loads((root / "status.json").read_text())
    except (OSError, ValueError) as error:
        raise RuntimeError("Bridge unavailable. Launch ./gradlew runClientLab in this checkout.") from error
    if status.get("schema") != 1 or not 0 <= time.time() * 1000 - status["heartbeat"] < 5000:
        raise RuntimeError("Bridge heartbeat is stale; confirm the correct client is running.")
    if Path(status["checkout"]).resolve() != ROOT:
        raise RuntimeError("Bridge belongs to a different checkout.")
    return status


def submit(root, status, world, operation, args):
    if not status["worldLoaded"]:
        raise RuntimeError("Open the requested singleplayer test world first.")
    if world not in (status["worldName"], status["world"]):
        raise RuntimeError(f"Wrong world: requested {world!r}, active {status['worldName']!r}.")
    ident = str(uuid.uuid4())
    now = int(time.time() * 1000)
    request = dict(schema=1, id=ident, session=status["session"], world=status["world"],
                   issuedAt=now, expiresAt=now + 30000, operation=operation, args=args)
    encoded = json.dumps(request)
    if len(encoded.encode()) > 16384:
        raise RuntimeError("Request exceeds 16 KiB.")
    request_path = root / "requests" / f"{ident}.json"
    temp = request_path.with_suffix(".tmp")
    with temp.open("x") as stream:
        stream.write(encoded)
    os.replace(temp, request_path)
    response_path = root / "responses" / request_path.name
    deadline = time.monotonic() + 36
    while time.monotonic() < deadline:
        if response_path.exists():
            response = json.loads(response_path.read_text())
            if response.get("id") != ident or response.get("request") != request:
                raise RuntimeError("Response did not match this exact request. Inspect the bridge files.")
            print(f"Evidence: {response_path}", file=sys.stderr)
            return response
        time.sleep(.1)
    raise RuntimeError(f"UNKNOWN outcome for {ident}; do not retry automatically. Inspect status, logs and {response_path}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", help="Required active world name or exact save path for every operation except status")
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("status", "snapshot", "maeve-status", "reload", "freeze", "unfreeze"):
        sub.add_parser(name)
    dump = sub.add_parser("maeve-dump"); dump.add_argument("--player")
    actor = sub.add_parser("architect-dump"); actor.add_argument("entity", help="Loaded Architect UUID from snapshot")
    scores = sub.add_parser("scores"); scores.add_argument("objective"); scores.add_argument("holders", nargs="+")
    function = sub.add_parser("function"); function.add_argument("name"); function.add_argument("--player")
    for name in ("step", "sprint"):
        sub.add_parser(name).add_argument("ticks", type=int)
    options = vars(parser.parse_args())
    command, world = options.pop("command"), options.pop("world")
    options = {key: value for key, value in options.items() if value is not None}
    root = ROOT / "run-lab" / "lab-bridge"
    try:
        status = read_status(root)
        if command == "status":
            result = status
        else:
            if not world:
                parser.error("--world is required; never infer the active test world")
            result = submit(root, status, world, command.replace("-", "_"), options)
        print(json.dumps(result, indent=2))
        if result.get("ok") is False:
            return 2
        if result.get("data", {}).get("commandOutcome") in ("FAILED", "ZERO_RESULT"):
            return 3
        return 0
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Lab bridge: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
