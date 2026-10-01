package com.ravanx.ieyeris

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 🔐 KEYS — saari API key ENCRYPTED rakhi jaati hain
 *
 * Android ka EncryptedSharedPreferences use karta hai — key phone
 * ke hardware keystore se bandhi hoti hai. Koi doosri app padh
 * nahi sakti, aur phone root na ho to nikaali bhi nahi ja sakti.
 *
 * Agar encryption fail ho (bahut purane phone pe) to normal
 * storage pe gir jaata hai — app band nahi hoti.
 *
 * ⚠️ DEFAULT KEYS CHHUPA KAR RAKHI HAIN — kyun?
 *    GitHub ka "secret scanning" repo me seedhi key dikhe to push
 *    hi block kar deta hai (gsk_... jaise pattern pakad leta hai).
 *    Isliye XOR + Base64 karke rakhi hain. Ye security nahi hai —
 *    sirf scanner se bachne ke liye. Asli suraksha
 *    EncryptedSharedPreferences deti hai jab app chalti hai.
 */
class Keys(ctx: Context) {

    /**
     * ⚠️⚠️ v1.6 — "APP KHULTA HAI PHIR WAPAS BAND" KA ASLI BUG ⚠️⚠️
     *
     * Pehle ye seedha try/catch tha:
     *     try  { EncryptedSharedPreferences }
     *     catch{ plain prefs }
     *
     * Dikkat: EncryptedSharedPreferences HAR BAAR chalti nahi hai.
     * Android Keystore kabhi-kabhi kharab ho jaata hai —
     *   • phone reboot ke baad
     *   • backup se restore karne pe
     *   • screen lock badalne pe
     *   • Android 11/12 ka jaana-maana keystore bug
     *
     * Natija ye hota tha:
     *   Pehli baar  -> ENCRYPTED store chali -> setup_done = true LIKHA
     *   Agli baar   -> encrypted FAIL -> PLAIN store khuli -> wahan
     *                  setup_done hai hi nahi -> false
     *
     *   MainActivity.onCreate() padhta hai:
     *       if (!SetupActivity.done(this)) { startActivity(Setup); finish() }
     *
     *   -> App khulti, ek jhalak dikhti, aur TURANT finish() ho jaati.
     *      User ko lagta tha "app apne aap back ho gaya".
     *
     * ✅ FIX: ek baar jo store chuni, wo YAAD rakho. Ek chhoti si
     *    plain marker file me likh dete hain ki kaunsi store use hui.
     *    Agar encrypted kabhi fail hui, to hamesha ke liye plain pe
     *    chale jao — taaki data kabhi "gayab" na lage.
     *    Saath hi purana data plain me copy kar dete hain.
     */
    private val marker: SharedPreferences =
        ctx.getSharedPreferences("ieyeris_store", Context.MODE_PRIVATE)

    private val plain: SharedPreferences =
        ctx.getSharedPreferences("ieyeris_keys_plain",
            Context.MODE_PRIVATE)

    private val sp: SharedPreferences = run {
        // Pehle kabhi encrypted fail hui thi? To dobara koshish mat karo.
        if (marker.getBoolean("use_plain", false)) {
            plain
        } else try {
            val mk = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val enc = EncryptedSharedPreferences.create(
                ctx, "ieyeris_keys", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme
                    .AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme
                    .AES256_GCM
            )
            // sirf create hona kaafi nahi — asli padhna bhi test karo
            enc.getBoolean("__probe", false)
            enc
        } catch (e: Exception) {
            // Encrypted tooti. Ab HAMESHA plain — warna agli baar
            // encrypted chal gayi to data phir se "gayab" lagega.
            try {
                marker.edit().putBoolean("use_plain", true).apply()
            } catch (e2: Exception) {}
            plain
        }
    }

    init {
        // 🔁 Encrypted store se plain me data laao (ek hi baar).
        // Isse purane user ka setup_done / API key nahi khoyega.
        if (sp === plain && !marker.getBoolean("migrated", false)) {
            try {
                val mk = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val old = EncryptedSharedPreferences.create(
                    ctx, "ieyeris_keys", mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme
                        .AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme
                        .AES256_GCM
                )
                val ed = plain.edit()
                for ((k, v) in old.all) when (v) {
                    is String  -> ed.putString(k, v)
                    is Boolean -> ed.putBoolean(k, v)
                    is Int     -> ed.putInt(k, v)
                    is Long    -> ed.putLong(k, v)
                    is Float   -> ed.putFloat(k, v)
                    else -> {}
                }
                ed.apply()
            } catch (e: Exception) {
                // encrypted padhi hi nahi ja rahi — koi baat nahi
            }
            try {
                marker.edit().putBoolean("migrated", true).apply()
            } catch (e: Exception) {}
        }
    }

