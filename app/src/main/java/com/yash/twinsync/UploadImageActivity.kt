package com.yash.twinsync.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yash.twinsync.TokenManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class UploadImageActivity : AppCompatActivity() {

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadImageToServer(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch image picker immediately
        pickImageLauncher.launch("image/*")
    }

    private fun uploadImageToServer(uri: Uri) {
        val token = TokenManager.getAccessToken(this) ?: return

        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: return
        val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

        val json = JSONObject().apply { put("shared_image", base64Image) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://twinsync.vercel.app/api/userdata/update/")
                    .post(body)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@UploadImageActivity, "Image uploaded!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@UploadImageActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UploadImageActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
