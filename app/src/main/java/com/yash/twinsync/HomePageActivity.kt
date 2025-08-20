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

class HomePageActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.homepage_activity)

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

        // Create invite code
        createInviteButton.setOnClickListener {
            val url = "https://twinsync.vercel.app/api/invitation/create-invitation/"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer ${TokenManager.getAccessToken(this)}")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
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

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
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

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@HomePageActivity, "Failed to fetch partner data", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        val json = JSONObject(responseBody)
                        val battery = json.optString("battery", "-")
                        val steps = json.optString("steps", "-")
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

                        findViewById<TextView>(R.id.partnerSteps).text =
                            getString(R.string.partner_steps, steps)

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

//        if (refreshToken != null) {
//            // send it in your logout API request
//            Toast.makeText(this, "Refresh token: $refreshToken", Toast.LENGTH_SHORT).show()
//        } else {
//            Toast.makeText(this, "No refresh token found", Toast.LENGTH_SHORT).show()
//        }

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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@HomePageActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
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
}
