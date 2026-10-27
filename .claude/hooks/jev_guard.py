"""PreToolUse hook: ask Jev whether a Bash command replaces a mod jar while the game runs.

Code owns the decision; Jev only supplies the semantic judgment ("does this command
overwrite a jar in a mods folder?"). Fails open on any error so it never blocks work.
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

API_URL = "https://api.typesafe.ai/v1/systemone"
MODEL = "jev-latest"
DENY_ABOVE = 0.7
ASK_ABOVE = 0.3
TIMEOUT_S = 8

QUESTION = {
    "type": "noul",
    "instructions": (
        "Does `command` copy, move, rename, extract or otherwise write a .jar file into "
        "a Minecraft `mods` directory, replacing or overwriting a mod jar that may "
        "already be there?"
    ),
    "criteria": {
        "true": "Writes or replaces a .jar inside a mods folder (cp, copy, mv, move, "
                "Copy-Item, Move-Item, xcopy, robocopy, redirect, unzip into it)",
        "false": "Only reads, lists, builds or inspects jars, or touches other paths",
    },
}


def game_running() -> bool:
    try:
        out = subprocess.run(
            ["tasklist", "/FI", "IMAGENAME eq javaw.exe", "/NH"],
            capture_output=True, text=True, timeout=5,
        ).stdout
        return "javaw.exe" in out.lower()
    except Exception:
        return False


def ask_jev(command: str, key: str) -> float:
    body = json.dumps({
        "model": MODEL,
        "state": {"command": command},
        "questions": {"replaces_mod_jar": QUESTION},
    }).encode()
    req = urllib.request.Request(
        API_URL, data=body, method="POST",
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
        return json.load(resp)["answers"]["replaces_mod_jar"]["noul"]


def decide(decision: str, reason: str) -> None:
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": decision,
        "permissionDecisionReason": reason,
    }}))


def main() -> None:
    try:
        command = json.load(sys.stdin).get("tool_input", {}).get("command", "")
        key = os.environ.get("TYPESAFE_API_KEY")
        # Cheap local gates first: no jar/mods mention or game not running -> no API call.
        if not key or not re.search(r"\.jar|mods", command, re.I) or not game_running():
            return
        p = ask_jev(command, key)
        if p > DENY_ABOVE:
            decide("deny", f"Jev: p={p:.2f} that this replaces a mod jar while javaw.exe is "
                           "running (causes NoClassDefFoundError). Close the game first.")
        elif p > ASK_ABOVE:
            decide("ask", f"Jev: p={p:.2f} that this touches a mod jar while the game is running.")
    except Exception:
        return  # fail open


if __name__ == "__main__":
    main()
