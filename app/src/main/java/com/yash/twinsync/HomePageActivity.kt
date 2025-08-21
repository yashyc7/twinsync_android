package com.yash.twinsync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.view.View
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog  // ADDED: For permission explanation dialog

class HomePageActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar

    // UPDATED: Added background location permission
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val backgroundLocationGranted = permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: true // true for older Android versions

        if (fineLocationGranted || coarseLocationGranted) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !backgroundLocationGranted) {
                // ADDED: Request background location separately
                showBackgroundLocationDialog()
            } else {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Location permission is required for GPS tracking", Toast.LENGTH_LONG).show()
            // ADDED: Show explanation and retry
            showPermissionExplanationDialog()
        }
    }

    // ADDED: Separate launcher for background location (Android 10+)
    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Background location permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Background location denied. GPS may not work when screen is off.", Toast.LENGTH_LONG).show()
        }
    }

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.homepage_activity)

        progressBar = findViewById(R.id.progressBar)

        val inviteCodeText = findViewById<TextView>(R.id.inviteCodeText)
        val createInviteButton = findViewById<Button>(R.id.createInviteButton)
        val copyButton = findViewById<Button>(R.id.copyButton)
        val acceptCodeInput = findViewById<EditText>(R.id.acceptCodeInput)
        val acceptButton = findViewById<Button>(R.id.acceptButton)
        val unlinkButton = findViewById<Button>(R.id.unlinkButton)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // Partner data UI section
        val partnerDataLayout = findViewById<LinearLayout>(R.id.partnerDataLayout)

        // Always fetch partner data on load
        fetchPartnerData()
        checkAndRequestPermissions()

        // ... rest of your existing onCreate code remains the same ...
        // (createInviteButton, copyButton, acceptButton, unlinkButton, logoutButton listeners)

        // Create invite code
        createInviteButton.setOnClickListener {
            val url = "https://twinsync.vercel.app/api/invitation/create-invitation/"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this)}")
                .build()

            runOnUiThread { progressBar.visibility=View.VISIBLE }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val jsonResp = JSONObject(responseBody)
                            val code = jsonResp.optString("invite_code")
                            inviteCodeText.text = code
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }

        // Copy invite code
        copyButton.setOnClickListener {
            val code = inviteCodeText.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invite Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied!", Toast.LENGTH_LONG).show()
            }
        }

        // Accept invitation
        acceptButton.setOnClickListener {
            val code = acceptCodeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter invite code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val url = "https://twinsync.vercel.app/api/invitation/accept-invitation/"
            val json = JSONObject()
            json.put("invite_code", code)

            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this)}")
                .build()
            runOnUiThread { progressBar.visibility=View.VISIBLE }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        if (response.isSuccessful) {
                            Toast.makeText(this@HomePageActivity, "Paired successfully 🎉", Toast.LENGTH_SHORT).show()
                            unlinkButton.visibility = View.VISIBLE
                            // Hide invite UI since now paired
                            toggleInviteUI(false)
                            fetchPartnerData()
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }

        // Unlink
        unlinkButton.setOnClickListener {
            val url = "https://twinsync.vercel.app/api/invitation/unlink/"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this)}")
                .build()

            runOnUiThread { progressBar.visibility=View.VISIBLE }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        progressBar.visibility=View.GONE
                        if (response.isSuccessful) {
                            Toast.makeText(this@HomePageActivity, "Unlinked successfully ✅", Toast.LENGTH_SHORT).show()
                            unlinkButton.visibility = View.GONE
                            partnerDataLayout.visibility = View.GONE
                            // Show invite UI again
                            toggleInviteUI(true)
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }

        // 🔹 Logout
        logoutButton.setOnClickListener {
            logoutUser()
        }
    }

    // Helper: toggle invite UI
    private fun toggleInviteUI(show: Boolean) {
        findViewById<Button>(R.id.createInviteButton).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.copyButton).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.inviteCodeText).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<EditText>(R.id.acceptCodeInput).visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.acceptButton).visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun fetchPartnerData() {
        val request = Request.Builder()
            .url("https://twinsync.vercel.app/api/userdata/partner-data/")
            .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this)}")
            .build()
        runOnUiThread { progressBar.visibility=View.VISIBLE }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility=View.GONE
                    Toast.makeText(this@HomePageActivity, "Failed to fetch partner data", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    progressBar.visibility=View.GONE
                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        val json = JSONObject(responseBody)
                        val battery = json.optString("battery", "-")
                        val gpsLat = json.optString("gps_lat", "-")
                        val gpsLon = json.optString("gps_lon", "-")
                        val mood = json.optString("mood", "-")
                        val updatedAt = json.optString("updated_at", "-")

                        findViewById<LinearLayout>(R.id.partnerDataLayout).visibility = View.VISIBLE
                        findViewById<Button>(R.id.unlinkButton).visibility = View.VISIBLE

                        // Hide invite UI since paired
                        toggleInviteUI(false)

                        findViewById<TextView>(R.id.partnerBattery).text =
                            getString(R.string.partner_battery, battery)

                        findViewById<TextView>(R.id.partnerGps).text =
                            getString(R.string.partner_gps, gpsLat, gpsLon)

                        findViewById<TextView>(R.id.partnerMood).text =
                            getString(R.string.partner_mood, mood)

                        findViewById<TextView>(R.id.partnerUpdatedAt).text =
                            getString(R.string.partner_updated_at, updatedAt)
                    } else {
                        // Not paired yet → show invite UI
                        toggleInviteUI(true)
                    }
                }
            }
        })
    }

    // 🔹 Logout function
    private fun logoutUser() {
        val refreshToken = TokenManager.getRefreshToken(this)

        if (refreshToken == null) {
            // No token → clear and go to login
            TokenManager.clearTokens(this)
            navigateToLogin()
            return
        }

        val url = "https://twinsync.vercel.app/api/auth/logout/"
        val json = JSONObject()
        json.put("refresh",refreshToken)

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        runOnUiThread { progressBar.visibility=View.VISIBLE }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility=View.GONE
                    Toast.makeText(this@HomePageActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility=View.GONE
                    if (response.isSuccessful) {
                        TokenManager.clearTokens(this@HomePageActivity)
                        Toast.makeText(this@HomePageActivity, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                        navigateToLogin()
                    } else {
                        Toast.makeText(this@HomePageActivity, "Logout failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // UPDATED: Request both foreground and background location permissions
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check basic location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // ADDED: For Android 10+, also request background location
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // ADDED: Show dialog explaining why background location is needed
    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Permission")
            .setMessage("To track your location when the screen is off, we need background location permission. This helps keep your partner updated even when you're not actively using the app.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(this, "Background location skipped. GPS may not work when screen is off.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    // ADDED: Show explanation for denied permissions
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to share your GPS coordinates with your partner. Without this permission, location sharing won't work.")
            .setPositiveButton("Try Again") { _, _ ->
                checkAndRequestPermissions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Location features will not work without permission", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}