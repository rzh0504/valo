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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rzh.valo.data.HaojiaoApi
import com.rzh.valo.data.MatchItem
import com.rzh.valo.data.MatchStatus
import com.rzh.valo.data.Participant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun formatTime(epochMillis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))

/** Expressive 按压回弹：按下去轻微缩小，松手弹回 */
@Composable
fun Modifier.bouncyPress(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 800f),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun TeamLogo(path: String?, size: Int, modifier: Modifier = Modifier) {
    val url = HaojiaoApi.imageUrl(path)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

@Composable
fun StatusPill(status: Int, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val (label, container, content) = when {
        status == MatchStatus.LIVE && emphasized -> Triple(
            "进行中", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
        )
        status == MatchStatus.LIVE -> Triple(
            "进行中", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
        )
        status == MatchStatus.FINISHED -> Triple(
            "已结束", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            "未开始", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (status == MatchStatus.LIVE) LiveDot(color = content)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LiveDot(color: Color = MaterialTheme.colorScheme.primary) {
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

/** Expressive 风格加载动画：三点律动 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, delayMillis = index * 130),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .graphicsLayer { translationY = dy }
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun MatchCard(item: MatchItem, onClick: () -> Unit, emphasized: Boolean = false) {
    val versus = item.versus
    val main = versus?.mainCamp?.firstOrNull()
    val guest = versus?.guestCamp?.firstOrNull()
    val mainWon = item.isFinished && versus?.isMainWin == 1
    val guestWon = item.isFinished && versus?.isMainWin == 2
    val interaction = remember { MutableInteractionSource() }
    val container = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.bouncyPress(interaction),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(item.startTime),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(item.status, emphasized = emphasized)
                Spacer(Modifier.weight(1f))
                Text(
                    "BO${item.boNum}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (alignEnd) {
            Text(
                participant?.displayName ?: "待定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (winner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScoreCenter(item: MatchItem, modifier: Modifier = Modifier) {
    val versus = item.versus
    val showScore = (item.isLive || item.isFinished) && versus?.mainCamp?.isNotEmpty() == true
    Box(modifier, contentAlignment = Alignment.Center) {
        if (showScore) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreText(versus.mainScore ?: "0", item.isFinished && versus.isMainWin == 1)
                Text(
                    " : ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScoreText(versus.guestScore ?: "0", item.isFinished && versus.isMainWin == 2)
            }
        } else {
            Text(
                "vs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreText(score: String, isWinner: Boolean) {
    Text(
        score,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
        color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

/** 可点击的表面容器（按压回弹） */
@Composable
fun TonalActionSurface(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content()
    }
}
