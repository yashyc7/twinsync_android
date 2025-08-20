package com.yash.twinsync
import android.content.Intent
import android.widget.Button
import android.widget.Toast
import android.widget.EditText
import android.os.Bundle
import okhttp3.*
import java.io.IOException
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody



class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        // ✅ Login button click
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }

        // ✅ Register button click (navigate to RegisterActivity later)
        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser(email: String, password: String) {
        val url = "https://twinsync.vercel.app/api/auth/login/"

        val json = JSONObject()
        json.put("email", email)
        json.put("password", password)

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())


        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonResp = JSONObject(responseBody ?: "{}")
                        val tokens = jsonResp.getJSONObject("tokens")
                        val access = tokens.optString("access")
                        val refresh = tokens.optString("refresh")

//                        Toast.makeText(this@LoginActivity, "Token: $access", Toast.LENGTH_LONG).show()

                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                        if (access.isNotEmpty() && refresh.isNotEmpty()) {
                            TokenManager.saveTokens(this@LoginActivity, access, refresh)
                        }

                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, HomePageActivity::class.java)
                        startActivity(intent)
                        finish()

                        // TODO: Move to home screen
                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}