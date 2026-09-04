package com.ravanx.ieyeris

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🔍 PHONE SCAN — poora phone padhne wala
 *
 * User ne kaha: "pahli Bari open hogi na to yah Mera pura phone
 * analyse karegi — ismein Kya Hai, Kaun Sa app Hai, Kaun Sa kya hai"
 *
 * Ye pehli baar app khulne par chalta hai aur poora phone dekh
 * kar samajhta hai:
 *   • Phone kaunsa hai, Android kaunsa
 *   • Kitne app hain, kaun-kaun se (kaam ke hisaab se bante)
 *   • Storage kitna, RAM kitni, battery kitni
 *   • Kaunse kaam ho sakte hain, kaunse nahi
 *
 * ═══ SAAF BAAT — YE JAADU NAHI HAI ═══
 *
 * Ye sirf WAHI padh sakta hai jo Android ek aam app ko padhne
 * deta hai. Ye NAHI dekh sakta:
 *   ✗ Aapke message / photo / call ka record
 *   ✗ Dusre app ka andar ka data
 *   ✗ Password ya bank ki jaankari
 *
 * Aur ye kuch bhi INTERNET PE NAHI BHEJTA. Sab phone ke andar
 * hi rehta hai — sirf isliye ki IEYE RIS ko pata rahe ki aapke
 * phone me kya-kya hai, taaki wo behtar kaam kar sake.
 *
 * ⚠️ Ye BACKGROUND thread pe chalna chahiye — 100+ app scan
 *    karne me 1-3 second lagte hain. UI thread pe app jam
 *    jayegi.
 */
object PhoneScan {

    data class Result(
        val brand: String,
        val model: String,
        val android: String,
        val sdk: Int,
        val totalApps: Int,
        val userApps: Int,
        val categories: Map<String, List<String>>,
        val ramTotalMb: Long,
        val ramFreeMb: Long,
        val storeTotalGb: Double,
        val storeFreeGb: Double,
        val battery: Int,
        val charging: Boolean,
        val can: List<String>,
        val cannot: List<String>,
        val at: Long = System.currentTimeMillis()
    )

    private fun file(c: Context) = File(c.filesDir, "phonescan.json")

    fun done(c: Context) = file(c).exists()

    /**
     * Kaam ke hisaab se app baatna.
     *
     * ⚠️ Android ka apna `category` (ApplicationInfo.category)
     *    bharosemand nahi hai — bahut app usme -1 dete hain.
     *    Isliye package ke naam se bhi milaate hain.
     */
    private val GROUPS = listOf(
        "Baat-cheet" to listOf("whatsapp", "telegram", "messenger",
            "signal", "imo", "snapchat", "discord", "skype",
            "hike", "botim", "wechat", "line", "viber"),
        "Social" to listOf("instagram", "facebook", "twitter",
            "x.android", "threads", "linkedin", "reddit",
            "sharechat", "moj", "josh", "pinterest", "tumblr"),
        "Video" to listOf("youtube", "netflix", "hotstar", "prime",
            "video", "mxplayer", "vlc", "sonyliv", "zee5",
            "jiocinema", "voot", "altbalaji", "player"),
        "Gaana" to listOf("spotify", "gaana", "wynk", "saavn",
            "music", "soundcloud", "audio", "podcast", "resso"),
        "Paisa" to listOf("phonepe", "paytm", "gpay", "googlepay",
            "bhim", "upi", "bank", "sbi", "hdfc", "icici", "axis",
            "kotak", "cred", "groww", "zerodha", "upstox",
            "policybazaar", "paisa"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio",
            "meesho", "snapdeal", "nykaa", "shop", "store",
            "blinkit", "zepto", "bigbasket", "jiomart"),
        "Khana" to listOf("swiggy", "zomato", "dominos", "pizza",
            "eatsure", "foodpanda", "magicpin"),
        "Safar" to listOf("uber", "ola", "rapido", "irctc", "redbus",
            "makemytrip", "goibibo", "yatra", "map", "maps",
            "ixigo", "namma", "metro"),
        "Kaam" to listOf("office", "word", "excel", "powerpoint",
            "docs", "sheets", "slides", "drive", "dropbox",
            "onedrive", "notion", "slack", "teams", "zoom",
            "meet", "webex", "trello", "evernote", "keep"),
        "Padhai" to listOf("byju", "unacademy", "vedantu", "toppr",
            "khan", "coursera", "udemy", "duolingo", "photomath",
            "doubtnut", "physicswallah", "edu", "learn", "school"),
        "Camera" to listOf("camera", "gallery", "photos", "gcam",
            "snapseed", "lightroom", "picsart", "canva", "capcut",
            "kinemaster", "inshot", "vn.android", "editor"),
        "Sehat" to listOf("fit", "health", "step", "yoga", "cure",
            "practo", "apollo", "pharm", "medi", "sleep"),
        "Khel" to listOf("game", "pubg", "bgmi", "freefire", "ludo",
            "candy", "clash", "chess", "carrom", "cricket",
            "dream11", "mpl", "rummy"),
        "Browser" to listOf("chrome", "browser", "firefox", "opera",
            "brave", "duckduckgo", "edge", "uc"),
        "System" to listOf("settings", "dialer", "contacts", "clock",
            "calculator", "calendar", "files", "launcher",
            "keyboard", "gboard", "swiftkey", "phone", "sms",
            "messaging", "recorder")
    )

