package com.yash.twinsync.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yash.twinsync.R
import com.yash.twinsync.TokenManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.widget.ProgressBar

class ViewPartnerImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_image)

        val progressBar = findViewById<ProgressBar>(R.id.loadingIndicator)
        val imageView = findViewById<ImageView>(R.id.partnerImageView)

        val token = TokenManager.getAccessToken(this) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://twinsync.vercel.app/api/userdata/partner-data/")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    val sharedImageStr = json.optString("shared_image", "")
                    if (sharedImageStr.isNotEmpty()) {
                        val base64Data = sharedImageStr.substringAfter("data:image/jpeg;base64,")
                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                        withContext(Dispatchers.Main) {
                            progressBar.visibility = ProgressBar.GONE
                            imageView.setImageBitmap(bitmap)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = ProgressBar.GONE
                            Toast.makeText(this@ViewPartnerImageActivity, "No image shared", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this@ViewPartnerImageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}

