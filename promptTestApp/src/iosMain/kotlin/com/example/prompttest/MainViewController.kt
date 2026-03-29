package com.example.prompttest

import androidx.compose.ui.window.ComposeUIViewController
import com.example.prompttest.genai.UnsupportedLlmClient

fun MainViewController() = ComposeUIViewController {
    App(llmClient = UnsupportedLlmClient())
}
