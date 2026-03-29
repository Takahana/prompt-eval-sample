package com.example.prompttest

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.example.prompttest.genai.UnsupportedLlmClient

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App(llmClient = UnsupportedLlmClient())
    }
}
