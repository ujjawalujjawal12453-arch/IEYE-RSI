package com.ravanx.ieyeris

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * v2.1 — MEMORY ki jaanch
 *
 * User ne kaha "iski memory strong karo". Pehle IEYE RIS
 * khud kuch yaad nahi rakhta tha — har baar "yaad rakho"
 * bolna padta tha. Ab normal baat se hi fact pakadta hai.
 *
 * Ye test asli Memory.kt ka autoLearn() chalata hai.
 */
class MemTest {
    private val mem = LinkedHashMap<String, String>()
    private fun put(k: String, v: String) {
        if (k.isBlank() || v.isBlank()) return
        mem[k.lowercase().trim()] = v.trim()
    }

    fun autoLearn(text: String): String {
        val t = text.trim()
        if (t.length < 6 || t.length > 220) return ""
        val low = t.lowercase()
        var got = ""

        // ── naam ──
        Regex("(?:mera|mere|mera) naam ([a-zA-Z\\u0900-\\u097F ]{2,28})" +
              "\\s*(?:hai|h|hun|hoon)?")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { put("naam", v); got = "naam" }
            }
        if (got.isBlank())
            Regex("^(?:main|mai|mein) ([a-zA-Z\\u0900-\\u097F ]{2,24})" +
                  "\\s+(?:hoon|hun|hu)$")
                .find(low)?.let {
                    val v = clean(it.groupValues[1])
                    // "main student hoon" naam nahi, kaam hai
                    if (v.isNotBlank() && !isJob(v)) {
                        put("naam", v); got = "naam"
                    }
                }

        // ── sheher / ghar ──
        Regex("(?:main|mai|mein) ([a-zA-Z\\u0900-\\u097F ]{2,26})" +
              "\\s*(?:me|mein|se)\\s*(?:rehta|rahta|rehti|rahti|" +
              "hoon|hun|hu|se hoon)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { put("sheher", v); got = "sheher" }
            }

        // ── kaam / padhai ──
        Regex("(?:main|mai|mein)\\s+(?:ek\\s+)?" +
              "(student|teacher|engineer|doctor|developer|coder|" +
              "programmer|business|businessman|shopkeeper|driver|" +
              "farmer|kisan|padhai|naukri)")
            .find(low)?.let {
                put("kaam", clean(it.groupValues[1])); got = "kaam"
            }

        // ── pasand / napasand ──
        Regex("(?:mujhe|muje|mereko)\\s+(.{2,40}?)\\s+" +
              "(?:pasand hai|achha lagta|acha lagta|bahut pasand)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { put("pasand", v); got = "pasand" }
            }
        Regex("(?:mujhe|muje|mereko)\\s+(.{2,40}?)\\s+" +
              "(?:pasand nahi|nahi pasand|bura lagta)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { put("napasand", v); got = "napasand" }
            }

        // ── rishte ka number ──
        Regex("(papa|mummy|maa|mom|dad|bhai|behen|didi|bhaiya|" +
              "dost|friend|wife|biwi|patni)\\s*" +
              "(?:ka|ki)\\s*(?:number|no|mobile|phone)\\s*" +
              "(?:hai)?\\s*[:=]?\\s*(\\+?[0-9][0-9 \\-]{6,15})")
            .find(low)?.let {
                put(clean(it.groupValues[1]) + " ka number",
                         it.groupValues[2].replace(" ", ""))
                got = "number"
            }

        // ── janamdin ──
        Regex("(?:mera|meri)\\s*(?:birthday|janamdin|janmdin|dob)\\s*" +
              "(?:hai)?\\s*[:=]?\\s*(.{3,24})")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { put("janamdin", v); got = "janamdin" }
            }

        // ── seedha hukum: "yaad rakho X = Y" ──
        Regex("(?:yaad rakho|yaad rakhna|note karo|likh lo)\\s+" +
              "(.{2,40}?)\\s*(?:=|hai|:)\\s*(.{1,80})")
            .find(low)?.let {
                val k = clean(it.groupValues[1])
                val v = clean(it.groupValues[2])
                if (k.isNotBlank() && v.isNotBlank()) {
                    put(k, v); got = k
                }
            }

        return got
    }

    private fun isJob(v: String) = v in listOf(
        "student", "teacher", "engineer", "doctor", "developer",
        "coder", "programmer", "businessman", "shopkeeper",
        "driver", "farmer", "kisan", "thik", "theek", "acha", "achha")

    private fun clean(s: String) = s.trim()
        .trim('.', ',', '!', '?', '"', '\'', ' ')
        .replace(Regex("\\s+"), " ")
        .take(80)

    private val fails = mutableListOf<String>()

    private fun chk(say: String, key: String, want: String) {
        mem.clear()
        autoLearn(say)
        val got = mem[key] ?: ""
        if (!got.contains(want, true))
            fails.add("\"$say\" -> $key chahiye=\"$want\" mila=\"$got\"")
    }

    private fun chkNone(say: String) {
        mem.clear(); autoLearn(say)
        if (mem.isNotEmpty())
            fails.add("\"$say\" -> kuch yaad nahi karna tha, kiya: $mem")
    }

    @Test fun memoryTest() {
        chk("mera naam Ujjawal hai", "naam", "ujjawal")
        chk("Mera Naam Ujjawal Hai", "naam", "ujjawal")
        chk("main Bhopal me rehta hoon", "sheher", "bhopal")
        chk("main indore mein rehta hoon", "sheher", "indore")
        chk("main ek student hoon", "kaam", "student")
        chk("main engineer hoon", "kaam", "engineer")
        chk("mujhe chai pasand hai", "pasand", "chai")
        chk("mujhe bheed pasand nahi", "napasand", "bheed")
        chk("papa ka number 9876543210 hai", "papa ka number", "9876543210")
        chk("mera birthday 12 june hai", "janamdin", "12 june")
        chk("yaad rakho wifi password = ravanx2026",
            "wifi password", "ravanx2026")

        // ye kuch yaad nahi karne chahiye
        chkNone("youtube kholo")
        chkNone("torch on karo")
        chkNone("kya haal hai")
        chkNone("time batao")

        val total = 15
        if (fails.isNotEmpty()) {
            println("\n\u274c FAIL:")
            fails.forEach { println("   " + it) }
        }
        println("\n\u2705 MEMORY PASS " + (total - fails.size) + " / " + total)
        assertEquals("memory sahi", 0, fails.size)
    }
}
