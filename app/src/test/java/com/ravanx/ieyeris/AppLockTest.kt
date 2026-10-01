package com.ravanx.ieyeris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 📱 v4.7 — APP LOCK ke test
 *
 * parse() sirf shabd pehchanta hai (Context nahi chahiye),
 * isliye JVM pe test ho jata hai.
 */
class AppLockTest {

    // ── lock all, kuch chhod ke ──

    @Test fun chhodKeBaakiSabLock() {
        assertEquals("lock_all|phonepe",
            AppLock.parse("phonepe ko chhod ke baaki sab lock kar do"))
    }

    @Test fun chhodKeDoApp() {
        assertEquals("lock_all|phonepe,chrome",
            AppLock.parse("phonepe aur chrome ko chhod ke sab lock karo"))
    }

    @Test fun exceptWala() {
        assertEquals("lock_all|whatsapp",
            AppLock.parse("whatsapp except sab lock"))
    }

    // ── sab lock ──

    @Test fun sabAppLock() {
        assertEquals("lock_all|", AppLock.parse("sab app lock kar do"))
    }

    @Test fun saareLock() {
        assertEquals("lock_all|", AppLock.parse("saare app lock karo"))
    }

    // ── kuch khaas app lock ──

    @Test fun doAppLock() {
        assertEquals("lock|whatsapp,youtube",
            AppLock.parse("whatsapp aur youtube lock kar do"))
    }

    @Test fun ekAppLock() {
        assertEquals("lock|phonepe",
            AppLock.parse("phonepe lock kar do"))
    }

    // ── kholo ──

    @Test fun sabKholDo() {
        assertEquals("unlock_all|", AppLock.parse("sab app khol do"))
    }

    @Test fun sabUnlock() {
        assertEquals("unlock_all|", AppLock.parse("sab unlock kar do"))
    }

    @Test fun ekAppKholDo() {
        assertEquals("unlock|whatsapp",
            AppLock.parse("whatsapp khol do"))
    }

    // ── screen lock ko mat chheeno ──

    @Test fun phoneLockScreenWalaHai() {
        // ye screen lock hai, app lock nahi
        assertNull(AppLock.parse("phone lock kar do"))
        assertNull(AppLock.parse("screen lock karo"))
    }

    // ── bekaar baat ──

    @Test fun aamBaatMatPakdo() {
        assertNull(AppLock.parse("chai bana do"))
        assertNull(AppLock.parse("mausam kaisa hai"))
    }

    // ── poora word hi pakdo, aadha nahi ──

    @Test fun unlockMeLockNahi() {
        // "unlock" me "lock" chhupa hai — par wo unlock hai,
        // alag se pehchan
        assertEquals("unlock_all|", AppLock.parse("sab unlock kar do"))
    }
}
