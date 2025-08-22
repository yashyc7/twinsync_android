package com.yash.twinsync.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yash.twinsync.R
import kotlinx.coroutines.launch

class UpdateMoodActivity : AppCompatActivity() {

    private lateinit var editMood: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnHappy: Button
    private lateinit var btnSad: Button
    private lateinit var btnTired: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_mood)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        editMood = findViewById(R.id.edit_mood)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        btnHappy = findViewById(R.id.btn_happy)
        btnSad = findViewById(R.id.btn_sad)
        btnTired = findViewById(R.id.btn_tired)
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener { saveMood() }
        btnCancel.setOnClickListener { finish() }

        // Quick mood selection buttons
        btnHappy.setOnClickListener { setQuickMood("Happy") }
        btnSad.setOnClickListener { setQuickMood("Sad") }
        btnTired.setOnClickListener { setQuickMood("Tired") }
    }

    private fun setQuickMood(mood: String) {
        editMood.setText(mood)
        editMood.setSelection(mood.length) // Place cursor at end
    }

    private fun saveMood() {
        val mood = editMood.text.toString().trim()

        if (mood.isEmpty()) {
            editMood.error = "Please enter your mood"
            editMood.requestFocus()
            return
        }

        if (mood.length > 50) {
            editMood.error = "Mood text is too long"
            editMood.requestFocus()
            return
        }

        // Disable button to prevent multiple clicks
        btnSave.isEnabled = false
        btnSave.text = "Updating..."

        lifecycleScope.launch {
            try {
                // Post mood to server
                MoodWidgetProvider.postUserMood(this@UpdateMoodActivity, mood)

                // Update widget UI immediately
                updateWidgetUI(mood)

                // Show success message
                Toast.makeText(this@UpdateMoodActivity, "Mood updated successfully!", Toast.LENGTH_SHORT).show()

                finish()
            } catch (e: Exception) {
                // Re-enable button on error
                btnSave.isEnabled = true
                btnSave.text = "Update Mood"
                Toast.makeText(this@UpdateMoodActivity, "Failed to update mood", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateWidgetUI(mood: String) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, MoodWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        widgetIds.forEach { widgetId ->
            MoodWidgetProvider.updateMoodWidget(this, appWidgetManager, widgetId, mood)
        }
    }

}