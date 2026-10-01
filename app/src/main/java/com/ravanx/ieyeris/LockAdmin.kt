package com.ravanx.ieyeris

import android.app.admin.DeviceAdminReceiver

/**
 * 🔒 Phone lock karne ke liye Android ko ye chahiye.
 * Sirf lockNow() use karte hain — aur kuch nahi.
 */
class LockAdmin : DeviceAdminReceiver()
