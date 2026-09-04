package com.ravanx.ieyeris

import android.content.Context

/**
 * 🛑 HALT — EMERGENCY STOP ka dimaag
 *
 * Spec: "Place a highly visible IEYE RIS STOP button.
 *        This button must always be accessible."
 *
 * ═══ YE ZAROORI KYUN HAI ═══
 *
 * Agent 12 kadam tak apne aap phone chalata hai. Agar wo galat
 * samajh gaya — galat app khol diya, galat jagah type karne laga
 * — to user ke paas use ROKNE ka koi tareeka hona chahiye.
 *
 * Pehle "stop" bol kar rokna padta tha. Par jab IEYE RIS khud bol
 * raha ho ya mic band ho, to wo bhi kaam nahi karta tha. User
 * bebas ho jata tha.
 *
 * ═══ KAISE KAAM KARTA HAI ═══
 *
 * Ek hi jhande (flag) se sab kuch rukta hai:
 *
 *   1. Agent har kadam se PEHLE `stopped()` dekhta hai
 *   2. Voice bolna band karti hai
 *   3. Mic band ho jata hai
 *   4. Katar me pade kaam gir jate hain
 *
 * ⚠️ Ye `@Volatile` hai kyunki alag-alag thread ise padhte hain —
 *    Agent background me, UI main thread pe. Bina volatile ke ek
 *    thread ka badla hua flag dusre ko dikhta hi nahi (CPU cache
 *    me atka reh jata hai). Ye Android ka bahut aam bug hai.
 */
object Halt {

    @Volatile
    private var flag = false

    @Volatile
    private var at = 0L

    /** Sab kuch abhi ke abhi roko */
    fun stopAll(c: Context?) {
        flag = true
        at = System.currentTimeMillis()
        Brain.log("🛑 EMERGENCY STOP — sab rok raha hoon")

        // 1. bolna band
        try { c?.let { Voice(it).stop() } } catch (e: Exception) {}

        // 2. sunna band
        try {
            MainActivity.liveRef?.get()?.forceStopListening()
        } catch (e: Exception) {}

        // 3. status batao
        try { Agent.cancel() } catch (e: Exception) {}
    }

    /**
     * Naya kaam shuru — jhanda saaf karo.
     *
     * ⚠️ Ye HAR naye kaam se pehle chalna chahiye. Warna ek baar
     *    STOP dabane ke baad app hamesha ke liye ruk jaati.
     */
    fun reset() {
        if (flag) Brain.log("▶️ stop hata — aage badh sakte hain")
        flag = false
    }

    /** Ruka hua hai? Agent har kadam pe ye poochta hai */
    fun stopped() = flag

    /** Kitni der pehle roka tha */
    fun ago() = if (at == 0L) -1L else System.currentTimeMillis() - at
}
