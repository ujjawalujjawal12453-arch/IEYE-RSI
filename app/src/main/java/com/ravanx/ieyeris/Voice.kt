package com.ravanx.ieyeris

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 🔊 VOICE — IEYE RIS ki awaaz
 *
 * Do rasta:
 *   1. SARVAM — asli Indian awaaz, Hinglish bahut achhi bolta hai
 *   2. Android ka apna TTS — backup, offline bhi chalta hai
 *
 * ⚠️ Sarvam ko internet chahiye aur ~1 second lagta hai. Isliye
 *    agar wo fail ho to turant Android wale pe gir jaata hai —
 *    IEYE RIS kabhi chup nahi rehta.
 */
class Voice(private val ctx: Context) {

    companion object {
        /**
         * ElevenLabs ki apni awaaz — "Adam", gehri aur saaf.
         * Ye har account me milti hai, clone karne ki zarurat
         * nahi. Apni awaaz chahiye to Settings me Voice ID daalo.
         */
        const val EL_DEFAULT_VOICE = "pNInz6obpgDQGcFmaJgB"

        /**
         * ═══ v2.0 — CHUP MODE ═══
         *
         * User bolta tha "ab kuch mat bolna" — app phir bhi
         * bolti rehti thi. Wajah: aisa koi mode tha hi nahi.
         *
         * Ab ye flag har say() ke shuru me dekha jata hai.
         * Chup mode me jawab sirf LIKHA hua aayega.
         *
         * ⚠️ Ye phone ke restart pe bhi yaad rehta hai, isliye
         *    Keys me save hota hai — warna user ko lagta ki
         *    hukum bhool gaya.
         */
        @Volatile
        private var muted: Boolean? = null

        fun isMuted(c: Context): Boolean {
            muted?.let { return it }
            val v = try { Keys(c).flag("voice_muted", false) }
                    catch (e: Exception) { false }
            muted = v
            return v
        }

        fun setMuted(c: Context, v: Boolean) {
            muted = v
            try { Keys(c).setFlag("voice_muted", v) } catch (e: Exception) {}
            Brain.log(if (v) "🔇 chup mode ON" else "🔊 chup mode OFF")
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private val keys = Keys(ctx)

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                // Hindi try karo, na mile to English
                val r = tts?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA ||
                    r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                tts?.setSpeechRate(1.02f)
                tts?.setPitch(0.92f)      // thoda bhaari — IEYE RIS jaisa
                ttsReady = true
            }
        }
    }

    /**
     * Bolo. Sarvam pehle, phir Android.
     *
     * ⚠️ onDone PAKKA chalega — chahe kuch bhi ho jaye. Isi pe
     *    mic wapas chalu hota hai, isliye ye miss nahi hona
     *    chahiye. Isliye `once` lagaya hai (do baar na chale)
     *    aur ek safety timer bhi.
     */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        val fired = java.util.concurrent.atomic.AtomicBoolean(false)
        val once = {
            if (fired.compareAndSet(false, true)) onDone?.invoke()
        }
        if (text.isBlank()) { once(); return }

        // 🔇 v2.0 — "ab kuch mat bolna" wala hukum yahan lagta hai.
        //    Bolna skip, par onDone PHIR BHI chalega — warna mic
        //    wapas chalu nahi hoga aur app atak jayegi.
        if (isMuted(ctx)) {
            Brain.log("🔇 chup mode — bola nahi: ${text.take(30)}")
            once(); return
        }

        // 🛑 v4.1 — STOP daba hai to bolo mat.
        //    Warna user STOP dabata tha aur IEYE RIS phir bhi
        //    poora jawab bol kar sunata rehta tha — sabse
        //    chidhane wali baat.
        if (Halt.stopped()) {
            Brain.log("🛑 stop — bola nahi")
            once(); return
        }

        stop()

        // Safety — 25 sec me kuch na ho to bhi aage badho
        val guard = Runnable { once() }
        hMain.postDelayed(guard, 25_000)
        val fin = {
            hMain.removeCallbacks(guard)
            once()
        }

        // ⚡ Chhota jawab ("Torch chalu") ke liye Sarvam ka 1 second
        //    intezaar bekaar hai — Android turant bol deta hai.
        if (text.length < 26 && ttsReady) { android(text, fin); return }

        // 🔊 AWAAZ KI LADDER
        //
        //   1. ElevenLabs  — sabse asli. Apni clone ki hui awaaz
        //                    bhi chal sakti hai.
        //   2. Sarvam      — Indian awaaz, Hinglish achhi
        //   3. Android TTS — hamesha kaam karta hai, offline bhi
        //
        // ⚠️ Har step fail hone par agla chalta hai. IEYE RIS
        //    kabhi chup nahi rehta.
        val el = keys.eleven()
        if (keys.useEleven() && el.isNotBlank()) {
            Thread {
                val f = try { elevenTTS(text) } catch (e: Exception) {
                    null }
                hMain.post {
                    if (f != null) { playFile(f, fin); return@post }
                    // ElevenLabs fail -> Sarvam -> Android
                    if (keys.useSarvam() && keys.sarvam().isNotBlank()) {
                        Thread {
                            val g = try { sarvamTTS(text) }
                                    catch (e: Exception) { null }
                            hMain.post {
                                if (g != null) playFile(g, fin)
                                else android(text, fin)
                            }
                        }.start()
                    } else android(text, fin)
                }
            }.start()
            return
        }

        if (keys.useSarvam() && keys.sarvam().isNotBlank()) {
            Thread {
                val f = try { sarvamTTS(text) } catch (e: Exception) {
                    null }
                hMain.post {
                    if (f != null) playFile(f, fin) else android(text, fin)
                }
            }.start()
        } else android(text, fin)
    }

    private val hMain = android.os.Handler(
        android.os.Looper.getMainLooper())

    private fun android(text: String, onDone: (() -> Unit)?) {
        if (!ttsReady) { onDone?.invoke(); return }
        tts?.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { onDone?.invoke() }
                @Deprecated("old api")
                override fun onError(id: String?) { onDone?.invoke() }
            })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jv")
    }

    /**
     * 🎙 ELEVENLABS — sabse asli awaaz.
     *
     * Yahi wo hai jisme aap apni khud ki awaaz clone karke
     * laga sakte ho. Settings me Voice ID daal dijiye.
     *
     * ⚠️ ElevenLabs seedha MP3 bytes deta hai (Sarvam ki tarah
     *    base64 JSON nahi). Isliye stream ko seedha file me
     *    likhte hain.
     *
     * ⚠️ Free plan me 10,000 akshar/mahina milte hain. Isliye
     *    text 700 pe kaat dete hain — warna ek lambi baat me
     *    hi cota khatam ho jayega.
     */
    private fun elevenTTS(text: String): File? = try {
        val vid = keys.elevenVoice().ifBlank { EL_DEFAULT_VOICE }
        val body = JSONObject().apply {
            put("text", text.take(700))
            put("model_id", keys.elevenModel()
                .ifBlank { "eleven_multilingual_v2" })
            put("voice_settings", JSONObject().apply {
                put("stability", 0.45)
                put("similarity_boost", 0.80)
                put("style", 0.15)
                put("use_speaker_boost", true)
            })
        }
        val c = (URL("https://api.elevenlabs.io/v1/text-to-speech/" +
                     vid + "?output_format=mp3_22050_32")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg")
            setRequestProperty("xi-api-key", keys.eleven())
        }
        OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
        if (c.responseCode !in 200..299) {
            val err = try {
                c.errorStream?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) { "" }
            Brain.log("11L ${c.responseCode}: ${(err ?: "").take(90)}")
            null
        } else {
            val f = File(ctx.cacheDir, "el_say.mp3")
            c.inputStream.use { i -> f.outputStream().use { o ->
                i.copyTo(o) } }
            if (f.length() > 500) f else null
        }
    } catch (e: Exception) {
        Brain.log("11L fail: " + (e.message ?: "").take(60))
        null
    }

    /**
     * Sarvam se awaaz banwao.
     * ⚠️ Sarvam base64 WAV deta hai — usko file me likhna padta hai.
     */
    private fun sarvamTTS(text: String): File? = try {
        val body = JSONObject().apply {
            put("text", text.take(480))
            put("target_language_code", "hi-IN")
            put("speaker", keys.voice())
            put("model", "bulbul:v3")
            put("speech_sample_rate", 22050)
        }
        val c = (URL("https://api.sarvam.ai/text-to-speech")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 9000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-subscription-key", keys.sarvam())
        }
        OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
        val out = c.inputStream.bufferedReader().use { it.readText() }
        val b64 = JSONObject(out).getJSONArray("audios").getString(0)
        val bytes = android.util.Base64.decode(b64,
            android.util.Base64.DEFAULT)
        File(ctx.cacheDir, "jv_say.wav").apply { writeBytes(bytes) }
    } catch (e: Exception) { null }

    private fun playFile(f: File, onDone: (() -> Unit)?) {
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setDataSource(f.absolutePath)
                setOnCompletionListener {
                    it.release(); player = null; onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    onDone?.invoke(); true
                }
                prepare()
                start()
            }
        } catch (e: Exception) { onDone?.invoke() }
    }

    fun stop() {
        try { tts?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
    }
}
