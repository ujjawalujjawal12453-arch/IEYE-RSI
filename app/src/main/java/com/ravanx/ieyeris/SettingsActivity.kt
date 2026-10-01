package com.ravanx.ieyeris

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ravanx.ieyeris.databinding.ActivitySettingsBinding

/**
 * ⚙️ SETTINGS — API keys aur awaaz
 * Saari key ENCRYPTED storage me jaati hai (Keys.kt).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        val k = Keys(this)

        /*
         * 🔒 v4.5 — API KEYS TAALE ME
         *
         * User ne kaha: "jab bhi wo aata hai na andar to wo kisi
         * bhi API vagaira ko CHANGE nahi kar sakta aur na hi
         * DEKH sakta hai"
         *
         * ⚠️ Yahan ek badi galti aasani se ho sakti thi: sirf
         *    box ko `visibility = GONE` karna KAAFI NAHI HAI.
         *    Field me text to phir bhi bhara hota — aur Android
         *    ka autofill / accessibility service use padh sakta
         *    hai. Isliye locked halat me field me ASLI KEY
         *    DAALTE HI NAHI. Sirf "••••••••••••" jaata hai.
         *
         *    Aur save karte waqt bhi: agar field me abhi bhi
         *    dots hain (matlab user ne khola hi nahi tha), to
         *    hum us key ko CHHUTE BHI NAHI — warna asli key
         *    ke upar "••••••" likh kar sab tod dete.
         */
        paintApi(k)

        b.wakeWord.setText(k.wakeWord())
        b.sarvamVoice.isChecked = k.useSarvam()
        b.elevenOn.isChecked = k.useEleven()

        b.apiUnlock.setOnClickListener {
            if (Owner.unlocked()) {
                // dobara dabaya -> band kar do
                Owner.lock()
                paintApi(Keys(this))
                Toast.makeText(this, "🔒 Wapas taala lag gaya",
                    Toast.LENGTH_SHORT).show()
            } else {
                Owner.ask(this,
                    "API keys dekhne/badalne ke liye " +
                    "password chahiye.") {
                    paintApi(Keys(this))
                }
            }
        }

        // 🎙 Voice ID
        b.vidOn.isChecked = VoiceID.enabled(this)
        b.vidStrict.progress = VoiceID.strictness(this)
        refreshVid()

        b.save.setOnClickListener {
            /*
             * ⚠️ API keys SIRF tab save hoti hain jab taala khula
             *    ho. Locked me field me dots hain — unhe save kar
             *    diya to asli key mit jayegi aur AI kaam karna
             *    band kar degi. Ye bug bahut chupa hua hota.
             */
            if (Owner.unlocked()) {
                k.set("groq", b.groq.text.toString())
                k.set("cf_acc", b.cfAcc.text.toString())
                k.set("cf_tok", b.cfTok.text.toString())
                k.set("sarvam", b.sarvam.text.toString())
                k.set("eleven", b.eleven.text.toString())
                k.set("eleven_voice", b.elevenVoice.text.toString())
            }
            k.set("wake_word",
                b.wakeWord.text.toString().ifBlank { "IEYE RIS" })
            k.setFlag("sarvam_voice", b.sarvamVoice.isChecked)
            k.setFlag("eleven_on", b.elevenOn.isChecked)
            VoiceID.setEnabled(this, b.vidOn.isChecked)
            VoiceID.setStrictness(this, b.vidStrict.progress)
            Toast.makeText(this, "Save ho gaya sir ✅",
                Toast.LENGTH_SHORT).show()
            finish()
        }
        b.back.setOnClickListener { finish() }

        // ═══ ⏳ v3.0 — NAKHRE (kitna sabr) ═══
        b.patBar.progress = k.patience() - 1
        b.patLabel.text = k.patienceLabel()
        b.patBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                    k.setPatience(p + 1)
                    b.patLabel.text = k.patienceLabel()
                }
                override fun onStartTrackingTouch(
                    sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(
                    sb: android.widget.SeekBar?) {}
            })

        // ═══ 🔍 v3.0 — PHONE SCAN ═══
        b.scanInfo.text = PhoneScan.lastReport(this).take(200)
        b.rescan.setOnClickListener {
            b.rescan.text = "🔍  Dekh raha hoon…"
            b.rescan.isEnabled = false
            Thread {
                // ⚠️ background — 100+ app scan me 1-3 sec lagte hain
                val r = try { PhoneScan.run(this) }
                        catch (e: Exception) { null }
                runOnUiThread {
                    b.rescan.text = "🔍  Phone dobara scan karo"
                    b.rescan.isEnabled = true
                    if (r == null) {
                        Toast.makeText(this, "Scan fail",
                            Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    b.scanInfo.text =
                        PhoneScan.lastReport(this).take(200)
                    val tv = android.widget.TextView(this)
                    tv.text = PhoneScan.report(r)
                    tv.textSize = 12.5f
                    tv.setTextIsSelectable(true)
                    tv.typeface = android.graphics.Typeface.MONOSPACE
                    tv.setTextColor(0xFFE2E8F0.toInt())
                    val p = (18 * resources.displayMetrics.density).toInt()
                    tv.setPadding(p, p, p, p)
                    val sc = android.widget.ScrollView(this)
                    sc.addView(tv)
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(sc)
                        .setPositiveButton("Theek hai", null)
                        .show()
                }
            }.start()
        }

        // ═══ 🔊 ElevenLabs — awaaz sun kar dekho ═══
        b.elevenTest.setOnClickListener {
            // ⚠️ locked me field me dots hain — save kiya to
            //    asli key mit jayegi
            if (Owner.unlocked()) {
                k.set("eleven", b.eleven.text.toString())
                k.set("eleven_voice", b.elevenVoice.text.toString())
            }
            k.setFlag("eleven_on", true)
            if (k.eleven().isBlank()) {
                Toast.makeText(this, "Pehle API key daaliye",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            b.elevenTest.text = "🔊  Bol raha hoon…"
            Voice(this).say(
                "Namaste sir. Main IEYE RIS hoon. " +
                "Ab main is awaaz me baat karunga."
            ) {
                runOnUiThread {
                    b.elevenTest.text = "🔊  Awaaz sun kar dekho"
                }
            }
        }

        // ═══ 🎤 Awaaz record karo ═══
        b.vidEnroll.setOnClickListener { enrollVoice() }

        // ═══ ✓ Test ═══
        b.vidTest.setOnClickListener {
            if (!VoiceID.enrolled(this)) {
                Toast.makeText(this, "Pehle awaaz record kariye",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            VoiceID.setStrictness(this, b.vidStrict.progress)
            b.vidTest.text = "🎤  Boliye… (2 sec)"
            Thread {
                val (ok, sc, msg) = VoiceID.verify(this)
                runOnUiThread {
                    b.vidTest.text = "✓  Test karo"
                    val pct = (sc * 100).toInt()
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(if (ok) "✅ Pehchan liya"
                                  else "❌ Pehchan nahi aaya")
                        .setMessage("Match: $pct%\n$msg\n\n" +
                            "Kam aa raha hai to 'kitna kada' " +
                            "wali line neeche khiskaiye.")
                        .setPositiveButton("Theek hai", null)
                        .show()
                }
            }.start()
        }

        // ═══ 🗑 Mitao ═══
        b.vidDelete.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Awaaz profile mitani hai?")
                .setMessage("Aapki awaaz ka record poori tarah " +
                            "mit jayega. Dobara record karna hoga.")
                .setPositiveButton("Haan, mitao") { _, _ ->
                    VoiceID.delete(this)
                    b.vidOn.isChecked = false
                    refreshVid()
                    Toast.makeText(this, "Mit gaya",
                        Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Nahi", null)
                .show()
        }

        // ═══ 🩺 AI JAANCH ═══
        //
        // User ko shak tha "API use hi nahi ho rahi". Ab wo
        // khud dekh sakta hai — har model ko sach me poochha
        // jaata hai aur natija samne aata hai.
        b.diag.setOnClickListener {
            // pehle jo type kiya hai wo save karo, warna purani
            // key se test hoga
            if (Owner.unlocked()) {
                k.set("groq", b.groq.text.toString().trim())
                k.set("cf_acc", b.cfAcc.text.toString().trim())
                k.set("cf_tok", b.cfTok.text.toString().trim())
            }

            val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🩺 Jaanch chal rahi hai…")
                .setMessage("Har AI model ko sach me poochh raha "
                    + "hoon.\nThoda ruko sir…")
                .setCancelable(false)
                .show()

            Thread {
                var out = try { Brain.diagnose(this) }
                    catch (e: Exception) {
                        "Jaanch me dikkat: " + e.message }

                // 🐞 v1.6 — AAKHRI CRASH bhi dikhao
                //
                //    App chup-chaap band ho jaye to yahan uski
                //    poori wajah likhi milegi. Ye sabse kaam ki
                //    cheez hai jab "app khulte hi band ho jata hai"
                //    jaisi dikkat aaye.
                out += "\n\n" + "─".repeat(34) + "\n🐞 AAKHRI CRASH\n"
                out += try {
                    val f = java.io.File(filesDir, "last_crash.txt")
                    if (f.exists()) f.readText().take(2400)
                    else "Koi crash nahi hua ✅"
                } catch (e: Exception) { "padha nahi ja saka" }

                // 🧠 v2.1 — MEMORY ka poora haal
                out += "\n\n" + "\u2500".repeat(34) + "\n"
                out += try { Memory(this).report() }
                       catch (e: Exception) { "memory padhi nahi gayi" }

                // 🔐 Store kaunsi chal rahi hai
                out += "\n\n" + "─".repeat(34) + "\n🔐 SETTING STORE\n"
                out += try {
                    val m = getSharedPreferences("ieyeris_store",
                        MODE_PRIVATE)
                    if (m.getBoolean("use_plain", false))
                        "PLAIN (encrypted store fail hui thi)\n" +
                        "⚠️ Ye normal hai — app phir bhi theek chalti hai"
                    else "ENCRYPTED ✅"
                } catch (e: Exception) { "pata nahi chala" }

                runOnUiThread {
                    dlg.dismiss()
                    val tv = android.widget.TextView(this)
                    tv.text = out
                    tv.setTextIsSelectable(true)
                    tv.textSize = 12f
                    tv.typeface = android.graphics.Typeface.MONOSPACE
                    tv.setTextColor(0xFFE2E8F0.toInt())
                    val pad = (16 * resources.displayMetrics.density)
                        .toInt()
                    tv.setPadding(pad, pad, pad, pad)
                    val sc = android.widget.ScrollView(this)
                    sc.addView(tv)

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(sc)
                        .setPositiveButton("Theek hai", null)
                        .setNeutralButton("Copy karo") { _, _ ->
                            val cm = getSystemService(
                                CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "IEYE RIS", out))
                            Toast.makeText(this,
                                "Copy ho gaya — mujhe bhej dijiye",
                                Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
            }.start()
        }
    }

    /**
     * 🔒 API section ka rang-roop — khula hai ya band.
     *
     * Locked  -> box chhupa, fields me sirf dots
     * Unlocked-> box khula, asli key dikhti hai
     */
    private fun paintApi(k: Keys) {
        val open = Owner.unlocked()
        val vis = if (open) android.view.View.VISIBLE
                  else android.view.View.GONE
        val inv = if (open) android.view.View.GONE
                  else android.view.View.VISIBLE

        b.apiBox.visibility = vis
        b.apiBox2.visibility = vis
        b.apiLock.visibility = inv
        b.eleven2Lock.visibility = inv

        if (open) {
            b.groq.setText(k.groq())
            b.cfAcc.setText(k.cfAcc())
            b.cfTok.setText(k.cfTok())
            b.sarvam.setText(k.sarvam())
            b.eleven.setText(k.eleven())
            b.elevenVoice.setText(k.elevenVoice())
            b.apiUnlock.text = "🔓  KHULA HAI — " +
                Owner.minsLeft() + " min  (band karne ko dabao)"
            b.apiLock.text = ""
        } else {
            // ⚠️ ASLI KEY YAHAN BILKUL NAHI JAATI
            b.groq.setText(Owner.mask(k.groq()))
            b.cfAcc.setText(Owner.mask(k.cfAcc()))
            b.cfTok.setText(Owner.mask(k.cfTok()))
            b.sarvam.setText(Owner.mask(k.sarvam()))
            b.eleven.setText(Owner.mask(k.eleven()))
            b.elevenVoice.setText("")
            b.apiUnlock.text = "🔓  KHOLO"
            b.apiLock.text =
                "🔒  API keys taale me hain\n" +
                "Groq · Cloudflare · Sarvam · ElevenLabs\n\n" +
                "Neeche 🔓 KHOLO dabaiye"
        }
    }

    /** Voice ID ka haal dikhao */
    private fun refreshVid() {
        val n = VoiceID.sampleCount(this)
        b.vidStatus.text = if (n > 0)
            "✅ Awaaz yaad hai ($n sample se bani)"
        else
            "Abhi setup nahi hua — neeche wala button dabaiye"
        b.vidStatus.setTextColor(
            if (n > 0) 0xFF22C55E.toInt() else 0xFF94A3B8.toInt())
    }

    /**
     * 🎤 Awaaz record — 3 baar.
     *
     * ⚠️ Har baar 3 second. Beech me user ko sochne ka waqt
     *    dena zaroori hai, warna wo taiyaar hone se pehle hi
     *    recording shuru ho jaati hai aur khali sample jaata hai.
     */
    private fun enrollVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO), 91)
            return
        }
        VoiceID.clearPending()
        val lines = listOf(
            "Namaste, main IEYE RIS ka malik hoon",
            "Aaj ka mausam kaisa hai bataiye",
            "IEYE RIS, mera phone chalao")
        step(0, lines)
    }

    private fun step(i: Int, lines: List<String>) {
        if (i >= VoiceID.NEED_SAMPLES) {
            val (ok, msg) = VoiceID.finish(this)
            refreshVid()
            if (ok) b.vidOn.isChecked = true
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(if (ok) "✅ Ho gaya" else "⚠️ Dobara")
                .setMessage(msg)
                .setPositiveButton("Theek hai", null)
                .show()
            return
        }
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sample ${i + 1} / ${VoiceID.NEED_SAMPLES}")
            .setMessage("Ye line saaf-saaf boliye:\n\n" +
                        "\"${lines[i]}\"\n\n" +
                        "Ready hain? Neeche daba kar bolna shuru " +
                        "kar dijiye — ${VoiceID.SAMPLE_SEC} second " +
                        "sunuga.")
            .setCancelable(false)
            .setPositiveButton("Bol raha hoon", null)
            .setNegativeButton("Cancel") { _, _ ->
                VoiceID.clearPending() }
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val btn = dlg.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                btn.isEnabled = false
                btn.text = "🎤 Sun raha hoon…"
                Thread {
                    val (ok, msg) = VoiceID.addSample()
                    runOnUiThread {
                        dlg.dismiss()
                        if (!ok) {
                            androidx.appcompat.app.AlertDialog
                                .Builder(this)
                                .setTitle("⚠️ Nahi hua")
                                .setMessage(msg)
                                .setPositiveButton("Dobara") { _, _ ->
                                    step(i, lines) }
                                .setNegativeButton("Rehne do", null)
                                .show()
                        } else {
                            Toast.makeText(this, msg,
                                Toast.LENGTH_SHORT).show()
                            b.vidStatus.postDelayed(
                                { step(i + 1, lines) }, 600)
                        }
                    }
                }.start()
            }
    }
}
