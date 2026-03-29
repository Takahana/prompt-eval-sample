#!/usr/bin/env python3
"""評価ビューア用HTTPサーバー。結果JSONの閲覧・手動評価の保存を行う。"""

import argparse
import json
import sys
from functools import partial
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

VIEWER_DIR = Path(__file__).parent


class EvalHandler(BaseHTTPRequestHandler):
    def __init__(self, state, *args, **kwargs):
        self.state = state
        super().__init__(*args, **kwargs)

    def log_message(self, format, *args):
        pass  # suppress default logging

    def _send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, path):
        content = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        if self.path == "/":
            self._send_html(VIEWER_DIR / "index.html")
        elif self.path == "/api/results":
            self._send_json(self.state["data"])
        elif self.path == "/api/summary":
            self._send_json(self._compute_summary())
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path == "/api/eval":
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length))
            idx = body["index"]
            results = self.state["data"]["results"]
            if 0 <= idx < len(results):
                for key in ("relevance", "actionability", "safety"):
                    if key in body:
                        results[idx]["eval"][key] = body[key]
                self._save()
                self._send_json({"ok": True})
            else:
                self._send_json({"error": "invalid index"}, 400)
        else:
            self.send_error(404)

    def _save(self):
        with open(self.state["path"], "w", encoding="utf-8") as f:
            json.dump(self.state["data"], f, ensure_ascii=False, indent=2)

    def _compute_summary(self):
        results = self.state["data"]["results"]
        prompt_ids = sorted(set(r["prompt_id"] for r in results))
        criteria = ["format", "relevance", "actionability", "safety"]
        summary = {}
        for pid in prompt_ids:
            entries = [r for r in results if r["prompt_id"] == pid]
            evaluated = [
                r for r in entries
                if all(r["eval"].get(c) is not None for c in criteria)
            ]
            s = {"total": len(entries), "evaluated": len(evaluated)}
            for c in criteria:
                rated = [r for r in entries if r["eval"].get(c) is not None]
                ok = sum(1 for r in rated if r["eval"][c] == "OK")
                s[f"{c}_ok"] = ok
                s[f"{c}_rated"] = len(rated)
            s["all_ok"] = sum(
                1 for r in evaluated
                if all(r["eval"][c] == "OK" for c in criteria)
            )
            summary[pid] = s
        return summary


def main():
    parser = argparse.ArgumentParser(description="評価ビューアサーバー")
    parser.add_argument("file", help="結果JSONファイルのパス")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    path = Path(args.file)
    if not path.exists():
        print(f"ファイルが見つかりません: {path}", file=sys.stderr)
        sys.exit(1)

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    state = {"data": data, "path": path}
    handler = partial(EvalHandler, state)
    server = HTTPServer(("localhost", args.port), handler)
    print(f"ビューアを起動しました: http://localhost:{args.port}")
    print("終了するには Ctrl+C を押してください")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nサーバーを停止しました")


if __name__ == "__main__":
    main()
