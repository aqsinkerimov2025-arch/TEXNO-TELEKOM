package az.texnotelekom.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF

@Composable
fun TexnoTelekomLogo() {
    Canvas(Modifier.size(220.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.43f
        drawCircle(Color(0xFF171419), radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
        drawCircle(Color(0xFFC8BBA8), radius = r * 0.94f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color(0xFF3B3530), radius = r * 0.76f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 5.dp.toPx()))
        drawCircle(Color(0xFFE4D7C3), radius = r * 0.68f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(215, 203, 184)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setShadowLayer(2f, 1f, 2f, android.graphics.Color.BLACK)
            }
            paint.textSize = size.width * 0.145f
            drawText("TEXNO", cx, cy - r * 0.38f, paint)
            val banner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(20, 18, 18)
                style = Paint.Style.FILL
            }
            drawRoundRect(RectF(cx-r*1.12f, cy-r*0.25f, cx+r*1.12f, cy+r*0.25f), 5f, 5f, banner)
            paint.textSize = size.width * 0.29f
            paint.color = android.graphics.Color.rgb(232, 218, 196)
            drawText("T-T", cx, cy + r*0.12f, paint)
            paint.textSize = size.width * 0.13f
            paint.color = android.graphics.Color.rgb(25, 23, 22)
            paint.clearShadowLayer()
            drawText("TELEKOM", cx, cy + r*0.57f, paint)
        }
    }
}
