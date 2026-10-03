package com.rzh.valo

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.rzh.valo.data.AppContainer
import com.rzh.valo.ui.App
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDomainMask

/** iOS 应用版本号（随版本发布更新） */
private const val IOS_VERSION_NAME = "1.0.7"

fun MainViewController() = ComposeUIViewController {
    val container = remember { AppContainer(iosFilesDir()) }
    App(
        container = container,
        versionName = IOS_VERSION_NAME,
    )
}

/** iOS 应用私有 Documents 目录（DataStore / 快照文件所在） */
@OptIn(ExperimentalForeignApi::class)
private fun iosFilesDir(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
        null,
        true,
        null,
    )
    return url?.path ?: (NSHomeDirectory() + "/Documents")
}
