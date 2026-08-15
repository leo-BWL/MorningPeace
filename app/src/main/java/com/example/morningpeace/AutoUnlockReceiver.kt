package com.example.morningpeace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when the block duration expires to auto-unlock the phone.
 */
class AutoUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AutoUnlockReceiver", "Auto-unlock triggered")
        BlockStateManager.deactivateBlock(context)
    }
}
