# prompt-eval: プロンプト評価パイプライン

ローカル（mlx-lm）とAndroid実機（Prompt API）でプロンプトを評価・比較するツール群。

## セットアップ

```bash
pip3 install -r prompt-eval/requirements.txt
```

## ファイル構成

```
prompt-eval/
  test_cases.json       # テストケース（10件）
  prompts.json          # プロンプトテンプレート（A〜L）
  run_eval.py           # ローカル評価（mlx-lm）
  run_device.py         # 実機評価（adb経由）
  run_all.py            # 一括実行（ローカル→実機→比較）
  compare.py            # 結果比較
  viewer/
    server.py           # 評価ビューア（HTTPサーバー）
    index.html          # ビューアUI
  results/              # 評価結果JSON
```

## 使い方

### ローカル評価（mlx-lm）

```bash
# 全プロンプト実行
python3 prompt-eval/run_eval.py

# 特定プロンプトのみ
python3 prompt-eval/run_eval.py --prompts prompt_J,prompt_L

# 別モデル指定
python3 prompt-eval/run_eval.py --model mlx-community/gemma-3n-E2B-it-lm-4bit
```

### 実機評価（Android Prompt API）

```bash
# APKインストール
./gradlew :promptTestApp:installDebug

# adb経由でバッチ実行→結果取得
python3 prompt-eval/run_device.py --prompts prompt_L
```

### 一括実行（ローカル→実機→比較）

```bash
# 全ステップ
python3 prompt-eval/run_all.py --prompts prompt_L

# ローカルだけスキップ
python3 prompt-eval/run_all.py --prompts prompt_L --skip-local

# 実機だけスキップ
python3 prompt-eval/run_all.py --prompts prompt_L --skip-device
```

### 結果比較

```bash
python3 prompt-eval/compare.py prompt-eval/results/eval_local.json prompt-eval/results/eval_device.json
```

### ビューア（手動評価）

```bash
python3 prompt-eval/viewer/server.py prompt-eval/results/eval_XXXXXXXX.json
# ブラウザで http://localhost:8765 を開く
```

- 左右矢印キーでエントリ移動
- OK/NGボタンで即座に評価保存
- フィルタ: 未評価のみ、プロンプト別
- サマリー表示で成功率確認

### 手動評価後の再集計

```bash
python3 prompt-eval/run_eval.py --summarize prompt-eval/results/eval_XXXXXXXX.json
```

## 評価指標

| 指標 | 判定基準 |
|------|---------|
| Format | 有効なJSON + action/reasonキーが存在 |
| Relevance | 入力内容に基づいた提案か（NG=無関係） |
| Actionability | 具体的で実行可能か（NG=抽象的） |
| Safety | 無理・危険でないか（NG=過負荷） |

- Formatは自動判定、他3指標はビューアで手動評価

## プロンプト改善の知見

- 小型モデルでは**短いプロンプトが安定**する
- 例示は**入力の活動名を含む具体的な内容**にする（汎用例はコピーされる）
- **「上の例をコピーせず」の明示指示**が例示コピーを抑制する
- 記録外活動への配慮は**1文で十分**（長いと不安定に）
- 実機（Prompt API）はローカル（gemma-3n-E4B）より**出力品質が高い**
