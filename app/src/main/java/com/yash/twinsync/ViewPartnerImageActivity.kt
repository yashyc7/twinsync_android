package com.yash.twinsync.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yash.twinsync.TokenManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ViewPartnerImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug toast
        Toast.makeText(this, "Opening partner image...", Toast.LENGTH_SHORT).show()

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(imageView)


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
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
