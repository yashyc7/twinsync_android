package com.yash.twinsync

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.yash.twinsync.adapters.DailyUpdatesAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.yash.twinsync.models.DailyUpdate
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import  java.util.*

class HomePageActivity : AppCompatActivity() {

    // UI Components
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var inviteSection: LinearLayout
    private lateinit var partnerDataCard: MaterialCardView
    private lateinit var inviteCodeText: TextView
    private lateinit var acceptCodeInput: TextInputEditText
    private lateinit var unlinkButton: Button

    // Partner data TextViews
    private lateinit var partnerMood: TextView
    private lateinit var partnerBattery: TextView
    private lateinit var partnerGps: TextView
    private lateinit var partnerUpdatedAt: TextView


    //daily updates and the pickup date button variables

    private lateinit var pickupDateButton : MaterialButton
    private lateinit var dailyUpdateArea: RecyclerView

    private lateinit var dailyUpdatesAdapter: DailyUpdatesAdapter

    private var partnerLat: Double? = null
    private var partnerLon: Double? = null


    // HTTP Client with timeout configuration
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Permission launchers
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        handleBackgroundLocationResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.homepage_activity)

        val recyclerView = findViewById<RecyclerView>(R.id.dailyUpdatesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        dailyUpdatesAdapter = DailyUpdatesAdapter(emptyList())
        recyclerView.adapter = dailyUpdatesAdapter

        initializeViews()
        setupClickListeners()

        // Initial data fetch and permission check
        fetchPartnerData()
        val today=getTodayDate()
        fetchDailyUpdates(today)
        checkAndRequestPermissions()
        requestBatteryOptimizationException()
    }

    private fun initializeViews() {
        loadingOverlay = findViewById(R.id.loadingOverlay)
        progressBar = findViewById(R.id.progressBar)
        inviteSection = findViewById(R.id.inviteSection)
        partnerDataCard = findViewById(R.id.partnerDataCard)
        inviteCodeText = findViewById(R.id.inviteCodeText)
        acceptCodeInput = findViewById(R.id.acceptCodeInput)
        unlinkButton = findViewById(R.id.unlinkButton)

        // Partner data views
        partnerMood = findViewById(R.id.partnerMood)
        partnerBattery = findViewById(R.id.partnerBattery)
        partnerGps = findViewById(R.id.partnerGps)
        partnerUpdatedAt = findViewById(R.id.partnerUpdatedAt)
        pickupDateButton=findViewById(R.id.pickDateButton)
        dailyUpdateArea=findViewById(R.id.dailyUpdatesRecyclerView)
    }

    private fun requestBatteryOptimizationException() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to request battery optimization exception", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun setupClickListeners() {
        findViewById<Button>(R.id.createInviteButton).setOnClickListener {
            createInviteCode()
        }

        findViewById<Button>(R.id.copyButton).setOnClickListener {
            copyInviteCode()
        }

        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            acceptInvitation()
        }

        findViewById<Button>(R.id.pickDateButton).setOnClickListener {
            showDatePicker()
        }

        unlinkButton.setOnClickListener {
            showUnlinkConfirmation()
        }

        partnerGps.setOnClickListener {
            val lat = partnerLat
            val lon = partnerLon
            if (lat != null && lon != null) {
                openGoogleMaps(this, lat, lon) // both are now Double
            } else {
                Toast.makeText(this, "No GPS location available", Toast.LENGTH_SHORT).show()
            }
        }




        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun createInviteCode() {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/invitation/create-invitation/",
                    "GET"
                )

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResp = JSONObject(responseBody)
                    val code = jsonResp.optString("invite_code")

                    runOnUiThread {
                        inviteCodeText.text = code
                        showToast("Invite code created successfully!")
                    }
                } else {
                    runOnUiThread {
                        showToast("Failed to create invite code: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Error creating invite code: ${e.localizedMessage}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun copyInviteCode() {
        val code = inviteCodeText.text.toString().trim()
        if (code.isNotEmpty() && code != getString(R.string.your_invite_code_will_appear_here)) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TwinSync Invite Code", code)
            clipboard.setPrimaryClip(clip)
            showToast("Invite code copied to clipboard!")
        } else {
            showToast("No invite code to copy. Create one first.")
        }
    }

    private fun acceptInvitation() {
        val code = acceptCodeInput.text.toString().trim()
        if (code.isEmpty()) {
            showToast("Please enter an invite code")
            acceptCodeInput.requestFocus()
            return
        }

        lifecycleScope.launch {
            showLoading(true)

            try {
                val json = JSONObject().put("invite_code", code)
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/invitation/accept-invitation/",
                    "POST",
                    json
                )

                if (response.isSuccessful) {
                    runOnUiThread {
                        showToast("Successfully paired! 🎉")
                        acceptCodeInput.text?.clear()
                        updateUIForPairedState(true)
                    }
                    fetchPartnerData()
                } else {
                    runOnUiThread {
                        showToast("Failed to accept invitation: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Error accepting invitation: ${e.localizedMessage}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showUnlinkConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Unlink Partner")
            .setMessage("Are you sure you want to unlink from your partner? This will stop sharing your data.")
            .setPositiveButton("Unlink") { _, _ -> unlinkPartner() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unlinkPartner() {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/invitation/unlink/",
                    "DELETE"
                )

                if (response.isSuccessful) {
                    runOnUiThread {
                        showToast("Successfully unlinked ✅")
                        updateUIForPairedState(false)
                    }
                } else {
                    runOnUiThread {
                        showToast("Failed to unlink: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Error unlinking: ${e.localizedMessage}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun fetchPartnerData() {
        lifecycleScope.launch {
            showLoading(true)
            try {
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/userdata/partner-data/",
                    "GET"
                )

                val responseBody = response.body?.string() ?: ""

                runOnUiThread {
                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            updatePartnerDataUI(json)
                            updateUIForPairedState(true)
                        } catch (e: JSONException) {
                            showToast("Error parsing partner data")
                            updateUIForPairedState(false)
                        }
                    } else {
                        updateUIForPairedState(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Failed to fetch partner data")
                    updateUIForPairedState(false)
                }
            }
            finally {
                showLoading(false)
            }
        }
    }

    private fun updatePartnerDataUI(json: JSONObject) {
        val battery = json.optString("battery", "Unknown")
        val gpsLat = json.optString("gps_lat", "-")
        val gpsLon = json.optString("gps_lon", "-")
        val mood = json.optString("mood", "Unknown")
        val updatedAt = json.optString("updated_at", "Never")

        // Save to class vars
        partnerLat =gpsLat.toDoubleOrNull()
        partnerLon =gpsLon.toDoubleOrNull()

        partnerBattery.text = "Battery: $battery%"
        partnerGps.text = if (gpsLat != "-" && gpsLon != "-") {
            "Location: $gpsLat, $gpsLon"
        } else {
            "Location: Not available"
        }
        partnerMood.text = "Mood: $mood"
        partnerUpdatedAt.text = "Last updated: $updatedAt"
    }

    private fun updateUIForPairedState(isPaired: Boolean) {
        inviteSection.visibility = if (isPaired) View.GONE else View.VISIBLE
        partnerDataCard.visibility = if (isPaired) View.VISIBLE else View.GONE
        unlinkButton.visibility = if (isPaired) View.VISIBLE else View.GONE
        pickupDateButton.visibility=if (isPaired) View.VISIBLE else View.GONE
        dailyUpdateArea.visibility=if (isPaired) View.VISIBLE else View.GONE
    }

    private suspend fun makeApiCall(
        url: String,
        method: String,
        jsonBody: JSONObject? = null
    ): Response = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this@HomePageActivity)}")
            .addHeader("Content-Type", "application/json")

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> {
                val body = jsonBody?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
                    ?: "".toRequestBody("application/json; charset=utf-8".toMediaType())
                requestBuilder.post(body)
            }
            "DELETE" -> requestBuilder.delete()
        }

        val request = requestBuilder.build()
        client.newCall(request).execute()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        lifecycleScope.launch {
            showLoading(true)

            try {
                val refreshToken = TokenManager.getRefreshToken(this@HomePageActivity)
                if (refreshToken == null) {
                    TokenManager.clearTokens(this@HomePageActivity)
                    navigateToLogin()
                    return@launch
                }

                val json = JSONObject().put("refresh", refreshToken)
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/auth/logout/",
                    "POST",
                    json
                )

                runOnUiThread {
                    if (response.isSuccessful) {
                        TokenManager.clearTokens(this@HomePageActivity)
                        showToast("Logged out successfully!")
                        navigateToLogin()
                    } else {
                        showToast("Logout failed: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Network error during logout: ${e.localizedMessage}")
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // If basic permissions are granted, check background location for Android 10+
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationDialog()
            }
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            showToast("Location permissions granted!")
            checkBackgroundLocationPermission()
        } else {
            showPermissionExplanationDialog()
        }
    }

    private fun handleBackgroundLocationResult(granted: Boolean) {
        if (granted) {
            showToast("Background location permission granted!")
        } else {
            showToast("Background location denied. GPS may not work when screen is off.")
        }
    }

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Permission")
            .setMessage("To track your location when the screen is off, we need background location permission. This helps keep your partner updated even when you're not actively using the app.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                showToast("Background location skipped. GPS may not work when screen is off.")
            }
            .show()
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to share your GPS coordinates with your partner. Without this permission, location sharing won't work.")
            .setPositiveButton("Try Again") { _, _ ->
                checkAndRequestPermissions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showToast("Location features will not work without permission")
            }
            .show()
    }

    private fun showLoading(show: Boolean) {
        runOnUiThread {
            loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun fetchDailyUpdates(date: String) {
        lifecycleScope.launch {
            showLoading(true)
            try {
                val json = JSONObject().put("date", date)
                val response = makeApiCall(
                    "https://twinsync.vercel.app/api/userdata/daily-updates/",
                    "POST",
                    json
                )

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(responseBody)

                    val updates = mutableListOf<DailyUpdate>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        updates.add(
                            DailyUpdate(
                                battery = if (obj.isNull("battery")) null else obj.getInt("battery"),
                                gpsLat = if (obj.isNull("gps_lat")) null else obj.getDouble("gps_lat"),
                                gpsLon = if (obj.isNull("gps_lon")) null else obj.getDouble("gps_lon"),
                                mood = if (obj.isNull("mood")) null else obj.getString("mood"),
                                note = obj.getString("note"),
                                loggedAt = obj.getString("logged_at")
                            )
                        )
                    }

                    runOnUiThread {
                        dailyUpdatesAdapter.updateData(updates)
                    }
                } else {
                    runOnUiThread { showToast("Failed to load daily updates") }
                }
            } catch (e: Exception) {
                runOnUiThread { showToast("Error: ${e.localizedMessage}") }
            } finally {
                showLoading(false)
            }
        }
    }


    private fun openGoogleMaps(context: Context, lat: Double, lon: Double) {
        try {
            val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Maps not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select a date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.format(Date(selection))
            fetchDailyUpdates(date)
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }




}