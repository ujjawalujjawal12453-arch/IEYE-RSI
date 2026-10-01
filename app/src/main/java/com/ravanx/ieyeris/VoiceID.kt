package com.ravanx.ieyeris

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 🎙 VOICE IDENTITY — "ye malik hi bol raha hai?"
 * ═══════════════════════════════════════════════
 *
 * Spec section 5-6.
 *
 * ════════════════════════════════════════════════
 *  ⚠️  SABSE PEHLE — SAAF BAAT
 * ════════════════════════════════════════════════
 *
 *  YE TAALA NAHI HAI.
 *
 *  Ye aapki awaaz aur kisi ANJAAN aadmi ki awaaz me farak
 *  kar leta hai — 80-90% baar. Par:
 *
 *    • Aapka bhai/dost jinki awaaz milti-julti hai — ghus
 *      sakte hain
 *    • Recording chala kar bhi ghusa ja sakta hai
 *    • Zukam ho, gala kharab ho, shor ho — to aapko hi
 *      pehchanne se mana kar sakta hai
 *
 *  Isliye ISKO KISI ZAROORI CHEEZ KA TAALA MAT BANANA.
 *  Spec me bhi yahi likha hai:
 *      "identity-confidence signal, not an unbreakable
 *       security mechanism"
 *
 *  Asli speaker-ID ke liye neural model chahiye (ECAPA-TDNN
 *  jaisa) — wo 20+ MB ka hota hai aur usko chalane ke liye
 *  alag library. Wo is app me nahi hai.
 *
 * ════════════════════════════════════════════════
 *  YE KAAM KAISE KARTA HAI
 * ════════════════════════════════════════════════
 *
 *  Har aadmi ki awaaz ka apna "rang" hota hai — kitni gehri,
 *  kitni patli, kaise goonjti hai. Hum ye naapte hain:
 *
 *    1. PITCH        — awaaz kitni gehri/patli (Hz)
 *    2. ENERGY BANDS — 12 alag frequency me kitna zor
 *    3. ZCR          — awaaz kitni "khurdari" hai
 *    4. SPREAD       — frequency kitni faili hui
 *
 *  Enrollment me 3 baar bolwate hain, teeno ka औसत rakhte
 *  hain. Baad me har baar milaan karke score dete hain.
 *
 *  Sab kuch PHONE KE ANDAR — koi recording kahin nahi jaati.
 */
object VoiceID {

    private const val SR = 16000        // 16 kHz — awaaz ke liye kaafi
    private const val BANDS = 12
    private const val DIM = BANDS + 4   // 12 band + pitch,zcr,spread,rms

    /** Kitne sample enrollment me chahiye */
    const val NEED_SAMPLES = 3

    /** Har sample kitne second ka */
    const val SAMPLE_SEC = 3

    data class Profile(
        val vec: FloatArray,      // औसत
        val sd: FloatArray,       // har feature ka bikharav
        val n: Int                // kitne sample se bana
    )

    // ─────────────────────────────────────────────
    //  SETTINGS
    // ─────────────────────────────────────────────

    fun enabled(c: Context) = Keys(c).flag("vid_on", false)
    fun setEnabled(c: Context, v: Boolean) =
        Keys(c).setFlag("vid_on", v)

    /** 0 = dheela (sabko pass), 100 = kada */
    fun strictness(c: Context): Int {
        return try {
            Keys(c).get("vid_strict", "55").toInt().coerceIn(0, 100)
        } catch (e: Exception) { 55 }
    }

    fun setStrictness(c: Context, v: Int) =
        Keys(c).set("vid_strict", v.coerceIn(0, 100).toString())

    fun enrolled(c: Context) = load(c) != null

    fun sampleCount(c: Context) = load(c)?.n ?: 0

    // ─────────────────────────────────────────────
    //  RECORD
    // ─────────────────────────────────────────────

