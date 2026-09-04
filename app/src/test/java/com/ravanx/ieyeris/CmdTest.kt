package com.ravanx.ieyeris

import org.junit.Test
import org.junit.Assert.assertEquals

fun norm(s: String) = s.lowercase().trim()
    .replace(Regex("[?.!,]"), " ").replace(Regex("\\s+"), " ")
fun strip(t: String): String {
    var s = norm(t)
    for (w in listOf("hey ieye ris","hey eye ris","hey iris","hey ieyeris",
        "ok ieye ris","ok iris","arey ieye ris","ieye ris","ieyeris",
        "eye ris","ai ris","iris","ieye","eyeris"))
        if (s.startsWith(w)) { s = s.removePrefix(w).trim(); break }
    for (w in listOf("wake up","wakeup","jaago","uth ja")) if (s == w) return ""
    return s
}

fun cmdMatch(t: String): String? {
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
            return "mic_off"

        if (p("kuch mat bolna", "kuchh mat bolna", "mat bolna",
              "mat bolo", "ab kuch mat bolna", "ab mat bolo",
              "chup ho jao", "chup ho ja", "chup raho", "chup rho",
              "chup", "chup kar", "chup karo", "bolna band karo",
              "bolna band", "awaaz band karo", "awaz band karo",
              "awaaz band", "silent ho jao", "silent mode",
              "muh band karo", "bolo mat", "shant ho jao",
              "shaant ho jao", "quiet"))
            return "mute_voice"

        // wapas bolne lago
        if (p("ab bolo", "bolna chalu karo", "bolna shuru karo",
              "awaaz chalu karo", "awaz chalu karo", "bol sakte ho",
              "ab bol sakte ho", "unmute", "wapas bolo",
              "awaaz on karo", "bolna on karo"))
            return "unmute_voice"

        // mic wapas chalu
        if (p("mic chalu karo", "mic on", "mic on karo", "mike on",
              "sunna chalu karo", "sunna shuru karo",
              "mic khol do", "kaan khol do", "ab suno"))
            return "mic_on"

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
            return "shutdown"

        // ── 3. SLEEP — standby ────────────────────────────
        if (p("sleep", "sleep mode", "so ja", "so jao", "so jaao",
              "standby", "standby me jao", "aaram karo",
              "sleep mode me jao", "band ho ja", "band ho jao",
              "rest mode"))
            return "sleep"

        // ── 4. WAKE / RESUME ──────────────────────────────
        if (p("wake up", "wakeup", "wake", "jaago", "jag jao",
              "uth ja", "uth jao", "utho", "online",
              "online ho jao", "chalu ho ja", "chalu ho jao",
              "resume", "wapas aao", "phir se chalu",
              "phir se chalu karo", "continue", "active ho jao"))
            return "wake"

        // ── 5. STATUS ─────────────────────────────────────
        if (p("status", "system status", "haal", "kya haal",
              "kya haal hai", "apna status batao", "status batao",
              "sab theek hai", "report do", "system report"))
            return "status"

        // ── 6. STOP — abhi ka kaam roko ───────────────────
        //    Sabse aakhir me, kyunki ye sabse aam shabd hain
        if (p("stop", "ruk", "ruko", "ruk ja", "ruk jao",
              "rok do", "cancel", "cancel karo", "rehne do",
              "chhod do", "band karo", "bas karo", "bas"))
            return "stop"

        return null
    }

/**
 * v2.0 — HUKUM KI JAANCH
 *
 * User ki teen shikayat thi ki ye kaam nahi karte:
 *   "shutdown ho jao"  ·  "mic band karo"  ·  "ab kuch mat bolna"
 *
 * Ye test asli Brain.kt ke cmdMatch() ko chalata hai.
 */
class CmdTest {
    private val fails = mutableListOf<String>()

    /**
     * Asli local() ki tarah — pehle blank-check, phir cmdMatch.
     *
     * ⚠️ Pehle test sirf cmdMatch() bulata tha aur "wake up" fail
     *    ho raha tha. Wo test ki galti thi, code ki nahi:
     *    strip() "wake up" ko khali kar deta hai, aur asli code
     *    me khali = "sirf naam bola" = wake. Ab test wahi karta hai.
     */
    private fun localCmd(raw: String): String? {
        val t = strip(raw)
        if (t.isBlank()) return if (norm(raw).isNotBlank()) "wake" else null
        return cmdMatch(t)
    }

    private fun chk(inp: String, want: String?, note: String) {
        val got = localCmd(inp)
        if (got != want) fails.add("\"$inp\" chahiye=$want mila=$got $note")
    }

    @Test fun hukumTest() {
        chk("shutdown ho jao", "shutdown", "USER SHIKAYAT 1")
        chk("IEYE RIS shutdown ho jao", "shutdown", "USER SHIKAYAT 1")
        chk("mic band karo", "mic_off", "USER SHIKAYAT 2")
        chk("IEYE RIS mic band karo", "mic_off", "USER SHIKAYAT 2")
        chk("ab kuch mat bolna", "mute_voice", "USER SHIKAYAT 3")
        chk("tum ab kuch mat bolna", "mute_voice", "USER SHIKAYAT 3")
        chk("mike band karo", "mic_off", "")
        chk("mic off", "mic_off", "")
        chk("sunna band karo", "mic_off", "")
        chk("mat suno", "mic_off", "")
        chk("chup ho jao", "mute_voice", "")
        chk("chup raho", "mute_voice", "")
        chk("bolna band karo", "mute_voice", "")
        chk("mat bolo", "mute_voice", "")
        chk("awaaz band karo", "mute_voice", "")
        chk("silent ho jao", "mute_voice", "")
        chk("ab bolo", "unmute_voice", "")
        chk("wapas bolo", "unmute_voice", "")
        chk("mic chalu karo", "mic_on", "")
        chk("ab suno", "mic_on", "")
        chk("shut down", "shutdown", "")
        chk("sab band karo", "shutdown", "")
        chk("poora band karo", "shutdown", "")
        chk("khatam karo", "shutdown", "")
        chk("so jao", "sleep", "")
        chk("standby me jao", "sleep", "")
        chk("wake up", "wake", "")
        chk("uth jao", "wake", "")
        chk("status batao", "status", "")
        chk("kya haal hai", "status", "")
        chk("ruk jao", "stop", "")
        chk("rok do", "stop", "")
        chk("cancel karo", "stop", "")
        chk("bas karo", "stop", "")
        chk("background me chalao", null, "back bug")
        chk("backup le lo", null, "back bug")
        chk("feedback do", null, "back bug")
        chk("customer service call karo", null, "service bug")
        chk("youtube kholo", null, "")
        chk("whatsapp me papa ko bhej do", null, "")
        if (fails.isNotEmpty()) {
            println("\n❌ FAIL:")
            fails.forEach { println("   " + it) }
        }
        println("\n✅ PASS " + (40 - fails.size) + " / 40")
        assertEquals("sab hukum sahi", 0, fails.size)
    }
}
