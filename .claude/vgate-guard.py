#!/usr/bin/env python3
"""Stop: warn when this repo opted into vgate but the plugin's hooks never ran in this session.

The gates live in the vgate plugin; if it is not installed or not loaded, they vanish without a
signal (gap-log G33). Every vgate hook call leaves a per-session heartbeat, so a missing one at
the end of a turn means the session is ungated. Deliberately independent of the plugin's code,
and a warning rather than a block: reviewers without the plugin must still be able to work.
"""
import json
import os
import subprocess
import sys

CONFIG_FILE = os.path.join(".claude", "vgate.json")


def alive_dir():
    return os.environ.get("VGATE_ALIVE_DIR") or os.path.join(os.path.expanduser("~"), ".claude", "vgate", "alive")


def main():
    data = json.load(sys.stdin)
    cwd = data.get("cwd", ".")
    root = subprocess.run(["git", "-C", cwd, "rev-parse", "--show-toplevel"],
                          capture_output=True, text=True).stdout.strip()
    session_id = data.get("session_id")
    if not root or not session_id or not os.path.exists(os.path.join(root, CONFIG_FILE)):
        return
    if os.path.exists(os.path.join(alive_dir(), session_id)):
        return
    print(json.dumps({"systemMessage": "⚠ vgate plugin hook이 이 세션에서 한 번도 실행되지 않음 — 완료·PR 게이트 없이 진행 중. "
                                       "~/.claude/skills/vgate 설치와 /reload-plugins(또는 새 세션)를 확인할 것"},
                     ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # a broken guard must be visible, not silent
        print(json.dumps({"systemMessage": f"⚠ vgate-guard 오류: {error}"}, ensure_ascii=False))
