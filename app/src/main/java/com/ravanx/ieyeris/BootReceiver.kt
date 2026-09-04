package com.ravanx.ieyeris

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Phone on hote hi IEYE RIS taiyaar.
 *
 * Do kaam:
 *   1. Wake service chalu (agar user ne on rakhi hai)
 *   2. ⏰ Saare reminder DOBARA lagao
 *
 * ⚠️ #2 bahut zaroori hai. Android phone restart pe SAARE alarm
 *    mita deta hai. Bina iske user ka "roz subah 7 baje uthana"
 *    ek baar reboot hote hi hamesha ke liye khatam ho jata —
 *    aur usse pata bhi nahi chalta.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            if (Keys(c).wake()) WakeService.start(c)
        } catch (e: Exception) {}

        try {
            Reminders.rescheduleAll(c)
        } catch (e: Exception) {}
    }
}
