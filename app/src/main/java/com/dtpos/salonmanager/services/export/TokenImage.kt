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

/** Token / booking slip as an image for WhatsApp: salon, big token number, name, date and time. */
object TokenImage {
    private const val WIDTH = 720

    fun render(
        salon: String,
        title: String,
        token: Int,
        name: String,
        whenText: String,
        service: String?,
        footer: String,
        accent: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, 980, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = accent
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 200f, fill)
        var y = 52f
        y += text(canvas, salon.uppercase(), 44f, Color.WHITE, bold = true, y = y)
        text(canvas, title.uppercase(), 28f, Color.argb(0xDD, 0xFF, 0xFF, 0xFF), bold = false, y = y + 14f, spacing = 0.25f)

        val box = RectF(150f, 250f, WIDTH - 150f, 560f)
        fill.color = Color.argb(0x1A, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(box, 36f, 36f, fill)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = accent }
        canvas.drawRoundRect(box, 36f, 36f, outline)
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; textSize = 190f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("#$token", WIDTH / 2f, box.centerY() - (number.descent() + number.ascent()) / 2f, number)

        y = 610f
        y += text(canvas, name, 46f, Color.BLACK, bold = true, y = y) + 14f
        y += text(canvas, whenText, 34f, Color.rgb(0x44, 0x40, 0x4C), bold = false, y = y) + 10f
        service?.takeIf { it.isNotBlank() }?.let { y += text(canvas, it, 32f, Color.rgb(0x62, 0x5E, 0x6B), bold = false, y = y) + 10f }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x9A, 0x96, 0xA3); strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        canvas.drawLine(60f, y + 20f, WIDTH - 60f, y + 20f, dash)
        y += 50f
        y += text(canvas, footer, 30f, Color.rgb(0x44, 0x40, 0x4C), bold = false, y = y)
        val height = (y + 50f).toInt().coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, 0, 0, WIDTH, height)
    }

    /** Saves to the share cache (FileProvider "shared/"). */
    fun cacheFile(context: Context, bitmap: Bitmap, token: Int): File? = try {
        val dir = File(context.cacheDir, "shared/tokens").apply { mkdirs() }
        File(dir, "Token_$token.png").also { file -> FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    } catch (e: Exception) {
        null
    }

    private fun text(canvas: Canvas, value: String, size: Float, color: Int, bold: Boolean, y: Float, spacing: Float = 0f): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = spacing
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, WIDTH - 120)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        canvas.save()
        canvas.translate(60f, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }
}
