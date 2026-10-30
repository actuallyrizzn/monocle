package com.actuallyrizzn.monocle

object LensText {
    // Firmware display.py: WIDTH=640 FONT_WIDTH=24 FONT_HEIGHT=48.
    // Overflow clips; y and y+48 count as TextOverlapError (inclusive).
    const val COLS = 26
    const val LINE_H = 50
    const val ORIGIN_X = 0
    const val ORIGIN_Y = 0
    const val CENTER_X = 320
    const val SCROLL_MS = 1800
    const val MAX_LINES = 48
    const val BAT_COLS = 4
    const val SUBJ_COLS = COLS - BAT_COLS - 1
    const val CHROME_ROWS = 2
    const val BODY_Y = CHROME_ROWS * LINE_H
    const val BODY_ROWS = 6
    const val ROWS = CHROME_ROWS + BODY_ROWS
    const val DEFAULT_SUBJECT = "Monocle"
    const val DEFAULT_FROM = "—"
    const val PRODUCT_SUBJECT = "Sanctum"
    const val PRODUCT_FROM = "Monocle Bridge"
    const val SUBJECT_COLOR = 0x29D0D0
    const val FROM_COLOR = 0xFFEE33
    const val BAT_COLOR = 0xFFEE33
    const val BODY_COLOR = 0xFFFFFF
    const val BRIGHTNESS = 0

    fun wrap(text: String, cols: Int = COLS, maxLines: Int = MAX_LINES): List<String> {
        val words = text.replace("\r", " ").replace("\n", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(" ")
        val lines = mutableListOf<String>()
        var cur = ""
        fun flush() {
            if (cur.isNotEmpty()) {
                lines += cur
                cur = ""
            }
        }
        for (raw in words) {
            var word = raw
            while (word.length > cols) {
                flush()
                lines += word.take(cols)
                word = word.drop(cols)
                if (lines.size >= maxLines) return lines.take(maxLines)
            }
            val trial = if (cur.isEmpty()) word else "$cur $word"
            if (trial.length <= cols) {
                cur = trial
            } else {
                flush()
                cur = word
            }
            if (lines.size >= maxLines) return lines.take(maxLines)
        }
        flush()
        return lines.take(maxLines).ifEmpty { listOf(" ") }
    }

    fun clip(text: String, cols: Int): String {
        val t = text.replace("\r", " ").replace("\n", " ").trim()
        if (t.isEmpty()) return " "
        return if (t.length <= cols) t else t.take(cols)
    }

    fun escapePy(text: String): String =
        text.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", "")

    fun execTimeoutMs(bodyLineCount: Int): Long {
        if (bodyLineCount <= BODY_ROWS) return 8_000L
        val windows = (bodyLineCount - BODY_ROWS + 1).coerceAtLeast(1)
        return (4_000L + windows * SCROLL_MS).coerceAtMost(60_000L)
    }
}
