package com.example.morningpeace

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val activity: Activity) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/leo-BWL/MorningPeace/releases/latest"
        private const val PREF_NAME = "UpdatePrefs"
        private const val PREF_LAST_CHECK = "last_check_time"
        private const val PREF_SKIPPED_VERSION = "skipped_version"
        private const val CHECK_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val NOTIFICATION_CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0)
        
        if (now - lastCheck < CHECK_COOLDOWN_MS) {
            Log.d(TAG, "Update check on cooldown.")
            return
        }

        prefs.edit().putLong(PREF_LAST_CHECK, now).apply()

        Thread {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val tagName = json.getString("tag_name").removePrefix("v")
                    val releaseNotes = json.optString("body", "No release notes provided.")
                    
                    val currentVersion = getCurrentVersion()
                    val skippedVersion = prefs.getString(PREF_SKIPPED_VERSION, "")

                    if (tagName != skippedVersion && isNewerVersion(currentVersion, tagName)) {
                        val assets = json.getJSONArray("assets")
                        var apkUrl: String? = null
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }

                        if (apkUrl != null) {
                            handler.post {
                                showUpdateDialog(tagName, currentVersion, releaseNotes, apkUrl)
                            }
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for update", e)
            }
        }.start()
    }

    private fun getCurrentVersion(): String {
        return try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val cur = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > cur) return true
            if (lat < cur) return false
        }
        return false
    }

    private fun showUpdateDialog(newVersion: String, currentVersion: String, releaseNotes: String, apkUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available 🚀")
            .setMessage("Version $newVersion is available! You're currently on $currentVersion.\n\n$releaseNotes")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(apkUrl, newVersion)
            }
            .setNeutralButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Skip This Version") { _, _ ->
                prefs.edit().putString(PREF_SKIPPED_VERSION, newVersion).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String, version: String) {
        val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val notificationBuilder = NotificationCompat.Builder(activity, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading Update")
            .setContentText("Version $version")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        Thread {
            try {
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val fileLength = connection.contentLength
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (dir != null && !dir.exists()) {
                    dir.mkdirs()
                }
                
                val outputFile = File(dir, "update_$version.apk")
                val input: InputStream = connection.inputStream
                val output = FileOutputStream(outputFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                var lastProgress = 0

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            notificationBuilder.setProgress(100, progress, false)
                            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                        }
                    }
                }
                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                handler.post {
                    notificationManager.cancel(NOTIFICATION_ID)
                    installApk(outputFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                handler.post {
                    notificationManager.cancel(NOTIFICATION_ID)
                    Toast.makeText(activity, "Update download failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for downloading app updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    apkFile
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                uri = Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            Toast.makeText(activity, "Failed to start installation.", Toast.LENGTH_SHORT).show()
        }
    }
}
