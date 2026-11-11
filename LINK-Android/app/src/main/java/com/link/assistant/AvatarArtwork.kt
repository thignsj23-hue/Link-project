package com.link.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** Clean vector mascot used when the user selects one of the built-in 2D characters. */
@androidx.compose.runtime.Composable
fun AvatarArtwork(character: String, modifier: Modifier = Modifier) {
    val head = when (character) {
        "Gato" -> Color(0xFF4541A5)
        "Robô" -> Color(0xFF356A84)
        "Panda" -> Color(0xFF303B55)
        else -> Color(0xFF493A9C)
    }
    Canvas(modifier.aspectRatio(1f).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1B6277), Color(0xFF10243A), Color(0xFF080D18))))) {
        val w = size.width
        val h = size.height
        val earLeft = Path().apply { moveTo(w * .20f, h * .47f); lineTo(w * .24f, h * .12f); lineTo(w * .49f, h * .34f); close() }
        val earRight = Path().apply { moveTo(w * .51f, h * .34f); lineTo(w * .77f, h * .12f); lineTo(w * .81f, h * .47f); close() }
        drawPath(earLeft, head); drawPath(earRight, head)
        val inner = if (character == "Panda") Color(0xFFE5D6FF) else Color(0xFF55D6E8)
        val innerLeft = Path().apply { moveTo(w * .27f, h * .36f); lineTo(w * .29f, h * .20f); lineTo(w * .42f, h * .34f); close() }
        val innerRight = Path().apply { moveTo(w * .58f, h * .34f); lineTo(w * .71f, h * .20f); lineTo(w * .73f, h * .36f); close() }
        drawPath(innerLeft, inner); drawPath(innerRight, inner)
        drawCircle(head, radius = w * .34f, center = Offset(w * .5f, h * .55f))
        if (character == "Robô") {
            drawLine(inner, Offset(w * .5f, h * .18f), Offset(w * .5f, h * .30f), strokeWidth = w * .025f)
            drawCircle(Color(0xFFFF6EC7), radius = w * .04f, center = Offset(w * .5f, h * .16f))
            drawRoundRect(Color(0xFF202A4B), topLeft = Offset(w * .25f, h * .43f), size = Size(w * .50f, h * .28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f))
            drawCircle(Color(0xFF75F3FF), radius = w * .045f, center = Offset(w * .39f, h * .53f))
            drawCircle(Color(0xFF75F3FF), radius = w * .045f, center = Offset(w * .61f, h * .53f))
            drawLine(inner, Offset(w * .41f, h * .63f), Offset(w * .59f, h * .63f), strokeWidth = w * .025f)
        } else {
            val eye = if (character == "Panda") Color.White else Color(0xFF70F2FF)
            if (character == "Panda") {
                drawOval(Color(0xFF151A2A), topLeft = Offset(w * .31f, h * .43f), size = Size(w * .15f, h * .18f))
                drawOval(Color(0xFF151A2A), topLeft = Offset(w * .54f, h * .43f), size = Size(w * .15f, h * .18f))
            }
            drawLine(eye, Offset(w * .37f, h * .52f), Offset(w * .43f, h * .52f), strokeWidth = w * .035f)
            drawLine(eye, Offset(w * .57f, h * .52f), Offset(w * .63f, h * .52f), strokeWidth = w * .035f)
            drawCircle(Color(0xFFFF8CCB), radius = w * .025f, center = Offset(w * .5f, h * .60f))
            val smile = Path().apply { moveTo(w * .43f, h * .65f); quadraticTo(w * .5f, h * .72f, w * .57f, h * .65f) }
            drawPath(smile, eye, style = Stroke(width = w * .024f))
        }
        drawCircle(Color(0xFF55D6E8).copy(alpha = .75f), radius = w * .48f, center = Offset(w * .5f, h * .5f), style = Stroke(width = w * .012f))
    }
}
