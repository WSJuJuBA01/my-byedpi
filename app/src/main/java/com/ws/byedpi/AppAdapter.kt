package com.ws.byedpi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var all: List<AppInfo> = emptyList()
    private var shown: List<AppInfo> = emptyList()
    private var query: String = ""

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
    }

    fun submit(list: List<AppInfo>) {
        all = list
        applyFilter()
    }

    fun filter(q: String) {
        query = q.trim()
        applyFilter()
    }

    private fun applyFilter() {
        shown = if (query.isEmpty()) {
            all
        } else {
            all.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = shown[position]
        holder.tvAppName.text = app.appName
        holder.tvPackageName.text = app.packageName
        holder.ivIcon.setImageDrawable(app.icon)
        holder.cbSelect.isChecked = app.isSelected

        holder.itemView.setOnClickListener {
            app.isSelected = !app.isSelected
            holder.cbSelect.isChecked = app.isSelected
            onItemClick(app)
        }
    }

    override fun getItemCount(): Int = shown.size
}