    private fun group(pkg: String, label: String): String {
        val p = (pkg + " " + label).lowercase()
        for ((name, keys) in GROUPS)
            if (keys.any { p.contains(it) }) return name
        return "Doosre"
    }

    // ═══════════════════════════════════════════
    //   ASLI SCAN
    // ═══════════════════════════════════════════

    fun run(c: Context): Result {
        val pm = c.packageManager

        // ── app ──
        val apps = try { AppRegistry.scan(c, true) }
                   catch (e: Exception) { emptyList() }
        val cats = LinkedHashMap<String, MutableList<String>>()
        var userCount = 0
        for (a in apps) {
            val sys = try {
                val ai = pm.getApplicationInfo(a.pkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) { false }
            if (!sys) userCount++
            val g = group(a.pkg, a.name)
            cats.getOrPut(g) { mutableListOf() }.add(a.name)
        }
        // har group me sirf 12 naam rakho — list bahut lambi na ho
        val trimmed = cats.mapValues { it.value.take(12) }
            .toList().sortedByDescending { it.second.size }.toMap()

        // ── RAM ──
        var ramT = 0L; var ramF = 0L
        try {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramT = mi.totalMem / (1024 * 1024)
            ramF = mi.availMem / (1024 * 1024)
        } catch (e: Exception) {}

        // ── storage ──
        var stT = 0.0; var stF = 0.0
        try {
            val s = StatFs(Environment.getDataDirectory().path)
            stT = s.blockCountLong * s.blockSizeLong /
                  (1024.0 * 1024 * 1024)
            stF = s.availableBlocksLong * s.blockSizeLong /
                  (1024.0 * 1024 * 1024)
        } catch (e: Exception) {}

        // ── battery ──
        var bat = -1; var chg = false
        try {
            val bm = c.getSystemService(Context.BATTERY_SERVICE)
                as BatteryManager
            bat = bm.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val i = c.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            chg = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                  st == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {}

        // ── kya kar sakta hoon ──
        val can = mutableListOf<String>()
        val cannot = mutableListOf<String>()

        fun perm(p: String) = try {
            c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

        if (perm(android.Manifest.permission.RECORD_AUDIO))
            can.add("Aapki awaaz sun sakta hoon")
        else cannot.add("Mic ki ijazat nahi — bol kar nahi bata sakte")

        if (perm(android.Manifest.permission.CALL_PHONE))
            can.add("Call laga sakta hoon")
        else cannot.add("Call ki ijazat nahi")

        if (perm(android.Manifest.permission.READ_CONTACTS))
            can.add("Contacts se naam dhoondh sakta hoon")
        else cannot.add("Contacts ki ijazat nahi — naam se call nahi")

        if (Eyes.on()) can.add("Screen dekh kar khud kaam kar sakta hoon")
        else cannot.add("Accessibility OFF — screen ke andar ka kaam nahi")

        if (Bubble.allowed(c)) can.add("Floating bubble laga sakta hoon")
        else cannot.add("Overlay ki ijazat nahi — bubble nahi dikhega")

        if (Reminders.canExact(c)) can.add("Exact waqt pe reminder")
        else cannot.add("Exact alarm nahi — reminder late baj sakta hai")

        can.add("$userCount app khol sakta hoon")
        can.add("Torch, WiFi, volume, brightness chala sakta hoon")

        // ye kabhi possible nahi
        cannot.add("Phone unlock nahi kar sakta (Android rokta hai)")
        cannot.add("Doosre app ka andar ka data nahi padh sakta")

        val r = Result(
            brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            android = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            totalApps = apps.size,
            userApps = userCount,
            categories = trimmed,
            ramTotalMb = ramT, ramFreeMb = ramF,
            storeTotalGb = stT, storeFreeGb = stF,
            battery = bat, charging = chg,
            can = can, cannot = cannot
        )
        save(c, r)
        Brain.log("🔍 phone scan: ${apps.size} app, " +
                  "${trimmed.size} kism")
        return r
    }

    // ═══════════════════════════════════════════
    //   save / load
    // ═══════════════════════════════════════════

    private fun save(c: Context, r: Result) {
        try {
            val o = JSONObject().apply {
                put("brand", r.brand); put("model", r.model)
                put("android", r.android); put("sdk", r.sdk)
                put("total", r.totalApps); put("user", r.userApps)
                put("ramT", r.ramTotalMb); put("ramF", r.ramFreeMb)
                put("stT", r.storeTotalGb); put("stF", r.storeFreeGb)
                put("bat", r.battery); put("chg", r.charging)
                put("at", r.at)
                put("cats", JSONObject().apply {
                    r.categories.forEach { (k, v) ->
                        put(k, v.joinToString(", "))
                    }
                })
            }
            file(c).writeText(o.toString())
        } catch (e: Exception) {}
    }

    /** AI ko bhejne ke liye — chhoti si line */
    fun forAI(c: Context): String = try {
        val o = JSONObject(file(c).readText())
        val sb = StringBuilder()
        sb.append("Phone: ${o.optString("brand")} ")
        sb.append("${o.optString("model")}, ")
        sb.append("Android ${o.optString("android")}. ")
        sb.append("${o.optInt("user")} app installed. ")
        val cats = o.optJSONObject("cats")
        if (cats != null) {
            val ks = cats.keys().asSequence().take(6).toList()
            if (ks.isNotEmpty())
                sb.append("Kism: " + ks.joinToString(", ") + ".")
        }
        sb.toString()
    } catch (e: Exception) { "" }

    // ═══════════════════════════════════════════
    //   dikhane ke liye
    // ═══════════════════════════════════════════

    fun report(r: Result): String {
        val sb = StringBuilder()
        sb.append("🔍 AAPKA PHONE — poora dekh liya\n\n")
        sb.append("📱 ${r.brand} ${r.model}\n")
        sb.append("   Android ${r.android}  (API ${r.sdk})\n\n")

        sb.append("📦 APP\n")
        sb.append("   Kul ........... ${r.totalApps}\n")
        sb.append("   Aapke apne .... ${r.userApps}\n\n")

        if (r.categories.isNotEmpty()) {
            sb.append("🗂 KIS KAAM KE\n")
            r.categories.forEach { (k, v) ->
                sb.append("   $k (${v.size}) — ")
                sb.append(v.take(5).joinToString(", "))
                if (v.size > 5) sb.append("…")
                sb.append("\n")
            }
            sb.append("\n")
        }

        sb.append("⚙️ PHONE KA HAAL\n")
        if (r.ramTotalMb > 0)
            sb.append("   RAM ........... ${r.ramFreeMb} MB khali " +
                      "/ ${r.ramTotalMb} MB\n")
        if (r.storeTotalGb > 0)
            sb.append(String.format(Locale.US,
                "   Storage ....... %.1f GB khali / %.1f GB%n",
                r.storeFreeGb, r.storeTotalGb))
        if (r.battery >= 0)
            sb.append("   Battery ....... ${r.battery}%" +
                      (if (r.charging) " (charge ho raha)" else "") + "\n")
        sb.append("\n")

        sb.append("✅ MAIN YE KAR SAKTA HOON\n")
        r.can.forEach { sb.append("   • $it\n") }

        if (r.cannot.isNotEmpty()) {
            sb.append("\n⚠️ YE NAHI KAR SAKTA\n")
            r.cannot.forEach { sb.append("   • $it\n") }
        }

        sb.append("\n🔒 Ye sab phone ke andar hi raha — ")
        sb.append("kahin bheja nahi gaya.")
        return sb.toString()
    }

    fun lastReport(c: Context): String = try {
        val o = JSONObject(file(c).readText())
        val d = SimpleDateFormat("d MMM, h:mm a", Locale.US)
            .format(Date(o.optLong("at")))
        "🔍 Aakhri scan: $d\n\n" + forAI(c)
    } catch (e: Exception) { "Abhi tak phone scan nahi hua." }
}
