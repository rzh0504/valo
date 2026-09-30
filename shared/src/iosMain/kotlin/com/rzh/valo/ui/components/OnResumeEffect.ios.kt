package com.rzh.valo.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun OnResumeEffect(key: Any?, onResume: () -> Unit) {
    // iOS 端暂无等价的生命周期钩子；TTL 缓存 + 下拉刷新已覆盖数据新鲜度
}
