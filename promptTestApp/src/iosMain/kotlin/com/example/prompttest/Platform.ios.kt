package com.example.prompttest

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

internal actual fun currentTimestamp(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyyMMdd_HHmmss"
    return formatter.stringFromDate(NSDate())
}
