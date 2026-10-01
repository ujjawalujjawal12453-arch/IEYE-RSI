package com.ravanx.ieyeris

import android.content.Context
import org.json.JSONObject

/**
 * 📖 OFFLINE — bina internet ke madad
 *
 * v4.2 — pehle har cheez ke liye AI chahiye tha. Net na ho
 * to app aadhi bekaar. Ab teen cheezein bina internet ke:
 *
 *   1. Saari command ki list   ("command batao")
 *   2. Dikkat ka hal           ("help")
 *   3. Shabdkosh               ("water hindi me")
 *
 * ⚠️ Shabdkosh AI se PEHLE dekha jata hai. Agar shabd yahan
 *    mil gaya to turant jawab — na net chahiye, na 2 second
 *    ka intezaar, na Groq ki limit kharch.
 */
object Offline {

    private var dict: JSONObject? = null
    private var rev: JSONObject? = null

    private fun load(c: Context) {
        if (dict != null) return
        try {
            val txt = c.assets.open("docs/dict.json")
                .bufferedReader().use { it.readText() }
            val o = JSONObject(txt)
            dict = o.optJSONObject("en_hi")
            rev = o.optJSONObject("hi_en")
            Brain.log("📖 shabdkosh: ${o.optInt("count")} shabd")
        } catch (e: Exception) {
            Brain.log("📖 dict fail: ${e.message}")
        }
    }

    /** Asset file padho */
    private fun read(c: Context, path: String): String = try {
        c.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: Exception) { "" }

    /** 📋 Saari command */
    fun commands(c: Context): String =
        read(c, "docs/commands.txt").ifBlank {
            "Command list nahi mili sir."
        }

    /** 🆘 Dikkat ka hal */
    fun help(c: Context): String =
        read(c, "docs/help.txt").ifBlank {
            "Help file nahi mili sir."
        }

    /**
     * 🌐 Offline translate.
     *
     * @return jawab, ya null agar shabd yahan nahi hai
     *         (tab AI se poochha jayega)
     *
     * ⚠️ Sirf EK shabd pe chalta hai. Poora vaakya AI hi
     *    kar sakta hai — jhooth nahi bolna chahiye ki
     *    shabdkosh se vaakya ban jayega.
     */
    fun translate(c: Context, word: String, toHindi: Boolean): String? {
        load(c)
        val w = word.trim().lowercase()
        if (w.isBlank() || w.contains(" ") && w.split(" ").size > 2)
            return null
        val src = if (toHindi) dict else rev
        val got = src?.optString(w, "") ?: ""
        if (got.isBlank()) return null
        return if (toHindi)
            "🌐 $word = $got\n\n<i>(offline shabdkosh se — turant)</i>"
        else
            "🌐 $word = $got\n\n<i>(offline shabdkosh se — turant)</i>"
    }

    /**
     * Bolne se pehchano ki offline se hi jawab de sakte hain.
     *
     * @return jawab ya null
     */
    fun tryAnswer(c: Context, raw: String): String? {
        val t = raw.lowercase().trim()

        // 📋 command list
        if (Regex("(command|kya kar sakte|kya kar sakta|" +
                  "sab command|command list|feature batao|" +
                  "kya kya kar sakte)").containsMatchIn(t))
            return commands(c)

        // 🆘 help
        if (Regex("(^help$|madad chahiye|dikkat aa rahi|" +
                  "problem hai|kaam nahi kar raha|troubleshoot)")
                .containsMatchIn(t))
            return help(c)

        return null
    }
}
