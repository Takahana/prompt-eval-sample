#!/usr/bin/env python3
"""Android実機でプロンプト評価を実行し、結果をPCに取得するスクリプト。

使い方:
  python3 prompt-eval/run_device.py --prompts prompt_L
  python3 prompt-eval/run_device.py --prompts prompt_J,prompt_L
"""

import argparse
import subprocess
import sys
import time
from pathlib import Path

PACKAGE = "com.example.prompttest"
ACTIVITY = f"{PACKAGE}/.MainActivity"
DEVICE_DIR = f"/sdcard/Android/data/{PACKAGE}/files"
RESULTS_DIR = Path(__file__).parent / "results"


def run_cmd(cmd: list[str], check: bool = True) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"コマンド失敗: {' '.join(cmd)}", file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        sys.exit(1)
    return result.stdout.strip()


def check_device():
    output = run_cmd(["adb", "devices"])
    lines = [l for l in output.splitlines()[1:] if l.strip() and "device" in l]
    if not lines:
        print("エラー: Android端末が接続されていません", file=sys.stderr)
        sys.exit(1)
    print(f"端末検出: {lines[0].split()[0]}")


def start_batch(prompts: str):
    print(f"バッチ実行開始: prompts={prompts}")
    run_cmd([
        "adb", "shell", "am", "start",
        "-n", ACTIVITY,
        "--es", "action", "batch_run",
        "--es", "prompts", prompts,
    ])


def wait_for_result(timeout: int = 600) -> str:
    """logcatを監視してBATCH_RESULT_PATHが出力されるのを待つ。"""
    print("実行完了を待機中...")

    # logcatをクリアしてから監視開始
    run_cmd(["adb", "logcat", "-c"], check=False)

    # 少し待ってからlogcat監視（intentが処理されるまでの猶予）
    time.sleep(2)

    proc = subprocess.Popen(
        ["adb", "logcat", "-s", "PromptTest:I"],
        stdout=subprocess.PIPE,
        text=True,
    )

    start = time.time()
    try:
        while time.time() - start < timeout:
            line = proc.stdout.readline()
            if not line:
                time.sleep(0.1)
                continue
            line = line.strip()
            if line:
                # Show progress
                if "] case_" in line:
                    print(f"  {line.split(': ', 1)[-1] if ': ' in line else line}")
                if "BATCH_RESULT_PATH=" in line:
                    path = line.split("BATCH_RESULT_PATH=")[-1]
                    return path
        print("エラー: タイムアウト", file=sys.stderr)
        sys.exit(1)
    finally:
        proc.terminate()


def pull_result(device_path: str) -> Path:
    RESULTS_DIR.mkdir(exist_ok=True)
    filename = Path(device_path).name
    local_path = RESULTS_DIR / filename
    print(f"結果を取得: {device_path} → {local_path}")
    run_cmd(["adb", "pull", device_path, str(local_path)])
    return local_path


def main():
    parser = argparse.ArgumentParser(description="Android実機でプロンプト評価を実行")
    parser.add_argument(
        "--prompts",
        default="prompt_L",
        help="実行するプロンプトIDをカンマ区切りで指定 (デフォルト: prompt_L)",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=600,
        help="タイムアウト秒数 (デフォルト: 600)",
    )
    args = parser.parse_args()

    check_device()
    start_batch(args.prompts)
    device_path = wait_for_result(args.timeout)
    local_path = pull_result(device_path)

    print(f"\n完了: {local_path}")
    print(f"ビューアで確認: python3 prompt-eval/viewer/server.py {local_path}")


if __name__ == "__main__":
    main()
