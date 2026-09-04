package com.ravanx.ieyeris

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 🚀 SETUP WIZARD — pehli baar khulne par
 * ══════════════════════════════════════
 *
 * Spec section 2.
 *
 * Pehle app seedha chat screen kholta tha aur permission ka
 * popup thok deta tha — user ko kuch samajh nahi aata tha ki
 * kya ho raha hai, kyun maanga ja raha hai.
 *
 * Ab ek saaf rasta:
 *
 *   WELCOME
 *     -> INITIALIZING (asli check, nakli nahi)
 *     -> PERMISSIONS (kyun chahiye, saaf likha)
 *     -> APP DISCOVERY (asli scan)
 *     -> READY
 *
 * ⚠️ "INITIALIZING" wale check ASLI hain. Har line tabhi OK
 *    hoti hai jab wo cheez sach me chal rahi ho. Nakli
 *    progress bar dikhana user ko dhokha dena hai.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var keys: Keys
    private val h = Handler(Looper.getMainLooper())
    private var step = 0

    private val PERMS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA
    )

    companion object {
        /** Setup ho chuka hai? */
        fun done(c: android.content.Context) =
            Keys(c).flag("setup_done", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keys = Keys(this)

        val sc = ScrollView(this)
        sc.setBackgroundColor(0xFF070B14.toInt())
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(28), dp(56), dp(28), dp(40))
        sc.addView(root)
        setContentView(sc)

        welcome()
    }

    // ═══════════════════════════════════════════
    //   1. WELCOME
    // ═══════════════════════════════════════════

    private fun welcome() {
        root.removeAllViews()
        step = 1

        // aankh wala orb
        val orb = TextView(this)
        orb.text = "👁"
        orb.textSize = 46f
        orb.gravity = Gravity.CENTER
        orb.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF7C3AED.toInt(), 0xFF00E6FF.toInt())
        ).apply { shape = GradientDrawable.OVAL }
        add(orb, dp(104), dp(104), dp(10))
        orb.alpha = 0f
        orb.scaleX = 0.5f; orb.scaleY = 0.5f
        orb.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(560).start()
        pulse(orb)

        val t = title("WELCOME TO")
        t.textSize = 13f
        t.setTextColor(0xFF94A3B8.toInt())
        t.alpha = 0f
        t.animate().alpha(1f).setStartDelay(300).setDuration(400).start()

        val n = title("IEYE RIS")
        n.textSize = 30f
        n.setLetterSpacing(0.16f)
        n.alpha = 0f
        n.translationY = dp(14).toFloat()
        n.animate().alpha(1f).translationY(0f)
            .setStartDelay(420).setDuration(460).start()

        val sub = body("INTELLIGENT EYES  •  INTELLIGENT RESPONSE\n" +
                       "INTELLIGENT SYSTEM")
        sub.textSize = 10.5f
        sub.setTextColor(0xFF00E6FF.toInt())
        sub.setLetterSpacing(0.08f)
        sub.alpha = 0f
        sub.animate().alpha(1f).setStartDelay(680).setDuration(400).start()

        space(dp(26))
        val p = body(
            "Main aapka apna AI assistant hoon.\n\n" +
            "Bolkar phone chala sakte ho — app kholna, message " +
            "bhejna, screen padhna, sab.\n\n" +
            "Shuru karne se pehle 4 chhote kadam hain.")
        p.alpha = 0f
        p.animate().alpha(1f).setStartDelay(880).setDuration(400).start()

        space(dp(30))
        val b = btn("SHURU KARO  →") { initializing() }
        b.alpha = 0f
        b.animate().alpha(1f).setStartDelay(1050).setDuration(400).start()

        space(dp(14))
        val skip = link("Baad me karunga") { finishSetup() }
        skip.alpha = 0f
        skip.animate().alpha(1f).setStartDelay(1200).setDuration(400)
            .start()
    }

    // ═══════════════════════════════════════════
    //   2. INITIALIZING — asli check
    // ═══════════════════════════════════════════

    private fun initializing() {
        root.removeAllViews()
        step = 2
        title("INITIALIZING")
        space(dp(6))
        val sub = body("System check ho raha hai…")
        space(dp(26))

        val lines = LinearLayout(this)
        lines.orientation = LinearLayout.VERTICAL
        root.addView(lines, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))

        // ⚠️ Har check ASLI hai — nakli "OK" nahi likhte
        val checks: List<Pair<String, () -> Pair<Boolean, String>>> =
            listOf(
                "VOICE SYSTEM" to {
                    val ok = android.speech.SpeechRecognizer
                        .isRecognitionAvailable(this)
                    ok to (if (ok) "OK" else "NAHI MILA")
                },
                "AI SYSTEM" to {
                    val k = keys.groq()
                    (k.isNotBlank()) to
                        (if (k.isNotBlank()) "OK" else "KEY NAHI")
                },
                "NETWORK" to {
                    val ok = online()
                    ok to (if (ok) "OK" else "OFFLINE")
                },
                "APP DISCOVERY" to {
                    val n = try { AppRegistry.scan(this, true).size }
                            catch (e: Exception) { 0 }
                    (n > 0) to (if (n > 0) "$n APPS" else "FAIL")
                },
                "STORAGE" to {
                    val ok = try {
                        filesDir.canWrite()
                    } catch (e: Exception) { false }
                    ok to (if (ok) "OK" else "FAIL")
                },
                "PERMISSIONS" to {
                    val g = PERMS.count {
                        ContextCompat.checkSelfPermission(this, it) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                    true to "$g/${PERMS.size} READY"
                }
            )

        var i = 0
        fun next() {
            if (i >= checks.size) {
                h.postDelayed({
                    sub.text = "SYSTEM READY"
                    sub.setTextColor(0xFF22C55E.toInt())
                    space(dp(26))
                    btn("AAGE BADHO  →") { permissions() }
                }, 320)
                return
            }
            val (nm, fn) = checks[i]
            val row = TextView(this)
            row.textSize = 12.5f
            row.typeface = android.graphics.Typeface.MONOSPACE
            row.setTextColor(0xFF64748B.toInt())
            row.setPadding(0, dp(7), 0, dp(7))
            val dots = ".".repeat(maxOf(2, 22 - nm.length))
            row.text = "$nm $dots …"
            lines.addView(row)

            // asli check background me
            Thread {
                val (ok, msg) = try { fn() }
                    catch (e: Exception) { false to "FAIL" }
                h.post {
                    row.text = "$nm $dots $msg"
                    row.setTextColor(
                        if (ok) 0xFF22C55E.toInt()
                        else 0xFFF59E0B.toInt())
                    i++
                    h.postDelayed({ next() }, 220)
                }
            }.start()
        }
        h.postDelayed({ next() }, 350)
    }

    // ═══════════════════════════════════════════
    //   3. PERMISSIONS
    // ═══════════════════════════════════════════

    private fun permissions() {
        root.removeAllViews()
        step = 3
        title("PERMISSIONS")
        space(dp(6))
        body("Har permission kyun chahiye — saaf likha hai. " +
             "Jo na dena ho, mat dijiye. Baaki sab phir bhi chalega.")
        space(dp(20))

        card("🎤", "MICROPHONE",
             "Aapki awaaz sunne ke liye. Iske bina bolkar kuch " +
             "nahi ho payega.",
             ContextCompat.checkSelfPermission(this,
                 Manifest.permission.RECORD_AUDIO) ==
                 PackageManager.PERMISSION_GRANTED)

        card("📞", "PHONE + CONTACTS",
             "\"Papa ko call lagao\" jaisa kaam karne ke liye.",
             ContextCompat.checkSelfPermission(this,
                 Manifest.permission.CALL_PHONE) ==
                 PackageManager.PERMISSION_GRANTED)

        card("👁", "ACCESSIBILITY  (sabse zaroori)",
             "Screen padhne aur button dabane ke liye. Iske bina " +
             "main sirf app khol sakta hoon — andar kuch nahi kar " +
             "sakta. Aapka data kahin nahi jaata.",
             Eyes.on())

        space(dp(20))
        btn("PERMISSION DO") {
            val need = PERMS.filter {
                ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
            }.toMutableList()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (need.isNotEmpty())
                ActivityCompat.requestPermissions(
                    this, need.toTypedArray(), 77)
            else appList()
        }
        space(dp(10))
        if (!Eyes.on()) {
            btn2("👁  ACCESSIBILITY ON KARO") {
                Eyes.openSettings(this)
            }
            space(dp(10))
        }
        link("Aage badho  →") { appList() }
    }

    override fun onRequestPermissionsResult(
        rc: Int, p: Array<out String>, r: IntArray
    ) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == 77) appList()
    }

    override fun onResume() {
        super.onResume()
        // Accessibility on karke wapas aaya? — page refresh
        if (step == 3) permissions()
    }

    // ═══════════════════════════════════════════
    //   4. APP DISCOVERY
    // ═══════════════════════════════════════════

    private fun appList() {
        root.removeAllViews()
        step = 4
        title("APP DISCOVERY")
        space(dp(6))
        val sub = body("Aapke phone ke app dhoondh raha hoon…")
        space(dp(20))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        root.addView(box, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))

        Thread {
            val apps = try { AppRegistry.scan(this, true) }
                       catch (e: Exception) { emptyList() }
            h.post {
                sub.text = "${apps.size} app mile — main inhe " +
                           "bolkar khol sakta hoon"
                sub.setTextColor(0xFF22C55E.toInt())
                apps.take(9).forEach { a ->
                    val r = TextView(this)
                    r.text = "✓  ${a.name}"
                    r.textSize = 13.5f
                    r.setTextColor(0xFFCBD5E1.toInt())
                    r.setPadding(dp(4), dp(6), 0, dp(6))
                    box.addView(r)
                }
                if (apps.size > 9) {
                    val r = TextView(this)
                    r.text = "   …aur ${apps.size - 9}"
                    r.textSize = 12.5f
                    r.setTextColor(0xFF64748B.toInt())
                    r.setPadding(dp(4), dp(6), 0, dp(6))
                    box.addView(r)
                }
                space(dp(24))
                btn("BAS, HO GAYA  →") { ready() }
            }
        }.start()
    }

    // ═══════════════════════════════════════════
    //   5. READY
    // ═══════════════════════════════════════════

    private fun ready() {
        root.removeAllViews()
        step = 5

        val tick = TextView(this)
        tick.text = "✓"
        tick.textSize = 44f
        tick.gravity = Gravity.CENTER
        tick.setTextColor(Color.WHITE)
        tick.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF22C55E.toInt())
        }
        add(tick, dp(88), dp(88), dp(14))
        tick.scaleX = 0.3f; tick.scaleY = 0.3f; tick.alpha = 0f
        tick.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(460).start()

        val t = title("SYSTEM READY")
        t.setTextColor(0xFF22C55E.toInt())
        space(dp(8))
        body("IEYE RIS taiyaar hai.\n\nBolkar dekhiye —")
        space(dp(14))

        listOf("\"IEYE RIS, YouTube kholo\"",
               "\"WhatsApp me Papa ko hi bhej do\"",
               "\"Torch on karo aur battery batao\"",
               "\"IEYE RIS status\"").forEach {
            val e = TextView(this)
            e.text = it
            e.textSize = 13f
            e.setTextColor(0xFF00E6FF.toInt())
            e.setPadding(dp(4), dp(6), 0, dp(6))
            root.addView(e)
        }

        space(dp(28))
        btn("CHALO SHURU KAREIN") { finishSetup() }
    }

    private fun finishSetup() {
        keys.setFlag("setup_done", true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ═══════════════════════════════════════════
    //   chhote helper
    // ═══════════════════════════════════════════

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun online(): Boolean = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork) != null
    } catch (e: Exception) { false }

    private fun add(v: View, w: Int, hh: Int, mb: Int) {
        val lp = LinearLayout.LayoutParams(w, hh)
        lp.bottomMargin = mb
        root.addView(v, lp)
    }

    private fun space(px: Int) {
        val v = View(this)
        root.addView(v, LinearLayout.LayoutParams(1, px))
    }

    private fun title(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 21f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(0xFFF8FAFC.toInt())
        t.gravity = Gravity.CENTER
        t.setLetterSpacing(0.1f)
        root.addView(t, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return t
    }

    private fun body(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 13.5f
        t.setTextColor(0xFF94A3B8.toInt())
        t.gravity = Gravity.CENTER
        t.setLineSpacing(dp(4).toFloat(), 1f)
        root.addView(t, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return t
    }

    private fun card(icon: String, head: String, why: String,
                     ok: Boolean) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.setPadding(dp(14), dp(13), dp(14), dp(13))
        c.background = GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(0xFF111827.toInt())
            setStroke(dp(1),
                if (ok) 0xFF22C55E.toInt() else 0xFF1E293B.toInt())
        }
        val ic = TextView(this)
        ic.text = icon
        ic.textSize = 20f
        c.addView(ic, LinearLayout.LayoutParams(dp(34),
            ViewGroup.LayoutParams.WRAP_CONTENT))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val hd = TextView(this)
        hd.text = if (ok) "$head   ✓" else head
        hd.textSize = 13f
        hd.setTypeface(null, android.graphics.Typeface.BOLD)
        hd.setTextColor(
            if (ok) 0xFF22C55E.toInt() else 0xFFF8FAFC.toInt())
        col.addView(hd)
        val wy = TextView(this)
        wy.text = why
        wy.textSize = 12f
        wy.setTextColor(0xFF64748B.toInt())
        wy.setPadding(0, dp(4), 0, 0)
        wy.setLineSpacing(dp(3).toFloat(), 1f)
        col.addView(wy)
        c.addView(col, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        root.addView(c, lp)
    }

    private fun btn(s: String, f: () -> Unit): TextView {
        val b = TextView(this)
        b.text = s
        b.textSize = 14.5f
        b.setTypeface(null, android.graphics.Typeface.BOLD)
        b.setTextColor(Color.WHITE)
        b.gravity = Gravity.CENTER
        b.setLetterSpacing(0.05f)
        b.setPadding(0, dp(16), 0, dp(16))
        b.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF7C3AED.toInt(), 0xFF8B5CF6.toInt())
        ).apply { cornerRadius = dp(13).toFloat() }
        b.isClickable = true
        b.setOnClickListener { f() }
        root.addView(b, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return b
    }

    private fun btn2(s: String, f: () -> Unit): TextView {
        val b = TextView(this)
        b.text = s
        b.textSize = 13.5f
        b.setTypeface(null, android.graphics.Typeface.BOLD)
        b.setTextColor(0xFF00E6FF.toInt())
        b.gravity = Gravity.CENTER
        b.setPadding(0, dp(15), 0, dp(15))
        b.background = GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(0xFF0B1020.toInt())
            setStroke(dp(1), 0xFF00E6FF.toInt())
        }
        b.isClickable = true
        b.setOnClickListener { f() }
        root.addView(b, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return b
    }

    private fun link(s: String, f: () -> Unit): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 13f
        t.setTextColor(0xFF64748B.toInt())
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(10), 0, dp(10))
        t.isClickable = true
        t.setOnClickListener { f() }
        root.addView(t, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        return t
    }

    private fun pulse(v: View) {
        val a = ValueAnimator.ofFloat(1f, 1.07f, 1f)
        a.duration = 2200
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener {
            val s = it.animatedValue as Float
            v.scaleX = s; v.scaleY = s
        }
        a.start()
    }
}
