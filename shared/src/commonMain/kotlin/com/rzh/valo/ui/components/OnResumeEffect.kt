package com.rzh.valo.ui.components

import androidx.compose.runtime.Composable

/** 回到前台时回调一次（Android ON_RESUME；iOS 简化为一次性调用，暂无前后台监听） */
@Composable
expect fun OnResumeEffect(key: Any? = Unit, onResume: () -> Unit)
