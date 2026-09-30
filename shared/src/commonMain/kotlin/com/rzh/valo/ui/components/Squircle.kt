package com.rzh.valo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled

/**
 * 连续曲率圆角 Shape（squircle）：给 Surface 等需要 Shape 对象的组件用，
 * 与 RoundedCornerShape 的四段圆弧相比角部曲率过渡更平滑。
 * shader 不可用或全局关闭时回落普通圆角轮廓。
 */
@Composable
fun squircleShape(cornerRadius: Dp): Shape {
    val enabled = isSquircleEnabled()
    return remember(cornerRadius, enabled) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
                val path = Path()
                path.addSquircleRect(
                    width = size.width,
                    height = size.height,
                    cornerRadius = with(density) { cornerRadius.toPx() },
                    squircleEnabled = enabled,
                )
                return Outline.Generic(path)
            }
        }
    }
}
