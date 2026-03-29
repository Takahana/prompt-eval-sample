package com.example.prompttest.genai

interface LocalLlmClient {
    suspend fun generate(prompt: String): String?
}
