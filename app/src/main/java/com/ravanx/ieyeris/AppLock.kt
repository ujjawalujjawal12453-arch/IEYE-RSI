package com.ravanx.ieyeris

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 📱 APP LOCK — v4.7
 *
 * User ne kaha:
 *   "Jo bolun wo app lock ho jana chahiye... jo-jo app ko
 *    chhodne ka bolu wo bilkul lock nahi honge, jo nahi bole
 *    wo lock honge"
 *
 * Kaam:
 *   "PhonePe ko chhod ke baaki sab lock kar do"  -> sab lock, PhonePe khula
 *   "WhatsApp aur YouTube app lock karo"         -> sirf ye dono lock
 *   "Sab app khol do"                            -> sab khul jaye
 *
 * ═══ KAISE CHALTA HAI ═══
 *
 * 1. Voice command -> Brain -> AppLock (ye file)
 * 2. Jab koi lock wali app screen pe aati hai, Eyes (accessibility
 *    service) bata deta hai -> hum turant poora screen dhak kar
 *    ek PIN ka darwaza dikhate hain.
 * 3. Sahi PIN (owner password, default 2244) daalne par wo app
 *    us waqt ke liye khul jati hai.
 *
 * ═══ ⚠️ IMAANDARI KI BAAT ═══
 *
 * Ye AAM app-lock hai — jaise Play Store ke har "app lock" app
 * karte hain. Kaam karta hai, par:
 *
 *   • Ye PIN waala lock hai, taala-todna namumkin nahi — jo banda
 *     app ko Settings se band kar de ya phone root kare, wo nikal
 *     jayega. (Har app-lock ka yahi haal hai.)
 *
 *   • WHATSAPP KE ANDAR EK KISI CHAT KO LOCK KARNA namumkin hai.
 *     WhatsApp doosri app ko apni chat dikhata hi nahi (ye sahi
 *     bhi hai — privacy). Isliye "WhatsApp pe is number ko chhod
 *     ke baaki chat lock" NAHI ho sakta. Jo ho sakta hai: poora
 *     WhatsApp lock karna. Ye main jhoot nahi bolunga.
 */
object AppLock {

    private const val K = "applock_pkgs"

    private var overlay: View? = null
    @Volatile private var currentPkg = ""   // abhi saamne kaunsa app
    @Volatile private var allowed = ""      // PIN se khola gaya app

    // ──────────────────────────────────────────────
    //  STATE (phone me save)
    // ──────────────────────────────────────────────
    fun locked(ctx: Context): Set<String> =
        Keys(ctx).get(K, "").split(",")
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun count(ctx: Context) = locked(ctx).size

    private fun save(ctx: Context, s: Set<String>) =
        Keys(ctx).set(K, s.joinToString(","))

    fun add(ctx: Context, pkgs: Set<String>) {
        val s = locked(ctx).toMutableSet(); s.addAll(pkgs); save(ctx, s)
    }

    fun remove(ctx: Context, pkgs: Set<String>) {
        val s = locked(ctx).toMutableSet(); s.removeAll(pkgs); save(ctx, s)
    }

    fun clear(ctx: Context) = save(ctx, emptySet())

    fun isLocked(ctx: Context, pkg: String) = pkg in locked(ctx)

    /** Naam -> package (AppRegistry ka fuzzy match) */
    private fun resolve(ctx: Context, names: List<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (n in names) AppRegistry.find(ctx, n)?.let { out.add(it.pkg) }
        return out
    }

    /** Ye hamesha khule rahenge — warna phone hi band ho jaye */
    private fun alwaysSafe(ctx: Context): Set<String> {
        val safe = mutableSetOf(ctx.packageName)
        try {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(i, 0)
                ?.activityInfo?.packageName?.let { safe.add(it) }
        } catch (e: Exception) {}
        return safe
    }

    private fun isSystemApp(ctx: Context, pkg: String): Boolean = try {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) { false }

