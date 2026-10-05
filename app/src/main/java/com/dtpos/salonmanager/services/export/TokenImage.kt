package com.dtpos.salonmanager.services.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Token / booking slip: salon, big token number, name, date and time. The same layout is used
 * for WhatsApp (colour, 720 px) and for the thermal printer (black and white at the paper's dot
 * width, so the print preview is exactly what prints).
 */
object TokenImage {
    const val SHARE_WIDTH = 720

    fun render(
        salon: String,
        title: String,
        token: Int,
        name: String,
        whenText: String,
        service: String?,
        footer: String,
        accent: Int,
        width: Int = SHARE_WIDTH,
        forPrinter: Boolean = false,
    ): Bitmap {
        val s = width / SHARE_WIDTH.toFloat()
        val ink = if (forPrinter) Color.BLACK else accent
        val grey = if (forPrinter) Color.BLACK else Color.rgb(0x44, 0x40, 0x4C)
        val bitmap = Bitmap.createBitmap(width, (980 * s).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = ink
        canvas.drawRect(0f, 0f, width.toFloat(), 200f * s, fill)
        var y = 52f * s
        y += text(canvas, width, s, salon.uppercase(), 44f, Color.WHITE, bold = true, y = y)
        text(canvas, width, s, title.uppercase(), 28f, if (forPrinter) Color.WHITE else Color.argb(0xDD, 0xFF, 0xFF, 0xFF), bold = forPrinter, y = y + 14f * s, spacing = 0.25f)

        val box = RectF(150f * s, 250f * s, width - 150f * s, 560f * s)
        if (!forPrinter) {
            fill.color = Color.argb(0x1A, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(box, 36f * s, 36f * s, fill)
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * s; color = ink }
        canvas.drawRoundRect(box, 36f * s, 36f * s, outline)
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 190f * s; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("#$token", width / 2f, box.centerY() - (number.descent() + number.ascent()) / 2f, number)

        y = 610f * s
        y += text(canvas, width, s, name, 46f, Color.BLACK, bold = true, y = y) + 14f * s
        y += text(canvas, width, s, whenText, 34f, grey, bold = forPrinter, y = y) + 10f * s
        service?.takeIf { it.isNotBlank() }?.let { y += text(canvas, width, s, it, 32f, grey, bold = false, y = y) + 10f * s }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (forPrinter) Color.BLACK else Color.rgb(0x9A, 0x96, 0xA3)
            strokeWidth = 3f * s
            pathEffect = DashPathEffect(floatArrayOf(14f * s, 10f * s), 0f)
        }
        canvas.drawLine(60f * s, y + 20f * s, width - 60f * s, y + 20f * s, dash)
        y += 50f * s
        y += text(canvas, width, s, footer, 30f, grey, bold = false, y = y)
        val height = (y + 50f * s).toInt().coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    /** Saves to the share cache (FileProvider "shared/"). */
    fun cacheFile(context: Context, bitmap: Bitmap, token: Int): File? = try {
        val dir = File(context.cacheDir, "shared/tokens").apply { mkdirs() }
        File(dir, "Token_$token.png").also { file -> FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    } catch (e: Exception) {
        null
    }

    private fun text(canvas: Canvas, width: Int, s: Float, value: String, size: Float, color: Int, bold: Boolean, y: Float, spacing: Float = 0f): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * s
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = spacing
        }
        val margin = 60f * s
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, (width - 2 * margin).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        canvas.save()
        canvas.translate(margin, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }
}
