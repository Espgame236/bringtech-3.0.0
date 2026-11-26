package com.bringtech.ft600
import android.content.Context
import android.graphics.*
import android.view.View

class Row(val label: String, val text: String, val frac: Float, val alert: Boolean = false)

class FtList(c: Context, val hint: String = "") : View(c) {
    var rows: List<Row> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setBackgroundColor(Color.BLACK); setPadding(0, 90, 0, 0) }
    fun upd(r: List<Row>) { rows = r; postInvalidate() }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val top = paddingTop + 10f
        val h = (height - top - 50f) / rows.size.coerceAtLeast(1)
        p.style = Paint.Style.FILL; p.typeface = Typeface.DEFAULT_BOLD
        rows.forEachIndexed { i, r ->
            val y = top + i * h; val col = if (r.alert) Color.RED else 0xFFFF9F1C.toInt()
            p.textAlign = Paint.Align.LEFT; p.color = 0xFFFF9F1C.toInt(); p.textSize = 0.035f * w
            cv.drawText(r.label, 0.04f * w, y + 0.045f * w, p)
            p.textAlign = Paint.Align.RIGHT; p.color = if (r.alert) Color.RED else Color.WHITE; p.textSize = 0.09f * w
            cv.drawText(r.text, 0.96f * w, y + 0.10f * w, p)
            p.color = 0xFF1B1F24.toInt(); cv.drawRect(0.04f * w, y + 0.12f * w, 0.96f * w, y + 0.15f * w, p)
            p.color = col; cv.drawRect(0.04f * w, y + 0.12f * w, 0.04f * w + 0.92f * w * r.frac.coerceIn(0f, 1f), y + 0.15f * w, p)
        }
        if (hint.isNotEmpty()) { p.textAlign = Paint.Align.CENTER; p.color = 0xFF8A97A5.toInt(); p.textSize = 0.03f * w; cv.drawText(hint, w / 2, height - 14f, p) }
    }
}
