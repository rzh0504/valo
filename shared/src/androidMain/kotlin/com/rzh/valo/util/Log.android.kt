package com.rzh.valo.util

import android.util.Log

actual fun logWarn(tag: String, message: String, error: Throwable?) {
    Log.w(tag, message, error)
}
