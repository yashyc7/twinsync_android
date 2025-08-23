package com.yash.twinsync.models

data class DailyUpdate(
    val battery: Int?,
    val gpsLat: Double?,
    val gpsLon: Double?,
    val mood: String?,
    val note: String,
    val loggedAt: String
)
