package com.djay.touchmenot

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log

object PrefsBridge {
    private const val PREFS_NAME = "tmn_prefs"
    private const val AUTH = "com.djay.touchmenot.preferences"
    val FLAGS_URI: Uri = Uri.parse("content://$AUTH/flags")

    fun makeWorldReadable(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dp = ctx.applicationContext.createDeviceProtectedStorageContext()
                dp.moveSharedPreferencesFrom(ctx.applicationContext, PREFS_NAME)
            } catch (t: Throwable) {
                Log.w("PrefsBridge", "DPS move failed: ${t.message}")
            }
        }
        val p = prefs(ctx)
        if (!p.contains("tmn_initialized")) {
            p.edit()
                .putBoolean("tmn_initialized", true)
                .putBoolean("tmn_block_airplane", false)
                .putBoolean("tmn_block_hotspot", false)
                .putBoolean("tmn_block_internet", false)
                .putBoolean("tmn_block_bluetooth", false)
                .putBoolean("tmn_block_dnd", false)
                .putBoolean("tmn_block_volume_ringer_mode", false)
                .putBoolean("tmn_block_power_controls", false)
                .apply()
            notifyChange(ctx)
        }
    }

    fun prefs(ctx: Context) =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ctx.applicationContext.createDeviceProtectedStorageContext() else ctx.applicationContext)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
        notifyChange(ctx)
    }

    fun notifyChange(ctx: Context) {
        try {
            ctx.applicationContext.contentResolver.notifyChange(FLAGS_URI, null)
        } catch (_: Throwable) {
        }
    }
}
