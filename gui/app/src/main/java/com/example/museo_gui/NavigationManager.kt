package com.example.museo_gui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import android.widget.*
import com.mapbox.geojson.Point
import com.example.museo_gui.models.*

class NavigationManager(private val context: Context) {

    fun startNavigation(destination: Point, museumName: String, minorMuseums: List<MuseumResponse>) {
        try {
            val lat = destination.latitude()
            val lng = destination.longitude()
            val encodedName = Uri.encode(museumName)

            if (minorMuseums.isNotEmpty()) {
                // Mostra dialog per scegliere le tappe
                showWaypointSelectionDialog(museumName, minorMuseums, lat, lng, encodedName)
            } else {
                // Navigazione standard senza tappe
                navigateToDestination(lat, lng, encodedName)
            }

        } catch (e: Exception) {
            Log.e("Navigation", "Errore nella navigazione", e)
            Toast.makeText(context, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDestination(lat: Double, lng: Double, encodedName: String) {
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedName")),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedName)")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/$encodedName"))
        )

        for (intent in intents) {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/$encodedName"))
        context.startActivity(browserIntent)
    }

    private fun showWaypointSelectionDialog(
        mainMuseum: String,
        minorMuseums: List<MuseumResponse>,
        destinationLat: Double,
        destinationLng: Double,
        encodedName: String
    ) {
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val titleText = TextView(context).apply {
            text = "Seleziona le tappe per $mainMuseum"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        container.addView(titleText)

        val subtitleText = TextView(context).apply {
            text = "Scegli i musei che vuoi includere nel tuo itinerario:"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        container.addView(subtitleText)

        val checkboxMap = mutableMapOf<MuseumResponse, CheckBox>()

        for (museum in minorMuseums) {
            val checkboxContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            val checkbox = CheckBox(context).apply {
                isChecked = true
                setPadding(0, 0, 16, 0)
            }

            val museumInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nameText = TextView(context).apply {
                text = museum.name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            val descriptionText = TextView(context).apply {
                text = museum.description
                textSize = 12f
                maxLines = 2
                setTextColor(Color.GRAY)
            }

            museumInfo.addView(nameText)
            museumInfo.addView(descriptionText)

            checkboxContainer.addView(checkbox)
            checkboxContainer.addView(museumInfo)

            container.addView(checkboxContainer)
            checkboxMap[museum] = checkbox
        }

        // Pulsanti per selezionare/deselezionare tutto
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val selectAllButton = Button(context).apply {
            text = "Seleziona tutto"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener {
                for (checkbox in checkboxMap.values) {
                    checkbox.isChecked = true
                }
            }
        }

        val deselectAllButton = Button(context).apply {
            text = "Deseleziona tutto"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener {
                for (checkbox in checkboxMap.values) {
                    checkbox.isChecked = false
                }
            }
        }

        buttonContainer.addView(selectAllButton)
        buttonContainer.addView(deselectAllButton)
        container.addView(buttonContainer)

        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Naviga") { _, _ ->
                val selectedMuseums = checkboxMap.filter { it.value.isChecked }.keys.toList()
                handleNavigationWithWaypoints(selectedMuseums, destinationLat, destinationLng, encodedName)
            }
            .setNegativeButton("Solo destinazione") { _, _ ->
                navigateToDestination(destinationLat, destinationLng, encodedName)
            }
            .setNeutralButton("Annulla", null)
            .create()

        dialog.show()
    }

    private fun handleNavigationWithWaypoints(
        selectedMuseums: List<MuseumResponse>,
        destinationLat: Double,
        destinationLng: Double,
        encodedName: String
    ) {
        if (selectedMuseums.isNotEmpty()) {
            val waypoints = selectedMuseums.joinToString("|") { museum ->
                "${museum.location.latitude},${museum.location.longitude}"
            }

            val mapsUrl = "https://www.google.com/maps/dir/?api=1&" +
                    "destination=$destinationLat,$destinationLng&" +
                    "waypoints=$waypoints&" +
                    "travelmode=walking"

            Log.d("Navigation", "Navigazione con ${selectedMuseums.size} tappe selezionate: $mapsUrl")

            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
            context.startActivity(browserIntent)

            Toast.makeText(
                context,
                "Itinerario creato con ${selectedMuseums.size} tappe",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            navigateToDestination(destinationLat, destinationLng, encodedName)
            Toast.makeText(context, "Navigazione verso la destinazione principale", Toast.LENGTH_SHORT).show()
        }
    }
}