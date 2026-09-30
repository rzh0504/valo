package com.rzh.valo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rzh.valo.data.ThemeMode
import com.rzh.valo.ui.App
import com.rzh.valo.ui.DeepLinkState
import com.rzh.valo.widget.updateAllScheduleWidgets

class MainActivity : ComponentActivity() {

    /** 小组件点击比赛行时携带的 match_id，导航到详情后清空 */
    private val deepLink = DeepLinkState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 仅首次创建时消费深链 extra：旋转等重建时 intent 仍带着同一 extra，
        // 重复读取会把用户再次导航到详情页
        if (savedInstanceState == null) {
            deepLink.pendingMatchId = intent?.getStringExtra(EXTRA_MATCH_ID)
        }
        val app = application as ValoApplication
        val appContext = applicationContext
        setContent {
            // onCreate 里的一次性 enableEdgeToEdge 只按当时系统配置决定状态栏图标深浅，
            // 之后切主题不会再跟上；这里按解析后的深色状态（应用内覆盖优先）实时刷新系统栏
            val themeMode by app.container.settingsStore.themeMode.collectAsState(ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            App(
                container = app.container,
                versionName = BuildConfig.VERSION_NAME,
                deepLink = deepLink,
                onWidgetDataChanged = { updateAllScheduleWidgets(appContext) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_MATCH_ID)?.let { deepLink.pendingMatchId = it }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }
}
