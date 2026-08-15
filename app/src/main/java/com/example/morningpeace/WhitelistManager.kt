package com.example.morningpeace

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Manages the app whitelist — apps that are allowed during blocks.
 * Default whitelist includes essential system apps (Clock, Phone, Dialer, Emergency).
 */
object WhitelistManager {

    private const val PREFS_NAME = "morningpeace_whitelist"
    private const val KEY_WHITELIST = "whitelisted_packages"

    // System packages that are always allowed (cannot be removed)
    val SYSTEM_WHITELIST = setOf(
        "com.example.morningpeace",          // Our app
        "com.android.systemui",              // System UI
        "com.android.settings",              // Settings (App Info blocked separately)
        "com.android.dialer",                // Dialer
        "com.google.android.dialer",         // Google Dialer
        "com.android.phone",                 // Phone app
        "com.android.incallui",              // In-call UI
        "com.android.emergency",             // Emergency info
        "com.android.launcher",              // Default launcher
        "com.android.launcher3",             // AOSP launcher
        "com.google.android.apps.nexuslauncher", // Pixel launcher
        "com.sec.android.app.launcher",      // Samsung launcher
        "com.huawei.android.launcher",       // Huawei launcher
        "com.miui.home",                     // Xiaomi launcher
        "com.oppo.launcher",                 // Oppo launcher
    )

    // Only package installers are fully blocked (uninstall prevention)
    val ALWAYS_BLOCKED = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )

    // Default user-facing whitelist (apps the user probably wants allowed)
    private val DEFAULT_USER_WHITELIST = setOf(
        "com.google.android.deskclock",      // Google Clock
        "com.android.deskclock",             // AOSP Clock
        "com.sec.android.app.clockpackage",  // Samsung Clock
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get the full whitelist (system + user-configured).
     */
    fun getFullWhitelist(context: Context): Set<String> {
        return SYSTEM_WHITELIST + getUserWhitelist(context)
    }

    /**
     * Get only the user-configured whitelist.
     */
    fun getUserWhitelist(context: Context): Set<String> {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_WHITELIST)) {
            // First launch: set default user whitelist
            saveUserWhitelist(context, DEFAULT_USER_WHITELIST)
            return DEFAULT_USER_WHITELIST
        }
        return prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    /**
     * Save the user-configured whitelist.
     */
    fun saveUserWhitelist(context: Context, packages: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_WHITELIST, packages)
            .apply()
    }

    /**
     * Add a package to the user whitelist.
     */
    fun addToWhitelist(context: Context, packageName: String) {
        val current = getUserWhitelist(context).toMutableSet()
        current.add(packageName)
        saveUserWhitelist(context, current)
    }

    /**
     * Remove a package from the user whitelist.
     */
    fun removeFromWhitelist(context: Context, packageName: String) {
        val current = getUserWhitelist(context).toMutableSet()
        current.remove(packageName)
        saveUserWhitelist(context, current)
    }

    /**
     * Check if a package is whitelisted (system or user).
     */
    fun isWhitelisted(context: Context, packageName: String): Boolean {
        return packageName in SYSTEM_WHITELIST || packageName in getUserWhitelist(context)
    }

    /**
     * Get the count of user-whitelisted apps that are actually installed.
     */
    fun getInstalledWhitelistCount(context: Context): Int {
        val pm = context.packageManager
        return getUserWhitelist(context).count { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Get display names of user-whitelisted installed apps.
     */
    fun getWhitelistAppNames(context: Context): List<String> {
        val pm = context.packageManager
        return getUserWhitelist(context).mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sorted()
    }
}
