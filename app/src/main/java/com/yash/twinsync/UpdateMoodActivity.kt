package com.yash.twinsync.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.yash.twinsync.R

class UpdateMoodActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_mood)

        val editMood = findViewById<EditText>(R.id.edit_mood)
        val btnSave = findViewById<Button>(R.id.btn_save)

        btnSave.setOnClickListener {
            val mood = editMood.text.toString()
            // Post mood to server
            MoodWidgetProvider.postUserMood(this, mood)

            // Update widget UI immediately
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, MoodWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            ids.forEach { id ->
                MoodWidgetProvider.updateMoodWidget(this, appWidgetManager, id, mood)
            }

            finish()
        }
    }
}
