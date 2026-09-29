package com.rzh.valo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rzh.valo.data.HaojiaoApi
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import com.rzh.valo.data.formatTime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 未开赛的相对开赛时间；已到点/已开赛返回 null */
fun relativeStartLabel(startTime: Long, now: Long = com.rzh.valo.data.nowMillis()): String? {
    val minutes = (startTime - now) / 60_000
    return when {
        startTime <= now -> null
        minutes < 1 -> "即将开始"
        minutes < 60 -> "$minutes 分钟后"
        else -> "${minutes / 60} 小时后"
    }
}

/** Expressive 按压回弹：按下去轻微缩小，松手弹回 */
fun Modifier.bouncyPress(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 800f),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 队伍/英雄图标：灰底衬托浅色队标，原样展示不裁切；传入 shape 时底与裁切做对应圆角，无图时用同色块占位 */
@Composable
fun TeamLogo(path: String?, size: Int, modifier: Modifier = Modifier, shape: Shape? = null) {
    val url = HaojiaoApi.imageUrl(path)
    val background = MiuixTheme.colorScheme.surfaceVariant
    val resolvedShape = shape ?: RoundedCornerShape(4.dp)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size.dp)
                .clip(resolvedShape)
                .background(background),
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(resolvedShape)
                .background(background),
        )
    }
}

@Composable
fun StatusPill(status: Int, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val scheme = MiuixTheme.colorScheme
    val (label, container, content) = when {
        status == MatchStatus.LIVE && emphasized -> Triple("进行中", scheme.primary, scheme.onPrimary)
        status == MatchStatus.LIVE -> Triple("进行中", scheme.primaryContainer, scheme.onPrimaryContainer)
        status == MatchStatus.FINISHED -> Triple("已结束", scheme.surfaceContainerHighest, scheme.onSurfaceVariantSummary)
        else -> Triple("未开始", scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (status == MatchStatus.LIVE) LiveDot(color = content)
            Text(label, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LiveDot(color: Color = MiuixTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "liveAlpha",
    )
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(7.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

/** 首屏加载骨架：仿比赛卡片的占位块，整体透明度脉冲 */
@Composable
fun SkeletonCards(modifier: Modifier = Modifier, cards: Int = 4) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Column(
        modifier = modifier.alpha(pulse),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cards) { SkeletonCard() }
    }
}

@Composable
private fun SkeletonCard() {
    val scheme = MiuixTheme.colorScheme
    val block = scheme.surfaceContainerHighest
    Card(
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row {
                Box(Modifier.size(48.dp, 14.dp).clip(CircleShape).background(block))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(36.dp, 14.dp).clip(CircleShape).background(block))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Spacer(Modifier.width(28.dp))
                Box(Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Spacer(Modifier.width(28.dp))
                Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(4.dp)).background(block))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(CircleShape).background(block))
        }
    }
}

@Composable
fun LoadingPane(modifier: Modifier = Modifier, label: String = "正在获取赛程") {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator()
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun MatchCard(
    item: MatchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    /** 未开赛时在时间旁追加相对开赛时间（仅今天页使用，跨天窗口无意义） */
    showCountdown: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    val versus = item.versus
    val main = versus?.mainCamp?.firstOrNull()
    val guest = versus?.guestCamp?.firstOrNull()
    val mainWon = item.isFinished && versus?.isMainWin == 1
    val guestWon = item.isFinished && versus?.isMainWin == 2

    Card(
        onClick = onClick,
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = if (emphasized) scheme.primaryContainer else scheme.surfaceContainer,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(item.startTime),
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (emphasized) scheme.onPrimaryContainer else scheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(item.status, emphasized = emphasized)
                if (showCountdown && item.status == MatchStatus.SCHEDULED) {
                    relativeStartLabel(item.startTime)?.let { label ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = scheme.primary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "BO${item.boNum}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (emphasized) scheme.onPrimaryContainer else scheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamSide(main, mainWon, alignEnd = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                ScoreCenter(item, modifier = Modifier.width(76.dp))
                Spacer(Modifier.width(10.dp))
                TeamSide(guest, guestWon, alignEnd = false, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle(item),
                style = MiuixTheme.textStyles.body2,
                color = if (emphasized) {
                    scheme.onPrimaryContainer.copy(alpha = 0.75f)
                } else {
                    scheme.onSurfaceVariantSummary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun subtitle(item: MatchItem): String {
    val parts = listOfNotNull(
        item.group?.nameMain,
        item.stage?.name,
        item.scheduleName?.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(" · ").ifBlank { item.tournament?.nameMain.orEmpty() }
}

@Composable
private fun TeamSide(participant: Participant?, winner: Boolean, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (alignEnd) {
            Text(
                participant?.displayName ?: "待定",
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (winner) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(8.dp))
        }
        TeamLogo(participant?.icon, size = 30)
        if (!alignEnd) {
            Spacer(Modifier.width(8.dp))
            Text(
                participant?.displayName ?: "待定",
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (winner) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScoreCenter(item: MatchItem, modifier: Modifier = Modifier) {
    val versus = item.versus
    val scheme = MiuixTheme.colorScheme
    val showScore = (item.isLive || item.isFinished) && versus?.mainCamp?.isNotEmpty() == true
    Box(modifier, contentAlignment = Alignment.Center) {
        if (showScore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreText(versus.mainScore ?: "0", item.isFinished && versus.isMainWin == 1)
                Text(
                    " : ",
                    style = MiuixTheme.textStyles.body1,
                    color = scheme.onSurfaceVariantSummary,
                )
                ScoreText(versus.guestScore ?: "0", item.isFinished && versus.isMainWin == 2)
            }
        } else {
            Text(
                "vs",
                style = MiuixTheme.textStyles.body1,
                color = scheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ScoreText(score: String, isWinner: Boolean) {
    Text(
        score,
        style = MiuixTheme.textStyles.title2,
        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
        color = if (isWinner) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
    )
}

/** 可点击的表面容器（按压回弹） */
@Composable
fun TonalActionSurface(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content()
    }
}
