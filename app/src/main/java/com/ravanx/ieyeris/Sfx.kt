package com.ravanx.ieyeris

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * 🔊 SFX — chhoti awaazein
 *
 * v4.2 — pehle app bilkul chup thi. Aap IRIS dabate the aur
 * pata hi nahi chalta tha ki suna ya nahi. Ab har cheez ki
 * apni awaaz hai.
 *
 * ⚠️ SoundPool use kiya hai, MediaPlayer nahi. Wajah:
 *    MediaPlayer har baar file kholta hai — 200-400ms lagta
 *    hai. Button dabane ki awaaz itni der baad aaye to bekaar
 *    lagti hai. SoundPool pehle se memory me rakhta hai,
 *    turant bajti hai.
 *
 * ⚠️ Chup mode (Voice.isMuted) me ye BHI band ho jati hai —
 *    warna user "chup raho" bole aur phir bhi beep aaye, ye
 *    chidhane wali baat hoti.
 */
object Sfx {

    const val WAKE = "wake"
    const val LISTEN = "listen"
    const val DONE = "done"
    const val ERROR = "error"
    const val STOP = "stop"
    const val NOTIFY = "notify"
    const val START = "start"
    const val SLEEP = "sleep"

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var ready = false

    /** Ek baar sab load kar lo */
    @Synchronized
    fun init(c: Context) {
        if (ready) return
        try {
            pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build()

            for (n in listOf(WAKE, LISTEN, DONE, ERROR,
                             STOP, NOTIFY, START, SLEEP)) {
                try {
                    val afd = c.assets.openFd("sfx/$n.wav")
                    ids[n] = pool!!.load(afd, 1)
                    afd.close()
                } catch (e: Exception) {
                    Brain.log("🔊 $n load fail")
                }
            }
            ready = true
            Brain.log("🔊 ${ids.size} awaaz load hui")
        } catch (e: Exception) {
            Brain.log("🔊 SoundPool fail: ${e.message}")
        }
    }

    /** Bajao. Chup mode me kuch nahi hoga. */
    fun play(c: Context, name: String) {
        try {
            if (Voice.isMuted(c)) return
            if (!Keys(c).flag("sfx_on", true)) return
            if (!ready) init(c)
            val id = ids[name] ?: return
            pool?.play(id, 0.55f, 0.55f, 1, 0, 1f)
        } catch (e: Exception) {}
    }

    fun release() {
        try { pool?.release() } catch (e: Exception) {}
        pool = null; ids.clear(); ready = false
    }
}
