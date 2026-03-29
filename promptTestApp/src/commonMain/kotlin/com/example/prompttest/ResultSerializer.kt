package com.example.prompttest

/**
 * BatchResultをrun_eval.pyと同じJSON形式に変換する。
 * 外部ライブラリ不要のシンプルな実装。
 */
object ResultSerializer {

    fun toJson(result: BatchResult): String = buildString {
        appendLine("{")
        appendLine("""  "run_id": "${escape(result.runId)}",""")
        appendLine("""  "model": "${escape(result.model)}",""")
        appendLine("""  "results": [""")
        for ((i, entry) in result.results.withIndex()) {
            appendLine("    {")
            appendLine("""      "test_case_id": "${escape(entry.testCaseId)}",""")
            appendLine("""      "prompt_id": "${escape(entry.promptId)}",""")
            appendLine("""      "input": "${escape(entry.input)}",""")
            appendLine("""      "raw_output": "${escape(entry.rawOutput)}",""")
            if (entry.parsedJson != null) {
                appendLine("""      "parsed_json": {""")
                val entries = entry.parsedJson.entries.toList()
                for ((j, kv) in entries.withIndex()) {
                    val comma = if (j < entries.size - 1) "," else ""
                    appendLine("""        "${escape(kv.key)}": "${escape(kv.value)}"$comma""")
                }
                appendLine("      },")
            } else {
                appendLine("""      "parsed_json": null,""")
            }
            appendLine("""      "eval": {""")
            appendLine("""        "format": "${entry.evalFormat}",""")
            appendLine("""        "relevance": null,""")
            appendLine("""        "actionability": null,""")
            appendLine("""        "safety": null""")
            appendLine("      }")
            val comma = if (i < result.results.size - 1) "," else ""
            appendLine("    }$comma")
        }
        appendLine("  ]")
        append("}")
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
