package com.example.morningpeace

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * Manages block state for Morning Block and Focus Lock.
 * Morning Block uses sleep detection (screen-off duration threshold).
 */
object BlockStateManager {

    private const val PREFS_NAME = "morningpeace_block"
    private const val KEY_IS_BLOCKED = "is_blocked"
    private const val KEY_BLOCK_START = "block_start_time"
    private const val KEY_BLOCK_DURATION = "block_duration_ms"
    private const val KEY_BLOCK_TYPE = "block_type"

    // Morning Block settings
    private const val KEY_MORNING_ENABLED = "morning_enabled"
    private const val KEY_INACTIVITY_THRESHOLD_HOURS = "inactivity_threshold_hours"
    private const val KEY_MORNING_DURATION_MINUTES = "morning_duration_minutes"

    // Sleep detection
    private const val KEY_SCREEN_OFF_TIME = "screen_off_time"

    // Focus Lock settings
    private const val KEY_FOCUS_DURATION_MINUTES = "focus_duration_minutes"

    const val ACTION_BLOCK_STATE_CHANGED = "com.example.morningpeace.BLOCK_STATE_CHANGED"
    const val ACTION_AUTO_UNLOCK = "com.example.morningpeace.AUTO_UNLOCK"
    const val EXTRA_IS_BLOCKED = "is_blocked"

    const val BLOCK_TYPE_MORNING = "morning"
    const val BLOCK_TYPE_FOCUS = "focus"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ══════════════════════════════════════════
    // Block Activation / Deactivation
    // ══════════════════════════════════════════

    fun activateBlock(context: Context, type: String, durationMinutes: Int) {
        val durationMs = durationMinutes * 60 * 1000L
        val now = System.currentTimeMillis()
        getPrefs(context).edit()
            .putBoolean(KEY_IS_BLOCKED, true)
            .putLong(KEY_BLOCK_START, now)
            .putLong(KEY_BLOCK_DURATION, durationMs)
            .putString(KEY_BLOCK_TYPE, type)
            .apply()
        scheduleAutoUnlock(context, durationMs)
        broadcastBlockState(context, true)
    }

    fun deactivateBlock(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_BLOCKED, false)
            .remove(KEY_BLOCK_START)
            .remove(KEY_BLOCK_DURATION)
            .remove(KEY_BLOCK_TYPE)
            .apply()
        cancelAutoUnlock(context)
        broadcastBlockState(context, false)
    }

    fun isBlocked(context: Context): Boolean {
        val prefs = getPrefs(context)
        val blocked = prefs.getBoolean(KEY_IS_BLOCKED, false)
        if (!blocked) return false
        val start = prefs.getLong(KEY_BLOCK_START, 0L)
        val duration = prefs.getLong(KEY_BLOCK_DURATION, 0L)
        if (start > 0 && duration > 0 && System.currentTimeMillis() - start >= duration) {
            deactivateBlock(context)
            return false
        }
        return true
    }

    fun getBlockType(context: Context): String {
        return getPrefs(context).getString(KEY_BLOCK_TYPE, "") ?: ""
    }

    fun getRemainingBlockTime(context: Context): Long {
        val prefs = getPrefs(context)
        val start = prefs.getLong(KEY_BLOCK_START, 0L)
        val duration = prefs.getLong(KEY_BLOCK_DURATION, 0L)
        if (start == 0L || duration == 0L) return 0L
        val remaining = duration - (System.currentTimeMillis() - start)
        return if (remaining > 0) remaining else 0L
    }

    // ══════════════════════════════════════════
    // Morning Block / Sleep Detection Settings
    // ══════════════════════════════════════════

    fun isMorningBlockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MORNING_ENABLED, false)
    }

    fun setMorningBlockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MORNING_ENABLED, enabled).apply()
    }

    fun getInactivityThresholdHours(context: Context): Int {
        return getPrefs(context).getInt(KEY_INACTIVITY_THRESHOLD_HOURS, 5)
    }

    fun setInactivityThresholdHours(context: Context, hours: Int) {
        getPrefs(context).edit().putInt(KEY_INACTIVITY_THRESHOLD_HOURS, hours).apply()
    }

    fun getMorningDurationMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_MORNING_DURATION_MINUTES, 30)
    }

    fun setMorningDurationMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_MORNING_DURATION_MINUTES, minutes).apply()
    }

    // ══════════════════════════════════════════
    // Screen Off/On Tracking (Sleep Detection)
    // ══════════════════════════════════════════

    fun recordScreenOff(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_SCREEN_OFF_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearScreenOffTime(context: Context) {
        getPrefs(context).edit().remove(KEY_SCREEN_OFF_TIME).apply()
    }

    /**
     * Called when user unlocks the phone (USER_PRESENT).
     * Returns true if inactivity threshold was exceeded (sleep detected).
     */
    fun checkSleepAndActivate(context: Context): Boolean {
        if (!isMorningBlockEnabled(context)) return false
        if (isBlocked(context)) return false

        val screenOffTime = getPrefs(context).getLong(KEY_SCREEN_OFF_TIME, 0L)
        if (screenOffTime == 0L) return false

        val offDurationMs = System.currentTimeMillis() - screenOffTime
        val thresholdMs = getInactivityThresholdHours(context) * 3600 * 1000L

        clearScreenOffTime(context)

        if (offDurationMs >= thresholdMs) {
            val durationMinutes = getMorningDurationMinutes(context)
            activateBlock(context, BLOCK_TYPE_MORNING, durationMinutes)
            return true
        }
        return false
    }

    // ══════════════════════════════════════════
    // Focus Lock Settings
    // ══════════════════════════════════════════

    fun getFocusDurationMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_FOCUS_DURATION_MINUTES, 30)
    }

    fun setFocusDurationMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_FOCUS_DURATION_MINUTES, minutes).apply()
    }

    // ══════════════════════════════════════════
    // Alarm Scheduling
    // ══════════════════════════════════════════

    private fun scheduleAutoUnlock(context: Context, durationMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoUnlockReceiver::class.java).apply {
            action = ACTION_AUTO_UNLOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + durationMs, pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + durationMs, pendingIntent)
        }
    }

    private fun cancelAutoUnlock(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoUnlockReceiver::class.java).apply {
            action = ACTION_AUTO_UNLOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun broadcastBlockState(context: Context, isBlocked: Boolean) {
        val intent = Intent(ACTION_BLOCK_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_BLOCKED, isBlocked)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
