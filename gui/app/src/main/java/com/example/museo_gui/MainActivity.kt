package com.example.museo_gui

import com.example.museo_gui.models.*
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.RenderedQueryGeometry
import Message
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.museo_gui.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import com.bumptech.glide.Glide
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import android.content.Intent
import kotlin.math.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import androidx.core.graphics.drawable.DrawableCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.eq
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.literal
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

// 1. Data class for card info
data class CardData(val title: String, val progress: Int)

// Define an interface to handle card clicks
interface OnCardClickListener {
    fun onCardClick(cardData: CardData)
}

class MainActivity : AppCompatActivity(), OnCardClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapView: MapView
    private lateinit var mapboxMap: MapboxMap
    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var chatApiService: ChatApiService
    private lateinit var artworkApiService: ArtworkApiService
    private val messages = mutableListOf<Message>()

    // Managers
    private lateinit var locationManager: AppLocationManager
    private lateinit var museumManager: MuseumManager
    private lateinit var mapManager: MapManager
    private lateinit var navigationManager: NavigationManager

    private lateinit var museumApiService: MuseumApiService
    private val allMuseums = mutableListOf<MuseumResponse>()
    private val mainMuseums = mutableListOf<MuseumResponse>()
    private val minorMuseums = mutableListOf<MuseumResponse>()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var fabMenu: FloatingActionButton

    private lateinit var sessionManager: SessionManager

    private var isMuseumsLoaded = false
    private var isRetryingMuseumLoad = false

    // Variables to hold the loaded preferences
    private var selectedMuseumTypes: Set<String> = emptySet()
    private var selectedArtworkTypes: Set<String> = emptySet()

    // Card viewer
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    companion object {
        private const val MUSEUM_DATA_SOURCE_ID = "museum-data-source"
        private const val USER_LOCATION_SOURCE_ID = "user-location-source"
        private const val USER_LOCATION_LAYER_ID = "user-location-layer"
        private val PARIS_DEFAULT_LOCATION = Point.fromLngLat(2.3522, 48.8566)
        private const val DEFAULT_FALLBACK_ZOOM = 12.0
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        fabMenu = findViewById(R.id.fabMenu)

        // Card viewer
        viewPager = findViewById(R.id.viewPagerCards)
        tabLayout = findViewById(R.id.tabLayoutIndicator)

        // Sample data
        val cardItems = listOf(
            CardData("Il Segreto del Faraone", 70),
            CardData("Quest 2", 40),
            CardData("Quest 3", 90)
        )

        // 3. Set up the adapter and link the indicator
        viewPager.adapter = CardsAdapter(cardItems, this)

        TabLayoutMediator(tabLayout, viewPager) { _, _ ->
            // This callback is required but can be empty if you only want dots
        }.attach()

        setupDrawer()
        initializeManagers()
        updateUserInfoInDrawer()
        setupChat()
        setupApiService()
        setupClickListeners()
        setupMap()

        // Carica sempre i musei ad ogni apertura
        // loadMuseums()

        checkLocationPermissionsAndStartUpdates()
    }

    // Implementation of the OnCardClickListener interface
    override fun onCardClick(cardData: CardData) {
        // Create an Intent to open the new activity (e.g., DetailActivity)
        val intent = Intent(this, DetailActivity::class.java)

        // Pass data using Intent extras
        intent.putExtra("card_title", cardData.title)
        intent.putExtra("card_progress", cardData.progress)

        // Start the new activity
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // Load preferences every time the activity becomes active.
        loadFilterPreferences()

        // Reload museums to apply the filter. The filtering logic is now inside loadMuseums.
        loadMuseums()
    }

    private fun loadFilterPreferences() {
        // Access the same SharedPreferences file used in PreferencesActivity
        val prefs = getSharedPreferences("FilterPreferences", Context.MODE_PRIVATE)

        // Load the sets. The second argument is the default value if the key is not found.
        // We use 'emptySet()' to avoid nulls.
        selectedMuseumTypes = prefs.getStringSet("selected_museum_types", emptySet()) ?: emptySet()
        selectedArtworkTypes = prefs.getStringSet("selected_artwork_types", emptySet()) ?: emptySet()

        // You can now use these sets for filtering
        Log.d("MainActivity", "Loaded museum preferences: $selectedMuseumTypes")
        Log.d("MainActivity", "Loaded artwork preferences: $selectedArtworkTypes")
    }

    /**
     * Filters a list of museums based on user preferences for museum and artwork types.
     * This function runs concurrently for performance.
     */
    private suspend fun getFilteredMuseums(
        museumsToFilter: List<MuseumResponse>,
        museumPrefs: Set<String>,
        artworkPrefs: Set<String>
    ): List<MuseumResponse> {
        // If both preference sets are empty, no filtering is needed.
        if (museumPrefs.isEmpty() && artworkPrefs.isEmpty()) {
            return museumsToFilter
        }

        Log.d("Filter", "Filtering with museumPrefs: $museumPrefs and artworkPrefs: $artworkPrefs")

        // Use withContext to run the heavy filtering off the main thread.
        return withContext(Dispatchers.IO) {
            museumsToFilter.map { museum ->
                // Launch an async coroutine for each museum to check them in parallel.
                async {
                    // Condition 1: The museum's own type is in the preferences.
                    val museumTypeMatch = museumPrefs.isNotEmpty() && museumPrefs.contains(museum.type)

                    // If it matches by museum type, include it immediately without the expensive artwork check.
                    if (museumTypeMatch) {
                        return@async museum
                    }

                    // Condition 2: The museum contains an artwork of a preferred type.
                    // This is only checked if the first condition is false and artwork preferences exist.
                    if (artworkPrefs.isNotEmpty() && checkMuseumForArtworkTypes(museum, artworkPrefs)) {
                        return@async museum
                    }

                    // If neither condition is met, this museum is filtered out.
                    null
                }
            }.awaitAll().filterNotNull() // Wait for all checks to complete and filter out the nulls.
        }
    }

    /**
     * Helper function to fetch artworks for a single museum and check if any match the user's preferences.
     * Returns true if a match is found, false otherwise.
     */
    private suspend fun checkMuseumForArtworkTypes(museum: MuseumResponse, artworkPrefs: Set<String>): Boolean {
        try {
            // Fetch the artworks for the given museum. I'm reusing your existing network logic.
            val artworksJson = fetchArtworksForMuseum(museum.name) ?: return false
            val artworks = parseArtworksResponse(artworksJson)

            if (artworks.isEmpty()) {
                return false
            }

            // Check if any artwork's type is in the preferred set.
            val isMatch = artworks.any { artwork -> artworkPrefs.contains(artwork.type) }
            if (isMatch) {
                Log.d("Filter", "Found matching artwork in ${museum.name}")
            }
            return isMatch

        } catch (e: Exception) {
            Log.e("Filter", "Could not check artwork types for ${museum.name}", e)
            return false // On error, assume it doesn't match to avoid incorrect inclusions.
        }
    }

    /**
     * Extracts the network call logic from your showArtworksList function to make it reusable.
     * It returns the raw JSON string or null on failure.
     */
    private suspend fun fetchArtworksForMuseum(museumName: String): String? {
        // This reuses the same robust OkHttp client setup from your showArtworksList function[cite: 148, 149, 150, 154].
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        val cleanedMuseumName = cleanMuseumNameForUrl(museumName)
        val url = "http://172.31.0.110:30503/getoperebymuseum/$cleanedMuseumName"
        val request = okhttp3.Request.Builder().url(url).get().build()

        return try {
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FilterFetch", "Failed to fetch artworks for $museumName", e)
            null
        }
    }

    private fun setupDrawer() {
        // Listener per il pulsante menu
        fabMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Setup del pulsante di chiusura nell'header
        setupDrawerCloseButton()

        // Listener per i menu del drawer
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    // Azione per Profilo
                    handleProfileClick()
                }
                R.id.nav_settings -> {
                    // Azione per Impostazioni
                    handleSettingsClick()
                }
                R.id.nav_help -> {
                    // Azione per Aiuto
                    handleHelpClick()
                }
                R.id.nav_preferences -> {
                    // Azione per Preferenze
                    handlePreferencesClick()
                }
                R.id.nav_about -> {
                    // Azione per Informazioni
                    handleAboutClick()
                }
                R.id.nav_logout -> {
                    // Azione per Logout
                    handleLogoutClick()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupDrawerCloseButton() {
        // Ottieni l'header view del navigation drawer
        val headerView = navigationView.getHeaderView(0)

        // Trova il pulsante di chiusura nell'header
        val closeButton = headerView.findViewById<ImageView>(R.id.nav_close_button)

        // Imposta il listener per chiudere il drawer
        closeButton?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    // Metodi per gestire i click sui menu items
    private fun handleHomeClick() {
        // Implementa la logica per Home
    }

    private fun handleProfileClick() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    private fun handlePreferencesClick() {
        val intent = Intent(this, PreferencesActivity::class.java)
        startActivity(intent)
    }

    private fun handleSettingsClick() {
        // Implementa la logica per Impostazioni
    }

    private fun handleHelpClick() {
        // Implementa la logica per Aiuto
    }

    private fun handleAboutClick() {
        // Implementa la logica per Informazioni
    }

    private fun handleLogoutClick() {
        // Esegui il logout
        sessionManager.logout()

        // Crea intent per andare al login (sostituisci LoginActivity con il nome della tua activity di login)
        val intent = Intent(this, LoginActivity::class.java)

        // Pulisci lo stack delle activity per evitare che l'utente torni indietro
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // Avvia l'activity di login
        startActivity(intent)

        // Chiudi l'activity corrente
        finish()
    }

    private fun updateUserInfoInDrawer() {
        // Ottieni l'header view del navigation drawer
        val headerView = navigationView.getHeaderView(0)

        // Trova le view per nome e email nell'header
        val userNameTextView = headerView.findViewById<TextView>(R.id.nav_header_username) // Sostituisci con l'ID corretto
        val userEmailTextView = headerView.findViewById<TextView>(R.id.nav_header_email) // Sostituisci con l'ID corretto

        // Recupera i dati dell'utente dal SessionManager usando le tue funzioni
        val username = sessionManager.getUsername()
        val email = sessionManager.getEmail()

        // Aggiorna le TextView con i dati dell'utente loggato
        userNameTextView?.text = username ?: "Nome utente non disponibile"
        userEmailTextView?.text = email ?: "Email non disponibile"
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun initializeManagers() {
        locationManager = AppLocationManager(this)
        museumManager = MuseumManager()
        navigationManager = NavigationManager(this)
        sessionManager = SessionManager(this)

        // Imposta i callback per la localizzazione
        locationManager.setLocationCallback(object : AppLocationManager.LocationCallback {
            override fun onLocationChanged(location: Point) {
                if (::mapManager.isInitialized) {
                    mapManager.updateUserLocationOnMap(location)
                }
            }

            override fun onInitialLocationSet(location: Point) {
                if (::mapManager.isInitialized) {
                    mapManager.flyToLocation(location)
                }
            }
        })
    }

    private fun loadMuseums() {
        // Reset dello stato prima di caricare
        isMuseumsLoaded = false

        lifecycleScope.launch {
            try {
                // Show a loading indicator to the user if you have one
                // binding.progressBar.visibility = View.VISIBLE

                museumManager.loadMuseums(object : MuseumManager.MuseumCallback {
                    override fun onMuseumsLoaded(museums: List<MuseumResponse>) {
                        // Keep the original full list of museums
                        allMuseums.clear()
                        allMuseums.addAll(museums)

                        // Launch a new coroutine to perform the filtering
                        lifecycleScope.launch {
                            Log.d("Filter", "Starting museum filtering based on preferences...")

                            // Apply the filter
                            val filteredMuseums = getFilteredMuseums(allMuseums, selectedMuseumTypes, selectedArtworkTypes)

                            Log.d("Filter", "Filtering complete. Original: ${allMuseums.size}, Filtered: ${filteredMuseums.size}")

                            // Clear and repopulate the main and minor museum lists with the *filtered* data
                            mainMuseums.clear()
                            minorMuseums.clear()

                            filteredMuseums.forEach { museum ->
                                if (museum.parent == null || museum.parent.isEmpty()) {
                                    mainMuseums.add(museum)
                                } else {
                                    minorMuseums.add(museum)
                                }
                            }

                            // Aggiorna la mappa con i nuovi dati filtrati
                            if (::mapManager.isInitialized) {
                                mapManager.updateMapWithMuseums(filteredMuseums)
                            }

                            // Aggiorna la mappa se lo stile è già caricato
                            if (::mapboxMap.isInitialized) {
                                mapboxMap.getStyle { style ->
                                    if (style != null) {
                                        updateMapWithMuseumData(style)
                                    }
                                }
                            }

                            // Hide the loading indicator
                            // binding.progressBar.visibility = View.GONE

                            isMuseumsLoaded = true
                            isRetryingMuseumLoad = false

                            Log.d("MainActivity", "Musei caricati correttamente")
                            Log.d("MainActivity", "Numero di musei caricati: ${filteredMuseums.size}")
                            Log.d("MainActivity", "Musei caricati: $filteredMuseums")
                            Log.d("MainActivity", "Musei principali caricati: ${mainMuseums.size}")
                            Log.d("MainActivity", "Musei minori caricati: ${minorMuseums.size}")
                        }
                    }

                    override fun onError(error: String) {
                        Log.e("MainActivity", "Errore caricamento musei: $error")
                        // Hide the loading indicator
                        // binding.progressBar.visibility = View.GONE
                        isMuseumsLoaded = false
                        showRetryOption()
                    }
                })
            } catch (e: Exception) {
                Log.e("MainActivity", "Eccezione durante il caricamento dei musei", e)
                isMuseumsLoaded = false
                Toast.makeText(this@MainActivity, "Errore di connessione. Verifica la tua connessione internet.", Toast.LENGTH_LONG).show()
                showRetryOption()
            }
        }
    }

    private fun updateMapWithMuseumData(style: Style) {
        try {
            // Rimuovi i layer esistenti se presenti
            try {
                style.removeStyleLayer("main-museums-layer")
                style.removeStyleLayer("minor-museums-layer")
                style.removeStyleSource(MUSEUM_DATA_SOURCE_ID)
            } catch (e: Exception) {
                Log.d("MapUpdate", "Layer/source non presenti, procedo con l'aggiunta")
            }

            // Aggiungi i nuovi dati
            if (allMuseums.isNotEmpty()) {
                addInvisibleMuseumData(style)
                addMuseumLayers(style)
            }
        } catch (e: Exception) {
            Log.e("MapUpdate", "Errore nell'aggiornamento della mappa", e)
        }
    }

    private fun showRetryOption() {
        // Crea un Snackbar con azione di retry
        val snackbar = Snackbar.make(
            binding.root,
            "Impossibile caricare i musei",
            Snackbar.LENGTH_INDEFINITE
        )

        snackbar.setAction("RIPROVA") {
            isRetryingMuseumLoad = true
            loadMuseums()
        }

        snackbar.show()
    }

    private fun checkLocationPermissionsAndStartUpdates() {
        if (!locationManager.hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                AppLocationManager.LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Controlla esplicitamente i permessi prima di avviare gli aggiornamenti
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.startLocationUpdates()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppLocationManager.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Controlla esplicitamente i permessi prima di avviare gli aggiornamenti
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.startLocationUpdates()
                }
            } else {
                Toast.makeText(this, "Permessi di localizzazione negati", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNavigation(destination: Point, museumName: String) {
        val selectedMinorMuseums = minorMuseums.filter { it.parent == museumName }
        Log.d("Navigation", "Selected minor museums: $selectedMinorMuseums")

        navigationManager.startNavigation(destination, museumName, selectedMinorMuseums)
    }

    private fun setupMap() {
        mapView = binding.mapView
        mapboxMap = mapView.getMapboxMap()
        mapManager = MapManager(mapboxMap)

        val initialCameraLocation = PARIS_DEFAULT_LOCATION
        val initialZoom = DEFAULT_FALLBACK_ZOOM

        mapboxMap.loadStyleUri(Style.SATELLITE_STREETS) { style ->

            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(initialCameraLocation)
                    .zoom(16.0)
                    .pitch(45.0)
                    .build()
            )

            val buildingsLayer = FillExtrusionLayer("3d-buildings", "composite").apply {
                sourceLayer("building")
                filter(Expression.eq(Expression.get("extrude"), Expression.literal("true")))
                minZoom(15.0)
                fillExtrusionColor(Expression.rgb(170.0, 170.0, 160.0)) // colore cemento chiaro
                fillExtrusionHeight(Expression.get("height"))
                fillExtrusionBase(Expression.get("min_height"))
                fillExtrusionOpacity(0.8) // completamente opachi
            }

            style.addLayer(buildingsLayer)

            Log.d("MapSetup", "Camera inizializzata con posizione di fallback")

            // Aggiungi i layer dei musei se disponibili
            if (allMuseums.isNotEmpty()) {
                addInvisibleMuseumData(style)
                addMuseumLayers(style)
            }

            // Aggiungi il layer per la posizione utente
            addUserLocationLayer(style)

            setupMapClickListener()
        }
    }

    private fun addUserLocationLayer(style: Style) {
        try {
            // Crea la source per la posizione utente
            val userLocationSource = geoJsonSource(USER_LOCATION_SOURCE_ID) {
                data(FeatureCollection.fromFeatures(emptyList()).toJson())
            }

            style.addSource(userLocationSource)

            // Aggiungi l'icona utente dal drawable
            addUserIconToStyle(style)

            // Crea il layer con l'icona - usa la nuova sintassi
            val userLocationLayer = SymbolLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID).apply {
                iconImage("user-location-icon")
                iconSize(1.0)
                iconAllowOverlap(true)
                iconIgnorePlacement(true)
                iconAnchor(IconAnchor.CENTER)
            }

            style.addLayer(userLocationLayer)

            Log.d("UserLocation", "Layer posizione utente aggiunto")

        } catch (e: Exception) {
            Log.e("UserLocation", "Errore nell'aggiunta del layer utente", e)
        }
    }

    private fun addUserIconToStyle(style: Style) {
        try {
            // Carica l'icona dal drawable
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_user)

            if (drawable != null) {
                // Converti il drawable in bitmap
                val bitmap = drawableToBitmap(drawable)

                // Aggiungi l'icona allo stile della mappa - usa addImage direttamente
                style.addImage("user-location-icon", bitmap)

                Log.d("UserLocation", "Icona utente aggiunta allo stile")
            } else {
                Log.e("UserLocation", "Drawable ic_user non trovato")
            }

        } catch (e: Exception) {
            Log.e("UserLocation", "Errore nell'aggiunta dell'icona utente", e)
        }
    }


    private fun addInvisibleMuseumData(style: Style) {
        // Crea features per i musei principali
        val mainFeatures = mainMuseums.map { museum ->
            val point = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
            val feature = Feature.fromGeometry(point)
            feature.addStringProperty("name", museum.name)
            feature.addStringProperty("description", museum.description)
            feature.addStringProperty("hours", museum.hours)
            feature.addStringProperty("price", museum.price)
            feature.addStringProperty("rating", museum.rating.toString())
            feature.addStringProperty("image", museum.imageurl)
            feature.addStringProperty("type", "main")
            feature.addStringProperty("museumType", museum.type ?: "N/A")
            feature
        }

        // Crea features per i musei minori
        val minorFeatures = minorMuseums.map { museum ->
            val point = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
            val feature = Feature.fromGeometry(point)
            feature.addStringProperty("name", museum.name)
            feature.addStringProperty("description", museum.description)
            feature.addStringProperty("hours", museum.hours)
            feature.addStringProperty("price", museum.price)
            feature.addStringProperty("rating", museum.rating.toString())
            feature.addStringProperty("image", museum.imageurl)
            feature.addStringProperty("type", "minor")
            feature.addStringProperty("museumType", museum.type ?: "N/A")
            feature.addStringProperty("parentMuseum", museum.parent ?: "")
            feature
        }

        val allFeatures = mainFeatures + minorFeatures
        val featureCollection = FeatureCollection.fromFeatures(allFeatures)

        // Usa data() invece di featureCollection()
        style.addSource(
            geoJsonSource(MUSEUM_DATA_SOURCE_ID) {
                data(featureCollection.toJson())
                cluster(false)
            }
        )
    }


    private fun addMuseumLayers(style: Style) {
        try {
            // --- Icona per Musei Principali (ROSSA) ---
            val mainMuseumIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_museum)
            mainMuseumIconDrawable?.let { drawable ->
                val tintedDrawable = DrawableCompat.wrap(drawable.mutate())
                DrawableCompat.setTint(tintedDrawable, ContextCompat.getColor(this, R.color.ios_red)) // Assicurati R.color.ios_red esista
                val bitmap = drawableToBitmap(tintedDrawable)
                style.addImage("main-museum-icon", bitmap)
            }

            // Layer per musei principali
            style.addLayer(
                symbolLayer("main-museums-layer", MUSEUM_DATA_SOURCE_ID) {
                    iconImage("main-museum-icon")
                    iconSize(0.8)
                    iconAllowOverlap(true)
                    // MODIFICA QUI: qualifica completamente la chiamata a 'get'
                    filter(eq(com.mapbox.maps.extension.style.expressions.dsl.generated.get("type"), literal("main")))
                }
            )

            // --- Icona per Musei Minori (BLU APPLE) ---
            val minorMuseumIconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_museum)
            minorMuseumIconDrawable?.let { drawable ->
                val tintedDrawable = DrawableCompat.wrap(drawable.mutate())
                DrawableCompat.setTint(tintedDrawable, ContextCompat.getColor(this, R.color.apple_blue_dark)) // Assicurati R.color.apple_blue esista
                val bitmap = drawableToBitmap(tintedDrawable)
                style.addImage("minor-museum-icon", bitmap)
            }

            // Layer per musei minori
            style.addLayer(
                symbolLayer("minor-museums-layer", MUSEUM_DATA_SOURCE_ID) {
                    iconImage("minor-museum-icon")
                    iconSize(0.6)
                    iconAllowOverlap(true)
                    // MODIFICA QUI: qualifica completamente la chiamata a 'get'
                    filter(eq(com.mapbox.maps.extension.style.expressions.dsl.generated.get("type"), literal("minor")))
                }
            )

        } catch (e: Exception) {
            Log.e("MapLayers", "Errore nell'aggiunta dei layer musei", e)
            Toast.makeText(this, "Errore caricamento icone mappa: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // La tua funzione drawableToBitmap rimane invariata
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }


    private fun setupMapClickListener() {
        mapboxMap.addOnMapClickListener { point ->
            Log.d("MapClick", "Click sulla mappa alle coordinate: ${point.longitude()}, ${point.latitude()}")

            // Cerca musei Mapbox nativi vicino al punto cliccato
            checkForNativeMuseumClick(point)

            true
        }
    }

    private fun checkForNativeMuseumClick(clickPoint: Point) {
        val screenCoordinate = mapboxMap.pixelForCoordinate(clickPoint)
        val queryGeometry = RenderedQueryGeometry(screenCoordinate)

        // Query per le features native di Mapbox
        mapboxMap.queryRenderedFeatures(queryGeometry, RenderedQueryOptions(emptyList(), null)) { queryResult ->
            queryResult.value?.let { features ->
                Log.d("MapClick", "Feature native trovate: ${features.size}")

                for (feature in features) {
                    // Controlla se la feature è un museo o attrazione
                    val featureType = getFeatureProperty(feature, "type")
                    val featureName = getFeatureProperty(feature, "name") ?:
                    getFeatureProperty(feature, "name_en") ?:
                    getFeatureProperty(feature, "name:en")

                    Log.d("MapClick", "Feature trovata - Type: $featureType, Name: $featureName")

                    if (isMuseumFeature(featureType, featureName)) {
                        Log.d("MapClick", "Museo nativo trovato: $featureName")

                        // Cerca nei nostri dati se abbiamo informazioni per questo museo
                        val museumData = findMuseumByName(featureName)

                        if (museumData != null) {
                            Log.d("MapClick", "Dati museo trovati per: ${museumData.name}")
                            showMuseumBottomSheet(
                                museumData.name,
                                museumData.description,
                                museumData.hours,
                                museumData.price,
                                museumData.rating.toString(), // Convert Double to String
                                museumData.imageurl, // Fixed: was imageUrl, should be imageurl
                                museumData.location.longitude, // Fixed: removed () - it's a property, not a function
                                museumData.location.latitude // Fixed: removed () - it's a property, not a function
                            )
                            return@let
                        } else {
                            Log.d("MapClick", "Nessun dato trovato per il museo: $featureName")
                        }
                    }
                }

                // Se non è stato trovato nessun museo nativo, controlla se siamo vicini a uno dei nostri musei
                checkNearbyCustomMuseums(clickPoint)
            }
        }
    }

    private fun getFeatureProperty(feature: Any, propertyName: String): String? {
        return try {
            when (feature) {
                is com.mapbox.maps.QueriedRenderedFeature -> {
                    val feat = feature.queriedFeature.feature
                    feat?.getProperty(propertyName)?.let { property ->
                        when {
                            property.isJsonPrimitive -> property.asString
                            property.isJsonNull -> null
                            else -> property.toString().replace("\"", "")
                        }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("MapData", "Errore nell'estrazione della proprietà $propertyName", e)
            null
        }
    }

    private fun isMuseumFeature(featureType: String?, featureName: String?): Boolean {
        val museumTypes = listOf(
            "museum", "gallery", "art_gallery", "attraction", "historic", "monument",
            "tourism", "cultural", "heritage", "archaeological_site"
        )

        val museumKeywords = listOf(
            "museum", "museo", "gallery", "galeria", "louvre", "prado", "uffizi",
            "hermitage", "vatican", "rijksmuseum", "british museum", "metropolitan"
        )

        val typeMatch = featureType?.lowercase()?.let { type ->
            museumTypes.any { type.contains(it) }
        } ?: false

        val nameMatch = featureName?.lowercase()?.let { name ->
            museumKeywords.any { name.contains(it) }
        } ?: false

        return typeMatch || nameMatch
    }

    private fun findMuseumByName(featureName: String?): MuseumResponse? {
        if (featureName == null) return null

        val searchName = featureName.lowercase()

        // Cerca prima per nome esatto
        allMuseums.forEach { museum ->
            if (museum.name.lowercase() == searchName) {
                return museum
            }
        }

        // Cerca per parole chiave
        allMuseums.forEach { museum ->
            val museumName = museum.name.lowercase()
            val keywords = listOf(museumName) +
                    when {
                        museumName.contains("louvre") -> listOf("louvre")
                        museumName.contains("british") -> listOf("british", "museum")
                        museumName.contains("metropolitan") -> listOf("metropolitan", "met")
                        museumName.contains("uffizi") -> listOf("uffizi", "gallery")
                        museumName.contains("hermitage") -> listOf("hermitage", "winter palace")
                        museumName.contains("prado") -> listOf("prado")
                        museumName.contains("vatican") -> listOf("vatican", "sistine")
                        museumName.contains("rijksmuseum") -> listOf("rijksmuseum", "amsterdam")
                        else -> emptyList()
                    }

            if (keywords.any { keyword -> searchName.contains(keyword) || keyword.contains(searchName) }) {
                return museum
            }
        }

        return null
    }
    private fun checkNearbyCustomMuseums(clickPoint: Point) {
        val maxDistance = 50.0 // metri

        // Controlla i musei principali
        mainMuseums.forEach { museum ->
            val museumPoint = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
            val distance = calculateDistance(clickPoint, museumPoint)
            if (distance <= maxDistance) {
                Log.d("MapClick", "Museo principale vicino trovato: ${museum.name} a ${distance.toInt()}m")
                showMuseumBottomSheet(
                    museum.name,
                    museum.description,
                    museum.hours,
                    museum.price,
                    museum.rating.toString(),
                    museum.imageurl,
                    museum.location.longitude,
                    museum.location.latitude,
                    "main"
                )
                return
            }
        }

        // Controlla i musei minori
        minorMuseums.forEach { museum ->
            val museumPoint = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
            val distance = calculateDistance(clickPoint, museumPoint)
            if (distance <= maxDistance) {
                Log.d("MapClick", "Museo minore vicino trovato: ${museum.name} a ${distance.toInt()}m")
                showMinorMuseumBottomSheet(museum)
                return
            }
        }
    }

    private fun showMinorMuseumBottomSheet(museum: MuseumResponse) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_museum, null)

        try {
            bottomSheetView.findViewById<TextView>(R.id.museumName)?.text = museum.name
            bottomSheetView.findViewById<TextView>(R.id.museumDescription)?.text =
                "${museum.description}\n\n🎨 Categoria: ${museum.type ?: "N/A"}\n🔗 Collegato al ${museum.parent ?: "N/A"}"
            bottomSheetView.findViewById<TextView>(R.id.museumHours)?.text = "Orari: ${museum.hours}"
            bottomSheetView.findViewById<TextView>(R.id.museumPrice)?.text = "Ingresso: ${museum.price}"
            bottomSheetView.findViewById<TextView>(R.id.museumRating)?.text =
                "${museum.rating} ⭐ (${(Math.random() * 2000).toInt()} recensioni)"

            val imageView = bottomSheetView.findViewById<ImageView>(R.id.museumImage)
            imageView?.let { img ->
                Glide.with(this)
                    .load(museum.imageurl)
                    .placeholder(R.drawable.ic_museum)
                    .error(R.drawable.ic_museum)
                    .centerCrop()
                    .into(img)
            }

            val museumPoint = Point.fromLngLat(museum.location.longitude, museum.location.latitude)
            bottomSheetView.findViewById<Button>(R.id.btnGetDirections)?.setOnClickListener {
                startNavigation(museumPoint, museum.name)
            }

            bottomSheetView.findViewById<Button>(R.id.btnMoreInfo)?.setOnClickListener {
                val aiPrompt = "Dimmi tutto quello che sai sul museo ${museum.name}. " +
                        "Menziona anche la sua relazione con il ${museum.parent} e perché " +
                        "vale la pena visitarlo insieme. Includi:" +
                        "\n- Storia e caratteristiche uniche" +
                        "\n- Collezioni principali" +
                        "\n- Perché visitarlo insieme al ${museum.parent}" +
                        "\n- Consigli per ottimizzare la visita"

                val userMessage = Message("🏛️ Informazioni su ${museum.name}", true)
                messagesAdapter.addMessage(userMessage)
                scrollToBottom()

                showChat()
                sendMessageToServer(aiPrompt)
                bottomSheetDialog.dismiss()
            }

            val btnViewArtworks = bottomSheetView.findViewById<Button>(R.id.btnViewArtworks)
            btnViewArtworks?.setOnClickListener {
                showArtworksList(museum.name)
            }

            bottomSheetDialog.setContentView(bottomSheetView)
            bottomSheetDialog.show()

        } catch (e: Exception) {
            Log.e("BottomSheet", "Errore nella bottom sheet per museo minore", e)
            Toast.makeText(this, "Errore nel caricamento delle informazioni del museo", Toast.LENGTH_SHORT).show()
        }
    }


    private fun calculateDistance(point1: Point, point2: Point): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val lat1Rad = Math.toRadians(point1.latitude())
        val lat2Rad = Math.toRadians(point2.latitude())
        val deltaLatRad = Math.toRadians(point2.latitude() - point1.latitude())
        val deltaLngRad = Math.toRadians(point2.longitude() - point1.longitude())

        val a = sin(deltaLatRad / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun showMuseumBottomSheet(
        name: String,
        description: String,
        hours: String,
        price: String,
        rating: String,
        imageUrl: String,
        longitude: Double,
        latitude: Double,
        type: String = "main"
    ) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_museum, null)

        try {
            bottomSheetView.findViewById<TextView>(R.id.museumName)?.text = name

            val museum = allMuseums.find { it.name == name }
            val museumType = museum?.type ?: "N/A"

            val displayDescription = if (type == "main") {
                val minorMuseums = museumManager.getMinorMuseumsByParent(name)
                if (minorMuseums.isNotEmpty()) {
                    "$description\n\n🎨 Categoria: $museumType\n🗺️ Musei collegati nelle vicinanze: ${minorMuseums.size}"
                } else {
                    "$description\n\n🎨 Categoria: $museumType"
                }
            } else {
                "$description\n\n🎨 Categoria: $museumType"
            }

            bottomSheetView.findViewById<TextView>(R.id.museumDescription)?.text = displayDescription
            bottomSheetView.findViewById<TextView>(R.id.museumHours)?.text = "Orari: $hours"
            bottomSheetView.findViewById<TextView>(R.id.museumPrice)?.text = "Ingresso: $price"
            bottomSheetView.findViewById<TextView>(R.id.museumRating)?.text = "$rating ⭐ (${(Math.random() * 5000).toInt()} recensioni)"

            val imageView = bottomSheetView.findViewById<ImageView>(R.id.museumImage)
            imageView?.let { img ->
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_museum)
                    .error(R.drawable.ic_museum)
                    .centerCrop()
                    .into(img)
            }

            bottomSheetView.findViewById<Button>(R.id.btnGetDirections)?.setOnClickListener {
                val museumLocation = Point.fromLngLat(longitude, latitude)
                startNavigation(museumLocation, name)
            }

            val btnViewArtworks = bottomSheetView.findViewById<Button>(R.id.btnViewArtworks)
            btnViewArtworks?.setOnClickListener {
                // Mostra stato di caricamento
                btnViewArtworks.text = "Caricamento..."
                btnViewArtworks.isEnabled = false

                lifecycleScope.launch {
                    try {
                        showArtworksList(name)
                    } finally {
                        // Ripristina il pulsante
                        btnViewArtworks.text = "Visualizza Opere"
                        btnViewArtworks.isEnabled = true
                    }
                }
            }

            bottomSheetView.findViewById<Button>(R.id.btnMoreInfo)?.setOnClickListener {
                val aiPrompt = "Dimmi tutto quello che sai sul museo $name. Includi informazioni su:" +
                        "\n- Storia e fondazione" +
                        "\n- Opere più famose e collezioni principali" +
                        "\n- Curiosità e aneddoti interessanti" +
                        "\n- Consigli pratici per la visita" +
                        "\n- Cosa non perdere assolutamente"

                val userMessage = Message("🏛️ Informazioni dettagliate su $name", true)
                messagesAdapter.addMessage(userMessage)
                scrollToBottom()

                showChat()
                sendMessageToServer(aiPrompt)
                bottomSheetDialog.dismiss()
            }

            bottomSheetDialog.setContentView(bottomSheetView)
            bottomSheetDialog.show()

        } catch (e: Exception) {
            Log.e("BottomSheet", "Errore nella bottom sheet", e)
            Toast.makeText(this, "Errore nel caricamento delle informazioni del museo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanMuseumNameForUrl(museumName: String): String {
        return museumName
            // Lettere accentate minuscole con URL encoding
            .replace("à", "%C3%A0")
            .replace("á", "%C3%A1")
            .replace("â", "%C3%A2")
            .replace("ã", "%C3%A3")
            .replace("ä", "%C3%A4")
            .replace("å", "%C3%A5")
            .replace("è", "%C3%A8")
            .replace("é", "%C3%A9")
            .replace("ê", "%C3%AA")
            .replace("ë", "%C3%AB")
            .replace("ì", "%C3%AC")
            .replace("í", "%C3%AD")
            .replace("î", "%C3%AE")
            .replace("ï", "%C3%AF")
            .replace("ò", "%C3%B2")
            .replace("ó", "%C3%B3")
            .replace("ô", "%C3%B4")
            .replace("õ", "%C3%B5")
            .replace("ö", "%C3%B6")
            .replace("ù", "%C3%B9")
            .replace("ú", "%C3%BA")
            .replace("û", "%C3%BB")
            .replace("ü", "%C3%BC")
            .replace("ý", "%C3%BD")
            .replace("ÿ", "%C3%BF")
            .replace("ñ", "%C3%B1")
            .replace("ç", "%C3%A7")

            // Lettere accentate maiuscole con URL encoding
            .replace("À", "%C3%80")
            .replace("Á", "%C3%81")
            .replace("Â", "%C3%82")
            .replace("Ã", "%C3%83")
            .replace("Ä", "%C3%84")
            .replace("Å", "%C3%85")
            .replace("È", "%C3%88")
            .replace("É", "%C3%89")
            .replace("Ê", "%C3%8A")
            .replace("Ë", "%C3%8B")
            .replace("Ì", "%C3%8C")
            .replace("Í", "%C3%8D")
            .replace("Î", "%C3%8E")
            .replace("Ï", "%C3%8F")
            .replace("Ò", "%C3%92")
            .replace("Ó", "%C3%93")
            .replace("Ô", "%C3%94")
            .replace("Õ", "%C3%95")
            .replace("Ö", "%C3%96")
            .replace("Ù", "%C3%99")
            .replace("Ú", "%C3%9A")
            .replace("Û", "%C3%9B")
            .replace("Ü", "%C3%9C")
            .replace("Ý", "%C3%9D")
            .replace("Ÿ", "%C5%B8")
            .replace("Ñ", "%C3%91")
            .replace("Ç", "%C3%87")

            // Caratteri speciali
            .replace(" ", "%20")    // Spazi
            .replace("'", "%27")    // Apostrofi
            .replace("&", "%26")    // E commerciale
            .replace("(", "%28")    // Parentesi
            .replace(")", "%29")
            .replace(",", "%2C")    // Virgole
            .replace(".", "%2E")    // Punti
            .replace("/", "%2F")    // Slash
            .replace(":", "%3A")    // Due punti
            .replace(";", "%3B")    // Punto e virgola
            .replace("?", "%3F")    // Punto interrogativo
            .replace("@", "%40")    // Chiocciola
            .replace("#", "%23")    // Cancelletto
            .replace("+", "%2B")    // Plus
            .replace("=", "%3D")    // Uguale
    }

    // --- Funzioni della chat ---
    private fun setupChat() {
        messagesAdapter = MessagesAdapter(messages)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messagesAdapter
        }
    }

    private fun setupApiService() {
        val callTimeoutSeconds = 60L     // Ridotto
        val connectTimeoutSeconds = 30L  // Ridotto
        val readTimeoutSeconds = 30L     // Ridotto
        val writeTimeoutSeconds = 30L    // Ridotto

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC // Cambiato da BODY a BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(loggingInterceptor)
            .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS)) // Pool connessioni
            .build()

        // Retrofit per la chat
        val chatRetrofit = Retrofit.Builder()
            .baseUrl("http://172.31.0.247:32299/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        chatApiService = chatRetrofit.create(ChatApiService::class.java)

        // Retrofit per i musei
        val museumRetrofit = Retrofit.Builder()
            .baseUrl("http://172.31.0.110:31813/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        museumApiService = museumRetrofit.create(MuseumApiService::class.java)

        // Retrofit per le opere con client ottimizzato
        val artworkClient = OkHttpClient.Builder()
            .callTimeout(45L, TimeUnit.SECONDS)
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(20L, TimeUnit.SECONDS)
            .writeTimeout(20L, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .addHeader("Connection", "close")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        val artworkRetrofit = Retrofit.Builder()
            .baseUrl("http://172.31.0.110:31705/opere/")
            .client(artworkClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        artworkApiService = artworkRetrofit.create(ArtworkApiService::class.java)
    }

    private fun showArtworksList(museumName: String) {
        lifecycleScope.launch {
            try {
                Log.d("Artworks", "Caricamento opere per museo: $museumName")

                // Pulisce il nome del museo per l'URL
                val cleanedMuseumName = cleanMuseumNameForUrl(museumName)
                Log.d("Artworks", "Nome museo pulito: $cleanedMuseumName")

                val responseBody = withContext(Dispatchers.IO) {
                    // Configurazione client con gestione migliorata degli errori
                    val client = OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(25, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .callTimeout(45, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        // Aggiunta gestione per connessioni instabili
                        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .addInterceptor { chain ->
                            val original = chain.request()
                            val request = original.newBuilder()
                                .addHeader("Connection", "close")
                                .addHeader("Accept", "application/json")
                                .addHeader("User-Agent", "MuseoApp/1.0")
                                .addHeader("Accept-Charset", "UTF-8")
                                .addHeader("Cache-Control", "no-cache")
                                .build()
                            chain.proceed(request)
                        }
                        .build()

                    val url = "http://172.31.0.110:30503/getoperebymuseum/$cleanedMuseumName"
                    Log.d("Artworks", "URL finale: $url")

                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    // Implementazione retry con backoff esponenziale
                    var lastException: Exception? = null
                    var attempt = 0
                    val maxRetries = 3

                    while (attempt < maxRetries) {
                        try {
                            Log.d("Artworks", "Tentativo ${attempt + 1} di $maxRetries")

                            val response = client.newCall(request).execute()

                            if (response.isSuccessful) {
                                val responseBody = response.body
                                if (responseBody != null) {
                                    try {
                                        // Leggi la risposta in chunks per gestire meglio i problemi di stream
                                        val contentLength = responseBody.contentLength()
                                        Log.d("Artworks", "Content-Length: $contentLength")

                                        val source = responseBody.source()
                                        val buffer = okio.Buffer()

                                        // Leggi in chunks per evitare problemi con stream lunghi
                                        var totalBytesRead = 0L
                                        val chunkSize = 8192L // 8KB chunks

                                        while (!source.exhausted()) {
                                            val bytesRead = source.read(buffer, chunkSize)
                                            if (bytesRead == -1L) break
                                            totalBytesRead += bytesRead

                                            // Log del progresso per debug
                                            if (contentLength > 0) {
                                                val progress = (totalBytesRead * 100 / contentLength).toInt()
                                                Log.d("Artworks", "Progresso lettura: $progress%")
                                            }
                                        }

                                        val result = buffer.readUtf8()
                                        Log.d("Artworks", "Risposta ricevuta completamente, lunghezza: ${result.length}")

                                        response.close()
                                        return@withContext result

                                    } catch (e: Exception) {
                                        Log.e("Artworks", "Errore durante la lettura del body", e)
                                        response.close()
                                        throw e
                                    }
                                } else {
                                    Log.e("Artworks", "Response body è null")
                                    response.close()
                                    throw Exception("Response body è null")
                                }
                            } else {
                                Log.e("Artworks", "Errore HTTP: ${response.code} - ${response.message}")
                                val errorBody = response.body?.string()
                                response.close()
                                throw Exception("HTTP ${response.code}: ${response.message} - $errorBody")
                            }

                        } catch (e: Exception) {
                            lastException = e
                            attempt++

                            Log.w("Artworks", "Tentativo $attempt fallito: ${e.message}")

                            if (attempt < maxRetries) {
                                // Backoff esponenziale: attendi 1s, 2s, 4s
                                val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                                Log.d("Artworks", "Attesa di ${delayMs}ms prima del prossimo tentativo")
                                delay(delayMs)
                            }
                        }
                    }

                    // Se tutti i tentativi falliscono, lancia l'ultima eccezione
                    throw lastException ?: Exception("Tutti i tentativi di connessione falliti")
                }

                if (responseBody != null && responseBody.isNotEmpty()) {
                    val artworks = parseArtworksResponse(responseBody)
                    if (artworks.isNotEmpty()) {
                        showArtworksBottomSheet(museumName, artworks)
                    } else {
                        Log.w("Artworks", "Nessuna opera trovata nel parsing per $museumName")
                        Toast.makeText(this@MainActivity, "Nessuna opera trovata per $museumName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("Artworks", "ResponseBody vuoto o null")
                    Toast.makeText(this@MainActivity, "Risposta vuota dal server", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("Artworks", "Errore nel caricamento delle opere", e)

                val errorMessage = when (e) {
                    is java.net.ProtocolException -> {
                        if (e.message?.contains("unexpected end of stream") == true) {
                            "Connessione interrotta dal server - riprova"
                        } else {
                            "Errore di protocollo - riprova tra qualche secondo"
                        }
                    }
                    is java.net.SocketTimeoutException -> "Timeout - il server impiega troppo tempo"
                    is java.net.ConnectException -> "Impossibile connettersi al server delle opere"
                    is java.io.IOException -> "Errore di rete - controlla la connessione"
                    else -> "Errore di connessione: ${e.message}"
                }

                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Funzione helper per il parsing migliorato
    private fun parseArtworksResponse(jsonString: String): List<Artwork> {
        return try {
            if (jsonString.isBlank()) {
                Log.w("Artworks", "Stringa JSON vuota")
                return emptyList()
            }

            val trimmedJson = jsonString.trim()
            Log.d("Artworks", "Parsing JSON, lunghezza: ${trimmedJson.length}")
            Log.d("Artworks", "Primi 200 caratteri: ${trimmedJson.take(200)}")

            val gson = Gson()

            // Prova prima come array
            if (trimmedJson.startsWith("[")) {
                val type = object : TypeToken<List<Artwork>>() {}.type
                val artworks = gson.fromJson<List<Artwork>>(trimmedJson, type)
                Log.d("Artworks", "Parsing come array completato: ${artworks.size} opere")
                return artworks
            } else {
                // Prova come oggetto
                val jsonObject = gson.fromJson(trimmedJson, JsonObject::class.java)

                val artworks = when {
                    jsonObject.has("artworks") -> {
                        Log.d("Artworks", "Trovato campo 'artworks'")
                        val type = object : TypeToken<List<Artwork>>() {}.type
                        gson.fromJson<List<Artwork>>(jsonObject.get("artworks"), type)
                    }
                    jsonObject.has("data") -> {
                        Log.d("Artworks", "Trovato campo 'data'")
                        val type = object : TypeToken<List<Artwork>>() {}.type
                        gson.fromJson<List<Artwork>>(jsonObject.get("data"), type)
                    }
                    jsonObject.has("results") -> {
                        Log.d("Artworks", "Trovato campo 'results'")
                        val type = object : TypeToken<List<Artwork>>() {}.type
                        gson.fromJson<List<Artwork>>(jsonObject.get("results"), type)
                    }
                    else -> {
                        Log.e("Artworks", "Struttura JSON non riconosciuta")
                        Log.e("Artworks", "Campi disponibili: ${jsonObject.keySet()}")
                        Log.e("Artworks", "JSON (primi 500 caratteri): ${trimmedJson.take(500)}")
                        emptyList()
                    }
                }

                Log.d("Artworks", "Parsing come oggetto completato: ${artworks.size} opere")
                return artworks
            }

        } catch (e: Exception) {
            Log.e("Artworks", "Errore nel parsing JSON", e)
            Log.e("Artworks", "JSON che ha causato l'errore (primi 1000 caratteri): ${jsonString.take(1000)}")
            emptyList()
        }
    }

    // Funzione helper per gestire i delay nelle coroutine
    private suspend fun delay(timeMillis: Long) {
        kotlinx.coroutines.delay(timeMillis)
    }
    private fun showArtworksBottomSheet(museumName: String, artworks: List<Artwork>) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_artworks, null)

        bottomSheetView.findViewById<TextView>(R.id.artworksTitle)?.text = "Opere di $museumName"

        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.recyclerViewArtworks)
        recyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ArtworksAdapter(artworks)
        }

        bottomSheetView.findViewById<Button>(R.id.btnCloseArtworks)?.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(bottomSheetView)
        bottomSheetDialog.show()
    }

    private fun setupClickListeners() {
        binding.fabChat.setOnClickListener {
            showChat()
        }

        binding.btnCloseChat.setOnClickListener {
            hideChat()
        }

        binding.btnSendMessage.setOnClickListener {
            sendMessage()
        }
    }

    private fun showChat() {
        binding.chatContainer.visibility = View.VISIBLE
        binding.chatContainer.translationY = binding.chatContainer.height.toFloat()

        // Nascondi entrambi i FAB con animazione
        binding.fabChat.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.fabChat.visibility = View.GONE
            }
            .start()

        fabMenu.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                fabMenu.visibility = View.GONE
            }
            .start()

        binding.chatContainer.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideChat() {
        binding.chatContainer.animate()
            .translationY(binding.chatContainer.height.toFloat())
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                binding.chatContainer.visibility = View.GONE
            }
            .start()

        // Mostra entrambi i FAB con animazione
        binding.fabChat.visibility = View.VISIBLE
        binding.fabChat.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        fabMenu.visibility = View.VISIBLE
        fabMenu.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty()) {
            val userMessage = Message(messageText, true)
            messagesAdapter.addMessage(userMessage)
            scrollToBottom()
            binding.editTextMessage.text.clear()
            sendMessageToServer(messageText)
        }
    }

    private fun sendMessageToServer(messageTextFromUser: String) {
        if (!::chatApiService.isInitialized) {
            Log.e("ApiService", "ERRORE FATALE: chatApiService non è stato inizializzato!")
            showError("Errore interno: Servizio API non pronto.")
            binding.progressBarThinking.visibility = View.GONE
            binding.btnSendMessage.isEnabled = true
            return
        }

        binding.progressBarThinking.visibility = View.VISIBLE
        binding.btnSendMessage.isEnabled = false

        lifecycleScope.launch {
            try {
                val requestPayload = mapOf("prompt" to messageTextFromUser)
                Log.d("ApiService", "Invio richiesta al server con payload: $requestPayload")

                val response: Response<Map<String, String>> = withContext(Dispatchers.IO) {
                    chatApiService.sendMessage(requestPayload)
                }

                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    val serverResponse = chatResponse?.get("response")

                    if (serverResponse != null) {
                        Log.d("ApiService", "Risposta ricevuta con successo: $serverResponse")
                        val botMessage = Message(serverResponse, false)
                        messagesAdapter.addMessage(botMessage)
                        scrollToBottom()
                    } else {
                        Log.e("ApiService", "Risposta successfull ma campo 'response' vuoto")
                        showError("Risposta dal server senza campo 'response'")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ApiService", "Errore dal server: ${response.code()} - ${response.message()}. Errore corpo: $errorBody")
                    showError("Errore nel server: ${response.code()} ($errorBody)")
                }
            } catch (e: Exception) {
                Log.e("ApiService", "Eccezione durante la chiamata API:", e)
                when (e) {
                    is java.net.SocketTimeoutException -> showError("Timeout: La richiesta ha impiegato troppo tempo.")
                    is java.net.UnknownHostException -> showError("Host sconosciuto: Impossibile trovare il server.")
                    is java.net.ConnectException -> showError("Errore di connessione: Server offline o IP/porta errati.")
                    else -> showError("Errore di comunicazione: ${e.javaClass.simpleName}")
                }
            } finally {
                binding.progressBarThinking.visibility = View.GONE
                binding.btnSendMessage.isEnabled = true
            }
        }
    }

    private fun scrollToBottom() {
        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // --- Metodi del ciclo di vita della MapView ---
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
    }
}

// 2. Adapter for the ViewPager2
// Modify your CardsAdapter
class CardsAdapter(private val items: List<CardData>, private val onCardClickListener: OnCardClickListener) : RecyclerView.Adapter<CardsAdapter.CardViewHolder>() {

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.cardTitle)
        val progressBar: ProgressBar = view.findViewById(R.id.cardProgressBar)

        init {
            // Set click listener for the entire card item
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCardClickListener.onCardClick(items[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.progressBar.progress = item.progress
        // You might also want to set the percentage text if you have it in your CardData
    }

    override fun getItemCount(): Int = items.size
}