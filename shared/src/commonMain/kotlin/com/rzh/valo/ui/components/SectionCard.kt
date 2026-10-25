package com.rzh.valo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 详情页通用区块：小标题 + 圆角低阴影容器 */
@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column {
        Text(
            title,
            style = MiuixTheme.textStyles.footnote1,
            color = scheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    }
}
