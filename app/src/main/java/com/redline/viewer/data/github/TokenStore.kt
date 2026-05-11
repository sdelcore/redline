package com.redline.viewer.data.github

import android.content.Context

class TokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("redline_auth", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_access_token"
    }
}
