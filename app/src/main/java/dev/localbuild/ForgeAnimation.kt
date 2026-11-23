package dev.localbuild

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

// Éventail d'étincelles vers le haut (radians, axe Y vers le bas : -π/2 = plein haut).
private val sparkAngles = floatArrayOf(-2.9f, -2.5f, -2.2f, -1.9f, -1.6f, -1.3f, -1.0f, -0.7f, -0.35f, -2.05f)
private val sparkSpeed = floatArrayOf(0.30f, 0.38f, 0.46f, 0.34f, 0.50f, 0.36f, 0.44f, 0.32f, 0.28f, 0.40f)

/**
 * Enclume + pièce incandescente + marteau qui frappe, avec gerbe d'étincelles à l'impact.
 * `active = false` : le marteau repose, la pièce est tiède.
 */
@Composable
fun ForgeAnimation(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "forge")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "forge-phase"
    )

    Canvas(modifier) {
        val w = size.width
        val phase = if (active) t else 0f

        // Angle du marteau : 50° levé -> 0° à l'impact (phase 0,5) -> remonte.
        val swing = when {
            !active -> 28f
            phase < 0.5f -> { val x = phase / 0.5f; 50f * (1f - x * x) }
            else -> { val x = (phase - 0.5f) / 0.5f; 50f * (1f - (1f - x) * (1f - x)) }
        }
        // Progression des étincelles après l'impact (-1 = aucune).
        val p = if (active && phase >= 0.5f) (phase - 0.5f) / 0.5f else -1f
        val glow = if (active) 0.6f + 0.4f * (if (p >= 0f) 1f - p else 0f) else 0.35f

        // Lueur derrière la pièce
        val glowCenter = Offset(0.5f * w, 0.55f * w)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Forge.Ember.copy(alpha = 0.55f * glow), Color.Transparent),
                center = glowCenter,
                radius = 0.32f * w
            ),
            radius = 0.32f * w,
            center = glowCenter
        )

        // Enclume
        val anvil = Path().apply {
            moveTo(0.08f * w, 0.58f * w)
            lineTo(0.80f * w, 0.58f * w)
            lineTo(0.80f * w, 0.66f * w)
            lineTo(0.66f * w, 0.66f * w)
            lineTo(0.70f * w, 0.80f * w)
            lineTo(0.78f * w, 0.80f * w)
            lineTo(0.78f * w, 0.90f * w)
            lineTo(0.26f * w, 0.90f * w)
            lineTo(0.26f * w, 0.80f * w)
            lineTo(0.34f * w, 0.80f * w)
            lineTo(0.38f * w, 0.66f * w)
            lineTo(0.24f * w, 0.66f * w)
            close()
        }
        drawPath(anvil, Forge.Steel)
        drawRect(Forge.SteelLight, Offset(0.12f * w, 0.58f * w), Size(0.68f * w, 0.022f * w))

        // Pièce incandescente
        drawRoundRect(
            color = lerp(Color(0xFFB5441A), Forge.Gold, glow),
            topLeft = Offset(0.34f * w, 0.52f * w),
            size = Size(0.32f * w, 0.06f * w),
            cornerRadius = CornerRadius(0.02f * w)
        )

        // Marteau : pivot en haut à droite, la tête tombe sur la pièce à 0°.
        val pivot = Offset(0.84f * w, 0.30f * w)
        val head = Offset(0.50f * w, 0.48f * w)
        rotate(degrees = swing, pivot = pivot) {
            drawLine(
                color = Color(0xFF8B5A2B),
                start = pivot,
                end = head,
                strokeWidth = 0.035f * w,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = Forge.SteelLight,
                topLeft = Offset(head.x - 0.08f * w, head.y - 0.045f * w),
                size = Size(0.16f * w, 0.09f * w),
                cornerRadius = CornerRadius(0.015f * w)
            )
        }

        // Étincelles
        if (p >= 0f) {
            val ox = 0.5f * w
            val oy = 0.52f * w
            for (i in sparkAngles.indices) {
                val a = sparkAngles[i]
                val s = sparkSpeed[i]
                val x = ox + cos(a) * s * p * w
                val y = oy + sin(a) * s * p * w + 0.35f * p * p * w
                drawCircle(
                    color = Forge.Gold.copy(alpha = 1f - p),
                    radius = 0.014f * w * (1.2f - p),
                    center = Offset(x, y)
                )
            }
        }
    }
}
