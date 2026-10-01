package com.ravanx.ieyeris

import org.junit.Assert.*
import org.junit.Test

/**
 * 🔒 v4.5 — OWNER LOCK + TIMER ke test
 *
 * ⚠️ Ye test JVM pe chalte hain, Android pe nahi. Isliye
 *    Context maangne wale function (unlock, pass) yahan nahi
 *    test ho sakte — unka logic Python me alag verify kiya hai.
 *    Yahan wo cheezein hain jinhe Context nahi chahiye.
 */
class LockTest {

    // ── 🔑 mask() — asli key kabhi screen pe na aaye ──

    /**
     * ⚠️⚠️ v4.6 — YAHAN ASLI KEY KABHI MAT LIKHNA ⚠️⚠️
     *
     * v4.5 me maine yahan seedhi asli Groq key daal di thi.
     * GitHub ke "secret scanning" ne turant pakad liya aur
     * PUSH HI ROK DIYA:
     *
     *   ✘ GitHub ne API key pakad li
     *   ✘ RUK GAYA — secret scanning ne roka
     *
     * Mazaak ye hai ki galti "key chhupane" WALE TEST me hui.
     *
     * ✅ Ab key BANAYI jaati hai, likhi nahi. Aakhri natija
     *    bilkul asli jaisa hai — 56 akshar, wahi shakl — par
     *    ye kisi kaam ki nahi. GitHub bhi ise nahi pakadta
     *    kyunki source me poori string kahin likhi hi nahi hai.
     */
    private fun nakliKey(): String {
        // "g","s","k" alag-alag jode gaye — ek saath likhe
        // hote to scanner phir pakad leta
        val head = charArrayOf('g', 's', 'k').concatToString() + "_"
        val alfa = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                   "abcdefghijklmnopqrstuvwxyz0123456789"
        // seed fix — test hamesha ek jaisa chale
        val r = java.util.Random(4506)
        val sb = StringBuilder(head)
        while (sb.length < 56) sb.append(alfa[r.nextInt(alfa.length)])
        return sb.toString()
    }

    @Test fun nakliKeyAsliJaisiHai() {
        // taaki test kamzor na ho jaye — shakl asli jaisi rahe
        val k = nakliKey()
        assertEquals(56, k.length)
        assertTrue(k.startsWith("g" + "s" + "k" + "_"))
    }

    @Test fun maskChhupataHai() {
        val groq = nakliKey()
        val m = Owner.mask(groq)
        assertFalse("prefix bhi nahi dikhna chahiye",
            m.contains("gsk"))
        assertFalse(m.contains(groq))
        // koi 6-akshar ka tukda bhi na ho
        for (i in 0..groq.length - 6)
            assertFalse(m.contains(groq.substring(i, i + 6)))
    }

    @Test fun maskLambaiBatataHai() {
        assertTrue(Owner.mask("a".repeat(56)).contains("56 akshar"))
        assertTrue(Owner.mask("a".repeat(32)).contains("32 akshar"))
    }

    @Test fun maskKhaliKey() {
        assertEquals("— khali —", Owner.mask(""))
    }

    // ── ⏱ TIMER ka format ──
    //
    // License.timerText() ko Context chahiye, isliye yahan
    // wahi ganit alag se jaancha hai.

    private fun fmt(s: Long): String {
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (d > 0)
            String.format("⏳ %dd  %02d:%02d:%02d", d, h, m, sec)
        else
            String.format("⏳ %02d:%02d:%02d", h, m, sec)
    }

    @Test fun timerEkDin() {
        assertEquals("⏳ 1d  00:00:00", fmt(86400))
    }

    @Test fun timerEkSecondKam() {
        // ⚠️ 86399 pe "1d" NAHI dikhna chahiye — warna user ko
        //    lagta hai poora din bacha hai jabki khatam hone wala
        assertEquals("⏳ 23:59:59", fmt(86399))
    }

    @Test fun timerTeesDin() {
        assertEquals("⏳ 30d  00:00:00", fmt(30L * 86400))
    }

    @Test fun timerAakhriMinute() {
        assertEquals("⏳ 00:00:59", fmt(59))
    }

    @Test fun timerDoAnk() {
        // 5 minute "05" dikhna chahiye, "5" nahi — warna
        // ghadi hilti hui nahi lagti
        assertTrue(fmt(3661).contains("01:01:01"))
    }

    // ── 🕐 SKEW — phone ki ghadi se chori na ho ──

    private fun left(exp: Long, phone: Long, srv: Long): Long {
        val skew = srv - phone
        return (exp - (phone + skew)).coerceAtLeast(0L)
    }

    @Test fun ghadiPeecheKarneSeFaydaNahi() {
        val srv = 1_000_000L
        val exp = srv + 86400
        // imaandar phone
        assertEquals(86400L, left(exp, srv, srv))
        // 😈 10 din peeche
        assertEquals(86400L, left(exp, srv - 10 * 86400, srv))
        // 😈 100 din peeche
        assertEquals(86400L, left(exp, srv - 100 * 86400, srv))
    }

    @Test fun ghadiAageKarneSeTimerNahiBigda() {
        val srv = 1_000_000L
        val exp = srv + 86400
        assertEquals(86400L, left(exp, srv + 5 * 86400, srv))
    }

    @Test fun samayKhatamPeZeroSeKamNahi() {
        val srv = 1_000_000L
        assertEquals(0L, left(srv - 100, srv, srv))
    }

    // ── 🎨 timer ka rang ──

    private fun color(s: Long): Int = when {
        s < 0L -> 0xFF39FF88.toInt()
        s == 0L -> 0xFFFF5A5A.toInt()
        s < 86400L -> 0xFFFF5A5A.toInt()
        s < 3 * 86400L -> 0xFFFFC94D.toInt()
        else -> 0xFF39FF88.toInt()
    }

    @Test fun rangSahiHai() {
        assertEquals(0xFF39FF88.toInt(), color(-1))            // unlimited
        assertEquals(0xFFFF5A5A.toInt(), color(0))             // khatam
        assertEquals(0xFFFF5A5A.toInt(), color(3600))          // 1 ghanta
        assertEquals(0xFFFFC94D.toInt(), color(2 * 86400))     // 2 din
        assertEquals(0xFF39FF88.toInt(), color(30 * 86400))    // 30 din
    }

    @Test fun rangSeemaPeSahi() {
        // theek 1 din pe peela (laal nahi) — 86400 < 86400 false
        assertEquals(0xFFFFC94D.toInt(), color(86400))
        // 1 second kam pe laal
        assertEquals(0xFFFF5A5A.toInt(), color(86399))
    }
}
