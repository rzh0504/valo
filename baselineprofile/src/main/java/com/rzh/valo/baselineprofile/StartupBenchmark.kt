package com.rzh.valo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 baseline profile 的用例：覆盖冷启动 + 首页/赛程页首屏渲染与滚动热路径，
 * 使这些代码随 APK 以 AOT 形式预编译，消除每次发版更新后的 JIT 编译窗口期卡顿。
 *
 * 交互全部使用固定坐标手势（应用未暴露 testTag），不依赖网络结果——
 * 首屏若停留在骨架屏或错误页，滚动路径同样会被执行并纳入 profile。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() = baselineProfileRule.collect(
        packageName = "com.rzh.valo",
        // 覆盖到的启动/首屏路径进 startup profile，安装时即完成 AOT 编译
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // 首页：等首帧数据渲染后上下滚动，覆盖 LazyColumn + 比赛卡片渲染路径
        device.waitForIdle()
        repeat(2) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 4, 40)
            device.waitForIdle()
        }
        device.swipe(device.displayWidth / 2, device.displayHeight / 3,
            device.displayWidth / 2, device.displayHeight * 4 / 5, 40)
        device.waitForIdle()

        // 横滑到「赛程」页：覆盖 Pager 切页 + 日期吸顶列表渲染路径，再滑回
        device.swipe(device.displayWidth * 9 / 10, device.displayHeight / 2,
            device.displayWidth / 10, device.displayHeight / 2, 30)
        device.waitForIdle()
        repeat(2) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 4, 40)
            device.waitForIdle()
        }
        device.swipe(device.displayWidth / 10, device.displayHeight / 2,
            device.displayWidth * 9 / 10, device.displayHeight / 2, 30)
        device.waitForIdle()
    }
}
