package com.example.prompttest

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.prompttest.genai.UnsupportedLlmClient

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Prompt Test App"
    ) {
        App(llmClient = UnsupportedLlmClient())
    }
}
