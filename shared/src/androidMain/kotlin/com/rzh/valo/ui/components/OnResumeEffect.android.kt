package com.rzh.valo.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
actual fun OnResumeEffect(key: Any?, onResume: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
}
