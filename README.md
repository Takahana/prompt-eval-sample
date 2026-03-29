# Prompt Eval Sample

オンデバイスLLM（ML Kit Prompt API / Gemma 3n）向けのプロンプト評価パイプラインと実機テストアプリのサンプルです。

## 概要

ローカル（mlx-lm）で高速にプロンプトを試行・改善し、Android実機（Prompt API）で最終検証する一連のワークフローを提供します。

```
ローカル（Gemma 3n）で高速に試行
        ↓
良いプロンプト候補を絞る
        ↓
Android実機（Prompt API）で最終評価
        ↓
結果を比較・分析
```

## 構成

```
prompt-eval/          Python評価ツール群
  run_eval.py           ローカル評価（mlx-lm）
  run_device.py         adb連携で実機評価
  run_all.py            ローカル→実機→比較の一括実行
  compare.py            結果比較
  viewer/               ブラウザベース評価UI
  test_cases.json       テストケース
  prompts.json          プロンプトテンプレート

promptTestApp/        Compose Multiplatform実機テストアプリ
  Android: ML Kit Prompt API対応
  Desktop/iOS/WasmJS: UI確認用
```

## セットアップ

### Python環境（ローカル評価用）

```bash
pip3 install -r prompt-eval/requirements.txt
```

> Apple Silicon Mac が必要です（mlx-lm を使用）

### Android（実機テスト用）

```bash
./gradlew :promptTestApp:installDebug
```

> Prompt API対応デバイス（Pixel 9以降）が必要です

## 使い方

詳細は [prompt-eval/README.md](prompt-eval/README.md) を参照してください。

```bash
# ローカル評価
python3 prompt-eval/run_eval.py --prompts prompt_L

# 実機評価
python3 prompt-eval/run_device.py --prompts prompt_L

# 一括実行（ローカル→実機→比較）
python3 prompt-eval/run_all.py --prompts prompt_L

# ビューアで結果確認・手動評価
python3 prompt-eval/viewer/server.py prompt-eval/results/eval_XXXXXXXX.json
```

## プロンプト改善の知見

10回の改善ループ（Prompt A→L）で得られた知見です。

### 小型オンデバイスモデル向けプロンプト設計の原則

1. **短いプロンプトが安定する** — 長い制約文は逆効果になることがある
2. **例示は入力に紐づく具体的な内容にする** — 汎用的な例はそのままコピーされる
3. **「上の例をコピーせず」の明示指示が効く** — 小型モデルでもこのメタ指示は理解する
4. **プレースホルダーは避ける** — `{"action": "次にやる行動"}` はそのまま出力される
5. **例示は1つで十分** — 多いとコピーリスクが増し、プロンプト長も増える

### 評価の結果

| Prompt | ローカル成功率 | 特徴 |
|--------|-------------|------|
| A（最小指示） | 40% | プレースホルダーのオウム返し |
| B（制約付き） | 0% | 制約が長すぎて暴走 |
| C（例示付き） | 100% | 例示コピー多発 |
| F（最小例示+コピー禁止） | 100% | コピー解消 |
| J（フレンドリー+記録外考慮） | 100% | 質・多様性ともに良好 |
| **L（J+高負荷対応）** | **90%** | **最終候補** |

実機（Prompt API）はローカル（gemma-3n-E4B 4bit）より出力品質が高く、LoRAアダプタの効果が確認できました。

## 技術スタック

- **ローカル評価**: Python + [mlx-lm](https://github.com/ml-explore/mlx-examples/tree/main/llms/mlx_lm) + gemma-3n-E4B-it-lm-4bit
- **実機テスト**: Kotlin + Compose Multiplatform + [ML Kit GenAI Prompt API](https://developers.google.com/ml-kit/genai/prompt)
- **評価ビューア**: HTML + vanilla JS + Python HTTPサーバー

## ライセンス

MIT
