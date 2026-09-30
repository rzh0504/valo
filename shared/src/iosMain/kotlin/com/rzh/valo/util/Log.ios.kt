package com.rzh.valo.util

actual fun logWarn(tag: String, message: String, error: Throwable?) {
    println("[$tag] $message${error?.let { ": $it" } ?: ""}")
}
