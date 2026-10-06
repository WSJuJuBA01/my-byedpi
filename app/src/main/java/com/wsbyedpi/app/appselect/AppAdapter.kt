package com.wsbyedpi.app.appselect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wsbyedpi.app.databinding.ItemAppBinding

class AppAdapter(
    private var appsList: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appsList[position]
        holder.binding.imgAppIcon.setImageDrawable(app.icon)
        holder.binding.tvAppName.text = app.name
        holder.binding.tvPackageName.text = app.packageName
        holder.binding.checkboxApp.isChecked = app.isSelected

        holder.itemView.setOnClickListener {
            app.isSelected = !app.isSelected
            holder.binding.checkboxApp.isChecked = app.isSelected
            onItemClick(app)
        }

        holder.binding.checkboxApp.setOnClickListener {
            app.isSelected = holder.binding.checkboxApp.isChecked
            onItemClick(app)
        }
    }

    override fun getItemCount(): Int = appsList.size

    fun updateData(newList: List<AppInfo>) {
        appsList = newList
        notifyDataSetChanged()
    }
}
