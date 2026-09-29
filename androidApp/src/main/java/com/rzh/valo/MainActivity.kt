package com.rzh.valo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
