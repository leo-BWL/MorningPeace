package com.example.morningpeace

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.morningpeace.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var activeBlockCountdownRunnable: Runnable? = null
    private lateinit var updateManager: UpdateManager

    private val blockStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateUI() }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateManager = UpdateManager(this)
        requestNotificationPermission()
        setupMorningBlock()
        setupFocusLock()
        setupWhitelist()
        setupServiceStatus()
        updateGreeting()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        updateManager.checkForUpdate()
        val filter = IntentFilter(BlockStateManager.ACTION_BLOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(blockStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(blockStateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        activeBlockCountdownRunnable?.let { handler.removeCallbacks(it) }
        try { unregisterReceiver(blockStateReceiver) } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════
    // Morning Block (Sleep Detection + Custom Duration)
    // ══════════════════════════════════════════

    private fun setupMorningBlock() {
        binding.switchMorningBlock.isChecked = BlockStateManager.isMorningBlockEnabled(this)
        updateSleepThresholdDisplay()

        // Init morning duration input
        val savedMorningDuration = BlockStateManager.getMorningDurationMinutes(this)
        setMorningInput(savedMorningDuration / 60, savedMorningDuration % 60)

        binding.switchMorningBlock.setOnCheckedChangeListener { _, isChecked ->
            BlockStateManager.setMorningBlockEnabled(this, isChecked)
        }

        // Sleep threshold picker
        binding.rowSleepThreshold.setOnClickListener {
            val current = BlockStateManager.getInactivityThresholdHours(this)
            val options = arrayOf("1 hour", "2 hours", "3 hours", "4 hours", "5 hours", "6 hours", "7 hours", "8 hours")
            val values = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.sleep_threshold))
                .setSingleChoiceItems(options, values.indexOf(current).coerceAtLeast(0)) { dlg, which ->
                    BlockStateManager.setInactivityThresholdHours(this, values[which])
                    updateSleepThresholdDisplay()
                    dlg.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // Morning duration chips
        binding.chipMorning15m.setOnClickListener { setMorningInput(0, 15) }
        binding.chipMorning30m.setOnClickListener { setMorningInput(0, 30) }
        binding.chipMorning1h.setOnClickListener { setMorningInput(1, 0) }
        binding.chipMorning2h.setOnClickListener { setMorningInput(2, 0) }

        // Save morning duration when input changes
        val morningWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val minutes = getMorningInputMinutes()
                if (minutes > 0) BlockStateManager.setMorningDurationMinutes(this@MainActivity, minutes)
                updateMorningChipHighlights()
            }
        }
        binding.etMorningHours.addTextChangedListener(morningWatcher)
        binding.etMorningMinutes.addTextChangedListener(morningWatcher)
    }

    private fun setMorningInput(hours: Int, minutes: Int) {
        binding.etMorningHours.setText(if (hours > 0) hours.toString() else "")
        binding.etMorningMinutes.setText(if (minutes > 0) minutes.toString() else "")
        val total = hours * 60 + minutes
        if (total > 0) BlockStateManager.setMorningDurationMinutes(this, total)
        updateMorningChipHighlights()
    }

    private fun getMorningInputMinutes(): Int {
        val h = binding.etMorningHours.text.toString().toIntOrNull() ?: 0
        val m = binding.etMorningMinutes.text.toString().toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun updateMorningChipHighlights() {
        val total = getMorningInputMinutes()
        val chips = mapOf(15 to binding.chipMorning15m, 30 to binding.chipMorning30m,
            60 to binding.chipMorning1h, 120 to binding.chipMorning2h)
        chips.forEach { (v, chip) ->
            if (v == total) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(getColor(R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip)
                chip.setTextColor(getColor(R.color.text_primary))
            }
        }
    }

    private fun updateSleepThresholdDisplay() {
        binding.tvSleepThreshold.text = getString(R.string.sleep_threshold_value,
            BlockStateManager.getInactivityThresholdHours(this))
    }

    // ══════════════════════════════════════════
    // Focus Lock (Peace Time)
    // ══════════════════════════════════════════

    private fun setupFocusLock() {
        binding.chip15m.setOnClickListener { setFocusInput(0, 15) }
        binding.chip30m.setOnClickListener { setFocusInput(0, 30) }
        binding.chip1h.setOnClickListener { setFocusInput(1, 0) }
        binding.chip2h.setOnClickListener { setFocusInput(2, 0) }

        val focusWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateFocusChipHighlights() }
        }
        binding.etFocusHours.addTextChangedListener(focusWatcher)
        binding.etFocusMinutes.addTextChangedListener(focusWatcher)
        setFocusInput(0, 30)

        binding.btnStartFocus.setOnClickListener {
            val totalMinutes = getFocusInputMinutes()
            if (totalMinutes <= 0) {
                Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (BlockStateManager.isBlocked(this)) {
                Toast.makeText(this, "A block is already active!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Please enable the Blocker Service first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Peace Time 🕊️")
                .setMessage("Lock your phone for ${formatDuration(totalMinutes)}?\n\nYou won't be able to cancel until it's done.")
                .setPositiveButton(getString(R.string.start_focus)) { _, _ ->
                    BlockStateManager.setFocusDurationMinutes(this, totalMinutes)
                    BlockStateManager.activateBlock(this, BlockStateManager.BLOCK_TYPE_FOCUS, totalMinutes)
                    startActivity(Intent(this, LockScreenActivity::class.java))
                    updateUI()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setFocusInput(hours: Int, minutes: Int) {
        binding.etFocusHours.setText(if (hours > 0) hours.toString() else "")
        binding.etFocusMinutes.setText(if (minutes > 0) minutes.toString() else "")
        updateFocusChipHighlights()
    }

    private fun getFocusInputMinutes(): Int {
        val h = binding.etFocusHours.text.toString().toIntOrNull() ?: 0
        val m = binding.etFocusMinutes.text.toString().toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun updateFocusChipHighlights() {
        val total = getFocusInputMinutes()
        val chips = mapOf(15 to binding.chip15m, 30 to binding.chip30m, 60 to binding.chip1h, 120 to binding.chip2h)
        chips.forEach { (v, chip) ->
            if (v == total) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(getColor(R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip)
                chip.setTextColor(getColor(R.color.text_primary))
            }
        }
    }

    // ══════════════════════════════════════════
    // Whitelist & Service Status
    // ══════════════════════════════════════════

    private fun setupWhitelist() {
        binding.cardWhitelist.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
    }

    private fun updateWhitelistSummary() {
        val names = WhitelistManager.getWhitelistAppNames(this)
        binding.tvWhitelistSummary.text = if (names.isEmpty()) getString(R.string.whitelist_desc)
        else {
            val d = names.take(3).joinToString(", ")
            if (names.size > 3) "$d +${names.size - 3} more" else d
        }
    }

    private fun setupServiceStatus() {
        binding.btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable Morning Peace in the list", Toast.LENGTH_LONG).show()
        }
        binding.cardServiceStatus.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "Enable Morning Peace in the list", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ══════════════════════════════════════════
    // UI Updates
    // ══════════════════════════════════════════

    private fun updateUI() {
        updateServiceStatus()
        updateActiveBlockCard()
        updateWhitelistSummary()
    }

    private fun updateServiceStatus() {
        val isServiceEnabled = isAccessibilityServiceEnabled()
        val isBlocked = BlockStateManager.isBlocked(this)

        when {
            isBlocked -> {
                binding.cardServiceStatus.setBackgroundResource(R.drawable.bg_service_blocking)
                binding.tvServiceIcon.text = "🔒"
                binding.tvServiceStatus.text = when (BlockStateManager.getBlockType(this)) {
                    BlockStateManager.BLOCK_TYPE_MORNING -> getString(R.string.status_morning_block)
                    BlockStateManager.BLOCK_TYPE_FOCUS -> getString(R.string.status_focus_lock)
                    else -> getString(R.string.service_enabled)
                }
                binding.tvServiceStatus.setTextColor(getColor(R.color.white))
                binding.btnEnableService.visibility = View.GONE
            }
            isServiceEnabled -> {
                binding.cardServiceStatus.setBackgroundResource(R.drawable.bg_service_active)
                binding.tvServiceIcon.text = "🛡️"
                binding.tvServiceStatus.text = getString(R.string.service_enabled)
                binding.tvServiceStatus.setTextColor(getColor(R.color.white))
                binding.btnEnableService.visibility = View.GONE
            }
            else -> {
                binding.cardServiceStatus.setBackgroundResource(R.drawable.bg_service_inactive)
                binding.tvServiceIcon.text = "⚠️"
                binding.tvServiceStatus.text = getString(R.string.service_disabled)
                binding.tvServiceStatus.setTextColor(getColor(R.color.text_secondary))
                binding.btnEnableService.visibility = View.VISIBLE
            }
        }
    }

    private fun updateActiveBlockCard() {
        if (BlockStateManager.isBlocked(this)) {
            binding.cardActiveBlock.visibility = View.VISIBLE
            binding.tvActiveBlockType.text = when (BlockStateManager.getBlockType(this)) {
                BlockStateManager.BLOCK_TYPE_MORNING -> getString(R.string.status_morning_block)
                BlockStateManager.BLOCK_TYPE_FOCUS -> getString(R.string.status_focus_lock)
                else -> "Block Active"
            }
            startActiveBlockCountdown()
        } else {
            binding.cardActiveBlock.visibility = View.GONE
            activeBlockCountdownRunnable?.let { handler.removeCallbacks(it) }
        }
    }

    private fun startActiveBlockCountdown() {
        activeBlockCountdownRunnable?.let { handler.removeCallbacks(it) }
        activeBlockCountdownRunnable = object : Runnable {
            override fun run() {
                if (!BlockStateManager.isBlocked(this@MainActivity)) {
                    binding.cardActiveBlock.visibility = View.GONE
                    updateUI(); return
                }
                val rem = BlockStateManager.getRemainingBlockTime(this@MainActivity)
                if (rem <= 0) { BlockStateManager.deactivateBlock(this@MainActivity); updateUI(); return }
                val h = (rem / 3600000).toInt(); val m = ((rem % 3600000) / 60000).toInt(); val s = ((rem % 60000) / 1000).toInt()
                binding.tvActiveBlockCountdown.text = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(activeBlockCountdownRunnable!!)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = getString(when {
            hour < 6 -> R.string.greeting_night; hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon; hour < 21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun formatDuration(minutes: Int) = when {
        minutes < 60 -> "$minutes min"; minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
