package com.example.prompttest

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual fun currentTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
