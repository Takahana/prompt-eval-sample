#!/usr/bin/env python3
"""プロンプト評価スクリプト: mlx-lm でローカル推論し、結果をJSONに出力する。"""

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path

from mlx_lm import load, generate


DEFAULT_MODEL = "mlx-community/gemma-3n-E4B-it-lm-4bit"
MAX_TOKENS = 256
SCRIPT_DIR = Path(__file__).parent
RESULTS_DIR = SCRIPT_DIR / "results"


def load_json(path: Path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build_prompt(template: str, activity_summary: str) -> str:
    return template.replace("{{activity_summary}}", activity_summary)


def extract_json(text: str) -> dict | None:
    """レスポンスからJSONオブジェクトを抽出する。"""
    # まずそのままパース
    try:
        return json.loads(text.strip())
    except json.JSONDecodeError:
        pass
    # ```json ... ``` ブロックを探す
    m = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.DOTALL)
    if m:
        try:
            return json.loads(m.group(1))
        except json.JSONDecodeError:
            pass
    # 最後の {...} を探す（ネスト対応: 最も外側のペアを貪欲マッチ）
    m = re.search(r"\{(?:[^{}]|\{[^{}]*\})*\}", text, re.DOTALL)
    if m:
        try:
            return json.loads(m.group(0))
        except json.JSONDecodeError:
            pass
    return None


def evaluate_format(parsed: dict | None) -> str:
    """Format評価: 有効なJSONで action, reason キーがあればOK。"""
    if parsed is None:
        return "NG"
    if "action" in parsed and "reason" in parsed:
        return "OK"
    return "NG"


def run_eval(model_name: str, prompt_filter: list[str] | None = None):
    test_cases = load_json(SCRIPT_DIR / "test_cases.json")
    prompts = load_json(SCRIPT_DIR / "prompts.json")

    if prompt_filter:
        prompts = [p for p in prompts if p["id"] in prompt_filter]
        if not prompts:
            print(f"エラー: 指定されたプロンプトが見つかりません: {prompt_filter}", file=sys.stderr)
            sys.exit(1)
        print(f"プロンプトフィルタ: {[p['id'] for p in prompts]}")

    print(f"モデル読み込み中: {model_name}")
    model, tokenizer = load(model_name)
    print("モデル読み込み完了\n")

    total = len(test_cases) * len(prompts)
    results = []

    for i, case in enumerate(test_cases):
        for j, prompt_def in enumerate(prompts):
            idx = i * len(prompts) + j + 1
            print(f"[{idx}/{total}] {case['id']} × {prompt_def['id']} ...", end=" ", flush=True)

            full_prompt = build_prompt(prompt_def["template"], case["input"])
            raw_output = generate(
                model,
                tokenizer,
                prompt=full_prompt,
                max_tokens=MAX_TOKENS,
            )

            parsed = extract_json(raw_output)
            format_eval = evaluate_format(parsed)

            results.append({
                "test_case_id": case["id"],
                "prompt_id": prompt_def["id"],
                "input": case["input"],
                "raw_output": raw_output,
                "parsed_json": parsed,
                "eval": {
                    "format": format_eval,
                    "relevance": None,
                    "actionability": None,
                    "safety": None,
                },
            })

            status = "OK" if format_eval == "OK" else "NG(format)"
            print(status)

    return results, test_cases, prompts


def summarize(results: list, prompts: list) -> dict:
    """プロンプト別のFormat成功率を集計する。"""
    summary = {}
    for prompt_def in prompts:
        pid = prompt_def["id"]
        entries = [r for r in results if r["prompt_id"] == pid]
        format_ok = sum(1 for r in entries if r["eval"]["format"] == "OK")
        # 全指標が評価済みのエントリで全体成功率も計算
        fully_evaluated = [
            r for r in entries
            if all(v is not None for v in r["eval"].values())
        ]
        all_ok = sum(
            1 for r in fully_evaluated
            if all(v == "OK" for v in r["eval"].values())
        )
        summary[pid] = {
            "total": len(entries),
            "format_ok": format_ok,
            "format_rate": format_ok / len(entries) if entries else 0,
            "fully_evaluated": len(fully_evaluated),
            "all_ok": all_ok,
            "all_ok_rate": all_ok / len(fully_evaluated) if fully_evaluated else None,
        }
    return summary


def print_summary(summary: dict):
    print("\n" + "=" * 50)
    print("評価サマリー")
    print("=" * 50)
    for pid, s in summary.items():
        print(f"\n{pid}:")
        print(f"  Format OK: {s['format_ok']}/{s['total']} ({s['format_rate']:.0%})")
        if s["fully_evaluated"] > 0:
            print(f"  全指標OK: {s['all_ok']}/{s['fully_evaluated']} ({s['all_ok_rate']:.0%})")
        else:
            print("  全指標OK: 未評価（手動評価後に --summarize で再集計）")


def save_results(results: list, summary: dict, model_name: str):
    RESULTS_DIR.mkdir(exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output = {
        "run_id": timestamp,
        "model": model_name,
        "results": results,
        "summary": summary,
    }
    path = RESULTS_DIR / f"eval_{timestamp}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n結果を保存しました: {path}")
    print(f"手動評価するには: python3 prompt-eval/viewer/server.py {path}")
    return path


def summarize_existing(path: Path):
    """既存の結果ファイルを読み込んで再集計する。"""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    # プロンプト一覧を結果から復元
    prompt_ids = sorted(set(r["prompt_id"] for r in data["results"]))
    prompts = [{"id": pid} for pid in prompt_ids]

    summary = summarize(data["results"], prompts)
    data["summary"] = summary

    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print_summary(summary)
    print(f"\nサマリーを更新しました: {path}")


def main():
    parser = argparse.ArgumentParser(description="プロンプト評価スクリプト")
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"使用するモデル (デフォルト: {DEFAULT_MODEL})",
    )
    parser.add_argument(
        "--prompts",
        type=str,
        metavar="IDS",
        help="実行するプロンプトIDをカンマ区切りで指定 (例: prompt_D,prompt_E,prompt_F)",
    )
    parser.add_argument(
        "--summarize",
        type=str,
        metavar="FILE",
        help="既存の結果ファイルを再集計する（手動評価後に使用）",
    )
    args = parser.parse_args()

    if args.summarize:
        summarize_existing(Path(args.summarize))
        return

    prompt_filter = [p.strip() for p in args.prompts.split(",")] if args.prompts else None
    results, test_cases, prompts = run_eval(args.model, prompt_filter)
    summary = summarize(results, prompts)
    print_summary(summary)
    save_results(results, summary, args.model)


if __name__ == "__main__":
    main()