    /**
     * Mic se seedha awaaz utho — PCM me.
     *
     * ⚠️ SpeechRecognizer se raw audio nahi milta, isliye
     *    AudioRecord use karna padta hai. Iske liye
     *    RECORD_AUDIO permission chahiye (pehle se hai).
     *
     * @return short array, ya null agar mic na mile
     */
    fun record(secs: Int = SAMPLE_SEC): ShortArray? {
        val min = AudioRecord.getMinBufferSize(
            SR, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val buf = ShortArray(SR * secs)
        var rec: AudioRecord? = null
        return try {
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, buf.size * 2))
            if (rec.state != AudioRecord.STATE_INITIALIZED) return null
            rec.startRecording()
            var off = 0
            while (off < buf.size) {
                val n = rec.read(buf, off, buf.size - off)
                if (n <= 0) break
                off += n
            }
            rec.stop()
            if (off < SR) null else buf.copyOf(off)
        } catch (e: SecurityException) {
            null                      // permission nahi
        } catch (e: Exception) {
            null
        } finally {
            try { rec?.release() } catch (e: Exception) {}
        }
    }

    // ─────────────────────────────────────────────
    //  FEATURES — awaaz ka "fingerprint"
    // ─────────────────────────────────────────────

    /**
     * Awaaz se {DIM} number nikalo.
     *
     * ⚠️ Ye MFCC nahi hai (wo bahut behtar hota par uske liye
     *    poora DCT+mel-filterbank chahiye). Ye uska halka roop
     *    hai — kaam chalane layak, perfect nahi.
     */
    fun features(pcm: ShortArray): FloatArray? {
        if (pcm.size < SR) return null

        // 1. sirf wahi hissa lo jahan sach me boli gayi ho
        val voiced = trimSilence(pcm) ?: return null
        if (voiced.size < SR / 2) return null

        val out = FloatArray(DIM)

        // 2. ENERGY BANDS — Goertzel se 12 frequency naapo
        //    (poora FFT bhaari hai, Goertzel chuni hui
        //     frequency ke liye kaafi hai aur bahut halka)
        val freqs = floatArrayOf(
            120f, 200f, 320f, 470f, 660f, 900f,
            1200f, 1600f, 2100f, 2800f, 3600f, 4600f)
        var tot = 0f
        val raw = FloatArray(BANDS)
        for (i in 0 until BANDS) {
            raw[i] = goertzel(voiced, freqs[i])
            tot += raw[i]
        }
        if (tot <= 0f) return null
        // normalize — awaaz kitni zor se boli, usse farak na pade
        for (i in 0 until BANDS) {
            out[i] = ln(1f + raw[i] / tot * 100f)
        }

        // 3. PITCH — autocorrelation se
        out[BANDS] = pitch(voiced) / 400f

        // 4. ZCR — kitni baar zero cross karti hai
        var z = 0
        for (i in 1 until voiced.size) {
            if ((voiced[i] >= 0) != (voiced[i - 1] >= 0)) z++
        }
        out[BANDS + 1] = z.toFloat() / voiced.size * 100f

        // 5. SPREAD — energy kitni faili hui hai
        var mean = 0f
        for (i in 0 until BANDS) mean += out[i]
        mean /= BANDS
        var v = 0f
        for (i in 0 until BANDS) v += (out[i] - mean) * (out[i] - mean)
        out[BANDS + 2] = sqrt(v / BANDS)

        // 6. RMS — kitni zor se
        var s = 0.0
        for (x in voiced) s += x.toDouble() * x
        out[BANDS + 3] = (sqrt(s / voiced.size) / 3000.0).toFloat()

        return out
    }

    /** Shuru-aakhir ki khamoshi kaat do */
    private fun trimSilence(pcm: ShortArray): ShortArray? {
        val win = 400
        var peak = 0.0
        var i = 0
        while (i + win < pcm.size) {
            var s = 0.0
            for (j in i until i + win) s += abs(pcm[j].toInt())
            peak = maxOf(peak, s / win)
            i += win
        }
        if (peak < 90) return null          // bilkul chup
        val th = peak * 0.22
        var a = -1
        var b = -1
        i = 0
        while (i + win < pcm.size) {
            var s = 0.0
            for (j in i until i + win) s += abs(pcm[j].toInt())
            if (s / win > th) { if (a < 0) a = i; b = i + win }
            i += win
        }
        if (a < 0 || b <= a) return null
        return pcm.copyOfRange(a, minOf(b, pcm.size))
    }

    /** Ek frequency pe kitna zor — Goertzel algorithm */
    private fun goertzel(x: ShortArray, freq: Float): Float {
        val k = 2.0 * cos(2.0 * Math.PI * freq / SR)
        var s0: Double
        var s1 = 0.0
        var s2 = 0.0
        val step = if (x.size > 24000) 2 else 1   // bade sample skip
        var i = 0
        while (i < x.size) {
            s0 = x[i] / 32768.0 + k * s1 - s2
            s2 = s1
            s1 = s0
            i += step
        }
        val p = s1 * s1 + s2 * s2 - k * s1 * s2
        return if (p > 0) sqrt(p).toFloat() else 0f
    }

    /** Awaaz kitni gehri — Hz me */
    private fun pitch(x: ShortArray): Float {
        val lo = SR / 400      // 400 Hz
        val hi = SR / 70       // 70 Hz
        var best = 0.0
        var bestLag = 0
        val n = minOf(x.size, SR)
        for (lag in lo..hi) {
            var s = 0.0
            var i = 0
            while (i + lag < n) {
                s += x[i].toDouble() * x[i + lag]
                i += 2
            }
            if (s > best) { best = s; bestLag = lag }
        }
        return if (bestLag > 0) SR.toFloat() / bestLag else 0f
    }

    // ─────────────────────────────────────────────
    //  ENROLL
    // ─────────────────────────────────────────────

    private val pending = mutableListOf<FloatArray>()

    fun clearPending() = pending.clear()
    fun pendingCount() = pending.size

    /**
     * Ek sample lo.
     * @return (ok, message)
     */
    fun addSample(): Pair<Boolean, String> {
        val pcm = record() ?: return false to
            "Mic nahi mila — permission check kariye"
        val f = features(pcm) ?: return false to
            "Kuch sunai nahi diya — thoda zor se boliye"
        pending.add(f)
        return true to "Sample ${pending.size}/$NEED_SAMPLES liya"
    }

    /**
     * Saare sample milakar profile banao.
     *
     * ⚠️ Agar teeno sample bahut alag hain to matlab shor tha
     *    ya alag-alag log bole. Aise me mana kar dete hain —
     *    warna profile kachra ban jayegi aur kabhi kaam nahi
     *    karegi.
     */
    fun finish(c: Context): Pair<Boolean, String> {
        if (pending.size < NEED_SAMPLES)
            return false to "Abhi ${NEED_SAMPLES - pending.size} " +
                            "sample aur chahiye"

        val vec = FloatArray(DIM)
        for (f in pending) for (i in 0 until DIM) vec[i] += f[i]
        val nSamp = pending.size.toFloat()
        for (i in 0 until DIM) vec[i] = vec[i] / nSamp

        val sd = FloatArray(DIM)
        for (f in pending) for (i in 0 until DIM) {
            val d = f[i] - vec[i]
            sd[i] += d * d
        }
        for (i in 0 until DIM) {
            sd[i] = sqrt(sd[i] / nSamp)
            if (sd[i] < 0.02f) sd[i] = 0.02f   // divide-by-zero se bacho
        }

        // sample aapas me kitne milte hain?
        var worst = 1f
        for (f in pending) {
            val s = score(Profile(vec, sd, pending.size), f)
            worst = minOf(worst, s)
        }
        if (worst < 0.35f) {
            pending.clear()
            return false to "Sample bahut alag-alag hain — shor " +
                            "wali jagah lagti hai. Shaant jagah pe " +
                            "dobara try kariye."
        }

        save(c, Profile(vec, sd, pending.size))
        pending.clear()
        return true to "Awaaz yaad kar li sir"
    }

    // ─────────────────────────────────────────────
    //  VERIFY
    // ─────────────────────────────────────────────

    /**
     * @return 0.0 (bilkul alag) se 1.0 (bilkul same)
     */
    fun score(p: Profile, f: FloatArray): Float {
        var d = 0f
        var w = 0f
        for (i in 0 until DIM) {
            // pitch aur band ko zyada wazan
            val wt = when {
                i < BANDS -> 1.0f
                i == BANDS -> 2.2f       // pitch sabse ahem
                else -> 0.6f
            }
            val z = (f[i] - p.vec[i]) / p.sd[i]
            d += wt * z * z
            w += wt
        }
        val dist = sqrt(d / w)
        // dist 0 -> 1.0, dist 3 -> ~0.05
        return (1f / (1f + dist * dist * 0.45f)).coerceIn(0f, 1f)
    }

    /**
     * Abhi jo bola gaya — malik hai?
     *
     * @return Triple(pass, score, message)
     */
    fun verify(c: Context): Triple<Boolean, Float, String> {
        val p = load(c) ?: return Triple(true, 1f,
            "Profile nahi bani — sabko allow")
        val pcm = record(2) ?: return Triple(false, 0f,
            "Mic nahi chala")
        val f = features(pcm) ?: return Triple(false, 0f,
            "Kuch sunai nahi diya")
        val s = score(p, f)
        val need = 0.25f + strictness(c) / 100f * 0.45f
        return if (s >= need)
            Triple(true, s, "Aapki awaaz pehchan li")
        else
            Triple(false, s, "Awaaz pehchan me nahi aayi")
    }

    /** Jo audio pehle se hai usi se check (dobara record nahi) */
    fun verifyPcm(c: Context, pcm: ShortArray):
            Triple<Boolean, Float, String> {
        val p = load(c) ?: return Triple(true, 1f, "profile nahi")
        val f = features(pcm) ?: return Triple(false, 0f, "khali")
        val s = score(p, f)
        val need = 0.25f + strictness(c) / 100f * 0.45f
        return Triple(s >= need, s,
            if (s >= need) "pehchan li" else "pehchan nahi aayi")
    }

    // ─────────────────────────────────────────────
    //  SAVE / LOAD / DELETE
    // ─────────────────────────────────────────────

    private fun file(c: Context) = File(c.filesDir, "voiceid.json")

    private var cache: Profile? = null
    private var loaded = false

    private fun save(c: Context, p: Profile) {
        try {
            val o = JSONObject()
            o.put("v", JSONArray().apply { p.vec.forEach { put(it) } })
            o.put("s", JSONArray().apply { p.sd.forEach { put(it) } })
            o.put("n", p.n)
            file(c).writeText(o.toString())
            cache = p
            loaded = true
        } catch (e: Exception) {}
    }

    fun load(c: Context): Profile? {
        if (loaded) return cache
        loaded = true
        cache = try {
            val o = JSONObject(file(c).readText())
            val va = o.getJSONArray("v")
            val sa = o.getJSONArray("s")
            if (va.length() != DIM) null
            else Profile(
                FloatArray(DIM) { va.getDouble(it).toFloat() },
                FloatArray(DIM) { sa.getDouble(it).toFloat() },
                o.optInt("n", 1))
        } catch (e: Exception) { null }
        return cache
    }

    /** Profile mita do — privacy center se */
    fun delete(c: Context) {
        try { file(c).delete() } catch (e: Exception) {}
        cache = null
        loaded = true
        pending.clear()
        Keys(c).setFlag("vid_on", false)
    }
}
