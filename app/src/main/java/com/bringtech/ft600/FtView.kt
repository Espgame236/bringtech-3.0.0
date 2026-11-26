package com.bringtech.ft600
import android.content.Context
import android.graphics.*
import android.view.View

class FtView(c: Context) : View(c) {
    var rpm = 0f; var speed = 0f; var load = 0f; var map = 0f; var tps = 0f
    var maxRpm = 5000f; var bat = 0f; var water = 0f; var air = 0f; var rec = false; var gx = 0f; var gz = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val OR = 0xFFFF9F1C.toInt()
    init { setBackgroundColor(Color.BLACK); setPadding(0, 90, 0, 0) }
    fun upd() = postInvalidate()

    private fun txt(cv: Canvas, s: String, x: Float, y: Float, sz: Float, col: Int = Color.WHITE, al: Paint.Align = Paint.Align.LEFT) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = col; p.textSize = sz; p.textAlign = al
        p.typeface = Typeface.DEFAULT_BOLD; cv.drawText(s, x, y, p)
    }
    private fun box(cv: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = col; cv.drawRect(l, t, r, b, p)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val y0 = paddingTop + 10f
        // LEDs de troca de marcha (acendem conforme o giro)
        val led = intArrayOf(0xFF2BE04A.toInt(), 0xFF2BE04A.toInt(), 0xFF2BE04A.toInt(), 0xFFFF4545.toInt(), 0xFFFF4545.toInt(),
            0xFFFF4545.toInt(), 0xFF2E9BFF.toInt(), 0xFF2E9BFF.toInt(), 0xFF2E9BFF.toInt(), 0xFF2E9BFF.toInt())
        for (i in 0..9) {
            p.style = Paint.Style.FILL; p.shader = null
            p.color = if (rpm / maxRpm * 10 > i) led[i] else 0xFF2A2F36.toInt()
            cv.drawCircle(w * (0.14f + i * 0.08f), y0 + 0.03f * w, 0.02f * w, p)
        }
        // RPM + barra
        txt(cv, rpm.toInt().toString(), 0.04f * w, y0 + 0.19f * w, 0.15f * w)
        txt(cv, maxRpm.toInt().toString(), 0.46f * w, y0 + 0.12f * w, 0.03f * w); txt(cv, "RPM", 0.46f * w, y0 + 0.15f * w, 0.03f * w)
        if (rec) {
            p.style = Paint.Style.FILL; p.shader = null; p.color = Color.RED; cv.drawCircle(0.78f * w, y0 + 0.125f * w, 0.012f * w, p)
            txt(cv, "REC", 0.80f * w, y0 + 0.135f * w, 0.03f * w, Color.RED)
        }
        val yb = y0 + 0.22f * w; val hb = 0.07f * w; val x0 = 0.04f * w; val x1 = 0.96f * w
        box(cv, x0, yb, x1, yb + hb, 0xFF1B1F24.toInt())
        val fx = x0 + (x1 - x0) * (rpm / maxRpm).coerceIn(0f, 1f)
        p.shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0xFFFFD21F.toInt(), 0xFFFF9F1C.toInt(), 0xFFFF3B1F.toInt()), null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL; cv.drawRect(x0, yb, fx, yb + hb, p); p.shader = null
        for (i in 1..(maxRpm / 1000).toInt()) txt(cv, i.toString(), x0 + (x1 - x0) * i * 1000f / maxRpm, yb + hb + 0.035f * w, 0.025f * w, Color.WHITE, Paint.Align.CENTER)
        // medidor de G (acelerometro do celular)
        val gcx = 0.2f * w; val gcy = y0 + 0.55f * w; val gr = 0.15f * w
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeWidth = 3f; p.color = 0xFF8A97A5.toInt()
        cv.drawCircle(gcx, gcy, gr, p); cv.drawCircle(gcx, gcy, gr / 2, p)
        cv.drawLine(gcx - gr, gcy, gcx + gr, gcy, p); cv.drawLine(gcx, gcy - gr, gcx, gcy + gr, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        cv.drawCircle(gcx + gx.coerceIn(-1f, 1f) * gr, gcy - gz.coerceIn(-1f, 1f) * gr, 0.018f * w, p)
        // centro: carga / direita: velocidade
        txt(cv, "Carga %", 0.5f * w, y0 + 0.41f * w, 0.03f * w, OR, Paint.Align.CENTER)
        txt(cv, load.toInt().toString(), 0.5f * w, y0 + 0.63f * w, 0.24f * w, Color.WHITE, Paint.Align.CENTER)
        txt(cv, "Veloc.", 0.83f * w, y0 + 0.41f * w, 0.03f * w, OR, Paint.Align.CENTER)
        txt(cv, speed.toInt().toString(), 0.83f * w, y0 + 0.55f * w, 0.14f * w, Color.WHITE, Paint.Align.CENTER)
        txt(cv, "km/h", 0.83f * w, y0 + 0.60f * w, 0.03f * w, Color.WHITE, Paint.Align.CENTER)
        // linha de baixo: MAP, Bat, TPS, Agua, Ar
        val y1 = y0 + 0.76f * w
        txt(cv, "MAP kPa", 0.04f * w, y1, 0.028f * w, OR); txt(cv, "%.0f".format(map), 0.04f * w, y1 + 0.07f * w, 0.065f * w)
        txt(cv, "Bat V", 0.25f * w, y1, 0.028f * w, OR); txt(cv, "%.1f".format(bat), 0.25f * w, y1 + 0.07f * w, 0.065f * w)
        txt(cv, "TPS", 0.45f * w, y1, 0.028f * w, OR)
        val tx0 = 0.45f * w; val tx1 = 0.75f * w
        box(cv, tx0, y1 + 0.02f * w, tx1, y1 + 0.065f * w, 0xFF1B1F24.toInt())
        box(cv, tx0, y1 + 0.02f * w, tx0 + (tx1 - tx0) * (tps / 100f).coerceIn(0f, 1f), y1 + 0.065f * w, 0xFF2BE04A.toInt())
        txt(cv, "0", tx0, y1 + 0.10f * w, 0.024f * w); txt(cv, "50", (tx0 + tx1) / 2, y1 + 0.10f * w, 0.024f * w, Color.WHITE, Paint.Align.CENTER)
        txt(cv, "100", tx1, y1 + 0.10f * w, 0.024f * w, Color.WHITE, Paint.Align.RIGHT)
        val bt = y1 - 0.02f * w; val bh = 0.20f * w
        for ((k, pair) in listOf("Agua" to water, "Ar" to air).withIndex()) {
            val bx = 0.80f * w + k * 0.10f * w
            txt(cv, pair.first, bx + 0.035f * w, bt - 0.01f * w, 0.026f * w, OR, Paint.Align.CENTER)
            box(cv, bx, bt, bx + 0.07f * w, bt + bh, 0xFF1B1F24.toInt())
            box(cv, bx, bt + bh * (1f - (pair.second / 120f).coerceIn(0f, 1f)), bx + 0.07f * w, bt + bh, if (pair.first == "Agua" && pair.second > 105f) Color.RED else OR)
            txt(cv, pair.second.toInt().toString(), bx + 0.035f * w, bt + bh + 0.04f * w, 0.03f * w, Color.WHITE, Paint.Align.CENTER)
        }
    }
}
