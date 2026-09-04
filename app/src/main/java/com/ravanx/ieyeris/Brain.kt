package com.ravanx.ieyeris

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🧠 IEYE RIS ka dimaag
 *
 * Do hisse:
 *   1. LOCAL match  — bina internet, turant (0ms)
 *   2. AI se poochho — jab local samajh na paye
 *
 * Local pehle chalta hai kyunki "flashlight on karo" ke liye
 * AI ko poochhna bewakoofi hai — 2 second waste aur data bhi.
 */
object Brain {

    data class Cmd(
        val action: String,
        val arg: String = "",
        val say: String = "",
        val fromAI: Boolean = false,
        /**
         * v2.2 — user ne asal me kya bola tha.
         *
         * "chat" action ko poora sawaal chahiye hota hai (AI ko
         * bhejne ke liye). Pehle sirf `say` milta tha jo AI ka
         * banaya 5-shabd ka jumla hota tha — us se asli jawab
         * kabhi nahi mil sakta tha.
         */
        val raw: String = ""
    )

    // ═══════════════════════════════════════════
    //   APP ke naam — bolne wale naam se package
    // ═══════════════════════════════════════════
    val APPS = mapOf(
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "map" to "com.google.android.apps.maps",
        "camera" to "camera",
        "gallery" to "gallery",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "in.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "snapchat" to "com.snapchat.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "linkedin" to "com.linkedin.android",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android",
        "uber" to "com.ubercab",
        "ola" to "com.olacabs.customer",
        "settings" to "settings",
        "calculator" to "calculator",
        "clock" to "clock",
        "calendar" to "com.google.android.calendar",
        "playstore" to "com.android.vending",
        "play store" to "com.android.vending",
        "files" to "files",
        "termux" to "com.termux",
        "discord" to "com.discord",
        "pinterest" to "com.pinterest",
        "reddit" to "com.reddit.frontpage",
        "hotstar" to "in.startv.hotstar",
        "jio" to "com.jio.myjio",
        "truecaller" to "com.truecaller"
    )

    private fun norm(s: String) = s.lowercase().trim()
        .replace(Regex("[?.!,]"), " ")
        .replace(Regex("\\s+"), " ")

    /**
     * Wake word / naam hata do —
     *   "IEYE RIS youtube kholo"  ->  "youtube kholo"
     *
     * ⚠️ norm() sab lowercase kar deta hai, isliye yahan list
     *    bhi LOWERCASE me honi chahiye. Pehle "IEYE RIS" bade
     *    akshar me tha — kabhi match hi nahi hota tha.
     *
     * Bolne me log alag-alag tarah kehte hain, isliye kai roop:
     *   "ieye ris", "eye ris", "ai ris", "iris"
     */
    fun strip(t: String): String {
        var s = norm(t)
        for (w in listOf(
            "hey ieye ris", "hey eye ris", "hey iris", "hey ieyeris",
            "ok ieye ris", "ok iris", "arey ieye ris",
            "ieye ris", "ieyeris", "eye ris", "ai ris", "iris",
            "ieye", "eyeris")) {
            if (s.startsWith(w)) {
                s = s.removePrefix(w).trim()
                break
            }
        }
        // "wake up" / "sleep" jaise shabd bhi hata do
        for (w in listOf("wake up", "wakeup", "jaago", "uth ja")) {
            if (s == w) return ""
        }
        return s
    }

    // ═══════════════════════════════════════════
    //   1. LOCAL — bina internet, turant
    // ═══════════════════════════════════════════
    /**
     * ⚠️ User ne saaf bola: "kuchh default vagaira nahin, sab AI
     *    karega". To ab local sirf tab chalta hai jab EK hi kaam
     *    ho. Do kaam ek saath bole -> seedha AI, kyunki local
     *    sirf pehla pakad kar doosra gira deta tha. Yahi wajah
     *    thi ki "ek saath kuch bolo to kuch nahi karta".
     */
    fun isMulti(raw: String): Boolean {
        val t = strip(raw)
        // "aur", "phir", "uske baad" — jodne wale shabd
        val joiners = Regex(
            "\\b(aur|and|phir|fir|uske baad|iske baad|then|" +
            "saath me|ke baad|tatha)\\b")
        val hits = joiners.findAll(t).count()
        if (hits == 0) return false
        // "aur" kabhi naam ka hissa bhi hota hai — kaam wale
        // shabd 2+ hone chahiye
        val verbs = Regex(
            "\\b(karo|kar do|kholo|khol do|band|chalu|on|off|lagao|" +
            "bhejo|batao|lo|do|padho|dabao|badhao|ghatao|play|chalao|" +
            "search|dhundo|set)\\b")
        return verbs.findAll(t).count() >= 2
    }

