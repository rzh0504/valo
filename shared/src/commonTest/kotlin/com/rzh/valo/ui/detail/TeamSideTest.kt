package com.rzh.valo.ui.detail

import com.rzh.valo.data.Participant
import com.rzh.valo.data.VersusInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamSideTest {

    private fun team(
        id: String? = null,
        name: String? = null,
        short: String? = null,
        icon: String? = null,
    ) = Participant(id = id, nameMain = name, nameShort = short, icon = icon)

    private fun versus(main: Participant?, guest: Participant?) = VersusInfo(
        mainCamp = main?.let(::listOf),
        guestCamp = guest?.let(::listOf),
    )

    @Test
    fun `按战队 ID 匹配主客队`() {
        val v = versus(team(id = "t1"), team(id = "t2"))
        assertEquals(1, teamSide(v, team(id = "t1")))
        assertEquals(2, teamSide(v, team(id = "t2")))
    }

    @Test
    fun `无 ID 时按队标匹配`() {
        val v = versus(team(icon = "/a.png"), team(icon = "/b.png"))
        assertEquals(2, teamSide(v, team(icon = "/b.png")))
    }

    @Test
    fun `队名匹配忽略大小写且长短名互通`() {
        val v = versus(team(name = "Edward Gaming", short = "EDG"), team(name = "Trace Esports"))
        assertEquals(1, teamSide(v, team(name = "edg")))
        assertEquals(2, teamSide(v, team(short = "TRACE ESPORTS")))
    }

    @Test
    fun `不在对阵中返回 0`() {
        val v = versus(team(id = "t1", name = "A"), team(id = "t2", name = "B"))
        assertEquals(0, teamSide(v, team(id = "t3", name = "C")))
        assertEquals(0, teamSide(null, team(id = "t1")))
    }

    @Test
    fun `队伍待定（camp 为 null）返回 0`() {
        assertEquals(0, teamSide(versus(null, null), team(id = "t1")))
    }

    @Test
    fun `空白队名不参与匹配`() {
        val v = versus(team(name = ""), team(name = "B"))
        assertEquals(0, teamSide(v, team(name = "")))
    }
}