package com.example.morningpeace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.morningpeace.databinding.ActivityLockScreenBinding

/**
 * Full-screen lock screen overlay. Shows countdown and whitelisted app shortcuts.
 * No give-up option — the block runs until the timer expires.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isBlocked = intent.getBooleanExtra(BlockStateManager.EXTRA_IS_BLOCKED, true)
            if (!isBlocked) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!BlockStateManager.isBlocked(this)) {
            finish()
            return
        }

        // Don't show over the lock screen — wait until the user has unlocked
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            finish()
            return
        }

        // Set title based on block type
        when (BlockStateManager.getBlockType(this)) {
            BlockStateManager.BLOCK_TYPE_MORNING -> {
                binding.tvLockIcon.text = "🌅"
                binding.tvLockTitle.text = getString(R.string.lock_title_morning)
                binding.tvLockSubtitle.text = getString(R.string.lock_subtitle_morning)
            }
            BlockStateManager.BLOCK_TYPE_FOCUS -> {
                binding.tvLockIcon.text = "🕊️"
                binding.tvLockTitle.text = getString(R.string.lock_title_focus)
                binding.tvLockSubtitle.text = getString(R.string.lock_subtitle_focus)
            }
            else -> {
                binding.tvLockIcon.text = "🔒"
            }
        }

        startCountdown()
        loadWhitelistShortcuts()

        val filter = IntentFilter(BlockStateManager.ACTION_BLOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
    }

    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!BlockStateManager.isBlocked(this@LockScreenActivity)) {
                    finish()
                    return
                }
                val remaining = BlockStateManager.getRemainingBlockTime(this@LockScreenActivity)
                if (remaining <= 0) {
                    BlockStateManager.deactivateBlock(this@LockScreenActivity)
                    finish()
                    return
                }
                val hours = (remaining / 3600000).toInt()
                val minutes = ((remaining % 3600000) / 60000).toInt()
                val seconds = ((remaining % 60000) / 1000).toInt()
                binding.tvCountdown.text = if (hours > 0) {
                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(countdownRunnable!!)
    }

    /**
     * Load whitelisted apps and display them as tappable shortcuts.
     */
    private fun loadWhitelistShortcuts() {
        val pm = packageManager
        val userWhitelist = WhitelistManager.getUserWhitelist(this)
        val container = binding.llWhitelistApps

        var hasApps = false
        for (pkg in userWhitelist) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val appIcon = pm.getApplicationIcon(appInfo)
                val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue

                val itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_whitelist_app, container, false)

                itemView.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(appIcon)
                itemView.findViewById<TextView>(R.id.tvAppName).text = appName

                itemView.setOnClickListener {
                    startActivity(launchIntent)
                }

                container.addView(itemView)
                hasApps = true
            } catch (_: PackageManager.NameNotFoundException) {
                // App not installed, skip
            }
        }

        binding.tvAllowedLabel.visibility = if (hasApps) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Cannot dismiss lock screen
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!BlockStateManager.isBlocked(this)) finish()
    }

    override fun onDestroy() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
