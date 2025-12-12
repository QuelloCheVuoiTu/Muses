package it.unisannio.muses.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import it.unisannio.muses.R
import it.unisannio.muses.utils.ThemeManager
import it.unisannio.muses.weather.WeatherRepository
import it.unisannio.muses.weather.WeatherResponse
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * WeatherActivity con interfaccia coerente allo stile dell'app e dati reali
 */
class WeatherActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "WeatherActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // Fallback coordinates for Benevento
        private const val FALLBACK_LAT = 41.1297
        private const val FALLBACK_LON = 14.7697
    }
    
    private lateinit var weatherRepository: WeatherRepository
    private lateinit var mainLayout: LinearLayout
    private lateinit var loadingView: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var contentLayout: LinearLayout
    private lateinit var locationManager: LocationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initializeTheme(this)
        
        weatherRepository = WeatherRepository(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        setupUI()
        requestLocationAndLoadWeather()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: checking permissions and refreshing weather data")
        // Ricontrolla i permessi ogni volta che l'utente torna alla pagina
        checkPermissionsAndRefreshWeather()
    }
    
    private fun checkPermissionsAndRefreshWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Location permissions not granted, showing location required message")
            // Mostra messaggio con pulsante invece di richiedere automaticamente
            showLocationRequiredMessage()
        } else {
            // Se i permessi ci sono, aggiorna i dati meteo
            Log.d(TAG, "Location permissions granted, refreshing weather data")
            getCurrentLocationAndLoadWeather()
        }
    }
    
    private fun setupUI() {
        // Main ScrollView
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@WeatherActivity, R.color.main_background))
        }
        
        // Main container
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24) // Padding top per status bar
        }
        
        // Loading view
        loadingView = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = 200
            }
        }
        mainLayout.addView(loadingView)
        
        // Error view
        errorView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.error_red))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 200
            }
        }
        mainLayout.addView(errorView)
        
        // Content layout
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        mainLayout.addView(contentLayout)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        // Nascondi l'action bar
        supportActionBar?.hide()
    }
    
    private fun requestLocationAndLoadWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Location permissions not granted, forcing permission request")
            
            // Forza sempre la richiesta dei permessi, indipendentemente dallo stato precedente
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getCurrentLocationAndLoadWeather()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted, loading weather data")
                    getCurrentLocationAndLoadWeather()
                } else {
                    Log.w(TAG, "Location permission denied, showing location required message")
                    showLocationRequiredMessage()
                }
                return
            }
        }
    }
    
    private fun getCurrentLocationAndLoadWeather() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED && 
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                showLocationRequiredMessage()
                return
            }
            
            // Try to get last known location first (faster)
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastKnownLocation != null) {
                Log.d(TAG, "Using last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                loadWeatherDataForLocation(lastKnownLocation.latitude, lastKnownLocation.longitude, "Posizione Attuale")
            } else {
                Log.w(TAG, "No last known location available, showing location required message")
                showLocationRequiredMessage()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}")
            showLocationRequiredMessage()
        }
    }
    
    private fun loadWeatherDataForLocation(latitude: Double, longitude: Double, locationName: String) {
        weatherRepository.getWeatherDataForLocation(latitude, longitude, locationName, object : WeatherRepository.WeatherCallback {
            override fun onLoading() {
                runOnUiThread {
                    showLoading()
                }
            }
            
            override fun onSuccess(weather: WeatherResponse) {
                runOnUiThread {
                    displayWeatherData(weather)
                    showContent()
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    showError(message)
                }
            }
        })
    }
    
    private fun loadWeatherData() {
        weatherRepository.getWeatherData(object : WeatherRepository.WeatherCallback {
            override fun onLoading() {
                runOnUiThread {
                    showLoading()
                }
            }
            
            override fun onSuccess(weather: WeatherResponse) {
                runOnUiThread {
                    displayWeatherData(weather)
                    showContent()
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    showError(message)
                }
            }
        })
    }
    
    private fun displayWeatherData(weather: WeatherResponse) {
        contentLayout.removeAllViews()
        
        // Header con icona meteo e temperatura principale
        createWeatherHeader(contentLayout, weather)
        
        // Spacer
        contentLayout.addView(createSpacer(24))
        
        // Cards con i dati meteo
        createWeatherCards(contentLayout, weather)
    }
    
    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        contentLayout.visibility = View.GONE
    }
    
    private fun showContent() {
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        contentLayout.visibility = View.VISIBLE
    }
    
    private fun showError(message: String) {
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        contentLayout.visibility = View.GONE
        errorView.text = message
    }
    
    private fun createWeatherHeader(parent: LinearLayout, weather: WeatherResponse) {
        // Container per header
        val headerCard = createCard().apply {
            val headerLayout = LinearLayout(this@WeatherActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }
            
            // Data
            val dateText = TextView(this@WeatherActivity).apply {
                text = getCurrentFormattedDate()
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
                gravity = Gravity.CENTER
            }
            headerLayout.addView(dateText)
            
            // Spacer
            headerLayout.addView(createSpacer(16))
            
            // Icona meteo dinamica
            val weatherIcon = ImageView(this@WeatherActivity).apply {
                setImageResource(getWeatherIcon(weather.weather[0].main))
                layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                    gravity = Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            headerLayout.addView(weatherIcon)
            
            // Spacer
            headerLayout.addView(createSpacer(16))
            
            // Temperatura principale
            val tempText = TextView(this@WeatherActivity).apply {
                text = "${weather.main.temp.roundToInt()}°C"
                textSize = 48f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_primary))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            headerLayout.addView(tempText)
            
            // Descrizione
            val descText = TextView(this@WeatherActivity).apply {
                text = weather.weather[0].description.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                }
                textSize = 18f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
                gravity = Gravity.CENTER
            }
            headerLayout.addView(descText)
            
            // Location
            val locationText = TextView(this@WeatherActivity).apply {
                text = "${weather.name}, Italia"
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
                gravity = Gravity.CENTER
            }
            headerLayout.addView(locationText)
            
            addView(headerLayout)
        }
        
        parent.addView(headerCard)
    }
    
    private fun createWeatherCards(parent: LinearLayout, weather: WeatherResponse) {
        // Percezione termica
        parent.addView(createWeatherDataCard(
            "Percezione", 
            "${weather.main.feelsLike.roundToInt()}°C", 
            "🌡️"
        ))
        parent.addView(createSpacer(16))
        
        // Umidità
        parent.addView(createWeatherDataCard(
            "Umidità", 
            "${weather.main.humidity}%", 
            "💧"
        ))
        parent.addView(createSpacer(16))
        
        // Vento
        parent.addView(createWeatherDataCard(
            "Vento", 
            "${(weather.wind.speed * 3.6).roundToInt()} km/h", // conversione m/s a km/h
            "🌬️"
        ))
        parent.addView(createSpacer(16))
        
        // Pressione
        parent.addView(createWeatherDataCard(
            "Pressione", 
            "${weather.main.pressure} hPa", 
            "📊"
        ))
        parent.addView(createSpacer(16))
        
        // Visibilità
        parent.addView(createWeatherDataCard(
            "Visibilità", 
            "${weather.visibility / 1000.0} km", 
            "👁️"
        ))
    }
    
    private fun createWeatherDataCard(label: String, value: String, icon: String): CardView {
        return createCard().apply {
            val cardLayout = LinearLayout(this@WeatherActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
            }
            
            // Icon
            val iconText = TextView(this@WeatherActivity).apply {
                text = icon
                textSize = 24f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 20
                }
            }
            cardLayout.addView(iconText)
            
            // Label e value
            val textLayout = LinearLayout(this@WeatherActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val labelText = TextView(this@WeatherActivity).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
            }
            textLayout.addView(labelText)
            
            val valueText = TextView(this@WeatherActivity).apply {
                text = value
                textSize = 20f
                setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_primary))
                typeface = Typeface.DEFAULT_BOLD
            }
            textLayout.addView(valueText)
            
            cardLayout.addView(textLayout)
            addView(cardLayout)
        }
    }
    
    private fun createCard(): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cardElevation = 8f
            radius = 16f
            setCardBackgroundColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_background))
        }
    }
    
    private fun createSpacer(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), resources.displayMetrics).toInt()
            )
        }
    }
    
    private fun getCurrentFormattedDate(): String {
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ITALIAN)
        return dateFormat.format(Date())
    }
    
    private fun getWeatherIcon(weatherMain: String): Int {
        return when (weatherMain.lowercase()) {
            "clear" -> R.drawable.ic_sunny
            "clouds" -> R.drawable.ic_cloudy
            "rain", "drizzle" -> R.drawable.ic_rainy
            "thunderstorm" -> R.drawable.ic_rainy
            "snow" -> R.drawable.ic_cloudy
            "mist", "fog", "haze" -> R.drawable.ic_cloudy
            else -> R.drawable.ic_sunny
        }
    }
    
    private fun openAppSettings() {
        Log.d(TAG, "Opening app settings for manual permission grant")
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            
            // Mostra un messaggio informativo
            Toast.makeText(this, "Abilita manualmente i permessi di localizzazione nelle impostazioni", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings: ${e.message}")
            Toast.makeText(this, "Impossibile aprire le impostazioni", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showLocationRequiredMessage() {
        // Nascondo loading e errore
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        
        // Pulisco e mostro il contenuto
        contentLayout.removeAllViews()
        contentLayout.visibility = View.VISIBLE
        
        // Creo messaggio semplice
        val messageCard = createCard()
        
        val messageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }
        
        val titleText = TextView(this).apply {
            text = "Nessun dato di localizzazione"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_primary))
            gravity = Gravity.CENTER
        }
        
        val messageText = TextView(this).apply {
            text = "Per visualizzare le informazioni meteo è necessario abilitare la geolocalizzazione.\n\nVai nelle impostazioni dell'app e concedi l'accesso alla posizione."
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        
        // Aggiungo pulsante per richiedere permessi
        val enableButton = Button(this).apply {
            text = "Apri Impostazioni"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@WeatherActivity, R.color.primary_color))
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                Log.d(TAG, "User clicked enable location button - opening app settings directly")
                openAppSettings()
            }
        }
        
        messageLayout.addView(titleText)
        messageLayout.addView(messageText)
        messageLayout.addView(enableButton)
        messageCard.addView(messageLayout)
        contentLayout.addView(createSpacer(20))
        contentLayout.addView(messageCard)
    }
    
    private fun showPermissionDeniedPermanentlyMessage() {
        // Nascondo loading e errore
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        
        // Pulisco e mostro il contenuto
        contentLayout.removeAllViews()
        contentLayout.visibility = View.VISIBLE
        
        // Creo messaggio per permessi negati definitivamente
        val messageCard = createCard()
        
        val messageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }
        
        val titleText = TextView(this).apply {
            text = "Permessi di localizzazione negati"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_primary))
            gravity = Gravity.CENTER
        }
        
        val messageText = TextView(this).apply {
            text = "I permessi di localizzazione sono stati negati definitivamente.\n\nPer visualizzare il meteo della tua posizione, vai in:\nImpostazioni → App → MUSES → Permessi → Posizione"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.ios_text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        
        // Aggiungo pulsante per aprire le impostazioni
        val settingsButton = Button(this).apply {
            text = "Apri Impostazioni App"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@WeatherActivity, R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@WeatherActivity, R.color.primary_color))
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                Log.d(TAG, "User clicked open app settings button")
                openAppSettings()
            }
        }
        
        messageLayout.addView(titleText)
        messageLayout.addView(messageText)
        messageLayout.addView(settingsButton)
        messageCard.addView(messageLayout)
        contentLayout.addView(createSpacer(20))
        contentLayout.addView(messageCard)
    }
}