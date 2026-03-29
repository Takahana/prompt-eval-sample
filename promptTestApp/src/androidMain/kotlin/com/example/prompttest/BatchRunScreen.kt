package com.example.prompttest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.prompttest.genai.LocalLlmClient
import kotlinx.coroutines.launch

@Composable
fun BatchRunScreen(promptIds: List<String>, llmClient: LocalLlmClient) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf("開始中...") }
    var isRunning by remember { mutableStateOf(true) }
    var resultSummary by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            val runner = BatchRunner(llmClient)
            val result = runner.runBatch(promptIds) { current, total, caseId, promptId ->
                progress = "[$current/$total] $caseId × $promptId"
            }

            val formatOk = result.results.count { it.evalFormat == "OK" }
            val total = result.results.size
            resultSummary = "完了: Format OK $formatOk/$total\nファイル: eval_device_${result.runId}.json"
            isRunning = false
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("バッチ実行中", style = MaterialTheme.typography.h5)
            Spacer(Modifier.height(8.dp))
            Text("プロンプト: ${promptIds.joinToString(", ")}")
            Spacer(Modifier.height(16.dp))

            if (isRunning) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(progress)
            } else {
                Text(resultSummary, style = MaterialTheme.typography.body1)
            }
        }
    }
}
