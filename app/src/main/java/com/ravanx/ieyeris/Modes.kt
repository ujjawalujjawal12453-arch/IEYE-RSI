package com.ravanx.ieyeris

import android.content.Context

/**
 * 🎓 MODES — spec section 27, 28, 29
 *
 * Teen khaas mizaaj:
 *   STUDY  — teacher ban jata hai (samjhao, quiz, revision)
 *   CODING — code samjhao, error batao, review karo
 *   NORMAL — aam IEYE RIS
 *
 * Aur TRANSLATION — mode nahi, hukum hai (kabhi bhi chalega).
 *
 * ═══ YE KYA HAI, SAAF BAAT ═══
 *
 * Mode ka matlab sirf itna hai ki AI ko ALAG HIDAYAT jaati hai.
 * Koi alag AI model nahi, koi alag app nahi. Wahi Groq/Gemini
 * hai — bas usse kaha jata hai "ab tum teacher ho".
 *
 * Maine ye jaanbujh kar aise banaya:
 *   • Nakli quiz screen banane se kuch fayda nahi tha (spec me
 *     saaf likha hai "Do NOT create fake UI")
 *   • Asli faayda hidayat badalne se hi aata hai — jawab poori
 *     tarah badal jate hain
 *
 * Mode phone me save rehta hai — app band karke kholo to bhi
 * wahi mode chalta rahega jab tak "normal mode" na bolo.
 */
object Modes {

    const val NORMAL = "normal"
    const val STUDY = "study"
    const val CODING = "coding"

    private const val KEY = "mode"

    fun get(c: Context): String = try {
        Keys(c).get(KEY, NORMAL).ifBlank { NORMAL }
    } catch (e: Exception) { NORMAL }

    fun set(c: Context, m: String) {
        try { Keys(c).set(KEY, m) } catch (e: Exception) {}
        Brain.log("🎓 mode = $m")
    }

    fun label(m: String) = when (m) {
        STUDY -> "🎓 Study Mode"
        CODING -> "💻 Coding Mode"
        else -> "⚡ Normal"
    }

    /**
     * Bole hue shabd se mode pehchano.
     * @return naya mode, ya null agar mode ki baat nahi thi
     */
    fun detect(t: String): String? {
        fun p(vararg w: String) = w.any { x ->
            Regex("(^|\\s)" + Regex.escape(x) + "($|\\s)")
                .containsMatchIn(t)
        }
        // ⚠️ TARTEEB ZAROORI HAI — "band" wala SABSE PEHLE.
        //
        //    Test ne ye pakda: "study mode band" ko pehle STUDY
        //    samajh liya jata tha, kyunki usme "study mode" bhi
        //    hai. User mode BAND karna chah raha tha par ULTA
        //    chalu ho jata tha.
        if (p("normal mode", "normal mode on", "mode band karo",
              "wapas normal", "study mode band", "coding mode band",
              "study mode band karo", "coding mode band karo",
              "mode hatao", "normal ho jao", "mode off",
              "study mode off", "coding mode off"))
            return NORMAL
        if (p("study mode", "padhai mode", "teacher mode",
              "study mode on", "padhai shuru", "padhao mujhe",
              "teacher ban jao", "study mode chalu karo"))
            return STUDY
        if (p("coding mode", "code mode", "programming mode",
              "coding mode on", "developer mode",
              "coding mode chalu karo", "coder ban jao"))
            return CODING
        return null
    }

    /**
     * AI ko dene wali khaas hidayat.
     *
     * ⚠️ Ye Brain ke asli system prompt ke SAATH jodi jati hai,
     *    uski jagah nahi lagti. Warna IEYE RIS apna naam aur
     *    kaam bhool jata.
     */
    fun prompt(m: String): String = when (m) {
        STUDY -> """
            |
            |🎓 ABHI TUM TEACHER HO (Study Mode):
            |• Har cheez AASAN bhasha me samjhao, jaise 10 saal ke
            |  bachche ko samjha rahe ho
            |• Misaal (example) zaroor do — bina example ke mat chhodo
            |• Lamba jawab mat do — 4-6 line kaafi hai
            |• Samjhane ke baad EK chhota sawaal poochho, taaki
            |  pata chale samajh aaya ya nahi
            |• "quiz lo" / "test lo" bole to 5 sawaal poochho,
            |  ek-ek karke, jawab ke baad sahi-galat batao
            |• "revision" bole to aaj ki baatein 5 point me
            |• Galat jawab pe daanto mat — dobara aasan tarike se
            |  samjhao
            |• Mushkil shabd ka Hindi matlab saath likho
            """.trimMargin()

        CODING -> """
            |
            |💻 ABHI TUM CODING GURU HO (Coding Mode):
            |• Code ka matlab LINE BY LINE samjhao
            |• Error mile to: (1) kya galat hai (2) kyun (3) fix
            |• Fix karke poora sahi code do, sirf batao mat
            |• Kaunsi bhasha hai wo khud pehchano
            |• Chhota chalne wala example zaroor do
            |• Naam, spacing, tarika — sudhaar ke liye 1-2 tip
            |• ⚠️ Phone pe koi anjaan code CHALAO MAT. Sirf
            |  samjhao aur likh kar do.
            |• Jawab me code hamesha ``` ke andar likho
            """.trimMargin()

        else -> ""
    }

