package com.ravanx.ieyeris

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🔑 LICENSE — app khulne se pehle key ki jaanch
 *
 * User ne kaha:
 *   "jab dalega na sabse pehle key hi maangega, aur daalte
 *    server se pehle poochega ki yahi wali hai na. Agar server
 *    bol deta hai ki yahi wali hai... uske bina kuch nahi.
 *    Matlab sab kuch SERVER SIDE se hi hoga. Agar server bole
 *    nahi hai to nahi."
 *
 * ═══ POORA SERVER-SIDE ═══
 *
 * Ye file khud koi faisla NAHI karti. Har baar server se
 * poochti hai aur jo server kahe wahi maanti hai:
 *
 *   • Timer server ki ghadi se — phone ki ghadi peeche karke
 *     koi zyada nahi chala sakta
 *   • Admin key band kare -> agla check hote hi app band
 *   • Device limit server ginta hai
 *
 * ⚠️ Ek imaandari ki baat: agar koi APK ko kholkar ye check
 *    hata de, to app chal jayegi. Ye har license system ke
 *    saath hota hai — Netflix se lekar Adobe tak. Iska poora
 *    ilaaj sirf ye hai ki asli kaam (AI) server pe ho, jo
 *    yahan pehle se hai (Groq/Gemini keys server se aati hain).
 *
 * ⚠️ Net na ho to kya? — 3 din ki chhoot rakhi hai. Wajah:
 *    net ki dikkat me app bilkul band ho jaye to user ka
 *    kaam ruk jata hai aur galti humari lagti hai. 3 din baad
 *    check zaroori.
 */
object License {

    /**
     * ⚠️ v4.4 — URL AB APP KE ANDAR SE DAAL SAKTE HO
     *
     * User ne poochha: "render ka URL kahan daalun... tum bata
     * dena kaun si file me hai"
     *
     * Ab file badalne ki ZARURAT NAHI. Do tarike:
     *
     *   1. App me — License screen pe "⚙️ Server badlo" button
     *      (sabse aasan, phone se hi ho jata hai)
     *
     *   2. Yahan — neeche wali line me apna URL daal do
     *      (agar sab users ko ek hi server pe bhejna ho)
     *
     * Khali chhoda to app khud maang legi — koi crash nahi.
     */
    private const val DEFAULT_SERVER = ""

    /** Net na ho to kitne din chhoot */
    private const val GRACE_DAYS = 3

    // ── phone me save ──
    private const val K_CODE = "lic_code"
    private const val K_OK = "lic_ok"
    private const val K_EXP = "lic_exp"        // server ki expiry
    private const val K_LAST = "lic_last"      // aakhri kaamyab check
    private const val K_MSG = "lic_msg"

    data class Result(
        val ok: Boolean,
        val msg: String,
        val daysLeft: Int = 0,
        val unlimited: Boolean = false,
        val offline: Boolean = false
    )

    fun server(c: Context): String =
        Keys(c).get("lic_server", "").ifBlank { DEFAULT_SERVER }
            .trimEnd('/')

    /**
     * ⚠️ v4.5 — URL BADALNA AB OWNER-ONLY
     *
     * User ne kaha: "URL sirf jo hai na wo nahi badal sakta,
     * uske liye ek password lagega — yeh raha 2244"
     *
     * Ye function ab bhi seedha set kar deta hai — TAALA UI par
     * lagaya hai (LicenseActivity me Owner.ask() ke andar).
     * Yahan taala JAANBUJH KAR nahi lagaya: agar yahan bhi
     * check hota to har call site pe Context ke saath password
     * ghumana padta aur bug ka khatra badhta.
     */
    fun setServer(c: Context, url: String) =
        Keys(c).set("lic_server", url.trim().trimEnd('/'))