    // ──────────────────────────────────────────────
    //  ASLI KAAM — arg = "op|names"
    //    lock_all|phonepe,chrome   sab lock, ye chhod
    //    lock|whatsapp,youtube     sirf ye lock
    //    unlock|whatsapp           khol do
    //    unlock_all|               sab khol do
    // ──────────────────────────────────────────────
    fun apply(ctx: Context, arg: String): String {
        val op = arg.substringBefore("|")
        val names = arg.substringAfter("|", "")
            .split(",", " ").map { it.trim() }
            .filter { it.isNotEmpty() }

        // ⚠️ "dusri app ke upar dikhe" ki izazat na ho to
        //    lock dikhega hi nahi — pehle wo le lo
        if (op.startsWith("lock")) {
            if (!Settings.canDrawOverlays(ctx)) {
                try {
                    ctx.startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {}
                return "⚠️ Ek izazat chahiye — \"dusri app ke upar " +
                       "dikhna\". Jo screen khuli hai usme IEYE RIS " +
                       "ko ON kar do, phir dobara boliye."
            }
        }

        return when (op) {
            "lock_all" -> {
                val except = resolve(ctx, names) + alwaysSafe(ctx)
                val all = AppRegistry.all(ctx)
                    .filter { !isSystemApp(ctx, it.pkg) && it.pkg !in except }
                    .map { it.pkg }.toSet()
                add(ctx, all)
                "${all.size} app lock ho gayi. " +
                    (if (names.isEmpty()) ""
                     else "${names.size} app chhod di hain.")
            }
            "lock" -> {
                val pkgs = resolve(ctx, names)
                add(ctx, pkgs)
                "${pkgs.size} app lock ho gayi."
            }
            "unlock" -> {
                val pkgs = resolve(ctx, names)
                remove(ctx, pkgs)
                "${pkgs.size} app khul gayi."
            }
            "unlock_all" -> { clear(ctx); "Sab app khul gaye." }
            else -> "Samajh nahi aaya sir."
        }
    }

    // ──────────────────────────────────────────────
    //  🧠 AWAZ SE PEHCHAAN — Brain yahi se poochta hai
    // ──────────────────────────────────────────────
    private val STOP = setOf(
        "ko", "karo", "kar", "do", "de", "dena", "dijiye", "karke",
        "karna", "aur", "or", "and", "app", "apps", "sab", "saare",
        "sare", "sara", "saara", "poora", "poori", "baki", "baaki",
        "mera", "mere", "mujhe", "mujhko", "ye", "yah", "wo", "woh",
        "jo", "ji", "to", "toh", "ke", "ki", "ka", "par", "pe", "bhi",
        "the", "hai", "hain", "na", "please", "plz", "chhod", "lock",
        "unlock", "khol", "hata", "hatao"
    )

    private fun hasWord(t: String, w: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(w) + "($|\\s)").containsMatchIn(t)

    /** trigger se pehle wale app ke naam nikalo */
    private fun namesBefore(t: String, triggers: List<String>): String {
        var idx = t.length
        for (tr in triggers) {
            val m = Regex("(^|\\s)" + Regex.escape(tr))
                .find(t)
            m?.let { if (it.range.first < idx) idx = it.range.first }
        }
        if (idx >= t.length) return ""
        val before = t.substring(0, idx)
        val out = LinkedHashSet<String>()
        for (tok in before.split(Regex("[\\s,]+"))) {
            val w = tok.trim()
            if (w.isEmpty()) continue
            if (w in STOP) continue
            out.add(w)
        }
        return out.joinToString(",")
    }

    /** Boli gayi baat -> arg string. Samajh na aaye to null. */
    fun parse(raw: String): String? {
        val t = " " + raw.lowercase().trim() + " "

        // ⚠️ Screen lock wala hukum mat chheeno — wo alag hai
        if (Regex("phone\\s+lock|screen\\s+lock|mobile\\s+lock|" +
                  "phone\\s+ko\\s+lock|screen\\s+ko\\s+lock")
                .containsMatchIn(t)) return null

        val unlockWord = hasWord(t, "unlock") ||
            Regex("(khol|khole|kholo|khol do|khol de|lock hata)").containsMatchIn(t)
        val lockWord = hasWord(t, "lock")
        if (!unlockWord && !lockWord) return null

        // 1) SAB KHOLO
        if (unlockWord && Regex("(\\s|^)(sab|saare|sare|poora|saara)").containsMatchIn(t))
            return "unlock_all|"

        // 2) ek-do app kholo
        if (unlockWord) {
            val n = namesBefore(t,
                listOf("unlock", "khol", "khole", "kholo", "khol do",
                       "khol de", "lock hata"))
            if (n.isNotEmpty()) return "unlock|$n"
        }

        // 3) "X ko chhod ke baaki sab lock"
        if (lockWord && Regex("(chhod|except|alawa|ilaawa|ilawa)").containsMatchIn(t)) {
            val n = namesBefore(t, listOf("chhod", "except", "alawa",
                "ilaawa", "ilawa"))
            return "lock_all|$n"
        }

        // 4) SAB LOCK (bina exception)
        if (lockWord && Regex("(\\s|^)(sab|saare|sare|poora|saara|saari)")
                .containsMatchIn(t))
            return "lock_all|"

        // 5) ek-do app lock
        if (lockWord) {
            val n = namesBefore(t, listOf("lock"))
            if (n.isNotEmpty()) return "lock|$n"
        }

        return null
    }

    /** Brain.local() yahi se poochta hai */
    fun detect(raw: String): Brain.Cmd? {
        val arg = parse(raw) ?: return null
        val say = when (arg.substringBefore("|")) {
            "unlock_all" -> "Sab app khul gayi."
            "unlock"     -> "Khol di."
            "lock_all"   -> "Lock kar diya."
            else         -> "Lock kar diya."
        }
        return Brain.Cmd("applock", arg, say)
    }

    // ──────────────────────────────────────────────
    //  👁️ EYES se window badalne ki khabar aati hai
    // ──────────────────────────────────────────────
    fun onWindow(ctx: Context, pkg: String) {
        if (pkg.isBlank() || pkg == ctx.packageName) return
        if (pkg != currentPkg) { currentPkg = pkg; allowed = "" }
        if (pkg == allowed) return
        if (!isLocked(ctx, pkg)) { hide(ctx); return }
        show(ctx, pkg)
    }

    // ──────────────────────────────────────────────
    //  🔒 PIN WALA DARWAZA (poora screen dhak deta hai)
    // ──────────────────────────────────────────────
    private fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg.substringAfterLast('.') }

