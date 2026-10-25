package com.rzh.valo.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rzh.valo.data.TeamReward
import com.rzh.valo.ui.components.StatsTableCell
import com.rzh.valo.ui.components.StatsTableHeaderRow
import com.rzh.valo.ui.components.tableZebraColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val RESULT_WIDTH = 68.dp
private val BONUS_WIDTH = 68.dp

/** 赛事名次表头；列宽与 [RewardRow] 共用，保证详情页与全部页对齐 */
@Composable
internal fun RewardTableHeader() {
    StatsTableHeaderRow(
        "赛事",
        listOf("战绩" to RESULT_WIDTH, "奖金" to BONUS_WIDTH),
    )
}

/** 名次行：赛事名独占首行，名次与日期（不含时分秒）位于次行，右侧为战绩与奖金 */
@Composable
internal fun RewardRow(reward: TeamReward, index: Int) {
    val scheme = MiuixTheme.colorScheme
    val date = reward.date?.substringBefore(' ').orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(tableZebraColor(index, scheme))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                reward.group?.nameMain.orEmpty(),
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reward.rank.orEmpty(),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (date.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        date,
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        StatsTableCell(
            "${reward.matchWinCount}胜${reward.matchCount - reward.matchWinCount}负",
            RESULT_WIDTH,
            secondary = true,
        )
        StatsTableCell(reward.bonus.orEmpty().ifBlank { "—" }, BONUS_WIDTH, secondary = true)
    }
}
