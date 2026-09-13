#!/usr/bin/env python3
"""Visible-input smoke check on an explicitly selected Android emulator.

This is a narrow sample test, not the general UX-observer driver. It never
dispatches internal payment actions. Device font scale is restored in finally.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", default="artifacts/native-smoke/android")
    parser.add_argument("--apk", default="androidApp/build/outputs/apk/debug/androidApp-debug.apk")
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    adb = shutil.which("adb") or (str(Path(sdk) / "platform-tools/adb") if sdk else None)
    if not adb:
        parser.error("Set ANDROID_HOME or add adb to PATH")
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    # A failed rerun must not leave the preceding run's PASS marker behind.
    (out / "smoke-result.json").unlink(missing_ok=True)

    def command(*parts, binary=False):
        result = subprocess.run([adb, "-s", args.serial, *parts], check=True, capture_output=True, timeout=30)
        return result.stdout if binary else result.stdout.decode()

    def tree():
        command("shell", "uiautomator", "dump", "/sdcard/harness-ui.xml")
        return ET.fromstring(command("shell", "cat", "/sdcard/harness-ui.xml"))

    def bounds(node):
        return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))

    def clickable(node, current):
        parents = {child: parent for parent in current.iter() for child in parent}
        while node.get("clickable") != "true":
            node = parents[node]
        return node

    def click_node(node, current):
        node = clickable(node, current)
        left, top, right, bottom = bounds(node)
        assert right > left and bottom > top, "Target has no visible bounds"
        command("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def wait_text(text):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            current = tree()
            node = next((n for n in current.iter("node") if n.get("text") == text), None)
            if node is not None:
                return current, node
        raise AssertionError(f"Visible text not found: {text}")

    def capture(name, current):
        (out / f"{name}.xml").write_bytes(ET.tostring(current, encoding="utf-8"))
        (out / f"{name}.png").write_bytes(command("exec-out", "screencap", "-p", binary=True))

    original_scale = command("shell", "settings", "get", "system", "font_scale").strip()
    try:
        command("install", "-r", args.apk)
        command("shell", "settings", "put", "system", "font_scale", "1.0")
        command("shell", "am", "force-stop", "dev.harness.sample")
        command("shell", "am", "start", "-n", "dev.harness.sample/.MainActivity")
        current, next_button = wait_text("결제 내용 확인")
        capture("contract", current)
        click_node(next_button, current)
        current, _ = wait_text("500,000원 결제하기")
        capture("confirm", current)
        edit = next(n for n in current.iter("node") if n.get("class") == "android.widget.EditText")
        click_node(edit, current)
        command("shell", "input", "text", "harness-memo")
        current, submit = wait_text("500,000원 결제하기")
        capture("keyboard", current)
        # UIAutomator may expose only the app window; OS insets provide the IME bounds.
        insets = command("shell", "dumpsys", "window", "displays")
        (out / "keyboard-insets.txt").write_text(insets)
        ime = re.search(r"type=ime frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\][^\n]*visible=true", insets)
        assert ime, "Visible IME insets were not observed; this check must not pass silently"
        keyboard_top = int(ime.group(2))
        assert bounds(clickable(submit, current))[3] <= keyboard_top, "Submit is obscured by the keyboard"
        click_node(submit, current)
        current, _ = wait_text("결제가\n완료됐어요")
        assert any(n.get("text") == "harness-memo" for n in current.iter("node")), "Input was not preserved"
        assert all(n.get("text") != "500,000원 결제하기" for n in current.iter("node")), "Completed screen offers resubmission"
        capture("result", current)
        (out / "smoke-result.json").write_text(json.dumps({
            "verdict": "PASS", "scope": "Android visible success flow, keyboard reachability, input preservation",
            "serial": args.serial, "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
            "sourceDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], text=True).strip()),
            "installedApkSha256": hashlib.sha256(Path(args.apk).read_bytes()).hexdigest(),
            "limitations": ["not a full native gate", "no screen reader audit", "no process restoration check"],
        }, indent=2) + "\n")
        print(f"Android smoke PASS: {out}")
    finally:
        if original_scale == "null":
            command("shell", "settings", "delete", "system", "font_scale")
        else:
            command("shell", "settings", "put", "system", "font_scale", original_scale)


if __name__ == "__main__":
    main()
