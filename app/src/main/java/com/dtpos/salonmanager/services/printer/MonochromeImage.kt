package com.dtpos.salonmanager.services.printer

/** 1-bit image for thermal printing. [pixels] is row-major, true = black dot. */
class MonochromeImage(val width: Int, val height: Int, private val pixels: BooleanArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "invalid image size" }
    }

    fun isBlack(x: Int, y: Int): Boolean = pixels[y * width + x]

    companion object {
        /** Printable width in dots: 384 for 58 mm, 576 for 80 mm printers (203 dpi). */
        const val DOTS_58MM = 384
        const val DOTS_80MM = 576

        /**
         * Converts ARGB pixels to black/white with Floyd-Steinberg dithering, which keeps
         * logos and photos recognisable on thermal paper. Transparent pixels become white.
         */
        fun fromArgb(argb: IntArray, width: Int, height: Int, dither: Boolean = true): MonochromeImage {
            require(argb.size == width * height) { "pixel count mismatch" }
            val luminance = FloatArray(argb.size) { i ->
                val c = argb[i]
                val alpha = (c ushr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                // Blend with white background according to alpha.
                lum * alpha / 255f + 255f * (255 - alpha) / 255f
            }
            val out = BooleanArray(argb.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val old = luminance[i]
                    val black = old < 128f
                    out[i] = black
                    if (!dither) continue
                    val error = old - if (black) 0f else 255f
                    if (x + 1 < width) luminance[i + 1] += error * 7 / 16
                    if (y + 1 < height) {
                        if (x > 0) luminance[i + width - 1] += error * 3 / 16
                        luminance[i + width] += error * 5 / 16
                        if (x + 1 < width) luminance[i + width + 1] += error * 1 / 16
                    }
                }
            }
            return MonochromeImage(width, height, out)
        }
    }
}
