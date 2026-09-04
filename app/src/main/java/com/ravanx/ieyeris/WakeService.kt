package com.ravanx.ieyeris

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 👂 WAKE SERVICE — "IEYE RIS" sunne wali service
 *
 * ⚠️ IMANDARI SE 3 BAATEIN:
 *
 * 1. Android me asli "always-on wake word" (jaise Google ka
 *    "Hey Google") ke liye phone ke chip ka special hardware
 *    chahiye, jo sirf Google/Samsung ko milta hai. Normal app
 *    ye nahi kar sakti.
 *
 * 2. Isliye ye service baar-baar chhota-chhota sunti hai aur
 *    check karti hai ki "IEYE RIS" bola gaya ya nahi. Kaam karta
 *    hai, par BATTERY zyada khaata hai.
 *
 * 3. Notification hamesha dikhega — Android ka niyam hai, hata
 *    nahi sakte. Isse pata chalta hai ki mic chalu hai.
 *
 * Battery bachane ke liye Settings me band kar sakte hain.
 */
class WakeService : Service() {

    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var fails = 0

    companion object {
        const val CH = "ieyeris_wake"
        const val ID = 7001

        /**
         * v4.0 — service abhi chal rahi hai?
         *
         * "wake up" ko pata hona chahiye ki kaan pehle se khule
         * hain ya nahi — warna dobara start karke do service
         * ban jaati thi aur dono mic maangti thi.
         */
        @Volatile
        var live: WakeService? = null
            private set

        fun on() = live != null

        fun start(c: Context) {
            val i = Intent(c, WakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26)
                c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, WakeService::class.java))
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        makeChannel()
        startForeground(ID, notif("Sun raha hoon — \"IEYE RIS\" boliye"))
        running = true
        live = this
        h.postDelayed({ listen() }, 700)
    }

    private fun makeChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "IEYE RIS",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Wake word sunne ke liye"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun notif(txt: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT)
        val bld = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CH) else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return bld.setContentTitle("IEYE RIS")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun listen() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf(); return
        }
        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { fails = 0 }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(p: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(e: Int) {
                    // Chup rehne pe bhi "error" aata hai — normal hai.
                    // Par lagataar fail ho to thoda ruk kar try karo,
                    // warna battery jal jayegi.
                    fails++
                    val wait = when {
                        fails > 12 -> 8000L
                        fails > 5  -> 3000L
                        else       -> 900L
                    }
                    h.postDelayed({ listen() }, wait)
                }

                override fun onResults(r: Bundle?) {
                    val heard = r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: arrayListOf()
                    check(heard)
                    h.postDelayed({ listen() }, 600)
                }

                override fun onPartialResults(r: Bundle?) {
                    val heard = r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: arrayListOf()
                    if (hasWake(heard)) {
                        try { rec?.cancel() } catch (ex: Exception) {}
                        wake()
                    }
                }
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try { rec?.startListening(i) } catch (e: Exception) {
            h.postDelayed({ listen() }, 3000)
        }
    }

    /** "IEYE RIS" ke jitne tarike log bolte/likhte hain */
    /**
     * Wake word suna?
     *
     * ⚠️⚠️ YAHI "AUTO BACK" KA ASLI BUG THA ⚠️⚠️
     *
     *   Purani list me "service" pada tha (JARVIS ke zamane
     *   ka — phone "jarvis" ko "service" sun leta tha).
     *
     *   Natija: mic background me sunta rehta tha, aur jaise
     *   hi kahin "service" jaisa shabd aaya —
     *       "customer service"
     *       "google play services"
     *       "is service ko band karo"
     *   — WakeService MainActivity ko SAAMNE PHENK deta tha.
     *
     *   User koi aur app khol raha hota tha, aur achanak
     *   IEYE RIS upar aa jaata tha. Usko lagta tha "app
     *   apne aap back ho gaya".
     *
     *   Ab:
     *     • "service" list se HATA diya
     *     • contains ki jagah POORA SHABD match
     *     • wake word 4 akshar se chhota ho to bhi poora match
     */
    private fun hasWake(list: List<String>): Boolean {
        val w = Keys(this).wakeWord().lowercase().trim()
        val alt = mutableListOf<String>()
        if (w.isNotBlank()) alt.add(w)
        alt.addAll(listOf(
            "ieye ris", "ieyeris", "eye ris", "iris",
            "आई रिस", "आईरिस"))
        return list.any { s ->
            val t = " " + s.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ").trim() + " "
            alt.any { a ->
                a.isNotBlank() && t.contains(" $a ")
            }
        }
    }

    private fun check(list: List<String>) {
        if (hasWake(list)) wake()
    }

    private fun wake() {
        // 🎙 AWAAZ CHECK — malik hai ya koi aur?
        //
        // ⚠️ Ye sirf tab chalta hai jab user ne Settings me
        //    khud chalu kiya ho AUR apni awaaz record ki ho.
        //    Default OFF hai — kyunki ye taala nahi hai, aur
        //    galti se bhi aapko rok sakta hai.
        if (VoiceID.enabled(this) && VoiceID.enrolled(this)) {
            Thread {
                val (ok, sc, _) = try { VoiceID.verify(this) }
                    catch (e: Exception) { Triple(true, 1f, "") }
                Brain.log("🎙 voice check: " +
                          "${(sc * 100).toInt()}% -> " +
                          (if (ok) "PASS" else "FAIL"))
                if (ok) h.post { openApp() }
                else h.post {
                    // chup-chaap mat raho — user ko batao
                    try {
                        Voice(this).say(
                            "Awaaz pehchan me nahi aayi.")
                    } catch (e: Exception) {}
                    h.postDelayed({ listen() }, 1200)
                }
            }.start()
            return
        }
        openApp()
    }

    private fun openApp() {
        // ⚠️ Do baar wake ke beech kam se kam 6 second.
        //    Warna ek hi baat pe (partial + final result)
        //    do baar khul jaata tha, aur user ko lagta tha
        //    app baar-baar saamne aa raha hai.
        val now = System.currentTimeMillis()
        if (now - lastWake < 6000) {
            Brain.log("👂 wake ignore (abhi-abhi hua tha)")
            return
        }
        lastWake = now

        // ⚠️ IEYE RIS pehle se saamne hai? To dobara mat kholo.
        if (MainActivity.isForeground) {
            Brain.log("👂 wake — app pehle se saamne hai")
            return
        }

        // 🔊 v4.2 — jaag gaya, awaaz do
        try { Sfx.play(this, Sfx.WAKE) } catch (e: Exception) {}

        // IEYE RIS khol do aur turant sunna shuru
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("listen", true)
        })
    }

    /** Aakhri baar kab jaga — bar-bar khulne se rok */
    private var lastWake = 0L

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        live = null
        h.removeCallbacksAndMessages(null)
        try { rec?.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }
}
