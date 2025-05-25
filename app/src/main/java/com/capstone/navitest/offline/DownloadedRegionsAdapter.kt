package com.capstone.navitest.offline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.capstone.navitest.R
import com.capstone.navitest.ui.LanguageManager

class DownloadedRegionsAdapter(
    private var regions: List<DownloadedRegion>,
    private val languageManager: LanguageManager,
    private val onDeleteClick: (DownloadedRegion) -> Unit
) : RecyclerView.Adapter<DownloadedRegionsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val regionName: TextView = itemView.findViewById(R.id.regionName)
        val downloadDate: TextView = itemView.findViewById(R.id.downloadDate)
        val regionSize: TextView = itemView.findViewById(R.id.regionSize)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloaded_region, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val region = regions[position]

        holder.regionName.text = region.name
        holder.downloadDate.text = languageManager.getLocalizedString(
            "다운로드: ${region.downloadDate}",
            "Downloaded: ${region.downloadDate}"
        )
        holder.regionSize.text = languageManager.getLocalizedString(
            "크기: ${region.size}",
            "Size: ${region.size}"
        )

        holder.deleteButton.text = languageManager.getLocalizedString("삭제", "Delete")
        holder.deleteButton.setOnClickListener {
            onDeleteClick(region)
        }
    }

    override fun getItemCount(): Int = regions.size

    fun updateRegions(newRegions: List<DownloadedRegion>) {
        regions = newRegions
        notifyDataSetChanged()
    }
}