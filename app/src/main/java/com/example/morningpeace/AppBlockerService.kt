package com.example.morningpeace

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.KeyguardManager
import android.view.inputmethod.InputMethodManager

/**
 * Accessibility Service that:
 * 1. Blocks non-whitelisted apps when a block is active
 * 2. Monitors screen off/on to detect sleep for Morning Block
 */
class AppBlockerService : AccessibilityService() {

    companion object {
        const val TAG = "AppBlockerService"
        private const val HELPER_OPEN_WINDOW_MS = 30_000L
        private const val HELPER_CONTINUE_WINDOW_MS = 120_000L
    }

    private var blockStateReceiver: BroadcastReceiver? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    private var lastBlockedTime = 0L
    private var lastUserAllowedAppTime = 0L
    private var trustedHelperPackage: String? = null
    private var trustedHelperTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AppBlockerService connected")

        // Register for block state changes
        blockStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Block state changed")
            }
        }
        val blockFilter = IntentFilter(BlockStateManager.ACTION_BLOCK_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(blockStateReceiver, blockFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(blockStateReceiver, blockFilter)
        }

        // Register for screen off/on to detect sleep
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF — recording timestamp")
                        BlockStateManager.recordScreenOff(context)
                    }
                    Intent.ACTION_USER_PRESENT,
                    Intent.ACTION_SCREEN_ON -> {
                        if (intent.action == Intent.ACTION_USER_PRESENT) {
                            Log.d(TAG, "User present — checking sleep detection")
                        } else {
                            Log.d(TAG, "Screen ON — checking keyguard + sleep detection")
                        }
                        maybeCheckSleepAndActivate(context)
                    }
                }
            }
        }
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, screenFilter)
    }

    private fun maybeCheckSleepAndActivate(context: Context) {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            Log.d(TAG, "Keyguard still locked — skipping sleep check")
            return
        }
        val triggered = BlockStateManager.checkSleepAndActivate(context)
        if (triggered) {
            Log.d(TAG, "Sleep detected! Morning block activated")
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(lockIntent)
        }
    }

    // Activity class name patterns that indicate App Info screens (bypass risk)
    private val APP_INFO_PATTERNS = listOf(
        "InstalledAppDetails",    // AOSP/Google App Info
        "InstalledAppDetailsTop", // App Info launcher
        "AppInfoBase",            // Base app info class
        "AppInfoDashboard",       // Pixel/modern Android app info
        "ApplicationDetails",     // OEM app details screens
        "AppStorageSettings",     // Clear storage/data screen
    )

    private val APP_MANAGEMENT_TEXT = listOf(
        "app info",
        "app details",
        "application info",
        "manage apps",
        "installed apps",
        "all apps",
        "force stop",
        "clear data",
        "clear storage",
        "storage & cache",
        "uninstall",
        "disable",
    )

    private val SAFE_SYSTEM_HELPER_PACKAGES = setOf(
        "com.android.documentsui",                // Android file picker
        "com.google.android.documentsui",         // Google file picker
        "com.android.providers.downloads",        // Downloads provider
        "com.android.providers.downloads.ui",     // Downloads UI
        "com.android.providers.media",            // Media picker/provider
        "com.android.providers.media.module",     // Android modular media picker
        "com.google.android.providers.media.module",
        "com.android.permissioncontroller",       // Runtime permission dialogs
        "com.google.android.permissioncontroller",
        "com.sec.android.app.myfiles",            // Samsung My Files picker
        "com.mi.android.globalFileexplorer",      // Xiaomi file picker
        "com.coloros.filemanager",                // Oppo/ColorOS file picker
        "com.oppo.filemanager",
        "com.huawei.hidisk",                      // Huawei Files
        "com.vivo.filemanager",
        "com.google.android.apps.nbu.files",      // Files by Google
    )

    private val COMMON_KEYBOARD_PACKAGES = setOf(
        "com.google.android.inputmethod.latin",    // Gboard
        "com.android.inputmethod.latin",           // AOSP keyboard
        "com.samsung.android.honeyboard",          // Samsung Keyboard
        "com.sec.android.inputmethod",             // Older Samsung Keyboard
        "com.touchtype.swiftkey",                  // Microsoft SwiftKey
        "com.grammarly.android.keyboard",          // Grammarly Keyboard
        "com.anysoftkeyboard",                     // AnySoftKeyboard
    )

    private val BLOCKED_EVENT_TYPES = setOf(
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentEvent = event ?: return
        if (currentEvent.eventType !in BLOCKED_EVENT_TYPES) return
        val packageName = currentEvent.packageName?.toString() ?: return
        val className = currentEvent.className?.toString() ?: ""

        if (!BlockStateManager.isBlocked(this)) return

        // ALWAYS block package installers (uninstall prevention)
        if (packageName in WhitelistManager.ALWAYS_BLOCKED) {
            launchLockScreen()
            return
        }

        // Block App Info screens specifically (even though Settings is whitelisted)
        // This prevents: recent apps → 3 dots → App Info → force stop / clear data
        if (isAppInfoScreen(packageName, className, currentEvent)) {
            Log.d(TAG, "Blocking App Info screen: $className")
            launchLockScreen()
            return
        }

        // Allow the active/enabled keyboard while typing inside a whitelisted app.
        if (isInputMethodPackage(packageName)) return

        // Allow safe system picker/helper screens only when a whitelisted app just opened them.
        if (isSafeSystemHelperPackage(packageName) && isSafeSystemHelperAllowed(packageName)) {
            markTrustedHelper(packageName)
            return
        }

        // Allow whitelisted packages
        if (WhitelistManager.isWhitelisted(this, packageName)) {
            if (packageName in WhitelistManager.getUserWhitelist(this)) {
                markUserAllowedApp()
            } else if (isLauncherPackage(packageName)) {
                clearTrustedHelper()
            }
            return
        }

        Log.d(TAG, "Blocking app: $packageName")
        launchLockScreen()
    }

    private fun isAppInfoScreen(
        packageName: String,
        className: String,
        event: AccessibilityEvent
    ): Boolean {
        // Only check within settings-like packages
        val isSettingsPackage = isSettingsPackage(packageName)
        if (!isSettingsPackage) return false

        // Many Android versions/OEMs use generic classes like SubSettings.
        // In that case, block screens where Morning Peace appears inside app-management UI.
        val screenText = getScreenText(event).lowercase()
        if (!screenText.contains(getString(R.string.app_name).lowercase()) &&
            !screenText.contains(applicationContext.packageName.lowercase())
        ) {
            return false
        }

        val isKnownAppInfoClass = APP_INFO_PATTERNS.any { pattern ->
            className.contains(pattern, ignoreCase = true)
        }
        val hasAppManagementText = APP_MANAGEMENT_TEXT.any { text ->
            screenText.contains(text)
        }

        return isKnownAppInfoClass || hasAppManagementText
    }

    private fun isSettingsPackage(packageName: String): Boolean {
        val lowerPackage = packageName.lowercase()
        return lowerPackage.contains("settings") ||
            lowerPackage == "com.miui.securitycenter" ||
            lowerPackage == "com.huawei.systemmanager" ||
            lowerPackage == "com.coloros.safecenter" ||
            lowerPackage == "com.oppo.safe" ||
            lowerPackage == "com.vivo.permissionmanager" ||
            lowerPackage == "com.iqoo.secure"
    }

    private fun isInputMethodPackage(packageName: String): Boolean {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledInputMethods = inputMethodManager?.enabledInputMethodList.orEmpty()
        if (enabledInputMethods.any { it.packageName == packageName }) return true

        return packageName in COMMON_KEYBOARD_PACKAGES
    }

    private fun isSafeSystemHelperPackage(packageName: String): Boolean {
        return packageName in SAFE_SYSTEM_HELPER_PACKAGES
    }

    private fun isSafeSystemHelperAllowed(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val openedFromUserAllowedApp = now - lastUserAllowedAppTime <= HELPER_OPEN_WINDOW_MS
        val continuingTrustedHelper = trustedHelperPackage == packageName &&
            now - trustedHelperTime <= HELPER_CONTINUE_WINDOW_MS

        return openedFromUserAllowedApp || continuingTrustedHelper
    }

    private fun markUserAllowedApp() {
        lastUserAllowedAppTime = System.currentTimeMillis()
    }

    private fun markTrustedHelper(packageName: String) {
        trustedHelperPackage = packageName
        trustedHelperTime = System.currentTimeMillis()
    }

    private fun clearTrustedHelper() {
        lastUserAllowedAppTime = 0L
        trustedHelperPackage = null
        trustedHelperTime = 0L
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        return packageName in setOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.miui.home",
            "com.oppo.launcher",
        )
    }

    private fun getScreenText(event: AccessibilityEvent): String {
        val texts = mutableListOf<String>()
        event.text?.mapNotNullTo(texts) { it?.toString() }
        rootInActiveWindow?.let { root ->
            collectNodeText(root, texts)
        }
        return texts.joinToString(separator = " ")
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectNodeText(child, texts)
            }
        }
    }

    private fun launchLockScreen() {
        // Don't show the overlay while the device lock screen is still visible
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) return

        val now = System.currentTimeMillis()
        if (now - lastBlockedTime < 1000) return
        lastBlockedTime = now

        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppBlockerService interrupted")
    }

    override fun onDestroy() {
        blockStateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenStateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        super.onDestroy()
    }
}
