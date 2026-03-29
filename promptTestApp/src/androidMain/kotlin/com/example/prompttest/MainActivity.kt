package com.example.prompttest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.prompttest.genai.PromptApiLlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val llmClient = PromptApiLlmClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val runner = BatchRunner(llmClient)
                val result = runner.runBatch(promptIds) { current, total, caseId, promptId ->
                    Log.i(TAG, "[$current/$total] $caseId × $promptId")
                }

                val json = ResultSerializer.toJson(result)
                val fileName = "eval_device_${result.runId}.json"
                val outputFile = File(getExternalFilesDir(null), fileName)
                outputFile.writeText(json)

                Log.i(TAG, "Batch run completed: ${outputFile.absolutePath}")
                Log.i(TAG, "BATCH_RESULT_PATH=${outputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Batch run failed", e)
            }
        }

        // Show a simple UI indicating batch is running
        setContent {
            BatchRunScreen(promptIds = promptIds, llmClient = llmClient)
        }
    }

    companion object {
        private const val TAG = "PromptTest"
    }
}
