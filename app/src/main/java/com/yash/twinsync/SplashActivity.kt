package com.yash.twinsync

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if token exists
        val token = TokenManager.getAccessToken(this)

        if (!token.isNullOrEmpty()) {
            // User already logged in → go to Home
            startActivity(Intent(this, HomePageActivity::class.java))
        } else {
            // No token → go to Login
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish() // Close splash so user can’t go back to it
    }
}