    fun show(ctx: Context, pkg: String) {
        if (overlay != null) return
        val app = ctx.applicationContext
        val d = (14 * app.resources.displayMetrics.density).toInt()

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF20A0F1A.toInt())
            setPadding(d * 2, d * 2, d * 2, d * 2)
        }

        root.addView(TextView(app).apply {
            text = "🔒 LOCKED"
            textSize = 26f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
        })
        root.addView(TextView(app).apply {
            text = appLabel(app, pkg) + " lock hai.\n" +
                   "Owner PIN daal kar kholiye."
            textSize = 15f; setTextColor(0xFF8FA3BD.toInt())
            gravity = Gravity.CENTER; setPadding(0, d, 0, d * 2)
            setLineSpacing((d * 0.4f).toFloat(), 1f)
        })

        val pin = EditText(app).apply {
            hint = "PIN"
            textSize = 20f; gravity = Gravity.CENTER
            letterSpacing = 0.4f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF4A5A70.toInt())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(d, d, d, d)
            background = GradientDrawable().apply {
                cornerRadius = (d * 1.2f)
                setColor(Color.parseColor("#0B1220"))
                setStroke(1, Color.parseColor("#253349"))
            }
        }
        root.addView(pin, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        fun bttn(txt: String, bg: String, fg: Int, act: () -> Unit) {
            root.addView(TextView(app).apply {
                text = txt; textSize = 15f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setTextColor(fg); setPadding(0, d, 0, d)
                letterSpacing = 0.1f
                background = GradientDrawable().apply {
                    cornerRadius = (d * 1.2f)
                    setColor(Color.parseColor(bg))
                }
                setOnClickListener { act() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = d
            })
        }

        bttn("🔓  KHOLO", "#39FF88", 0xFF07090F.toInt()) {
            if (pin.text.toString().trim() == Owner.pass(app)) {
                allowed = currentPkg
                hide(app)
                android.widget.Toast.makeText(app,
                    "🔓 Khul gaya", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                pin.setText("")
                pin.hint = "❌ Galat PIN — dobara"
                pin.setHintTextColor(0xFFFF6B6B.toInt())
            }
        }
        bttn("🏠  GHAR JAAO", "#0B1220", 0xFF8FA3BD.toInt()) {
            hide(app)
            Eyes.live?.home()
        }

        val wm = app.getSystemService(Context.WINDOW_SERVICE)
            as WindowManager
        val type = if (android.os.Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.CENTER
        try { wm.addView(root, lp); overlay = root } catch (e: Exception) {}
    }

    fun hide(ctx: Context) {
        val v = overlay ?: return
        overlay = null
        try {
            val wm = ctx.applicationContext
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {}
    }

    fun isOpen() = overlay != null
}
