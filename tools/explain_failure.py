#!/usr/bin/env python3
"""Condenses a Gradle build log into one actionable GitHub error annotation.

GitHub surfaces only a handful of annotations per step, so this prints a single compact payload that
starts with compiler diagnostics and then the Gradle failure block (including JUnit failure summaries).
"""
import re
import sys


def main() -> int:
    path = sys.argv[1]
    target = sys.argv[2] if len(sys.argv) > 2 else "unknown"
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError as exc:
        print(f"::error title=BuildFailure({target})::log unreadable: {exc}")
        return 0

    lines = text.splitlines()
    picked: list[str] = []

    # 1. Compiler diagnostics, with the symbol/location lines that follow them.
    for i, line in enumerate(lines):
        if re.search(r"error: ", line):
            picked.append(line.strip())
            for follow in lines[i + 1:i + 3]:
                if re.match(r"\s+(symbol|location|required|found):", follow):
                    picked.append(follow.strip())

    # 2. JUnit failures.
    for line in lines:
        if re.match(r".*> .* FAILED$", line.strip()) or "AssertionFailedError" in line:
            picked.append(line.strip())

    # 3. Gradle "What went wrong" block and unresolved dependencies.
    for i, line in enumerate(lines):
        if "What went wrong" in line or "Could not resolve" in line:
            picked.extend(item.strip() for item in lines[i:i + 6])

    if not picked:
        picked = [line.strip() for line in lines[-40:] if line.strip()]

    seen: set[str] = set()
    unique = [line for line in picked if line and not (line in seen or seen.add(line))]
    payload = " | ".join(unique)
    print(f"::error title=BuildFailure({target})::{payload[:3000]}")

    # Also echo a readable tail so `jobs` API consumers (and humans) can see it in the step summary.
    print("\n".join(unique[:40]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
