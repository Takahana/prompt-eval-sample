package com.example.prompttest

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val BATCH_DONE_MARKER = "BATCH_DONE"
const val BATCH_FAILED_MARKER = "BATCH_FAILED"

@Composable
fun BatchRunScreen(promptIds: List<String>, status: State<BatchStatus>) {
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

            when (val s = status.value) {
                is BatchStatus.Idle -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("開始待ち")
                }
                is BatchStatus.Running -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(s.progress)
                }
                is BatchStatus.Completed -> {
                    Text(
                        "$BATCH_DONE_MARKER ${s.formatOk}/${s.total}",
                        style = MaterialTheme.typography.body1,
                    )
                    Text("ファイル: ${s.outputPath}", style = MaterialTheme.typography.caption)
                }
                is BatchStatus.Failed -> {
                    Text(
                        "$BATCH_FAILED_MARKER ${s.message}",
                        style = MaterialTheme.typography.body1,
                    )
                }
            }
        }
    }
}