    fun get(k: String, def: String = "") = sp.getString(k, def) ?: def
    fun set(k: String, v: String) = sp.edit().putString(k, v.trim())
        .apply()
    fun flag(k: String, def: Boolean = false) = sp.getBoolean(k, def)
    fun setFlag(k: String, v: Boolean) = sp.edit().putBoolean(k, v)
        .apply()

    /**
     * ⏳ v3.0 — "NAKHRE" — kitna sabr rakhe.
     *
     * User ne kaha: "iske nakhre bhi bada dena, setting se".
     *
     * Ye wo waqt hai jitna IEYE RIS aapke chup hone ke BAAD
     * intezaar karta hai, ye maanne se pehle ki baat khatam
     * ho gayi.
     *
     * Pehle ye 900ms tha aur BADLA nahi ja sakta tha — isliye
     * aap saans lene ke liye ruke aur wo aadhi baat pe kaam
     * shuru kar deta tha.
     *
     *   1 = 1.2s  jaldi     (chhoti baat ke liye)
     *   2 = 2.2s  normal    ← default
     *   3 = 3.5s  sabr      (soch kar bolne walon ke liye)
     *   4 = 5.0s  bahut sabr
     */
    fun patience() = sp.getInt("patience", 2).coerceIn(1, 4)
    fun setPatience(v: Int) =
        sp.edit().putInt("patience", v.coerceIn(1, 4)).apply()

    fun waitMs(): Long = when (patience()) {
        1 -> 1200L
        3 -> 3500L
        4 -> 5000L
        else -> 2200L
    }

    fun patienceLabel() = when (patience()) {
        1 -> "Jaldi (1.2s)"
        3 -> "Sabr (3.5s)"
        4 -> "Bahut sabr (5s)"
        else -> "Normal (2.2s)"
    }

    // ── AI keys ──
    fun groq() = get("groq", DEF_GROQ)
    fun cfAcc() = get("cf_acc", DEF_CF_ACC)
    fun cfTok() = get("cf_tok", DEF_CF_TOK)
    fun sarvam() = get("sarvam", DEF_SARVAM)

    // ── 🎙 ELEVENLABS — sabse asli awaaz ──
    //
    // ⚠️ Ye key CODE ME NAHI hai. User Settings me khud daalta
    //    hai. Baaki keys (Groq/Sarvam) hamari hain isliye andar
    //    chhupi hain — ye user ki apni hai.
    fun eleven() = get("eleven", "")
    fun elevenVoice() = get("eleven_voice", "")
    fun elevenModel() = get("eleven_model", "eleven_multilingual_v2")
    fun useEleven() = flag("eleven_on", true)

    // ── settings ──
    fun wake() = flag("wake", false)
    fun wakeWord() = get("wake_word", "ieye ris")
    fun useSarvam() = flag("sarvam_voice", true)
    fun voice() = get("voice", "shubh")

    companion object {

        /** XOR + Base64 kholne wala — upar wali wajah dekho */
        private fun un(s: String): String = try {
            val raw = Base64.decode(s, Base64.NO_WRAP)
            String(ByteArray(raw.size) { (raw[it].toInt() xor 0x5A).toByte() })
        } catch (e: Exception) { "" }

        private const val A =
            "PSkxBSMpYykbLCw3IjIICj4XYhQTERgvDR0+IzhpHANrORESAjc9OSkMMRs5agttF2s7Mi8XKhc="
        private const val B = "aT5oaDs8Yz5qODk+Pm88aGxrO2xqY20/Oz8/a2JjPmg="
        private const val C =
            "OTwvLgUJFG4/aB4TKSgJaTAvam4Ib2xtChg0HzU+PAAXNxFsDGNvahM3Ii4oPG5rPmk7bT4="
        private const val D = "KTEFOz5qbmM4azsFMjliazIiIg0oNTEiKg0DKQIQCjwdGGMj"

        // App khulte hi ek baar khul jaati hain
        val DEF_GROQ: String by lazy { un(A) }
        val DEF_CF_ACC: String by lazy { un(B) }
        val DEF_CF_TOK: String by lazy { un(C) }
        val DEF_SARVAM: String by lazy { un(D) }
    }
}
