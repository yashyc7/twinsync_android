package com.yash.twinsync.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yash.twinsync.R
import com.yash.twinsync.models.DailyUpdate

class DailyUpdatesAdapter(private var updates: List<DailyUpdate>) :
    RecyclerView.Adapter<DailyUpdatesAdapter.UpdateViewHolder>() {

    inner class UpdateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val note: TextView = itemView.findViewById(R.id.updateNote)
        val mood: TextView = itemView.findViewById(R.id.updateMood)
        val battery: TextView = itemView.findViewById(R.id.updateBattery)
        val gps: TextView = itemView.findViewById(R.id.updateGps)
        val time: TextView = itemView.findViewById(R.id.updateTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_update, parent, false)
        return UpdateViewHolder(view)
    }

    override fun onBindViewHolder(holder: UpdateViewHolder, position: Int) {
        val update = updates[position]
        holder.note.text = update.note
        holder.mood.text = "Mood: ${update.mood ?: "-"}"
        holder.battery.text = "Battery: ${update.battery ?: "-"}%"
        holder.gps.text = if (update.gpsLat != null && update.gpsLon != null)
            "GPS: ${update.gpsLat}, ${update.gpsLon}" else "GPS: Not available"
        holder.time.text = update.loggedAt.replace("T", " ").substring(0, 16)
    }

    override fun getItemCount() = updates.size

    fun updateData(newUpdates: List<DailyUpdate>) {
        updates = newUpdates
        notifyDataSetChanged()
    }
}
