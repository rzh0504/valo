package com.rzh.valo.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/** miuix-nav 路由（@Serializable 以支持状态恢复） */
@Serializable
sealed interface Route : NavKey {
    /** 主界面：底部页签（今天 / 赛程 / 设置） */
    @Serializable
    data object Main : Route

    @Serializable
    data class Match(val matchId: String) : Route

    @Serializable
    data class TeamInfo(
        val teamId: String,
        val name: String = "",
        val short: String = "",
        val icon: String = "",
    ) : Route

    @Serializable
    data class TeamSchedule(
        val teamId: String,
        val name: String = "",
        val short: String = "",
        val icon: String = "",
    ) : Route

    @Serializable
    data class Tournament(val tournamentId: String, val name: String = "") : Route
}