    // ══════════════════════════════════════════════════════
    //  ⏱ v4.5 — REAL-TIME TIMER
    //
    //  User ne kaha: "jab bhi koi login vagaira karta hai na
    //  sabse upar timer chalu ho jaye, real time ka timer.
    //  Aise ek din ka hai to ek din ka pura timer chalu ho
    //  jaye, upar real time me chalu hona hi chahiye. Timer
    //  chahe kuch bhi ho, timer to chalu hoga ek hi daalne
    //  ke baad."
    //
    //  ⚠️ Timer SERVER KI GHADI se chalta hai, phone ki nahi.
    //     Server har jawab me `server_time` bhejta hai. Hum
    //     server aur phone ka farak (offset) yaad rakh lete
    //     hain. Isse phone ki date peeche karke koi timer
    //     nahi badha sakta — sabse aam chori yahi hoti hai.
    // ══════════════════════════════════════════════════════

    private const val K_UNLTD = "lic_unltd"
    private const val K_SKEW = "lic_skew"     // server - phone (sec)

    /** Server ki expiry (epoch sec). 0 = pata nahi / unlimited */
    fun expiresAt(c: Context): Long =
        Keys(c).get(K_EXP, "0").toLongOrNull() ?: 0L

    fun unlimited(c: Context): Boolean = Keys(c).flag(K_UNLTD, false)

    /**
     * Abhi ka sahi waqt — server ki ghadi ke hisaab se.
     *
     * Phone ka waqt + jo farak humne aakhri baar naapa tha.
     */
    fun nowSec(c: Context): Long {
        val skew = Keys(c).get(K_SKEW, "0").toLongOrNull() ?: 0L
        return System.currentTimeMillis() / 1000 + skew
    }

    /** Kitne second bache — 0 se kam kabhi nahi */
    fun secondsLeft(c: Context): Long {
        if (unlimited(c)) return -1L        // -1 = hamesha
        val exp = expiresAt(c)
        if (exp <= 0L) return 0L
        return (exp - nowSec(c)).coerceAtLeast(0L)
    }

    /**
     * Timer ka text — "12d 04:37:11" jaisa.
     *
     * ⚠️ Second hamesha dikhta hai. User ne saaf kaha "real
     *    time ka timer" — matlab hilta hua dikhna chahiye,
     *    warna use lagta hai ruka hua hai.
     */
    fun timerText(c: Context): String {
        if (!activated(c)) return "🔒 LICENSE BAND"
        val s = secondsLeft(c)
        if (s < 0L) return "♾️  HAMESHA KE LIYE"
        if (s == 0L) return "⌛ SAMAY KHATAM"
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (d > 0)
            String.format("⏳ %dd  %02d:%02d:%02d", d, h, m, sec)
        else
            String.format("⏳ %02d:%02d:%02d", h, m, sec)
    }

    /** Timer ka rang — aakhri din me laal, aakhri 3 din me peela */
    fun timerColor(c: Context): Int {
        val s = secondsLeft(c)
        return when {
            s < 0L -> 0xFF39FF88.toInt()          // unlimited — hara
            s == 0L -> 0xFFFF5A5A.toInt()         // khatam — laal
            s < 86400L -> 0xFFFF5A5A.toInt()      // 1 din — laal
            s < 3 * 86400L -> 0xFFFFC94D.toInt()  // 3 din — peela
            else -> 0xFF39FF88.toInt()            // theek — hara
        }
    }

    fun savedCode(c: Context): String = Keys(c).get(K_CODE, "")

    /**
     * Har phone ki apni pehchan.
     *
     * ⚠️ ANDROID_ID use kiya hai. Ye app uninstall karne par
     *    BHI wahi rehta hai (factory reset pe badalta hai).
     *    Isse user app dobara install karke naya device slot
     *    nahi le sakta.
     */
    fun deviceId(c: Context): String = try {
        val a = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        if (a.isBlank() || a == "9774d56d682e549c") {
            // kuch purane phone me ye fake ID deta hai
            var s = Keys(c).get("lic_dev", "")
            if (s.isBlank()) {
                s = java.util.UUID.randomUUID().toString().take(20)
                Keys(c).set("lic_dev", s)
            }
            s
        } else a
    } catch (e: Exception) { "unknown" }

