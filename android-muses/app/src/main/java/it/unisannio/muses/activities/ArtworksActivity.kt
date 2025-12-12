package it.unisannio.muses.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.unisannio.muses.R
import it.unisannio.muses.adapters.ArtworkAdapter
import it.unisannio.muses.data.models.Artwork
import it.unisannio.muses.data.repositories.ArtworkRepository
import it.unisannio.muses.utils.ThemeManager
import kotlinx.coroutines.launch

class ArtworksActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_MUSEUM_ID = "museum_id"
        const val EXTRA_MUSEUM_NAME = "museum_name"
        
        fun newIntent(context: Context, museumId: String, museumName: String): Intent {
            return Intent(context, ArtworksActivity::class.java).apply {
                putExtra(EXTRA_MUSEUM_ID, museumId)
                putExtra(EXTRA_MUSEUM_NAME, museumName)
            }
        }
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingCard: View
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateCard: View
    private lateinit var tvMuseumName: TextView
    private lateinit var btnBack: ImageButton
    
    private lateinit var artworkAdapter: ArtworkAdapter
    private lateinit var artworkRepository: ArtworkRepository
    
    private var museumId: String = ""
    private var museumName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize theme
        ThemeManager.initializeTheme(this)
        
        setContentView(R.layout.activity_artworks)
        
        // Get extras
        museumId = intent.getStringExtra(EXTRA_MUSEUM_ID) ?: ""
        museumName = intent.getStringExtra(EXTRA_MUSEUM_NAME) ?: "Museum Artworks"
        
        initViews()
        setupRecyclerView()
        loadArtworks()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewArtworks)
        progressBar = findViewById(R.id.progressBar)
        loadingCard = findViewById(R.id.loadingCard)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        emptyStateCard = findViewById(R.id.emptyStateCard)
        tvMuseumName = findViewById(R.id.tvMuseumName)
        btnBack = findViewById(R.id.btnBack)
        
        // Set museum name in header
        tvMuseumName.text = "$museumName - Artworks"
        
        // Setup back button
        btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        artworkAdapter = ArtworkAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = artworkAdapter
    }
    
    private fun loadArtworks() {
        if (museumId.isEmpty()) {
            Log.e("ArtworksActivity", "Museum ID is empty")
            showEmptyState()
            return
        }
        
        artworkRepository = ArtworkRepository()
        
        // Show loading
        loadingCard.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateCard.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val response = artworkRepository.getArtworksByMuseum(museumId)
                
                if (response.isSuccessful) {
                    val artworks = response.body() ?: emptyList()
                    Log.d("ArtworksActivity", "Loaded ${artworks.size} artworks for museum $museumId")
                    
                    if (artworks.isNotEmpty()) {
                        // Show artworks
                        artworkAdapter.updateArtworks(artworks)
                        recyclerView.visibility = View.VISIBLE
                        loadingCard.visibility = View.GONE
                        emptyStateCard.visibility = View.GONE
                    } else {
                        // Show empty state
                        showEmptyState()
                    }
                } else {
                    Log.e("ArtworksActivity", "Failed to load artworks: ${response.code()}")
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("ArtworksActivity", "Error loading artworks", e)
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        loadingCard.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyStateCard.visibility = View.VISIBLE
    }
}