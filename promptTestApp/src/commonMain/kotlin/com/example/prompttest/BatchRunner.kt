package com.example.prompttest

import com.example.prompttest.generated.GeneratedPrompts
import com.example.prompttest.generated.GeneratedTestCases
import com.example.prompttest.generated.PromptTemplate
import com.example.prompttest.generated.TestCase
import com.example.prompttest.genai.LocalLlmClient
import kotlinx.coroutines.delay

data class EvalEntry(
    val testCaseId: String,
    val promptId: String,
    val input: String,
    val rawOutput: String,
    val parsedJson: Map<String, String>?,
    val evalFormat: String,
)

data class BatchResult(
    val runId: String,
    val model: String,
    val results: List<EvalEntry>,
)

class BatchRunner(private val llmClient: LocalLlmClient) {

    suspend fun runBatch(
        promptIds: List<String>,
        onProgress: (current: Int, total: Int, caseId: String, promptId: String) -> Unit = { _, _, _, _ -> },
    ): BatchResult {
        val prompts = GeneratedPrompts.prompts.filter { it.id in promptIds }
        val testCases = GeneratedTestCases.cases
        val total = testCases.size * prompts.size
        val results = mutableListOf<EvalEntry>()
        var current = 0

        for (case in testCases) {
            for (prompt in prompts) {
                current++
                onProgress(current, total, case.id, prompt.id)

                if (current > 1) {
                    delay(3000)
                }

                val fullPrompt = prompt.template.replace("{{activity_summary}}", case.input)
                val rawOutput = llmClient.generate(fullPrompt) ?: ""

                val parsed = tryParseJson(rawOutput)
                val formatOk = parsed != null && "action" in parsed && "reason" in parsed

                results.add(
                    EvalEntry(
                        testCaseId = case.id,
                        promptId = prompt.id,
                        input = case.input,
                        rawOutput = rawOutput,
                        parsedJson = parsed,
                        evalFormat = if (formatOk) "OK" else "NG",
                    )
                )
            }
        }

        return BatchResult(
            runId = currentTimestamp(),
            model = "prompt-api",
            results = results,
        )
    }

    private fun tryParseJson(text: String): Map<String, String>? {
        // Extract JSON object from text
        val jsonStr = extractJsonString(text) ?: return null
        return parseSimpleJsonObject(jsonStr)
    }

    private fun extractJsonString(text: String): String? {
        // Try ```json ... ``` block first
        val codeBlockRegex = Regex("""```(?:json)?\s*(\{.*\})\s*```""", RegexOption.DOT_MATCHES_ALL)
        codeBlockRegex.find(text)?.let { return it.groupValues[1] }

        // Try to find a JSON object with possible nesting
        val objectRegex = Regex("""\{(?:[^{}]|\{[^{}]*\})*\}""", RegexOption.DOT_MATCHES_ALL)
        objectRegex.find(text)?.let { return it.value }

        return null
    }

    private fun parseSimpleJsonObject(json: String): Map<String, String>? {
        val result = mutableMapOf<String, String>()
        val fieldRegex = Regex(""""(\w+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        for (match in fieldRegex.findAll(json)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            result[key] = value
        }
        return if (result.isNotEmpty()) result else null
    }
}

internal expect fun currentTimestamp(): String
