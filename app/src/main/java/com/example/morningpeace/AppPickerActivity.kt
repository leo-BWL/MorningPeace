package com.example.morningpeace

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morningpeace.databinding.ActivityAppPickerBinding

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()
    private val selectedPackages = mutableSetOf<String>()

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val appIcon: android.graphics.drawable.Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedPackages.addAll(WhitelistManager.getUserWhitelist(this))

        adapter = AppListAdapter(this, emptyList(), selectedPackages) { pkg, isChecked ->
            if (isChecked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
            updateSelectedCount()
            updateSelectedSection()
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnDone.setOnClickListener {
            WhitelistManager.saveUserWhitelist(this, selectedPackages)
            finish()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterApps(s?.toString() ?: "") }
        })

        // Load selected apps immediately (fast — only loads selected ones)
        loadSelectedAppsQuick()
        // Then load the full list in background
        loadInstalledApps()
    }

    /**
     * Quickly load only the selected apps for the top section.
     * This avoids waiting for the full app list to load.
     */
    private fun loadSelectedAppsQuick() {
        val pm = packageManager
        val container = binding.llSelectedApps
        container.removeAllViews()

        if (selectedPackages.isEmpty()) {
            binding.sectionSelected.visibility = View.GONE
            binding.dividerSelected.visibility = View.GONE
            return
        }

        binding.sectionSelected.visibility = View.VISIBLE
        binding.dividerSelected.visibility = View.VISIBLE
        binding.tvSelectedHeader.text = getString(R.string.selected_header, selectedPackages.size)

        for (pkg in selectedPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val appIcon = pm.getApplicationIcon(appInfo)

                val chipView = LayoutInflater.from(this)
                    .inflate(R.layout.item_selected_chip, container, false)
                chipView.findViewById<ImageView>(R.id.ivChipIcon).setImageDrawable(appIcon)
                chipView.findViewById<TextView>(R.id.tvChipName).text = appName
                chipView.findViewById<TextView>(R.id.btnChipRemove).setOnClickListener {
                    selectedPackages.remove(pkg)
                    updateSelectedCount()
                    loadSelectedAppsQuick()
                    adapter.notifyDataSetChanged()
                }
                container.addView(chipView)
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        updateSelectedCount()
    }

    private fun loadInstalledApps() {
        Thread {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    pm.getLaunchIntentForPackage(appInfo.packageName) != null &&
                    appInfo.packageName != packageName &&
                    appInfo.packageName !in WhitelistManager.SYSTEM_WHITELIST
                }
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        appIcon = pm.getApplicationIcon(appInfo)
                    )
                }
                .sortedWith(
                    compareByDescending<AppInfo> { it.packageName in selectedPackages }
                        .thenBy { it.appName.lowercase() }
                )

            runOnUiThread {
                allApps = installedApps
                adapter.updateApps(installedApps)
                updateSelectedCount()
            }
        }.start()
    }

    private fun filterApps(query: String) {
        if (query.isEmpty()) adapter.updateApps(allApps)
        else adapter.updateApps(allApps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        })
    }

    private fun updateSelectedCount() {
        binding.tvSelectedCount.text = getString(R.string.apps_selected, selectedPackages.size)
    }

    private fun updateSelectedSection() {
        loadSelectedAppsQuick()
    }

    class AppListAdapter(
        private val context: Context,
        private var apps: List<AppInfo>,
        private val selectedPackages: Set<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val tvName: TextView = view.findViewById(R.id.tvAppName)
            val cbSelected: CheckBox = view.findViewById(R.id.cbSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_app, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.ivIcon.setImageDrawable(app.appIcon)
            holder.tvName.text = app.appName
            holder.cbSelected.setOnCheckedChangeListener(null)
            holder.cbSelected.isChecked = app.packageName in selectedPackages
            holder.cbSelected.setOnCheckedChangeListener { _, isChecked -> onToggle(app.packageName, isChecked) }
            holder.itemView.setOnClickListener { holder.cbSelected.isChecked = !holder.cbSelected.isChecked }
        }

        override fun getItemCount() = apps.size
        fun updateApps(newApps: List<AppInfo>) { apps = newApps; notifyDataSetChanged() }
    }
}
