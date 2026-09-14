package com.noqira.driver

import android.content.Context
import java.util.UUID

object SessionStore {
    private const val PREFS = "noqira_driver"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_TOKEN = "app_token"

    fun deviceId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var value = p.getString(KEY_DEVICE, null)
        if (value.isNullOrBlank()) {
            value = "DRV-" + UUID.randomUUID().toString()
            p.edit().putString(KEY_DEVICE, value).apply()
        }
        return value
    }

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply()
    }
}
