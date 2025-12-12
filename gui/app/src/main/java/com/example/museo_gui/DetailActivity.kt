// DetailActivity.kt
package com.example.museo_gui // Make sure this matches your package name

import android.os.Bundle
import android.widget.TextView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail) // Link to your new layout file

        // Retrieve data passed from MainActivity
        val cardTitle = intent.getStringExtra("card_title")
        val cardProgress = intent.getIntExtra("card_progress", 0) // 0 is a default value if not found

        // Find views in your activity_detail.xml (you'll need to add these IDs)
        val detailTitleTextView: TextView = findViewById(R.id.detailTitleTextView)
        val detailProgressBar: ProgressBar = findViewById(R.id.detailProgressBar)
        val detailProgressPercentage: TextView = findViewById(R.id.detailProgressPercentage) // If you have one

        // Set the retrieved data to your views
        detailTitleTextView.text = cardTitle
        detailProgressBar.progress = cardProgress
        detailProgressPercentage.text = "$cardProgress%" // Example for percentage
    }
}