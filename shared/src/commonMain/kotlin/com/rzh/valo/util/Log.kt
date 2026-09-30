package com.rzh.valo.util

/** 平台日志（Android Logcat / iOS println），公共层统一入口 */
expect fun logWarn(tag: String, message: String, error: Throwable? = null)
