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

/** Token slip designs the salon can choose in printer settings. */
enum class TokenStyle {
    /** Dark band with the salon name, number in a rounded box. */
    CLASSIC,
    /** Huge number across the slip: readable from across the room. */
    BIG,
    /** Ticket with a border, side notches and the number on a dark block. */
    TICKET,
    /** Number and details side by side: about half the paper. */
    COMPACT,
}

/**
 * Token / booking slip: salon, big token number, name, date and time, services. The same layout is
 * used for WhatsApp (colour, 720 px) and for the thermal printer (black and white at the paper's
 * dot width, so the print preview is exactly what prints). On paper every line is bold so it
 * prints dark on any thermal printer.
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
        style: TokenStyle = TokenStyle.CLASSIC,
    ): Bitmap {
        val d = Draw(width, accent, forPrinter)
        val content = Content(salon, title, token, name, whenText, service?.takeIf { it.isNotBlank() }, footer)
        return when (style) {
            TokenStyle.CLASSIC -> d.classic(content)
            TokenStyle.BIG -> d.big(content)
            TokenStyle.TICKET -> d.ticket(content)
            TokenStyle.COMPACT -> d.compact(content)
        }
    }

    /** Saves to the share cache (FileProvider "shared/"). */
    fun cacheFile(context: Context, bitmap: Bitmap, token: Int): File? = try {
        val dir = File(context.cacheDir, "shared/tokens").apply { mkdirs() }
        File(dir, "Token_$token.png").also { file -> FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    } catch (e: Exception) {
        null
    }

    private class Content(
        val salon: String,
        val title: String,
        val token: Int,
        val name: String,
        val whenText: String,
        val service: String?,
        val footer: String,
    )

    private class Draw(val width: Int, accent: Int, val forPrinter: Boolean) {
        val s = width / SHARE_WIDTH.toFloat()
        val ink = if (forPrinter) Color.BLACK else accent
        val grey = if (forPrinter) Color.BLACK else Color.rgb(0x44, 0x40, 0x4C)
        val soft = Color.argb(0x1A, Color.red(accent), Color.green(accent), Color.blue(accent))
        val bitmap: Bitmap = Bitmap.createBitmap(width, (2000 * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun crop(bottom: Float): Bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, bottom.toInt().coerceIn(1, bitmap.height))

        /** Centred text block; returns its height. On paper every line is bold. */
        fun text(value: String, size: Float, color: Int, bold: Boolean, y: Float, spacing: Float = 0f, left: Float = 60f * s, right: Float = width - 60f * s, center: Boolean = true): Float {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * s
                this.color = color
                typeface = Typeface.create("sans-serif", if (bold || forPrinter) Typeface.BOLD else Typeface.NORMAL)
                letterSpacing = spacing
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, (right - left).toInt().coerceAtLeast(1))
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).build()
            canvas.save()
            canvas.translate(left, y)
            layout.draw(canvas)
            canvas.restore()
            return layout.height.toFloat()
        }

        /** The token number in [box]: as large as fits. */
        fun number(token: Int, box: RectF, color: Int, maxSize: Float) {
            val label = "#$token"
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = maxSize * s
            }
            val fit = box.width() * 0.86f
            if (paint.measureText(label) > fit) paint.textSize *= fit / paint.measureText(label)
            canvas.drawText(label, box.centerX(), box.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
        }

        fun dashed(y: Float, from: Float = 60f * s, to: Float = width - 60f * s) {
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (forPrinter) Color.BLACK else Color.rgb(0x9A, 0x96, 0xA3)
                strokeWidth = 3f * s
                pathEffect = DashPathEffect(floatArrayOf(14f * s, 10f * s), 0f)
            }
            canvas.drawLine(from, y, to, y, dash)
        }

        /** Name, date / time, services, dashed line and the footer; returns the bottom. */
        fun details(c: Content, top: Float, nameSize: Float = 48f): Float {
            var y = top
            y += text(c.name, nameSize, Color.BLACK, bold = true, y = y) + 14f * s
            y += text(c.whenText, 36f, grey, bold = forPrinter, y = y) + 10f * s
            c.service?.let { y += text(it, 33f, grey, bold = false, y = y) + 10f * s }
            dashed(y + 20f * s)
            y += 50f * s
            y += text(c.footer, 30f, grey, bold = false, y = y)
            return y + 50f * s
        }

        fun classic(c: Content): Bitmap {
            fill.color = ink
            canvas.drawRect(0f, 0f, width.toFloat(), 200f * s, fill)
            var y = 52f * s
            y += text(c.salon.uppercase(), 44f, Color.WHITE, bold = true, y = y)
            text(c.title.uppercase(), 28f, if (forPrinter) Color.WHITE else Color.argb(0xDD, 0xFF, 0xFF, 0xFF), bold = forPrinter, y = y + 14f * s, spacing = 0.25f)
            val box = RectF(150f * s, 250f * s, width - 150f * s, 560f * s)
            if (!forPrinter) {
                fill.color = soft
                canvas.drawRoundRect(box, 36f * s, 36f * s, fill)
            }
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f * s; color = ink }
            canvas.drawRoundRect(box, 36f * s, 36f * s, outline)
            number(c.token, box, ink, 190f)
            return crop(details(c, 610f * s))
        }

        fun big(c: Content): Bitmap {
            var y = 44f * s
            y += text(c.salon.uppercase(), 44f, ink, bold = true, y = y, spacing = 0.04f) + 6f * s
            y += text(c.title.uppercase(), 28f, grey, bold = true, y = y, spacing = 0.35f) + 10f * s
            fill.color = ink
            canvas.drawRect(60f * s, y, width - 60f * s, y + 8f * s, fill)
            y += 8f * s
            val box = RectF(30f * s, y, width - 30f * s, y + 330f * s)
            number(c.token, box, ink, 320f)
            y = box.bottom
            canvas.drawRect(60f * s, y, width - 60f * s, y + 8f * s, fill)
            return crop(details(c, y + 36f * s, nameSize = 54f))
        }

        fun ticket(c: Content): Bitmap {
            val inset = 22f * s
            var y = inset + 40f * s
            y += text(c.salon.uppercase(), 42f, ink, bold = true, y = y) + 6f * s
            y += text(c.title.uppercase(), 26f, grey, bold = true, y = y, spacing = 0.35f) + 22f * s
            val block = RectF(130f * s, y, width - 130f * s, y + 240f * s)
            fill.color = ink
            canvas.drawRoundRect(block, 28f * s, 28f * s, fill)
            number(c.token, block, Color.WHITE, 180f)
            y = block.bottom + 46f * s
            val tear = y
            y += 34f * s
            val bottom = details(c, y)
            // Border with a notch on each side at the tear line, like a cinema ticket.
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * s; color = ink }
            val frame = RectF(inset, inset, width - inset, bottom)
            canvas.drawRoundRect(frame, 30f * s, 30f * s, border)
            val notch = 26f * s
            fill.color = Color.WHITE
            canvas.drawCircle(inset, tear, notch, fill)
            canvas.drawCircle(width - inset, tear, notch, fill)
            canvas.drawCircle(inset, tear, notch, border)
            canvas.drawCircle(width - inset, tear, notch, border)
            fill.color = Color.WHITE
            canvas.drawRect(0f, tear - notch - 4f * s, inset - 2.5f * s, tear + notch + 4f * s, fill)
            canvas.drawRect(width - inset + 2.5f * s, tear - notch - 4f * s, width.toFloat(), tear + notch + 4f * s, fill)
            dashed(tear, inset + notch + 14f * s, width - inset - notch - 14f * s)
            return crop(bottom + inset + 10f * s)
        }

        fun compact(c: Content): Bitmap {
            val top = 30f * s
            val box = RectF(30f * s, top, 290f * s, top + 260f * s)
            fill.color = ink
            canvas.drawRoundRect(box, 26f * s, 26f * s, fill)
            number(c.token, box, Color.WHITE, 150f)
            val left = 320f * s
            val right = width - 30f * s
            var y = top + 6f * s
            y += text(c.salon.uppercase(), 30f, ink, bold = true, y = y, left = left, right = right, center = false) + 8f * s
            y += text(c.name, 42f, Color.BLACK, bold = true, y = y, left = left, right = right, center = false) + 8f * s
            y += text(c.whenText, 30f, grey, bold = forPrinter, y = y, left = left, right = right, center = false) + 6f * s
            c.service?.let { y += text(it, 28f, grey, bold = false, y = y, left = left, right = right, center = false) }
            y = maxOf(y, box.bottom) + 26f * s
            dashed(y, 30f * s, width - 30f * s)
            y += 22f * s
            y += text(c.footer, 26f, grey, bold = false, y = y, left = 30f * s, right = width - 30f * s)
            return crop(y + 34f * s)
        }
    }
}