    /**
     * ═══ v2.0 — IEYE RIS ke apne hukum, ASLI BOLI ME ═══
     *
     * Pehle exact match tha, isliye ye teeno kaam nahi karte the:
     *   "shutdown ho jao"      ❌
     *   "mic band karo"        ❌
     *   "ab tum kuch mat bolna" ❌
     *
     * Ab har hukum ke liye kai roop rakhe hain aur POORA phrase
     * dhoondhte hain — chahe user aage-peeche kuch bhi bole.
     *
     * ⚠️ Tarteeb ka dhyan: pehle SABSE KHAAS cheez dekho.
     *    "mic band karo" ko "band karo" (stop) se pehle pakadna
     *    hoga, warna galat kaam ho jayega.
     */
    private fun cmdMatch(t: String): Cmd? {
        // poora phrase — beech me kahin bhi ho
        fun p(vararg w: String) = w.any { x ->
            Regex("(^|\\s)" + Regex.escape(x) + "($|\\s)")
                .containsMatchIn(t)
        }

        // ── 1. MIC BAND / CHUP HO JAO ────────────────────
        //    Sabse pehle, kyunki "mic band karo" me "band karo"
        //    bhi hai — warna wo stop samajh leta.
        //
        //    Do alag cheezein hain:
        //      mic band  = sunna band karo (kaan band)
        //      chup raho = bolna band karo (muh band)
        if (p("mic band karo", "mic band", "mic off", "mike band karo",
              "mike band", "maik band", "mic bandh karo",
              "sunna band karo", "sunna band", "mic hata do",
              "mic close karo", "mic band kar do",
              "kaan band karo", "mat suno", "ab mat suno",
              "sunna band kar do"))
            return Cmd("mic_off", "", "")

        if (p("kuch mat bolna", "kuchh mat bolna", "mat bolna",
              "mat bolo", "ab kuch mat bolna", "ab mat bolo",
              "chup ho jao", "chup ho ja", "chup raho", "chup rho",
              "chup", "chup kar", "chup karo", "bolna band karo",
              "bolna band", "awaaz band karo", "awaz band karo",
              "awaaz band", "silent ho jao", "silent mode",
              "muh band karo", "bolo mat", "shant ho jao",
              "shaant ho jao", "quiet"))
            return Cmd("mute_voice", "", "")

        // wapas bolne lago
        if (p("ab bolo", "bolna chalu karo", "bolna shuru karo",
              "awaaz chalu karo", "awaz chalu karo", "bol sakte ho",
              "ab bol sakte ho", "unmute", "wapas bolo",
              "awaaz on karo", "bolna on karo"))
            return Cmd("unmute_voice", "", "")

        // mic wapas chalu
        if (p("mic chalu karo", "mic on", "mic on karo", "mike on",
              "sunna chalu karo", "sunna shuru karo",
              "mic khol do", "kaan khol do", "ab suno"))
            return Cmd("mic_on", "", "")

        // ── 2. SHUTDOWN — sab band ────────────────────────
        //    "shutdown ho jao" wali shikayat yahan theek hui
        if (p("shutdown", "shut down", "shutdown ho jao",
              "shut down ho jao", "shutdown hojao", "shatdaun",
              "shutdown karo", "shutdown kar do",
              "bilkul band", "bilkul band ho jao",
              "poora band karo", "poora band", "sab band karo",
              "sab kuch band karo", "sab band ho jao",
              "system band karo", "app band karo",
              "khatam karo", "bandh karo sab"))
            return Cmd("shutdown", "", "")

        // ── 3. SLEEP — standby ────────────────────────────
        if (p("sleep", "sleep mode", "so ja", "so jao", "so jaao",
              "standby", "standby me jao", "aaram karo",
              "sleep mode me jao", "band ho ja", "band ho jao",
              "rest mode"))
            return Cmd("sleep", "", "")

        // ── 4. WAKE / RESUME ──────────────────────────────
        if (p("wake up", "wakeup", "wake", "jaago", "jag jao",
              "uth ja", "uth jao", "utho", "online",
              "online ho jao", "chalu ho ja", "chalu ho jao",
              "resume", "wapas aao", "phir se chalu",
              "phir se chalu karo", "continue", "active ho jao"))
            return Cmd("wake", "", "")

        // ── 5. STATUS ─────────────────────────────────────
        if (p("status", "system status", "haal", "kya haal",
              "kya haal hai", "apna status batao", "status batao",
              "sab theek hai", "report do", "system report"))
            return Cmd("status", "", "")

        // ── 6. STOP — abhi ka kaam roko ───────────────────
        //    Sabse aakhir me, kyunki ye sabse aam shabd hain
        if (p("stop", "ruk", "ruko", "ruk ja", "ruk jao",
              "rok do", "cancel", "cancel karo", "rehne do",
              "chhod do", "band karo", "bas karo", "bas"))
            return Cmd("stop", "", "")

        return null
    }