    /** Pehle se chalu hai? (turant, bina net ke) */
    fun activated(c: Context): Boolean {
        val k = Keys(c)
        if (!k.flag(K_OK, false)) return false
        if (k.get(K_CODE, "").isBlank()) return false

        // server ki expiry nikal gayi?
        // ⚠️ v4.5 — server ki ghadi se naapte hain (nowSec), phone
        //    ki se nahi. Phone ki date peeche karke koi extra din
        //    nahi chura sakta.
        val exp = k.get(K_EXP, "0").toLongOrNull() ?: 0
        if (exp > 0 && nowSec(c) >= exp) return false

        // 3 din se check nahi hua?
        val last = k.get(K_LAST, "0").toLongOrNull() ?: 0
        val age = System.currentTimeMillis() / 1000 - last
        if (last > 0 && age > GRACE_DAYS * 86400L) return false

        return true
    }

    fun savedMsg(c: Context): String = Keys(c).get(K_MSG, "")

    /**
     * 🌐 Server se poochho — ASLI JAANCH.
     *
     * ⚠️ Ye BACKGROUND thread se hi bulao. Network call UI
     *    thread pe Android turant exception phenk deta hai.
     */
    fun check(c: Context, code: String = ""): Result {
        val k = Keys(c)
        val useCode = code.ifBlank { k.get(K_CODE, "") }.trim().uppercase()
        if (useCode.isBlank())
            return Result(false, "Key daaliye.")

        val body = JSONObject().apply {
            put("code", useCode)
            put("device", deviceId(c))
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
            put("android", Build.VERSION.RELEASE ?: "")
            put("ver", try { BuildConfig.VERSION_NAME } catch (e: Exception) { "" })
        }.toString()

        return try {
            val out = post(server(c) + "/api/check", body)
            val j = JSONObject(out)
            val ok = j.optBoolean("ok", false)
            val msg = j.optString("msg", "")

            /*
             * ⏱ v4.5 — SERVER KI GHADI SE MILAO
             *
             * Server har jawab me `server_time` bhejta hai. Uska
             * aur phone ka farak yaad rakh lete hain, taaki timer
             * server ke hisaab se chale.
             *
             * ⚠️ Ye ok/fail DONO me karte hain — server ne "nahi"
             *    bola tab bhi uska waqt to sahi hi hai.
             */
            val st = j.optLong("server_time", 0L)
            if (st > 0L) {
                val skew = st - System.currentTimeMillis() / 1000
                // 1 saal se zyada farak = kuch to gadbad, chhod do
                if (kotlin.math.abs(skew) < 365L * 86400L)
                    k.set(K_SKEW, skew.toString())
            }

            if (ok) {
                k.set(K_CODE, useCode)
                k.setFlag(K_OK, true)
                k.set(K_EXP, j.optLong("expires_at", 0).toString())
                k.setFlag(K_UNLTD, j.optBoolean("unlimited", false))
                k.set(K_LAST, (System.currentTimeMillis() / 1000).toString())
                k.set(K_MSG, "")
                Brain.log("🔑 license OK — ${j.optInt("days_left")} din")
            } else {
                // ⚠️ Server ne saaf mana kiya — turant band.
                //    Yahi user ne maanga tha: "agar server bole
                //    nahi hai to nahi".
                k.setFlag(K_OK, false)
                k.set(K_MSG, msg)
                Brain.log("🔑 license FAIL — $msg")
            }
            Result(ok, msg,
                j.optInt("days_left", 0),
                j.optBoolean("unlimited", false))

        } catch (e: Exception) {
            /*
             * ⚠️⚠️ v4.4 — "INTERNET CHAHIE" WALA BUG ⚠️⚠️
             *
             * User ki shikayat: "internet chahie yah kyon aa
             * raha hai, ise theek karna"
             *
             * ASLI WAJAH: pehle HAR error pe wahi ek message
             * aata tha — "Internet chahiye". Chahe:
             *   • server ka URL hi galat ho
             *   • server band ho (Render sleep me)
             *   • net sach me na ho
             *
             * User ka net bilkul theek tha, par URL abhi tak
             * set hi nahi hua tha. Usko lagta tha net ki dikkat
             * hai aur wo ghanton net check karta rehta.
             *
             * Ab har wajah ka apna saaf message hai — aur seedha
             * hal bhi bataya jata hai.
             */
            val em = (e.message ?: "").lowercase()
            val srv = server(c)
            Brain.log("🔑 license fail: ${e.javaClass.simpleName} " +
                      "— ${em.take(60)}")

            // 1️⃣ Server ka pata hi set nahi
            if (srv.isBlank() || srv.contains("ieyeris-license.onrender")) {
                return Result(false,
                    "⚙️ Server ka pata set nahi hai.\n\n" +
                    "Neeche \"Server badlo\" pe tap karke apna\n" +
                    "Render ka link daaliye:\n" +
                    "https://aapka-app.onrender.com")
            }

            // 2️⃣ Net ki chhoot (pehle se chalu key)
            if (activated(c)) {
                val last = k.get(K_LAST, "0").toLongOrNull() ?: 0
                val leftD = GRACE_DAYS -
                    ((System.currentTimeMillis() / 1000 - last) / 86400).toInt()
                return Result(true,
                    "📶 Server se baat nahi hui — $leftD din tak " +
                    "app chal jayegi", offline = true)
            }

            // 3️⃣ Asli wajah pehchano
            val msg = when {
                em.contains("unable to resolve host") ||
                em.contains("no address") ->
                    "🌐 Server ka pata nahi mila.\n\n" +
                    "• Link sahi hai? $srv\n" +
                    "• Ya internet band hai?"

                em.contains("timeout") || em.contains("timed out") ->
                    "⏳ Server ne jawab nahi diya.\n\n" +
                    "Render free plan pe server so jata hai.\n" +
                    "1 minute ruk kar dobara try kijiye —\n" +
                    "pehli baar 50 second lagte hain."

                em.contains("connect") || em.contains("refused") ->
                    "🔌 Server se connect nahi hua.\n\n" +
                    "$srv\n\n" +
                    "Render pe deploy hua hai? Check kijiye."

                em.contains("ssl") || em.contains("trust") ||
                em.contains("certificate") ->
                    "🔒 HTTPS me dikkat.\n\n" +
                    "Link https:// se shuru hona chahiye,\n" +
                    "http:// se nahi."

                em.contains("network is unreachable") ||
                em.contains("econnreset") ->
                    "📶 Internet nahi hai.\n\n" +
                    "WiFi ya mobile data on kijiye."

                else ->
                    "⚠️ Server se baat nahi hui.\n\n" +
                    "Server: $srv\n" +
                    "Wajah: ${em.take(70)}"
            }
            Result(false, msg)
        }
    }

    /** Background me chup-chaap check (app khulne par) */
    fun checkAsync(c: Context, onDone: ((Result) -> Unit)? = null) {
        Thread {
            val r = try { check(c) }
                    catch (e: Exception) { Result(false, "Dikkat") }
            onDone?.invoke(r)
        }.start()
    }

    fun clear(c: Context) {
        val k = Keys(c)
        k.set(K_CODE, ""); k.setFlag(K_OK, false)
        k.set(K_EXP, "0"); k.set(K_LAST, "0"); k.set(K_MSG, "")
        k.setFlag(K_UNLTD, false); k.set(K_SKEW, "0")
    }

    // ── network ──
    private fun post(url: String, body: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // ⚠️ 15s connect — mobile data pe TLS handshake hi
            //    1-3 second le leta hai. Kam rakha to net theek
            //    hone par bhi fail hota hai.
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val s = if (code in 200..299) c.inputStream else c.errorStream
            return s?.bufferedReader()?.use { it.readText() } ?: "{}"
        } finally {
            try { c.disconnect() } catch (e: Exception) {}
        }
    }
}
