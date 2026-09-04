package com.ravanx.ieyeris

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════
 *   👁  I R I S   C O R E   —  v2.0
 * ═══════════════════════════════════════════════════════
 *
 * Ek hi bada gol button jo poori app chalata hai.
 * Beech me asli IRIS (aankh) bani hai — dabate hi sunna
 * chalu ho jata hai.
 *
 * Spec section 8 aur 36 ka "circular AI core with 6 states".
 *
 * ⚠️ Ye poori tarah CODE se bani hai — koi image nahi.
 *    Wajah: har phone ka size alag hota hai. Image lagate
 *    to chhote phone pe dhundhli aur bade pe faili hui
 *    dikhti. Canvas pe banane se har jagah ekdum saaf.
 *
 * ⚠️ Har cheez ek hi `phase` se chalti hai (0 se 1 tak
 *    ghoomta rehta hai). Isse alag-alag animation aapas me
 *    sync rehti hain aur battery bhi kam khaati hai —
 *    6 animator ki jagah sirf 1.
 *
 * 6 HAALAT (spec ke mutabik):
 *   IDLE      — halka saans leta hua, neela
 *   LISTENING — aankh khuli, laharein bahar, cyan tez
 *   THINKING  — teen chhalle alag raftaar se ghoomte
 *   SPEAKING  — laharein andar-bahar, gulabi
 *   WORKING   — chaap ghoomti hui, banafshi
 *   ERROR     — laal, kaanpta hua
 */
