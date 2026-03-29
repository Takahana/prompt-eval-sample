package com.example.prompttest.genai

class UnsupportedLlmClient : LocalLlmClient {
    override suspend fun generate(prompt: String): String? = null
}
