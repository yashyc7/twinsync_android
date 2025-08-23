package com.yash.twinsync.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.yash.twinsync.R
import com.yash.twinsync.models.DailyUpdate
import android.content.Context
import android.content.Intent
import android.net.Uri

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

        holder.gps.setOnClickListener {
            val lat = update.gpsLat
            val lon = update.gpsLon
            if (lat != null && lon != null) {
                openGoogleMaps(holder.itemView.context, lat, lon)
            } else {
                Toast.makeText(holder.itemView.context, "No GPS location available", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun getItemCount() = updates.size

    fun updateData(newUpdates: List<DailyUpdate>) {
        updates = newUpdates
        notifyDataSetChanged()
    }

    fun openGoogleMaps(context: Context, lat: Double, lon: Double) {
        try {
            // Create geo URI with coordinates
            val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")

            // Create intent for Google Maps
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps") // Force open in Google Maps
            }

            // Launch intent
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Maps not available", Toast.LENGTH_SHORT).show()
        }
    }


}