class CoreView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null, def: Int = 0
) : View(ctx, attrs, def) {

    enum class S { IDLE, LISTENING, THINKING, SPEAKING, WORKING, ERROR }

    var state: S = S.IDLE
        set(v) {
            if (field == v) return
            field = v
            // haalat badalte hi ek chhota jhatka — user ko
            // saaf pata chale ki kuch hua hai
            pulse = 1f
            invalidate()
        }

    /** 0..1 — mic kitni tez awaaz sun raha hai */
    var level: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f) }

    private var phase = 0f          // 0..1, hamesha ghoomta
    private var pulse = 0f          // haalat badalne ka jhatka
    private var anim: ValueAnimator? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val tick = Path()

    // 🎨 RAVAN X ke rang
    private val cyan = Color.parseColor("#00E6FF")
    private val purple = Color.parseColor("#7C3AED")
    private val pink = Color.parseColor("#EC4899")
    private val red = Color.parseColor("#EF4444")
    private val deep = Color.parseColor("#070B14")

    /** Is haalat ka mukhya rang */
    private fun tint(): Int = when (state) {
        S.IDLE -> cyan
        S.LISTENING -> cyan
        S.THINKING -> purple
        S.SPEAKING -> pink
        S.WORKING -> purple
        S.ERROR -> red
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // ⚠️ Animator band karna ZAROORI hai. Warna view
        //    hatne ke baad bhi ghoomta rehta hai aur battery
        //    pita rehta hai (memory leak bhi).
        anim?.cancel()
        anim = null
    }

    private fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                if (pulse > 0f) pulse = max(0f, pulse - 0.035f)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 2f || h < 2f) return

        val cx = w / 2f
        val cy = h / 2f
        val R = min(w, h) / 2f - dp(6f)
        if (R < 10f) return

        val tw = 2.0 * Math.PI * phase          // 0..2π
        val col = tint()

        // saans — IDLE me dheere, LISTENING me awaaz ke saath
        val breath = when (state) {
            S.LISTENING -> 0.04f + level * 0.14f
            S.SPEAKING -> 0.05f + (sin(tw * 3).toFloat() + 1f) * 0.045f
            S.ERROR -> 0.03f + (sin(tw * 11).toFloat()) * 0.02f
            else -> 0.03f + (sin(tw * 2).toFloat() + 1f) * 0.018f
        } + pulse * 0.06f

        // ── 1. BAHARI CHAMAK ──
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, R * (1.32f + breath),
            intArrayOf(
                withA(col, (54 + pulse * 70).toInt()),
                withA(col, 16), Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, R * (1.32f + breath), p)
        p.shader = null

        // ── 2. LAHARIYAN — sunte/bolte waqt bahar jaati ──
        if (state == S.LISTENING || state == S.SPEAKING) {
            ring.style = Paint.Style.STROKE
            for (i in 0 until 3) {
                // har lahar alag waqt pe nikalti hai
                val t = ((phase * 2f + i / 3f) % 1f)
                val rr = R * (0.86f + t * 0.62f)
                val a = ((1f - t) * 110 *
                         (0.42f + level * 0.58f)).toInt()
                if (a <= 1) continue
                ring.color = withA(col, a)
                ring.strokeWidth = dp(2f) * (1f - t * 0.55f)
                c.drawCircle(cx, cy, rr, ring)
            }
        }

        // ── 3. GHOOMTE CHALLE ──
        // THINKING me teen, alag raftaar aur ulti disha —
        // isse "soch raha hai" wala ehsas aata hai
        val arcs = when (state) {
            S.THINKING -> 3
            S.WORKING -> 2
            else -> 1
        }
        for (i in 0 until arcs) {
            val rr = R * (1.0f - i * 0.11f)
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            val dir = if (i % 2 == 0) 1f else -1f
            val speed = when (state) {
                S.THINKING -> 300f + i * 130f
                S.WORKING -> 260f
                S.ERROR -> 90f
                else -> 42f
            }
            val sweep = when (state) {
                S.THINKING -> 74f + i * 16f
                S.WORKING -> 120f
                S.IDLE -> 300f
                else -> 250f
            }
            ring.style = Paint.Style.STROKE
            ring.strokeWidth = dp(if (i == 0) 2.6f else 1.7f)
            ring.shader = LinearGradient(
                cx - rr, cy - rr, cx + rr, cy + rr,
                intArrayOf(withA(col, 235), withA(col, 55)),
                null, Shader.TileMode.CLAMP
            )
            c.drawArc(rect, (phase * speed * dir) + i * 47f,
                      sweep, false, ring)
            ring.shader = null
        }

        // ── 4. TECH DAANTE (chhoti lakeerein) ──
        // Ye "machine" wala ehsas deti hain
        ring.color = withA(col, 46)
        ring.strokeWidth = dp(1.4f)
        tick.reset()
        val n = 44
        for (i in 0 until n) {
            val a = (i.toFloat() / n) * 2f * Math.PI +
                    tw * 0.14
            val long = i % 4 == 0
            val r1 = R * (if (long) 1.10f else 1.05f)
            val r2 = R * 1.14f
            tick.moveTo(cx + (cos(a) * r1).toFloat(),
                        cy + (sin(a) * r1).toFloat())
            tick.lineTo(cx + (cos(a) * r2).toFloat(),
                        cy + (sin(a) * r2).toFloat())
        }
        c.drawPath(tick, ring)

        // ── 5. AANKH KA GOLA (safed hissa) ──
        val eyeR = R * (0.70f + breath * 0.30f)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy - eyeR * 0.16f, eyeR,
            intArrayOf(
                Color.parseColor("#16233F"),
                Color.parseColor("#0B1020"), deep
            ),
            floatArrayOf(0f, 0.66f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, eyeR, p)
        p.shader = null

        // ── 6. IRIS — asli aankh ──
        //    Sote waqt patli ho jati hai (aankh bandh jaisi)
        val irisR = eyeR * when (state) {
            S.LISTENING -> 0.70f + level * 0.10f
            S.THINKING -> 0.60f
            S.SPEAKING -> 0.66f
            S.ERROR -> 0.55f
            else -> 0.64f
        }
        p.shader = RadialGradient(
            cx, cy, irisR,
            intArrayOf(
                withA(col, 255), withA(col, 190),
                withA(purple, 150), withA(deep, 235)
            ),
            floatArrayOf(0f, 0.42f, 0.76f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, irisR, p)
        p.shader = null

        // iris ki reshe (asli aankh jaisi lakeerein)
        ring.strokeWidth = dp(1.1f)
        for (i in 0 until 26) {
            val a = (i / 26f) * 2f * Math.PI + tw * 0.4
            val f = 0.40f + ((i * 37) % 23) / 23f * 0.34f
            ring.color = withA(
                if (i % 3 == 0) cyan else col,
                (58 + ((i * 53) % 60)))
            c.drawLine(
                cx + (cos(a) * irisR * 0.40f).toFloat(),
                cy + (sin(a) * irisR * 0.40f).toFloat(),
                cx + (cos(a) * irisR * (0.40f + f)).toFloat(),
                cy + (sin(a) * irisR * (0.40f + f)).toFloat(),
                ring)
        }

        // iris ka kinara
        ring.color = withA(col, 210)
        ring.strokeWidth = dp(1.7f)
        c.drawCircle(cx, cy, irisR, ring)

        // ── 7. PUTLI (kaala beech) ──
        val pupR = irisR * when (state) {
            S.LISTENING -> 0.34f - level * 0.09f   // tez awaaz -> sikudti
            S.THINKING -> 0.30f
            S.ERROR -> 0.46f
            else -> 0.37f
        }
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#04070E")
        c.drawCircle(cx, cy, pupR, p)

        // putli ke andar chamak — "zinda" lagti hai
        p.shader = RadialGradient(
            cx, cy, pupR,
            intArrayOf(withA(pink, 130), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, pupR * 0.72f, p)
        p.shader = null

        // ── 8. ROSHNI KA DHABBA (upar-baayen) ──
        p.color = withA(Color.WHITE, 62)
        c.drawCircle(cx - eyeR * 0.30f, cy - eyeR * 0.34f,
                     eyeR * 0.13f, p)
        p.color = withA(Color.WHITE, 30)
        c.drawCircle(cx + eyeR * 0.22f, cy + eyeR * 0.28f,
                     eyeR * 0.07f, p)

        // ── 9. GHOOMTA NUQTA — "zinda hai" ka saboot ──
        if (state != S.IDLE) {
            val a = tw * 1.7
            val rr = R * 1.19f
            p.color = withA(col, 225)
            c.drawCircle(cx + (cos(a) * rr).toFloat(),
                         cy + (sin(a) * rr).toFloat(), dp(2.6f), p)
        }
    }

    // ── chhote helper ──
    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Rang me alpha lagao (0-255) */
    private fun withA(c: Int, a: Int) = Color.argb(
        a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
}
