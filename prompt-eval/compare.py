#!/usr/bin/env python3
"""ローカル(mlx-lm)と実機(Prompt API)の評価結果を比較するスクリプト。

使い方:
  python3 prompt-eval/compare.py prompt-eval/results/eval_local.json prompt-eval/results/eval_device.json
"""

import argparse
import json
import sys
from pathlib import Path


def load_results(path: Path) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def compare(local_path: Path, device_path: Path):
    local = load_results(local_path)
    device = load_results(device_path)

    local_map = {(r["test_case_id"], r["prompt_id"]): r for r in local["results"]}
    device_map = {(r["test_case_id"], r["prompt_id"]): r for r in device["results"]}

    common_keys = sorted(set(local_map.keys()) & set(device_map.keys()))

    if not common_keys:
        print("比較可能なエントリがありません。同じプロンプトIDとテストケースIDを含む結果ファイルを指定してください。")
        sys.exit(1)

    print(f"比較: {local_path.name} (local: {local['model']}) vs {device_path.name} (device: {device['model']})")
    print(f"共通エントリ: {len(common_keys)}件")
    print("=" * 80)

    for key in common_keys:
        case_id, prompt_id = key
        l = local_map[key]
        d = device_map[key]

        l_parsed = l.get("parsed_json") or {}
        d_parsed = d.get("parsed_json") or {}

        l_action = l_parsed.get("action", "(なし)")
        d_action = d_parsed.get("action", "(なし)")
        l_message = l_parsed.get("message", "")
        d_message = d_parsed.get("message", "")
        l_reason = l_parsed.get("reason", "(なし)")
        d_reason = d_parsed.get("reason", "(なし)")

        print(f"\n--- {case_id} × {prompt_id} ---")
        print(f"入力: {l['input'][:60]}...")

        if l_message or d_message:
            print(f"  [message]")
            print(f"    local : {l_message}")
            print(f"    device: {d_message}")

        print(f"  [action]")
        print(f"    local : {l_action}")
        print(f"    device: {d_action}")

        print(f"  [reason]")
        print(f"    local : {l_reason[:80]}{'...' if len(l_reason) > 80 else ''}")
        print(f"    device: {d_reason[:80]}{'...' if len(d_reason) > 80 else ''}")

        print(f"  [format]  local={l['eval']['format']}  device={d['eval']['format']}")

    # Summary
    print("\n" + "=" * 80)
    print("サマリー")
    print("=" * 80)

    prompt_ids = sorted(set(k[1] for k in common_keys))
    for pid in prompt_ids:
        entries = [(k, local_map[k], device_map[k]) for k in common_keys if k[1] == pid]
        l_format_ok = sum(1 for _, l, _ in entries if l["eval"]["format"] == "OK")
        d_format_ok = sum(1 for _, _, d in entries if d["eval"]["format"] == "OK")
        total = len(entries)
        print(f"\n{pid} ({total}件):")
        print(f"  Format OK: local={l_format_ok}/{total}  device={d_format_ok}/{total}")

        # Count entries where both have evaluations
        l_all_ok = sum(
            1 for _, l, _ in entries
            if all(l["eval"].get(c) == "OK" for c in ["format", "relevance", "actionability", "safety"])
        )
        d_all_ok = sum(
            1 for _, _, d in entries
            if all(d["eval"].get(c) == "OK" for c in ["format", "relevance", "actionability", "safety"])
        )
        l_evaluated = sum(
            1 for _, l, _ in entries
            if all(l["eval"].get(c) is not None for c in ["format", "relevance", "actionability", "safety"])
        )
        d_evaluated = sum(
            1 for _, _, d in entries
            if all(d["eval"].get(c) is not None for c in ["format", "relevance", "actionability", "safety"])
        )
        if l_evaluated:
            print(f"  全指標OK: local={l_all_ok}/{l_evaluated}")
        if d_evaluated:
            print(f"  全指標OK: device={d_all_ok}/{d_evaluated}")


def main():
    parser = argparse.ArgumentParser(description="ローカルと実機の評価結果を比較")
    parser.add_argument("local", help="ローカル評価結果JSONファイル")
    parser.add_argument("device", help="実機評価結果JSONファイル")
    args = parser.parse_args()

    compare(Path(args.local), Path(args.device))


if __name__ == "__main__":
    main()
