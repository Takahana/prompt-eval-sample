package com.example.prompttest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.prompttest.generated.GeneratedPrompts
import com.example.prompttest.generated.GeneratedTestCases
import com.example.prompttest.genai.LocalLlmClient
import kotlinx.coroutines.launch

@Composable
fun App(llmClient: LocalLlmClient) {
    val runner = remember { PromptRunner(llmClient) }
    val testCases = GeneratedTestCases.cases
    val prompts = GeneratedPrompts.prompts
    val scope = rememberCoroutineScope()

    var selectedCaseIndex by remember { mutableStateOf(0) }
    var selectedPromptIndex by remember { mutableStateOf(0) }
    var generatedPrompt by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var caseDropdownExpanded by remember { mutableStateOf(false) }
    var promptDropdownExpanded by remember { mutableStateOf(false) }

    val selectedCase = testCases[selectedCaseIndex]
    val selectedPrompt = prompts[selectedPromptIndex]

    LaunchedEffect(selectedCaseIndex, selectedPromptIndex) {
        generatedPrompt = runner.buildPrompt(selectedPrompt, selectedCase)
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Prompt Test App") },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Test Case Selector
            Text("テストケース", style = MaterialTheme.typography.subtitle1)
            Box {
                OutlinedButton(onClick = { caseDropdownExpanded = true }) {
                    Text("${selectedCase.id}: ${selectedCase.notes}")
                }
                DropdownMenu(
                    expanded = caseDropdownExpanded,
                    onDismissRequest = { caseDropdownExpanded = false }
                ) {
                    testCases.forEachIndexed { index, case ->
                        DropdownMenuItem(onClick = {
                            selectedCaseIndex = index
                            caseDropdownExpanded = false
                        }) {
                            Text("${case.id}: ${case.notes}")
                        }
                    }
                }
            }

            // Input Preview
            Text("入力", style = MaterialTheme.typography.subtitle2)
            Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                Text(selectedCase.input, modifier = Modifier.padding(12.dp))
            }

            // Prompt Selector
            Text("プロンプト", style = MaterialTheme.typography.subtitle1)
            Box {
                OutlinedButton(onClick = { promptDropdownExpanded = true }) {
                    Text("${selectedPrompt.id}: ${selectedPrompt.name}")
                }
                DropdownMenu(
                    expanded = promptDropdownExpanded,
                    onDismissRequest = { promptDropdownExpanded = false }
                ) {
                    prompts.forEachIndexed { index, prompt ->
                        DropdownMenuItem(onClick = {
                            selectedPromptIndex = index
                            promptDropdownExpanded = false
                        }) {
                            Text("${prompt.id}: ${prompt.name}")
                        }
                    }
                }
            }

            // Generated Prompt Preview
            Text("生成されたプロンプト", style = MaterialTheme.typography.subtitle2)
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp), elevation = 2.dp) {
                Text(
                    generatedPrompt,
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.body2
                )
            }

            // Run Button
            Button(
                onClick = {
                    isRunning = true
                    result = ""
                    scope.launch {
                        result = runner.run(selectedPrompt, selectedCase)
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "生成中..." else "実行")
            }

            // Result
            if (result.isNotEmpty()) {
                Text("結果", style = MaterialTheme.typography.subtitle1)
                Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Text(result, modifier = Modifier.padding(12.dp))
                }
            }
        }
        }
    }
}
