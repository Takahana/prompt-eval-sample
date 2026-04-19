package com.example.prompttest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.prompttest.genai.PromptApiLlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

sealed interface BatchStatus {
    data object Idle : BatchStatus
    data class Running(val progress: String) : BatchStatus
    data class Completed(val outputPath: String, val formatOk: Int, val total: Int) : BatchStatus
    data class Failed(val message: String) : BatchStatus
}

class MainActivity : ComponentActivity() {

    private val llmClient = PromptApiLlmClient()
    private val batchStatus = mutableStateOf<BatchStatus>(BatchStatus.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getStringExtra("action") == "batch_run") {
            handleBatchRun(intent)
        } else {
            setContent {
                App(llmClient = llmClient)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("action") == "batch_run") {
            handleBatchRun(intent)
        }
    }

    private fun handleBatchRun(intent: Intent) {
        val promptIds = intent.getStringExtra("prompts")
            ?.split(",")
            ?.map { it.trim() }
            ?: listOf("prompt_L")

        Log.i(TAG, "Batch run started: prompts=$promptIds")
        batchStatus.value = BatchStatus.Running("開始中...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val runner = BatchRunner(llmClient)
                val result = runner.runBatch(promptIds) { current, total, caseId, promptId ->
                    val msg = "[$current/$total] $caseId × $promptId"
                    Log.i(TAG, msg)
                    batchStatus.value = BatchStatus.Running(msg)
                }

                val json = ResultSerializer.toJson(result)
                val fileName = "eval_device_${result.runId}.json"
                val outputFile = File(getExternalFilesDir(null), fileName)
                outputFile.writeText(json)

                val formatOk = result.results.count { it.evalFormat == "OK" }
                val total = result.results.size

                Log.i(TAG, "Batch run completed: ${outputFile.absolutePath}")
                Log.i(TAG, "BATCH_RESULT_PATH=${outputFile.absolutePath}")
                batchStatus.value = BatchStatus.Completed(outputFile.absolutePath, formatOk, total)
            } catch (e: Exception) {
                Log.e(TAG, "Batch run failed", e)
                batchStatus.value = BatchStatus.Failed(e.message ?: "unknown error")
            }
        }

        setContent {
            BatchRunScreen(promptIds = promptIds, status = batchStatus)
        }
    }

    companion object {
        private const val TAG = "PromptTest"
    }
}
