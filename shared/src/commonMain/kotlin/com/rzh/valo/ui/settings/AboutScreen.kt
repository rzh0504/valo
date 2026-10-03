package com.rzh.valo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.rzh.valo.ui.LocalVersionName

/** 关于页：版本、开源仓库、数据来源与免责声明 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val versionName = LocalVersionName.current
    val uriHandler = LocalUriHandler.current
    var showDisclaimer by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "关于",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        // miuix 弹层依赖根 Scaffold 注册，dialog 必须位于 Scaffold content 子树内，否则静默不可见
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                item(key = "app") {
                    SmallTitle(text = "应用")
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "版本",
                            summary = versionName.ifBlank { "1.0.8" },
                        )
                        ArrowPreference(
                            title = "开源仓库",
                            summary = "github.com/rzh0504/valo",
                            onClick = { runCatching { uriHandler.openUri("https://github.com/rzh0504/valo") } },
                        )
                        ArrowPreference(
                            title = "数据来源",
                            summary = "haojiao.cc",
                            onClick = { runCatching { uriHandler.openUri("https://web.haojiao.cc") } },
                        )
                        ArrowPreference(
                            title = "免责声明",
                            onClick = { showDisclaimer = true },
                        )
                    }
                }
            }

            if (showDisclaimer) {
                OverlayDialog(
                    show = true,
                    onDismissRequest = { showDisclaimer = false },
                    title = "免责声明",
                    summary = "赛程与赛事数据来自 haojiao.cc",
                    onDismissFinished = { },
                ) {
                    Column {
                        Text(
                            "本应用仅为赛程查询工具，请合理使用；仅供个人学习和研究使用，不得用于商业用途，请勿高频请求接口。\n\n" +
                                "数据可能存在延迟或疏漏，赛程与赛果以赛事官方发布为准。\n\n" +
                                "本应用与 Riot Games 无关，VALORANT 及相关标识归其各自所有者所有。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            text = "知道了",
                            onClick = { showDisclaimer = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
