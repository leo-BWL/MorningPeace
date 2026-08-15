package com.example.morningpeace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * No longer needed for scheduled alarms since sleep detection is event-based.
 * Kept as a no-op in case it's referenced elsewhere.
 */
class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("MorningAlarmReceiver", "Received (no-op, sleep detection is event-based)")
    }
}
