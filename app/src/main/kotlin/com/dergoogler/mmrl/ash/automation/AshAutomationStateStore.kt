package com.dergoogler.mmrl.ash.automation

import android.content.Context

class AshAutomationStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun shouldNotify(signal: AshAlertSignal, now: Long): Boolean {
        val prefix = signal.kind.name
        val previousKey = preferences.getString("$prefix.key", null)
        val previousAt = preferences.getLong("$prefix.at", 0L)
        return previousKey != signal.key || now - previousAt >= signal.repeatAfterMillis
    }

    fun markNotified(signal: AshAlertSignal, now: Long) {
        val prefix = signal.kind.name
        preferences.edit()
            .putString("$prefix.key", signal.key)
            .putLong("$prefix.at", now)
            .apply()
    }

    fun hasActive(kind: AshAlertKind): Boolean =
        preferences.contains("${kind.name}.key")

    fun clear(kind: AshAlertKind) {
        val prefix = kind.name
        preferences.edit()
            .remove("$prefix.key")
            .remove("$prefix.at")
            .apply()
    }

    fun clearAllSignals() {
        AshAlertKind.entries.forEach(::clear)
    }

    fun recordSuccess(now: Long, summary: String) {
        preferences.edit()
            .putLong(KEY_LAST_CHECK, now)
            .putLong(KEY_LAST_SUCCESS, now)
            .putString(KEY_LAST_SUMMARY, summary)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordFailure(now: Long, error: String) {
        preferences.edit()
            .putLong(KEY_LAST_CHECK, now)
            .putString(KEY_LAST_ERROR, error)
            .apply()
    }

    val lastCheckAt: Long
        get() = preferences.getLong(KEY_LAST_CHECK, 0L)

    companion object {
        private const val PREFERENCES = "ash_automation_state"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_SUMMARY = "last_summary"
        private const val KEY_LAST_ERROR = "last_error"
    }
}
