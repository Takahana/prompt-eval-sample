#!/usr/bin/env python3
"""ローカル評価 → 実機評価 → 比較 を一括実行するスクリプト。

使い方:
  python3 prompt-eval/run_all.py --prompts prompt_L
  python3 prompt-eval/run_all.py --prompts prompt_J,prompt_L --skip-local
  python3 prompt-eval/run_all.py --prompts prompt_L --skip-device
"""

import argparse
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent


def run_script(args: list[str], description: str) -> subprocess.CompletedProcess:
    print(f"\n{'=' * 60}")
    print(f"  {description}")
    print(f"{'=' * 60}\n")
    result = subprocess.run(args, cwd=SCRIPT_DIR.parent)
    if result.returncode != 0:
        print(f"\nエラー: {description} が失敗しました (exit code {result.returncode})", file=sys.stderr)
        sys.exit(1)
    return result


def find_latest_result(prefix: str) -> Path | None:
    results_dir = SCRIPT_DIR / "results"
    if not results_dir.exists():
        return None
    files = sorted(results_dir.glob(f"{prefix}*.json"), reverse=True)
    return files[0] if files else None


def main():
    parser = argparse.ArgumentParser(description="ローカル→実機→比較の一括実行")
    parser.add_argument(
        "--prompts",
        default="prompt_L",
        help="プロンプトIDをカンマ区切り (デフォルト: prompt_L)",
    )
    parser.add_argument(
        "--skip-local",
        action="store_true",
        help="ローカル評価をスキップ（既存結果を使用）",
    )
    parser.add_argument(
        "--skip-device",
        action="store_true",
        help="実機評価をスキップ（既存結果を使用）",
    )
    parser.add_argument(
        "--local-result",
        type=str,
        help="ローカル結果ファイルを直接指定",
    )
    parser.add_argument(
        "--device-result",
        type=str,
        help="実機結果ファイルを直接指定",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=600,
        help="実機評価のタイムアウト秒数 (デフォルト: 600)",
    )
    args = parser.parse_args()

    local_result = Path(args.local_result) if args.local_result else None
    device_result = Path(args.device_result) if args.device_result else None

    # Step 1: ローカル評価
    if not args.skip_local and not local_result:
        run_script(
            [sys.executable, str(SCRIPT_DIR / "run_eval.py"), "--prompts", args.prompts],
            "Step 1/3: ローカル評価 (mlx-lm)",
        )
        local_result = find_latest_result("eval_")
        # eval_device_ にマッチしないように除外
        results_dir = SCRIPT_DIR / "results"
        candidates = sorted(
            [f for f in results_dir.glob("eval_*.json") if not f.name.startswith("eval_device_")],
            reverse=True,
        )
        local_result = candidates[0] if candidates else None
    elif not local_result:
        results_dir = SCRIPT_DIR / "results"
        candidates = sorted(
            [f for f in results_dir.glob("eval_*.json") if not f.name.startswith("eval_device_")],
            reverse=True,
        )
        local_result = candidates[0] if candidates else None

    if not local_result or not local_result.exists():
        print("エラー: ローカル結果ファイルが見つかりません", file=sys.stderr)
        sys.exit(1)
    print(f"\nローカル結果: {local_result}")

    # Step 2: 実機評価
    if not args.skip_device and not device_result:
        run_script(
            [
                sys.executable, str(SCRIPT_DIR / "run_device.py"),
                "--prompts", args.prompts,
                "--timeout", str(args.timeout),
            ],
            "Step 2/3: 実機評価 (Android Prompt API)",
        )
        device_result = find_latest_result("eval_device_")
    elif not device_result:
        device_result = find_latest_result("eval_device_")

    if not device_result or not device_result.exists():
        print("エラー: 実機結果ファイルが見つかりません", file=sys.stderr)
        sys.exit(1)
    print(f"実機結果: {device_result}")

    # Step 3: 比較
    run_script(
        [sys.executable, str(SCRIPT_DIR / "compare.py"), str(local_result), str(device_result)],
        "Step 3/3: 結果比較",
    )

    # ビューア案内
    print(f"\n{'=' * 60}")
    print("  完了")
    print(f"{'=' * 60}")
    print(f"\nローカル結果をビューアで確認:")
    print(f"  python3 prompt-eval/viewer/server.py {local_result}")
    print(f"\n実機結果をビューアで確認:")
    print(f"  python3 prompt-eval/viewer/server.py {device_result}")


if __name__ == "__main__":
    main()
