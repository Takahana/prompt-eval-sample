package com.example.prompttest.genai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.delay

class PromptApiLlmClient : LocalLlmClient {

    private var generativeModel: GenerativeModel? = null
    private var isReady = false

    private suspend fun ensureReady(): GenerativeModel? {
        generativeModel?.let { if (isReady) return it }

        val model = try {
            generativeModel ?: Generation.getClient().also { generativeModel = it }
        } catch (e: Exception) {
            println("PromptApiLlmClient: Failed to get GenerativeModel: ${e.message}")
            return null
        }

        when (model.checkStatus()) {
            FeatureStatus.UNAVAILABLE -> {
                println("PromptApiLlmClient: Gemini Nano is unavailable on this device")
                return null
            }
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                println("PromptApiLlmClient: Downloading Gemini Nano model...")
                if (!awaitDownload(model)) {
                    println("PromptApiLlmClient: Model download failed")
                    return null
                }
                println("PromptApiLlmClient: Model download completed")
            }
            FeatureStatus.AVAILABLE -> {}
        }

        try {
            model.warmup()
            println("PromptApiLlmClient: Warmup completed")
        } catch (e: Exception) {
            println("PromptApiLlmClient: Warmup failed: ${e.message}")
        }

        isReady = true
        return model
    }

    override suspend fun generate(prompt: String): String? {
        val model = ensureReady() ?: return null

        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val response = model.generateContent(prompt)
                return response.candidates.firstOrNull()?.text
            } catch (e: Exception) {
                lastException = e
                val message = e.message ?: ""
                if ("ErrorCode 9" in message || "quota" in message.lowercase()) {
                    val backoff = attempt * 5000L
                    println("PromptApiLlmClient: BUSY (attempt $attempt/3), waiting ${backoff}ms...")
                    delay(backoff)
                } else {
                    println("PromptApiLlmClient: Generation failed: ${e.message}")
                    return null
                }
            }
        }
        println("PromptApiLlmClient: All retries exhausted: ${lastException?.message}")
        return null
    }

    fun close() {
        generativeModel?.close()
        generativeModel = null
        isReady = false
    }

    private suspend fun awaitDownload(generativeModel: GenerativeModel): Boolean {
        var success = false
        generativeModel.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted ->
                    println("PromptApiLlmClient: Download started")
                is DownloadStatus.DownloadProgress ->
                    println("PromptApiLlmClient: Downloaded ${status.totalBytesDownloaded} bytes")
                is DownloadStatus.DownloadCompleted -> {
                    println("PromptApiLlmClient: Download completed")
                    success = true
                }
                is DownloadStatus.DownloadFailed -> {
                    println("PromptApiLlmClient: Download failed: ${status.e.message}")
                    success = false
                }
            }
        }
        return success
    }
}