    // ═══════════════════════════════════════════
    //   🌐 TRANSLATION — spec section 29
    // ═══════════════════════════════════════════

    /**
     * "X ko english me translate karo" jaisi baat pakdo.
     *
     * @return Pair(kis bhasha me, kya translate karna hai)
     *         null = translation ki baat nahi thi
     */
    fun translation(raw: String): Pair<String, String>? {
        val t = raw.trim()
        val low = t.lowercase()

        // "<text> ko <bhasha> me translate/anuvaad karo"
        Regex("^(.{2,300}?)\\s+(?:ko|ka)\\s+" +
              "(english|angrezi|hindi|hindee|marathi|gujarati|" +
              "tamil|telugu|kannada|bengali|punjabi|urdu|" +
              "spanish|french|german|japanese|chinese|arabic)" +
              "\\s*(?:me|mein|m|main)?\\s*" +
              "(?:translate|anuvaad|tarjuma|convert|badlo|karo|kar do)",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[2]),
                        cut(t, it.groupValues[1]))
        }

        // "translate karo <text> in english"
        Regex("^(?:translate|anuvaad|tarjuma)\\s+(?:karo|kar do)?\\s*" +
              "(.{2,300}?)\\s+(?:in|to|me|mein)\\s+" +
              "(english|angrezi|hindi|marathi|gujarati|tamil|telugu|" +
              "kannada|bengali|punjabi|urdu|spanish|french|german|" +
              "japanese|chinese|arabic)$",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[2]),
                        cut(t, it.groupValues[1]))
        }

        // "<bhasha> me kya kehte hain <text>"
        Regex("^(english|angrezi|hindi|marathi|gujarati|tamil|telugu|" +
              "kannada|bengali|punjabi|urdu|spanish|french|german|" +
              "japanese|chinese|arabic)\\s*(?:me|mein|m)\\s*" +
              "(?:kya|kaise)\\s*(?:kehte|bolte|likhte)\\s*" +
              "(?:hain|hai|h)\\s*(.{2,300})$",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[1]),
                        cut(t, it.groupValues[2]))
        }

        // sirf "translate karo" — pichhli baat translate karni hai
        if (Regex("^(?:isko|ise|ye|yeh)?\\s*" +
                  "(?:english|angrezi|hindi)\\s*(?:me|mein)?\\s*" +
                  "(?:translate|anuvaad|tarjuma|bolo|likho|karo)" +
                  "\\s*(?:karo|kar do)?$")
                .containsMatchIn(low)) {
            val l = if (low.contains("hindi")) "Hindi" else "English"
            return Pair(l, "")     // khali = pichhla message
        }
        return null
    }

    /**
     * Asli (bade-chhote akshar wala) text kaato.
     *
     * ⚠️ Regex lowercase pe chalta hai, par translate ASLI text
     *    ka hona chahiye — warna naam aur bade akshar bigad jate
     *    hain ("Ujjawal" -> "ujjawal").
     */
    private fun cut(orig: String, lowPart: String): String {
        val i = orig.lowercase().indexOf(lowPart.trim())
        return if (i >= 0) orig.substring(i, i + lowPart.trim().length)
               else lowPart.trim()
    }

    private fun lang(s: String) = when (s.lowercase()) {
        "angrezi", "english" -> "English"
        "hindi", "hindee" -> "Hindi"
        else -> s.replaceFirstChar { it.uppercase() }
    }

    /** Translation ke liye AI ko bhejne wali baat */
    fun translatePrompt(to: String, text: String) = """
        |Neeche diye text ka $to me translate karo.
        |
        |Sirf translation likho — na koi safai, na "yahan hai", na
        |quote. Bas seedha jawab.
        |
        |Agar $to Hindi hai to Devanagari me likho, saath me ek line
        |me Roman (Latin) me bhi likho taaki bolne me aasani ho.
        |
        |TEXT:
        |$text
        """.trimMargin()
}
