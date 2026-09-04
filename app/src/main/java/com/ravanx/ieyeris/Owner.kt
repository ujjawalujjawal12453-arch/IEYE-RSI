package com.ravanx.ieyeris

import android.content.Context

/**
 * 🔒 OWNER LOCK — v4.5
 *
 * User ne kaha (exact):
 *   "URL sirf jo hai na wo nahi badal sakta, uske liye ek
 *    password lagega — yeh raha 2244. Isi ko daalne ke baad
 *    wo password vagaira change matlab URL vagaira change kar
 *    sakta hai. Aur dekho jab bhi wo aata hai na andar to wo
 *    kisi bhi API vagaira ko change nahi kar sakta aur na hi
 *    dekh sakta hai."
 *
 * ═══ MATLAB KYA HUA ═══
 *
 * Do tarah ke log app kholte hain:
 *
 *   1. AAM USER (jise key mili hai)
 *      → app chala sakta hai
 *      → server ka URL BADAL NAHI sakta
 *      → API keys na DEKH sakta hai, na BADAL sakta hai
 *
 *   2. OWNER (aap — Ujjawal)
 *      → password 2244 daalte hi sab khul jata hai
 *      → URL badlo, API keys dekho/badlo, password badlo
 *
 * ═══ ⚠️ IMAANDARI KI BAAT — YE KITNA SURAKSHIT HAI? ═══
 *
 * Ye lock AAM USER ko rokta hai — jo 99% log hain. Wo screen
 * pe API key dekh hi nahi payega, chhu bhi nahi payega.
 *
 * Par jo banda APK ko decompile kar sakta hai, wo keys nikal
 * sakta hai — kyunki keys app ke andar hain (XOR+Base64 me).
 * Ye har Android app ke saath sach hai, koi bhi tareeka isse
 * nahi bacha sakta. Main jhooth nahi bolunga.
 *
 * Iska ASLI ilaaj sirf ek hai: keys phone me rakho hi mat,
 * license server pe rakho aur AI call bhi wahin se karwao.
 * Wo bada badlav hai — abhi nahi kiya, aap bologe to karunga.
 *
 * ═══ SESSION ═══
 *
 * Unlock SIRF MEMORY me rehta hai — kisi file me nahi.
 * App band hui, ya 10 minute beete, to apne aap wapas lock.
 * Wajah: aapne unlock karke phone rakh diya aur koi utha le,
 * to bhi keys safe rahen.
 */
object Owner {

    /** Aapka password — pehli baar ka */
    private const val DEFAULT_PASS = "2244"

    private const val K_PASS = "owner_pass"

    /** 10 minute baad apne aap lock */
    private const val SESSION_MS = 10 * 60 * 1000L

    // ⚠️ JAANBUJH KAR memory me — disk pe nahi.
    //    Disk pe hota to app band karke bhi unlocked reh jata.
    @Volatile private var unlockedAt = 0L

    /** Abhi ka password (badla ho to wahi) */
    fun pass(c: Context): String =
        Keys(c).get(K_PASS, "").ifBlank { DEFAULT_PASS }

    /** Password badlo — sirf tab jab pehle se unlocked ho */
    fun setPass(c: Context, old: String, new: String): Pair<Boolean, String> {
        if (old.trim() != pass(c))
            return false to "❌ Purana password galat hai."
        val n = new.trim()
        if (n.length < 4)
            return false to "Password kam se kam 4 akshar ka rakhiye."
        if (n.length > 24)
            return false to "Password bahut lamba hai (24 tak)."
        Keys(c).set(K_PASS, n)
        // password badla -> session bhi tod do, naya daal kar aao
        lock()
        return true to "✅ Password badal gaya. Dobara login kijiye."
    }

    /** Password daalo */
    fun unlock(c: Context, p: String): Boolean {
        val ok = p.trim() == pass(c)
        if (ok) unlockedAt = System.currentTimeMillis()
        return ok
    }

    fun lock() { unlockedAt = 0L }

    /** Abhi owner khula hai? */
    fun unlocked(): Boolean {
        if (unlockedAt == 0L) return false
        if (System.currentTimeMillis() - unlockedAt > SESSION_MS) {
            unlockedAt = 0L
            return false
        }
        return true
    }

    /** Kitna waqt bacha (minute) — UI me dikhane ke liye */
    fun minsLeft(): Int {
        if (!unlocked()) return 0
        val left = SESSION_MS - (System.currentTimeMillis() - unlockedAt)
        return ((left / 60000L) + 1).toInt().coerceAtLeast(1)
    }

    /**
     * 🔑 API key ko chhupa kar dikhao.
     *
     * Locked hai to sirf itna: "••••••••••••  (56 akshar)"
     * Poori key kabhi screen pe nahi aati.
     *
     * ⚠️ Pehle 4 akshar bhi NAHI dikhate. Kyun? "gsk_" ya
     *    "cfut_" jaisa prefix dekh kar bhi banda samajh jata
     *    hai kaunsi service hai. Locked matlab locked.
     */
    fun mask(v: String): String =
        if (v.isBlank()) "— khali —"
        else "•".repeat(12) + "   (" + v.length + " akshar)"

    /**
     * Password poochhne wala dialog — poori app me yahi ek jagah.
     *
     * @param why user ko kyun poochh rahe hain (saaf batao,
     *            warna banda ghabra jata hai)
     */
    fun ask(
        act: android.app.Activity,
        why: String,
        onOk: () -> Unit
    ) {
        // pehle se khula hai to dobara mat poochho
        if (unlocked()) { onOk(); return }

        val d = (16 * act.resources.displayMetrics.density).toInt()
        val e = android.widget.EditText(act).apply {
            hint = "Owner password"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.25f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(d, d, d, d)
        }
        val box = android.widget.LinearLayout(act).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(d + d / 2, d / 2, d + d / 2, 0)
            addView(e)
        }

        val dlg = androidx.appcompat.app.AlertDialog.Builder(act)
            .setTitle("🔒 Owner password")
            .setMessage("$why\n\nYe sirf malik ke liye hai.")
            .setView(box)
            .setPositiveButton("Kholo", null)   // null = khud handle
            .setNegativeButton("Rehne do", null)
            .create()
        dlg.show()

        // ⚠️ setOnClickListener BAAD me lagaya hai — warna galat
        //    password pe bhi dialog band ho jata hai aur user ko
        //    dobara poore rasta chalna padta hai.
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                if (unlock(act, e.text.toString())) {
                    dlg.dismiss()
                    android.widget.Toast.makeText(act,
                        "🔓 Khul gaya — " + minsLeft() + " minute tak",
                        android.widget.Toast.LENGTH_SHORT).show()
                    onOk()
                } else {
                    e.setText("")
                    e.hint = "❌ Galat — dobara"
                    e.setHintTextColor(0xFFFF6B6B.toInt())
                    try {
                        e.startAnimation(
                            android.view.animation.TranslateAnimation(
                                -14f, 14f, 0f, 0f).apply {
                                duration = 60; repeatCount = 3
                                repeatMode = android.view.animation
                                    .Animation.REVERSE
                            })
                    } catch (x: Exception) {}
                }
            }
    }
}