    fun local(raw: String): Cmd? {
        val t = strip(raw)

        // ═══ IEYE RIS ke apne hukum (spec section 7) ═══
        //
        //   "IEYE RIS wake up"   -> chalu
        //   "IEYE RIS sleep"     -> standby
        //   "IEYE RIS stop"      -> abhi ka kaam rok
        //   "IEYE RIS status"    -> haal batao
        //   "IEYE RIS resume"    -> wapas chalu
        //
        // ⚠️ Ye AI ke paas nahi jaate — turant chahiye.
        val raw0 = norm(raw)
        if (t.isBlank()) {
            // sirf naam bola, aur kuch nahi -> jaag jao
            if (raw0.isNotBlank()) return Cmd("wake", "", "")
            return null
        }
        // ⚠️⚠️ v2.0 — YE HUKUM PEHLE KAAM HI NAHI KARTE THE ⚠️⚠️
        //
        //   Pehle yahan `t in listOf(...)` tha — matlab BILKUL
        //   HOOBAHOO milna chahiye tha. User jo asal me bolta hai
        //   wo kabhi match hi nahi hota tha:
        //
        //     "shutdown ho jao"     -> list me "shutdown" hai,
        //                              par "shutdown ho jao" nahi ❌
        //     "mic band karo"       -> list me tha hi nahi        ❌
        //     "ab kuch mat bolna"   -> list me tha hi nahi        ❌
        //     "chup ho jao"         -> list me "chup ho ja" tha,
        //                              "jao" wala nahi            ❌
        //
        //   User ki shikayat bilkul sahi thi. Ab POORA SHABD
        //   dhoondhte hain, jitni bhi lambi baat ho.
        cmdMatch(t)?.let { return it }

        // ═══ v2.2 — MODE badalna (spec 27/28) ═══
        //    AI ke paas bhejne ki zarurat nahi — turant hona chahiye
        Modes.detect(t)?.let {
            return Cmd("mode", it, "")
        }

        // ═══ v2.2 — REMINDER (spec 26) ═══
        //
        // ⚠️ Ye AI se PEHLE hai, jaanbujh kar. Waqt ka hisaab
        //    hum khud sahi karte hain — AI kabhi "kal 5 baje" ko
        //    aaj ka 5 baja bana deta tha, aur reminder guzar
        //    chuke waqt pe lag jata tha (yaani kabhi bajta hi nahi).
        if (Regex("(^|\\s)(reminder|yaad dila|yaad dilana|" +
                  "yaad dilao|yad dila)").containsMatchIn(t)) {
            if (Regex("(list|dikhao|batao|kitne|kaun se|sare|saare)")
                    .containsMatchIn(t))
                return Cmd("remind_list", "", "")
            if (Regex("(hata|delete|remove|cancel|mita)")
                    .containsMatchIn(t)) {
                val n = Regex("\\d+").find(t)?.value
                    ?: if (Regex("(sab|sare|saare|all)")
                            .containsMatchIn(t)) "all" else ""
                return Cmd("remind_del", n, "")
            }
            return Cmd("remind", t, "")
        }
        // "5 minute baad chai" — bina "reminder" bole bhi
        if (Regex("\\d{1,3}\\s*(minute|min|ghante|ghanta)\\s*" +
                  "(baad|bad|me|mein)").containsMatchIn(t) &&
            Regex("(uthana|uthao|batana|bata dena|bula|alarm|" +
                  "yaad|remind|dilana)").containsMatchIn(t)) {
            return Cmd("remind", t, "")
        }

        // ═══ v4.0 — GAANA (turant, AI ki zarurat nahi) ═══
        if (Regex("(gaana|gana|song|music|track)").containsMatchIn(t) ||
            Regex("(^|\\s)(play|pause)(\\s|$)").containsMatchIn(t)) {
            when {
                Regex("(agla|next|aage wala|skip)").containsMatchIn(t) ->
                    return Cmd("music", "next", "")
                Regex("(pichla|previous|prev|peeche wala|wapas wala)")
                    .containsMatchIn(t) -> return Cmd("music", "prev", "")
                Regex("(rok|pause|thehr)").containsMatchIn(t) ->
                    return Cmd("music", "pause", "")
                Regex("(gaana band|music band|song band|gana band)")
                    .containsMatchIn(t) -> return Cmd("music", "stop", "")
                Regex("(chala|play|shuru|bajao|laga)").containsMatchIn(t) &&
                    !Regex("(youtube|yt|video)").containsMatchIn(t) ->
                    return Cmd("music", "play", "")
            }
        }

        // ═══ v4.0 — NOTE ═══
        //
        // ⚠️ TARTEEB ZAROORI — "padho/dikhao" wala PEHLE.
        //    Test ne pakda: "note batao" ko naya note samajh
        //    kar "batao" likh leta tha. User note SUNNA chah
        //    raha tha, aur ULTA ek bekaar note ban jata tha.
        if (Regex("(^|\\s)(note|notes)\\s*(dikhao|batao|padho|list|" +
                  "kya hai|sunao|suna do|dekhao)").containsMatchIn(t))
            return Cmd("notes", "", "")
        Regex("^(?:note|likh|likh lo|likh do|note karo|yaad kar lo)" +
              "\\s+(.{2,200})$").find(t)?.let {
            return Cmd("note", it.groupValues[1].trim(), "")
        }

        // ═══ v3.1 — AUTO-REPLY (spec 30) ═══
        if (Regex("(auto ?reply|apne aap jawab|khud jawab|" +
                  "automatic reply)").containsMatchIn(t)) {
            val off = Regex("(band|off|hata|mat|nahi)")
                .containsMatchIn(t)
            return Cmd("auto_reply", if (off) "off" else "on", "")
        }

        // ═══ v2.2 — TRANSLATE (spec 29) ═══
        Modes.translation(raw)?.let { (lang, txt) ->
            return Cmd("translate", "$lang|$txt", "")
        }

        // do kaam ek saath -> local mat karo, AI hi sambhale
        if (isMulti(raw)) return null

        fun has(vararg w: String) = w.any { t.contains(it) }

        // ⚠️⚠️ AUTO-BACK WALA BUG ⚠️⚠️
        //
        //   has() `contains` karta hai. Isliye "back" ne
        //   in sab pe back daba diya:
        //       "background me chalao"   ❌
        //       "backup le lo"           ❌
        //       "feedback do"            ❌
        //       "playback band karo"     ❌
        //       "callback aaya"          ❌
        //
        //   User ki shikayat "auto back" yahi thi — bina
        //   bole hi peeche chala jaata tha.
        //
        //   word() POORA shabd dekhta hai. "background" me
        //   "back" hai par POORA shabd "background" hai —
        //   isliye match nahi hoga.
        fun word(vararg w: String) = w.any { x ->
            Regex("(^|\\s)" + Regex.escape(x) + "($|\\s)")
                .containsMatchIn(t)
        }
        val on = has(" on", "chalu", "khol", "jala", "start", "enable")
        val off = has(" off", "band", "bujha", "stop", "disable", "bandh")

        // ── SEARCH kisi app me ──
        // "youtube pe arijit singh search karo"
        Regex("(?:youtube|yt)\\s*(?:pe|par|me|main|mein)?\\s*(.+?)" +
              "\\s*(?:search|dhundo|dhoondo|chalao|baja|play)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim(),
                    "YouTube pe dhoondh raha hoon")
            }
        Regex("(?:youtube|yt)\\s*(?:pe|par|me)?\\s*" +
              "(?:search|dhundo|chalao|play)\\s+(.+)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim(),
                    "YouTube pe dhoondh raha hoon")
            }
        Regex("(?:google|net|internet)\\s*(?:pe|par|me)?\\s*" +
              "(.+?)\\s*(?:search|dhundo|dhoondo)").find(t)?.let {
            return Cmd("google", it.groupValues[1].trim(),
                "Google kar raha hoon")
        }
        Regex("(?:search|dhundo|dhoondo)\\s+(.+?)\\s*" +
              "(?:on|pe|par)?\\s*(?:google)?$").find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length > 1) return Cmd("google", q, "Dhoondh raha hoon")
        }

        // ── APP kholo ──
        if (has("khol", "open", "chalu karo", "launch", "start karo")) {
            for ((name, pkg) in APPS) {
                if (t.contains(name)) {
                    // "flashlight on karo" APP nahi hai
                    if (name == "camera" && has("flash")) break
                    return Cmd("open_app", pkg, "$name khol raha hoon")
                }
            }
        }

        // ── FLASHLIGHT / TORCH ──
        if (has("flash", "torch", "batti", "light")) {
            if (off) return Cmd("torch_off", "", "Torch band")
            if (on || has("torch", "flash")) return Cmd("torch_on", "",
                "Torch chalu")
        }

        // ── PHONE LOCK ──
        if (has("lock") && !has("unlock")) {
            return Cmd("lock", "", "Phone lock kar raha hoon")
        }
        if (has("unlock")) {
            return Cmd("no_unlock", "",
                "Sir, phone unlock karna Android ki security ke " +
                "khilaf hai — koi bhi app ye nahi kar sakta. " +
                "Aapko khud fingerprint lagana padega.")
        }

        // ── WIFI / BLUETOOTH / DATA ──
        if (has("wifi", "wi-fi")) return Cmd("wifi",
            if (off) "off" else "on", "WiFi settings khol raha hoon")
        if (has("bluetooth", "blue tooth")) return Cmd("bluetooth",
            if (off) "off" else "on", "Bluetooth settings")
        if (has("mobile data", "data on", "data off", "internet on",
                "internet off"))
            return Cmd("data", if (off) "off" else "on", "Data settings")
        if (has("airplane", "flight mode", "hawai"))
            return Cmd("airplane", "", "Airplane mode settings")

        // ── VOLUME ──
        if (has("volume", "awaaz", "sound", "aawaz")) {
            if (has("badha", "up", "zyada", "tez", "increase"))
                return Cmd("vol_up", "", "Volume badha raha hoon")
            if (has("kam", "down", "ghata", "decrease", "chota"))
                return Cmd("vol_down", "", "Volume kam")
            if (off || has("mute", "silent", "chup"))
                return Cmd("mute", "", "Silent kar diya")
            if (has("full", "max", "poora"))
                return Cmd("vol_max", "", "Full volume")
        }
        if (has("silent", "mute")) return Cmd("mute", "", "Silent mode")

        // ── BRIGHTNESS ──
        if (has("bright", "roshni", "screen light")) {
            if (has("badha", "up", "zyada", "tez"))
                return Cmd("bright_up", "", "Brightness badha di")
            if (has("kam", "down", "ghata"))
                return Cmd("bright_down", "", "Brightness kam ki")
        }

        // ── CALL ──
        Regex("(?:call|phone|ring|lagao|milao)\\s+(?:karo\\s+)?(.+?)" +
              "\\s*(?:ko|se)?\\s*(?:call|phone|lagao|milao)?$")
            .find(t)?.let {
                val who = it.groupValues[1].trim()
                    .removeSuffix("ko").trim()
                if (who.length > 1 && !who.contains("karo"))
                    return Cmd("call", who, "$who ko call laga raha hoon")
            }
        Regex("(.+?)\\s+ko\\s+(?:call|phone|ring)").find(t)?.let {
            return Cmd("call", it.groupValues[1].trim(),
                "Call laga raha hoon")
        }

        // ── SMS ──
        Regex("(.+?)\\s+ko\\s+(?:message|msg|sms)\\s*(?:bhejo|karo)?" +
              "\\s*(.*)").find(t)?.let {
            return Cmd("sms", it.groupValues[1].trim() + "|" +
                it.groupValues[2].trim(), "Message ready kar raha hoon")
        }

        // ── WHATSAPP message ──
        Regex("whatsapp\\s*(?:pe|par|se)?\\s*(.+?)\\s+ko\\s*(.*)")
            .find(t)?.let {
                return Cmd("whatsapp", it.groupValues[1].trim() + "|" +
                    it.groupValues[2].trim(), "WhatsApp khol raha hoon")
            }

        // ── SCREENSHOT ──
        if (has("screenshot", "screen shot", "screen capture"))
            return Cmd("screenshot", "", "Screenshot le raha hoon")

        // ── BATTERY ──
        if (has("battery", "charge", "power"))
            return Cmd("battery", "", "")

        // ── ALARM / TIMER ──
        Regex("(\\d{1,2})\\s*(?:baje|bje|:|\\s)?\\s*(\\d{0,2})\\s*" +
              "(?:ka)?\\s*alarm").find(t)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: 7
            val m = it.groupValues[2].toIntOrNull() ?: 0
            return Cmd("alarm", "$h:$m", "Alarm laga raha hoon")
        }
        if (has("alarm")) return Cmd("alarm_app", "", "Clock khol raha hoon")
        Regex("(\\d+)\\s*(?:minute|min|mint)\\s*(?:ka)?\\s*timer")
            .find(t)?.let {
                return Cmd("timer", it.groupValues[1],
                    "Timer laga raha hoon")
            }

        // ── TIME / DATE ──
        if (has("time kya", "kitne baje", "samay kya", "what time"))
            return Cmd("time", "", "")
        if (has("date kya", "aaj ki date", "aaj kaunsi", "what date"))
            return Cmd("date", "", "")

        // ── MUSIC ──
        Regex("(.+?)\\s*(?:gaana|song|music)\\s*(?:bajao|chalao|play)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim() + " song",
                    "Gaana chala raha hoon")
            }
        if (has("gaana bajao", "music chalao", "song play",
                "gana chalao"))
            return Cmd("open_app", "com.spotify.music", "Music chalu")

        // ── SCREEN CONTROL (Eyes se) ──
        // ⚠️ word() — poora shabd. "background"/"backup" ab
        //    back nahi dabayenge.
        if (word("back", "peeche", "pichla", "wapas") ||
            t == "wapas jao" || t == "peeche jao")
            return Cmd("back", "", "")
        if (word("home", "ghar") || t == "home jao" ||
            t == "home screen")
            return Cmd("home", "", "")
        if (word("recent", "recents") || t == "sare app" ||
            t == "app list")
            return Cmd("recents", "", "")
        if (has("notification", "notif") &&
            has("khol", "dikha", "kholo", "padho", "sunao")) {
            return if (has("padho", "sunao", "kya", "batao"))
                Cmd("read_notif", "", "") else Cmd("notif_panel", "", "")
        }
        if (has("scroll", "neeche karo", "upar karo", "sarkao")) {
            return Cmd("scroll",
                if (has("upar", "up")) "up" else "down", "")
        }
        if (has("screen pe kya", "screen padho", "kya likha",
                "ye padho", "isko padho", "read screen"))
            return Cmd("read_screen", "", "")

        // "X pe click karo" / "X dabao"
        Regex("(.+?)\\s*(?:pe|par|ko)?\\s*(?:click|tap|dabao|daba do|press)")
            .find(t)?.let {
                val w = it.groupValues[1].trim()
                if (w.length in 2..30 && !w.contains("screen"))
                    return Cmd("tap", w, "")
            }
        Regex("(?:click|tap|dabao|press)\\s+(?:karo\\s+)?(?:on\\s+)?(.+)")
            .find(t)?.let {
                val w = it.groupValues[1].trim()
                    .removeSuffix("pe").removeSuffix("par").trim()
                if (w.length in 2..30) return Cmd("tap", w, "")
            }

        // ── YAAD RAKHO ──
        Regex("(?:yaad rakho|yaad rakhna|remember|note karo)\\s+(?:ki\\s+)?(.+)")
            .find(t)?.let {
                return Cmd("remember", it.groupValues[1].trim(), "")
            }
        Regex("mera\\s+(.+?)\\s+(?:hai|he)\\s*(.*)").find(t)?.let {
            val k = it.groupValues[1].trim()
            val v = it.groupValues[2].trim()
            if (v.isNotBlank() && k.length < 25)
                return Cmd("remember", "$k = $v", "")
        }
        Regex("(?:mera|meri|mere)\\s+(.+?)\\s+kya\\s+(?:hai|he)")
            .find(t)?.let {
                return Cmd("recall", it.groupValues[1].trim(), "")
            }
        if (has("kya yaad hai", "sab yaad", "kya kya pata"))
            return Cmd("all_facts", "", "")

        // ── APP BAND KARO ──
        if (has("band karo", "close karo", "app band") &&
            !has("torch", "flash", "wifi", "bluetooth", "volume",
                 "data", "screen")) {
            for ((name, _) in APPS) if (t.contains(name))
                return Cmd("close_app", name, "")
            return Cmd("back", "", "")
        }

        // ── COPY / PASTE ──
        if (has("copy karo", "copy kar do")) {
            val what = t.substringAfter("copy").trim()
                .removePrefix("karo").removePrefix("kar do").trim()
            if (what.isNotBlank()) return Cmd("copy", what, "")
            return Cmd("copy_screen", "", "")
        }

        // ── SHARE ──
        Regex("(?:share|bhejo)\\s+(?:karo\\s+)?(.+)").find(t)?.let {
            val w = it.groupValues[1].trim()
            if (w.length > 2 && !w.contains(" ko "))
                return Cmd("share", w, "")
        }

        // ── SPEAK / TARJUMA ──
        Regex("(?:translate|tarjuma)\\s+(?:karo\\s+)?(.+)")
            .find(t)?.let { return Cmd("ai_ask", t, "") }

        // ── CALCULATOR ──
        Regex("^(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×÷])\\s*(\\d+(?:\\.\\d+)?)")
            .find(t)?.let {
                return Cmd("calc", t, "")
            }
        if (has("plus", "minus", "multiply", "divide", "jod", "ghata",
                "guna", "bhag") && Regex("\\d").containsMatchIn(t))
            return Cmd("calc", t, "")

        // ── DND / RINGER ──
        if (has("dnd", "disturb", "pareshan mat"))
            return Cmd("dnd", if (off) "off" else "on", "")
        if (has("ringtone", "ring mode", "normal mode", "vibrate"))
            return Cmd("ringer",
                if (has("vibrate")) "vibrate"
                else if (has("silent")) "silent" else "normal", "")

        // ── ROTATE / SCREEN ──
        if (has("rotate", "ghumao", "landscape", "portrait"))
            return Cmd("rotate", "", "")

        // ── UNINSTALL / APP INFO ──
        Regex("(.+?)\\s*(?:app)?\\s*(?:uninstall|hatao|delete)")
            .find(t)?.let {
                return Cmd("uninstall", it.groupValues[1].trim(), "")
            }

        // ── CHAT (baat-cheet) ──
        if (has("hello", "hi ieyeris", "hey", "namaste", "namaskar"))
            return Cmd("chat", "", "Namaste sir! Batayiye kya karna hai?")
        if (has("kaise ho", "kaisa hai", "how are you"))
            return Cmd("chat", "", "Main bilkul theek hoon sir. Aap batao?")
        if (has("kaun ho", "tum kaun", "who are you"))
            return Cmd("chat", "",
                "Main IEYE RIS hoon sir — aapka apna assistant. " +
                "RAVAN X HOSTING TEAM ne banaya hai.")
        if (has("thank", "shukriya", "dhanyawad"))
            return Cmd("chat", "", "Koi baat nahi sir.")
        if (has("bye", "alvida", "so ja", "band ho ja"))
            return Cmd("chat", "", "Theek hai sir, main yahin hoon.")

        return null   // local samajh nahi paya — AI se poochho
    }

    // ═══════════════════════════════════════════
    //   2. AI — jab local fail ho
    // ═══════════════════════════════════════════
    private const val SYS = """Tum IEYE RIS ho — Android assistant.
User Hinglish bolta hai. SIRF ek JSON ARRAY do, aur kuch nahi:
[{"action":"...","arg":"...","say":"..."}]

⚡ Ek se ZYADA kaam bole to array me SAB daalo, sahi tarteeb me.
   "torch on karo aur youtube kholo"
   -> [{"action":"torch_on","arg":"","say":"Torch on"},
       {"action":"open_app","arg":"youtube","say":"YouTube khol raha hoon"}]
   Ek hi kaam ho to bhi ARRAY do (ek item ka).

action aur uska arg:
open_app   arg=app naam        yt_search  arg=search text
google     arg=search text     call       arg=naam
sms        arg="naam|message"  whatsapp   arg="naam|message"
torch_on  torch_off  lock  screenshot  battery  time  date
wifi/bluetooth/dnd  arg=on|off
vol_up  vol_down  vol_max  mute  bright_up  bright_down
back  home  recents  read_notif  read_screen  scroll arg=up|down
tap        arg=button ka naam    type      arg=likhna hai
remember   arg="cheez = value"   recall    arg=kya
alarm      arg="7:30"            timer     arg=minute
calc       arg=sawaal            chat      arg=""
remind     arg=poori baat        remind_list  arg=""
remind_del arg=number ya "all"
mode       arg=study|coding|normal
auto_reply arg=on|off
music      arg=play|pause|next|prev    note   arg=likhna hai
notes      arg=""                      share  arg=text
uninstall  arg=app naam                app_info arg=app naam
battery_saver  data  airplane  location   (arg="")
translate  arg="bhasha|text"

zaroori:
- sms/whatsapp me arg me DONO daalo, pipe | se:
  "papa ko whatsapp karo aa raha hoon" -> arg="papa|aa raha hoon"
  poora message likho, aadha nahi.
- say = chhota Hinglish jawab (4-7 shabd).
- samajh na aaye to action "chat" do.
- screen pe kuch dikh raha ho to uska istemaal karo.

🧠 v4.0 — AKALMAND BANO, TOTA MAT BANO:
- User jitna bole utna karo, PAR uske hisaab se SOCHO bhi.
  "bahar jaa raha hoon" -> torch/data ki zarurat ho sakti hai,
  puchho mat, jo saaf hai wahi karo.
- Aadhi baat ho to ANDAZA lagao, mat poochho:
  "gaana laga do" -> yt_search arg="" nahi;
  user ki pasand memory me ho to wahi lagao.
- Jo pehle bataya hai wo YAAD rakho. "papa ko message karo"
  me number dobara mat maango — memory me hai to use karo.
- say me hamesha ye batao ki KYA kiya, sirf "ho gaya" mat likho.
- Kaam na ho sake to SAAF mana karo aur WAJAH batao. Jhooth
  mat bolo ki ho gaya.
- Ek hi baat me kai kaam ho to sahi TARTEEB socho —
  "wifi on karke youtube kholo" me wifi PEHLE."""

    fun aiMulti(ctx: Context, text: String): List<Cmd> {
        val keys = Keys(ctx)
        val mem = Memory(ctx)
        var t = strip(text)

        // IEYE RIS ko yaad dilao: pichhli baat + user ke facts
        val extra = StringBuilder()
        mem.factLine().takeIf { it.isNotBlank() }
            ?.let { extra.append(it).append("\n") }
        mem.context(2).takeIf { it.isNotBlank() }
            ?.let { extra.append("Pichhli baat:\n").append(it)
                .append("\n") }
        // 👁 SCREEN — user ne bola "puri screen dekh sake".
        //    Ab HAMESHA screen ka text jaata hai (jab Eyes on ho).
        //    Pehle sirf kuch khaas shabd pe jaata tha, isliye
        //    IEYE RIS ko pata hi nahi chalta tha ki samne kya hai.
        //
        //    Speed ka dar tha, par maine naap kar dekha:
        //    qwen3.8 pe 900 extra characters se sirf ~0.03s
        //    farak padta hai. Sauda faayde ka hai.
        if (Eyes.on()) {
            try {
                Eyes.live?.readScreen()?.take(900)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        extra.append("Screen pe abhi ye dikh raha hai:\n")
                            .append(it).append("\n")
                    }
            } catch (e: Exception) { }
        }

        // 📱 PHONE ME KAUNSE APP HAIN
        //
        // ⚠️ Bina iske AI andaza lagata tha. "Spotify kholo"
        //    bolta tha jab Spotify hai hi nahi, ya "Zomato"
        //    ko pehchanta hi nahi tha kyunki wo hardcoded
        //    list me nahi thi. Ab use asli list milti hai.
        // 🔍 v3.0 — phone ka poora naksha (pehli baar scan hua tha)
        try {
            PhoneScan.forAI(ctx).takeIf { it.isNotBlank() }
                ?.let { extra.append(it).append("\n") }
        } catch (e: Exception) {}

        try {
            AppRegistry.forAI(ctx, 55).takeIf { it.isNotBlank() }
                ?.let { extra.append(it).append("\n") }
        } catch (e: Exception) { }
        if (extra.isNotEmpty())
            t = extra.toString() + "\n---\nUser bola: " + t

        // ⚡ 26 Aug 2026: maine dono ko live test kiya —
        //    gpt-oss-20b  → 0.26s, 4/5 sahi   (sabse TEZ)
        //    qwen3.6-27b  → 0.39s, 5/5 sahi   (sabse PAKKA)
        //    Isliye pehle tez wala, wo na de to pakka wala.
        val gk = keys.groq()
        log("▸ \"" + strip(text).take(50) + "\"")
        if (gk.isBlank()) log("✘ Groq key hai hi nahi!")
        if (gk.isNotBlank()) {
            val url = "https://api.groq.com/openai/v1/chat/completions"
            // 🥇 26 Aug 2026 live benchmark (maine khud chalaya):
            //    qwen3.8-27b  → 0.19s, 8/8 sahi  ← SABSE TEZ + PAKKA
            //    gpt-oss-20b  → 0.27s, 7/8
            //    qwen3.6-27b  → 0.36s, 5/5
            //    gpt-oss-120b → 0.42s, 5/5
            for ((m, ex) in listOf(
                "qwen/qwen3.8-27b" to mapOf<String, Any>(
                    "reasoning_effort" to "none"),
                "openai/gpt-oss-20b" to mapOf<String, Any>(
                    "reasoning_effort" to "low"),
                "qwen/qwen3.6-27b" to mapOf<String, Any>(
                    "reasoning_effort" to "none"),
                "openai/gpt-oss-120b" to mapOf<String, Any>(
                    "reasoning_effort" to "low")
            )) {
                val t0 = System.currentTimeMillis()
                lastError = ""
                val r = askOpenAI(url, gk, m, t, ex)
                val ms = System.currentTimeMillis() - t0
                val nm = m.substringAfter("/").take(14)
                if (r.isNotEmpty()) {
                    log("✓ $nm ${ms}ms → " +
                        r.joinToString("+") { it.action })
                    // v2.2 — "chat" ko asli sawaal chahiye
                    return r.map { it.copy(raw = text) }
                }
                log("✗ $nm ${ms}ms " +
                    (if (lastError.isBlank()) "khali jawab"
                     else lastError))
            }
        }
        // Cloudflare backup
        val acc = keys.cfAcc()
        val tok = keys.cfTok()
        if (acc.isNotBlank() && tok.isNotBlank()) {
            val t0 = System.currentTimeMillis()
            lastError = ""
            val r = askCF(acc, tok, t)
            val ms = System.currentTimeMillis() - t0
            if (r.isNotEmpty()) {
                log("✓ cloudflare ${ms}ms → " +
                    r.joinToString("+") { it.action })
                return r.map { it.copy(raw = text) }
            }
            log("✗ cloudflare ${ms}ms " +
                (if (lastError.isBlank()) "khali" else lastError))
        }
        log("✘ SAB FAIL — koi AI jawab nahi de payi")
        return emptyList()
    }

    /** Purana single-cmd rasta — kahin aur use ho to chalta rahe */
    fun ai(ctx: Context, text: String): Cmd? =
        aiMulti(ctx, text).firstOrNull()

    /**
     * ═══ v2.2 — ASLI BAAT-CHEET (JSON nahi, seedha jawab) ═══
     *
     * Ye aiMulti() se BILKUL alag hai:
     *   aiMulti() -> JSON command deta hai ("app kholo")
     *   talk()    -> seedhi baat karta hai ("photosynthesis kya hai")
     *
     * ⚠️ Pehle "chat" action sirf `c.say` lauta deta tha — yaani
     *    4-7 shabd ka chhota sa jumla jo aiMulti ne banaya tha.
     *    Isliye Study Mode ka koi matlab hi nahi tha: aap sawaal
     *    poochte the, jawab "Ji sir?" jaisa aata tha.
     *
     *    Ab asli sawaal AI ko poora bheja jata hai, mode ki
     *    hidayat ke saath. Study Mode me teacher jaisa, Coding
     *    Mode me guru jaisa jawab milta hai.
     *
     * @param sysExtra mode ki khaas hidayat (Modes.prompt se)
     */
    fun talk(ctx: Context, question: String,
             sysExtra: String = ""): String {
        val keys = Keys(ctx)
        val mem = Memory(ctx)

        val sys = StringBuilder()
        sys.append("Tum IEYE RIS ho — Ujjawal X Official ka banaya ")
        sys.append("Android assistant. RAVAN X HOSTING TEAM.\n")
        sys.append("User Hinglish (Hindi Latin akshar me) bolta hai — ")
        sys.append("tum bhi wahi bhasha bolo, seedhi aur saaf.\n")
        sys.append("Jhoot mat bolo. Na pata ho to saaf keh do ")
        sys.append("\"mujhe nahi pata\".\n")
        if (sysExtra.isNotBlank()) sys.append(sysExtra)

        val u = StringBuilder()
        mem.factLine().takeIf { it.isNotBlank() }
            ?.let { u.append(it).append("\n\n") }
        mem.context(8).takeIf { it.isNotBlank() }
            ?.let { u.append("Ab tak ki baat:\n").append(it)
                     .append("\n\n") }
        u.append("SAWAAL: ").append(question)

        // Wahi 4-model ladder jo aiMulti use karta hai
        val out = try {
            askText(sys.toString(), u.toString(), keys)
        } catch (e: Exception) { "" }

        return out.ifBlank {
            "Sir, abhi jawab nahi mil paaya. " +
            (if (lastError.isNotBlank()) "($lastError) " else "") +
            "Dobara poochhiye."
        }
    }

    /** Seedha text jawab — 4 model ki ladder */
    private fun askText(sys: String, user: String,
                        keys: Keys): String {
        val g = keys.groq()
        if (g.isNotBlank()) {
            for (m in listOf("qwen/qwen3.8-27b",
                             "openai/gpt-oss-20b")) {
                val r = try {
                    plainOpenAI(
                        "https://api.groq.com/openai/v1/chat/completions",
                        g, m, sys, user)
                } catch (e: Exception) { "" }
                if (r.isNotBlank()) return r
            }
        }
        // Cloudflare backup
        val acc = keys.cfAcc(); val tok = keys.cfTok()
        if (acc.isNotBlank() && tok.isNotBlank()) {
            val r = try { plainCF(acc, tok, sys, user) }
                    catch (e: Exception) { "" }
            if (r.isNotBlank()) return r
        }
        return ""
    }

    private fun plainOpenAI(url: String, key: String, model: String,
                            sys: String, user: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", sys) })
                put(JSONObject().apply {
                    put("role", "user"); put("content", user) })
            })
            // ⚠️ Study/Coding jawab lambe hote hain — 400 kam pad
            //    raha tha aur jawab beech me kat jata tha
            put("max_tokens", 900)
            put("temperature", 0.4)
            if (model.startsWith("qwen")) put("reasoning_effort", "none")
            if (model.contains("gpt-oss")) put("reasoning_effort", "low")
        }
        val out = post(url, body.toString(),
            mapOf("Authorization" to "Bearer $key"))
        return try {
            JSONObject(out).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message")
                .getString("content").trim()
        } catch (e: Exception) { "" }
    }

    private fun plainCF(acc: String, tok: String,
                        sys: String, user: String): String {
        val url = "https://api.cloudflare.com/client/v4/accounts/" +
                  "$acc/ai/run/@cf/meta/llama-3.1-8b-instruct"
        val body = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", sys) })
                put(JSONObject().apply {
                    put("role", "user"); put("content", user) })
            })
            put("max_tokens", 900)
        }
        val out = post(url, body.toString(),
            mapOf("Authorization" to "Bearer $tok"))
        return try {
            JSONObject(out).getJSONObject("result")
                .getString("response").trim()
        } catch (e: Exception) { "" }
    }

    private fun askOpenAI(
        url: String, key: String, model: String, text: String,
        extra: Map<String, Any> = emptyMap()
    ): List<Cmd> = try {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", SYS)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", text)
                })
            })
            put("max_tokens", 400)
            put("temperature", 0.05)
            extra.forEach { (k, v) -> put(k, v) }
        }
        // Network hichki pe ek baar dobara — mobile data pe
        // pehla connect aksar gir jaata hai
        var out = ""
        var tries = 0
        while (tries < 2) {
            tries++
            try {
                out = post(url, body.toString(),
                    mapOf("Authorization" to "Bearer $key"))
                break
            } catch (e: Exception) {
                val m = (e.message ?: "").lowercase()
                // key galat / limit khatam ho to dobara try
                // karne ka koi fayda nahi — turant agla model
                if (tries >= 2 || m.contains("key") ||
                    m.contains("limit") || m.contains("mana")) throw e
                log("  ↻ dobara try")
                Thread.sleep(400)
            }
        }
        val msg = JSONObject(out).getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message")
        parse(msg.optString("content", ""))
    } catch (e: Exception) { emptyList() }

    private fun askCF(
        acc: String, tok: String, text: String
    ): List<Cmd> = try {
        val url = "https://api.cloudflare.com/client/v4/accounts/" +
            "$acc/ai/run/@cf/meta/llama-3.3-70b-instruct-fp8-fast"
        val body = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", SYS)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", text)
                })
            })
            put("max_tokens", 400)
        }
        val out = post(url, body.toString(),
            mapOf("Authorization" to "Bearer $tok"))
        val r = JSONObject(out).getJSONObject("result")
        var txt = r.optString("response", "")
        if (txt.isBlank()) {
            txt = r.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content", "") ?: ""
        }
        parse(txt)
    } catch (e: Exception) { emptyList() }

    /**
     * AI ke jawab se command nikalo.
     *
     * AI kabhi array deta hai [{...},{...}], kabhi akela {...},
     * aur aksar aage-peeche bakwas bhi likh deta hai. Dono
     * sambhal lo.
     */
    private fun parse(raw: String): List<Cmd> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<Cmd>()

        // pehle ARRAY dhoondho
        val la = raw.indexOf('[')
        val lb = raw.lastIndexOf(']')
        if (la >= 0 && lb > la) {
            try {
                val arr = JSONArray(raw.substring(la, lb + 1))
                for (i in 0 until arr.length()) {
                    one(arr.optJSONObject(i))?.let { out.add(it) }
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) { }
        }

        // warna akela object
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a >= 0 && b > a) {
            try {
                one(JSONObject(raw.substring(a, b + 1)))
                    ?.let { out.add(it) }
            } catch (e: Exception) { }
        }
        return out
    }

    private fun one(j: JSONObject?): Cmd? {
        if (j == null) return null
        var act = j.optString("action", "").trim()
        if (act.isBlank()) act = j.optString("cmd", "").trim()
        if (act.isBlank()) return null

        // AI kabhi-kabhi apne hisaab se naam bana deta hai —
        // unhe hamare naam pe le aao
        val fix = mapOf(
            "wifi_on" to "wifi", "wifi_off" to "wifi",
            "bluetooth_on" to "bluetooth", "bluetooth_off" to "bluetooth",
            "dnd_on" to "dnd", "dnd_off" to "dnd",
            "flashlight_on" to "torch_on", "flashlight_off" to "torch_off",
            "torch" to "torch_on", "say" to "chat", "speak" to "chat",
            "reply" to "chat", "answer" to "chat", "none" to "chat",
            "open" to "open_app", "app" to "open_app",
            "youtube" to "yt_search", "search" to "google",
            "volume_up" to "vol_up", "volume_down" to "vol_down",
            "volume_max" to "vol_max", "brightness_up" to "bright_up",
            "brightness_down" to "bright_down"
        )
        var arg = j.optString("arg", "").trim()
        if (arg.isBlank()) arg = j.optString("value", "").trim()

        // "wifi_off" jaisa aaya to arg me off bhar do
        if (arg.isBlank()) {
            if (act.endsWith("_off")) arg = "off"
            else if (act.endsWith("_on") && fix.containsKey(act))
                arg = "on"
        }
        act = fix[act] ?: act

        return Cmd(act, arg, j.optString("say", "").trim(), true)
    }

    // ═══════════════════════════════════════════
    //   📋 LOG — kya hua, kyun fail hua
    //
    //   ⚠️ Sabse badi dikkat yahi thi: API fail hoti thi to
    //      catch me chali jaati thi aur user ko sirf "samajh
    //      nahi aaya sir" dikhta tha. Asli wajah kabhi pata
    //      nahi chalti thi. Ab sab yahan likha jaata hai —
    //      Settings me "AI Test" se dekh sakte ho.
    // ═══════════════════════════════════════════
    private val logs = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun log(s: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss",
            java.util.Locale.US).format(java.util.Date())
        logs.add("[$t] $s")
        while (logs.size > 60) logs.poll()
    }

    fun logs(): String = logs.joinToString("\n")

    fun clearLogs() = logs.clear()

    var lastError: String = ""
        private set

    /**
     * Agent ke liye — seedha API call, JSON parse kiye bina.
     * Error hone pe khali string, exception nahi.
     */
    fun callRaw(url: String, key: String, body: String): String = try {
        val out = post(url, body,
            mapOf("Authorization" to "Bearer $key"))
        JSONObject(out).getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message")
            .optString("content", "")
    } catch (e: Exception) {
        log("  ✗ callRaw: " + (e.message ?: "").take(60))
        ""
    }

    private fun post(
        url: String, body: String, headers: Map<String, String>
    ): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // ⚠️⚠️ YE ASLI BUG THA ⚠️⚠️
            //    connectTimeout 3 second tha. WiFi pe theek hai,
            //    par mobile data (4G) pe connect + TLS handshake
            //    hi 1-3 second le leta hai. Thoda network slow
            //    hua ki API chup-chaap fail — user ko sirf
            //    "samajh nahi aaya" dikhta tha.
            //
            //    Ab 15s connect, 25s read. Der lagegi to lagegi,
            //    par kaam to hoga.
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
            doInput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode

            // ⚠️ Java me inputStream 4xx/5xx pe EXCEPTION phenkta
            //    hai. Asli error errorStream me hota hai. Pehle
            //    wo padha hi nahi jaata tha — isliye "401 galat
            //    key" jaisi saaf baat bhi chhup jaati thi.
            if (code !in 200..299) {
                val err = try {
                    c.errorStream?.bufferedReader()
                        ?.use { it.readText() } ?: ""
                } catch (e: Exception) { "" }

                val why = when (code) {
                    401 -> "API key galat ya expire ho gayi"
                    403 -> "API ne mana kiya (key ya region block)"
                    429 -> "Ek minute ki limit khatam — 60 sec ruko"
                    in 500..599 -> "Server down hai ($code)"
                    else -> "HTTP $code"
                }
                lastError = why
                log("✘ $why  ${err.take(160)}")
                throw java.io.IOException(why)
            }

            return c.inputStream.bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } finally {
            try { c.disconnect() } catch (e: Exception) { }
        }
    }

    // ═══════════════════════════════════════════
    //   🩺 KHUD JAANCH — API chal rahi hai ya nahi
    //
    //   User ko shak tha "API use hi nahi ho rahi". Ab wo
    //   khud dekh sakta hai — Settings me button hai.
    // ═══════════════════════════════════════════
    fun diagnose(ctx: Context): String {
        val k = Keys(ctx)
        val sb = StringBuilder()
        sb.append("🩺 IEYE RIS JAANCH\n")
        sb.append("─────────────────────\n\n")

        // 1. Internet
        val net = try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val n = cm.activeNetwork
            val cap = cm.getNetworkCapabilities(n)
            when {
                cap == null -> "❌ NAHI"
                cap.hasTransport(android.net.NetworkCapabilities
                    .TRANSPORT_WIFI) -> "✅ WiFi"
                cap.hasTransport(android.net.NetworkCapabilities
                    .TRANSPORT_CELLULAR) -> "✅ Mobile data"
                else -> "✅ Hai"
            }
        } catch (e: Exception) { "❓ pata nahi" }
        sb.append("Internet   : ").append(net).append("\n")

        // 2. Key hai ya nahi
        val gk = k.groq()
        sb.append("Groq key   : ")
        if (gk.isBlank()) sb.append("❌ NAHI HAI\n")
        // 🔒 v4.5 — locked me key ka TUKDA BHI nahi dikhate.
        //    "gsk_xyz…" dekh kar bhi banda kaafi kuch samajh
        //    leta hai. User ne kaha "na hi dekh sakta hai".
        else if (Owner.unlocked())
            sb.append("✅ hai (").append(gk.length)
                .append(" akshar, ").append(gk.take(7)).append("…)\n")
        else sb.append("✅ hai (").append(gk.length)
            .append(" akshar) 🔒\n")

        sb.append("Eyes (👁)  : ")
            .append(if (Eyes.on()) "✅ ON" else "❌ OFF — screen "
                + "nahi dekh sakta")
            .append("\n\n")

        if (gk.isBlank()) {
            sb.append("⚠️ Key hi nahi hai. Settings me daalo.\n")
            return sb.toString()
        }

        // 3. Har model ko sach me poochho
        sb.append("MODEL TEST — \"torch on karo aur\n")
        sb.append("             youtube kholo\"\n\n")
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val q = "torch on karo aur youtube kholo"
        var koiChala = false

        for ((m, ex) in listOf(
            "qwen/qwen3.8-27b" to mapOf<String, Any>(
                "reasoning_effort" to "none"),
            "openai/gpt-oss-20b" to mapOf<String, Any>(
                "reasoning_effort" to "low"),
            "qwen/qwen3.6-27b" to mapOf<String, Any>(
                "reasoning_effort" to "none")
        )) {
            val t0 = System.currentTimeMillis()
            lastError = ""
            val r = askOpenAI(url, gk, m, q, ex)
            val ms = System.currentTimeMillis() - t0
            val short = m.substringAfter("/").take(16)
            if (r.isNotEmpty()) {
                koiChala = true
                sb.append("✅ ").append(short).append("\n")
                sb.append("   ").append(ms).append("ms · ")
                    .append(r.size).append(" kaam mile\n")
                sb.append("   ")
                    .append(r.joinToString(" + ") { it.action })
                    .append("\n\n")
            } else {
                sb.append("❌ ").append(short).append("\n")
                sb.append("   ").append(ms).append("ms · ")
                    .append(if (lastError.isBlank()) "jawab nahi aaya"
                            else lastError).append("\n\n")
            }
        }

        // 4. Cloudflare backup
        val acc = k.cfAcc(); val ct = k.cfTok()
        if (acc.isNotBlank() && ct.isNotBlank()) {
            val t0 = System.currentTimeMillis()
            lastError = ""
            val r = askCF(acc, ct, q)
            val ms = System.currentTimeMillis() - t0
            sb.append(if (r.isNotEmpty()) "✅" else "❌")
                .append(" Cloudflare (backup)\n   ")
                .append(ms).append("ms")
                .append(if (r.isEmpty() && lastError.isNotBlank())
                    " · " + lastError else "").append("\n\n")
            if (r.isNotEmpty()) koiChala = true
        }

        sb.append("─────────────────────\n")
        sb.append(if (koiChala) "✅ AI CHAL RAHI HAI"
                  else "❌ KOI AI NAHI CHALI")
        sb.append("\n\n")

        if (logs.isNotEmpty()) {
            sb.append("PICHHLE KAAM:\n")
            sb.append(logs.toList().takeLast(18).joinToString("\n"))
        }
        return sb.toString()
    }
}
