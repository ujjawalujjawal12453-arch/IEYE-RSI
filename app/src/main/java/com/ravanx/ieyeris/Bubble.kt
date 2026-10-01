package com.ravanx.ieyeris

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 🫧 BUBBLE — home screen pe tairta hua IEYE RIS
 *
 * Kya hai:
 *   • Ek chhota chamakta circle — har jagah dikhta hai
 *   • Ungli se kahin bhi kheench kar rakh do
 *   • Ek tap = sunna shuru (continuous)
 *   • Bolte waqt ring ghoomti hai + rang badalta hai
 *   • Lamba dabao = app khul jaye
 *
 * ⚠️ Iske liye "Doosri apps ke upar dikhao" permission chahiye.
 *    Android ise apne aap nahi deta — user ko settings me
 *    khud on karna padta hai (aur sahi hi hai).
 */
class Bubble : Service() {

    companion object {
        var live: Bubble? = null
        const val CH = "ieyeris_bubble"
        const val ID = 7002

        fun on() = live != null

        fun allowed(c: Context) =
            Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)

        fun askPerm(c: Context) {
            c.startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + c.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun start(c: Context) {
            if (!allowed(c)) { askPerm(c); return }
            val i = Intent(c, Bubble::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i)
            else c.startService(i)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, Bubble::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var ring: TextView? = null
    private var core: TextView? = null
    private var iris: CoreView? = null
    private var bar: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private val h = Handler(Looper.getMainLooper())

    private var pulse: ValueAnimator? = null

    // ═══════════════ shuru ═══════════════

    override fun onBind(i: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        live = this
        chan()
        startForeground(ID, notif("Tap karke boliye"))

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        build()
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun build() {
        /*
         * ═══ v3.1 — MASTER CIRCLE ═══
         *
         * User ne kaha: "mere screen ke samne ek button aana
         * chahie... ek master circle, aur Main jab usko bolo
         * tab vah sunna chahie"
         *
         * Pehle yahan sirf ek gulabi circle aur ⚡ emoji tha.
         * Ab wahi asli IRIS lagta hai jo app ke andar hai —
         * poori app ka ek hi chehra.
         *
         * Size 62 -> 78 kiya, taaki saaf dikhe aur ungli se
         * dabana aasan ho.
         */
        val size = dp(78)

        root = FrameLayout(this)

        // ── 👁 asli IRIS ──
        iris = CoreView(this)
        root!!.addView(iris, FrameLayout.LayoutParams(size, size))

        // purana code inhe chhoo sakta hai — rakhe hain
        ring = TextView(this)
        core = TextView(this)

        // ── neeche awaaz ka bar ──
        bar = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.parseColor("#00E6FF"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root!!.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = resources.displayMetrics.heightPixels / 3
        }

        touch()
        try { wm.addView(root, lp) } catch (e: Exception) { stopSelf() }
        breathe()
    }

    // ═══════════════ ungli se kheencho ═══════════════

    @SuppressLint("ClickableViewAccessibility")
    private fun touch() {
        var ix = 0; var iy = 0
        var tx = 0f; var ty = 0f
        var t0 = 0L
        var moved = false

        root?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp!!.x; iy = lp!!.y
                    tx = e.rawX; ty = e.rawY
                    t0 = System.currentTimeMillis()
                    moved = false
                    core?.animate()?.scaleX(0.88f)?.scaleY(0.88f)
                        ?.setDuration(90)?.start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp!!.x = ix + dx
                    lp!!.y = iy + dy
                    try { wm.updateViewLayout(root, lp) }
                    catch (ex: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    core?.animate()?.scaleX(1f)?.scaleY(1f)
                        ?.setDuration(120)?.start()
                    val held = System.currentTimeMillis() - t0
                    if (!moved) {
                        if (held > 550) openApp() else toggle()
                    } else snap()
                    true
                }
                else -> false
            }
        }
    }

    /** Kinare pe chipka do — beech me latka na rahe */
    private fun snap() {
        val w = resources.displayMetrics.widthPixels
        val target = if (lp!!.x + dp(31) > w / 2) w - dp(74) else dp(12)
        ValueAnimator.ofInt(lp!!.x, target).apply {
            duration = 220
            addUpdateListener {
                lp!!.x = it.animatedValue as Int
                try { wm.updateViewLayout(root, lp) }
                catch (e: Exception) {}
            }
            start()
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ═══════════════ animation ═══════════════

    /**
     * v3.1 — saans ab CoreView khud leta hai (uska apna
     * animator hai). Yahan alag animator chalane se do
     * animation aapas me ladte the aur battery bhi zyada
     * jaati thi.
     */
    private fun breathe() {
        pulse?.cancel(); pulse = null
        iris?.state = CoreView.S.IDLE
    }

    // ═══════════════ tap = VoiceActivity kholo ═══════════════
    //
    // ⚠️ YAHI ASLI FIX HAI. Pehle mic yahin Service me chalta tha —
    //    Android 11+ me wo "chalta" to hai par SUNTA KUCH NAHI.
    //    Isliye aap bolte the aur kuch nahi hota tha.
    //
    //    Ab tap karte hi ek transparent Activity khulti hai jisme
    //    mic 100% kaam karta hai. Dikhne me lagta hai bubble hi
    //    sun raha hai.

    private fun toggle() {
        flash()
        startActivity(Intent(this, VoiceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                      Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    /** Tap pe chhoti si chamak — pata chale ki dabaya gaya */
    private fun flash() {
        iris?.state = CoreView.S.LISTENING
        iris?.animate()?.scaleX(1.18f)?.scaleY(1.18f)
            ?.setDuration(110)?.withEndAction {
                iris?.animate()?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(150)?.start()
            }?.start()
    }

    // ═══════════════ notification ═══════════════

    private fun chan() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager).createNotificationChannel(
                NotificationChannel(CH, "IEYE RIS Bubble",
                    NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                })
        }
    }

    private fun notif(txt: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CH)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("IEYE RIS")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun note(txt: String) {
        try {
            (getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager).notify(ID, notif(txt))
        } catch (e: Exception) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onDestroy() {
        pulse?.cancel()
        try { root?.let { wm.removeView(it) } } catch (e: Exception) {}
        root = null
        live = null
        super.onDestroy()
    }
}
