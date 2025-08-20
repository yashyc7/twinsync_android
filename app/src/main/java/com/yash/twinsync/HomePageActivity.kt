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

//        val token = TokenManager.getAccessToken(this)
//        Toast.makeText(this, "Token: $token", Toast.LENGTH_LONG).show()
//        println("DEBUG TOKEN: $token")


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
                            val code=jsonResp.optString("invite_code")
                            inviteCodeText.text = code
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
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
                    runOnUiThread { Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@HomePageActivity, "Paired successfully 🎉", Toast.LENGTH_SHORT).show()
                            unlinkButton.visibility = Button.VISIBLE
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
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
                    runOnUiThread { Toast.makeText(this@HomePageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@HomePageActivity, "Unlinked successfully ✅", Toast.LENGTH_SHORT).show()
                            unlinkButton.visibility = Button.GONE
                        } else {
                            Toast.makeText(this@HomePageActivity, "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }
}
