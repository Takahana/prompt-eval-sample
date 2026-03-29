package com.example.prompttest

import com.example.prompttest.generated.PromptTemplate
import com.example.prompttest.generated.TestCase
import com.example.prompttest.genai.LocalLlmClient

class PromptRunner(private val llmClient: LocalLlmClient) {

    fun buildPrompt(template: PromptTemplate, testCase: TestCase): String {
        return template.template.replace("{{activity_summary}}", testCase.input)
    }

    suspend fun run(template: PromptTemplate, testCase: TestCase): String {
        val prompt = buildPrompt(template, testCase)
        return llmClient.generate(prompt) ?: "(生成失敗: モデルが利用できません)"
    }
}
