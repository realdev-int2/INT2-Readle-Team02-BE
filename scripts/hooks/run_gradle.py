#!/usr/bin/env python3
import os
import subprocess
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2]
GRADLEW = "gradlew.bat" if os.name == "nt" else "./gradlew"


def main() -> int:
    if not sys.argv[1:]:
        print("usage: run_gradle.py <task> [<task> ...]", file=sys.stderr)
        return 2

    env = os.environ.copy()
    env.setdefault("GRADLE_USER_HOME", str(BACKEND / ".gradle"))

    return subprocess.call([GRADLEW, *sys.argv[1:], "--no-daemon"], cwd=BACKEND, env=env)


if __name__ == "__main__":
    raise SystemExit(main())
