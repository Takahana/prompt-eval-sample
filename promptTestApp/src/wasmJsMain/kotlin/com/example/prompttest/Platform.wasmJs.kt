package com.example.prompttest

import kotlin.js.Date

internal actual fun currentTimestamp(): String {
    val d = Date()
    val pad = { n: Int -> n.toString().padStart(2, '0') }
    return "${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}"
}
