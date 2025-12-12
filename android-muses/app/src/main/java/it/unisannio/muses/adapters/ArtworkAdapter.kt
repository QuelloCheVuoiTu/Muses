package it.unisannio.muses.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import it.unisannio.muses.R
import it.unisannio.muses.data.models.Artwork

class ArtworkAdapter(
    private var artworks: List<Artwork>
) : RecyclerView.Adapter<ArtworkAdapter.ArtworkViewHolder>() {

    class ArtworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val artworkImage: ImageView = itemView.findViewById(R.id.artworkImage)
        val artworkName: TextView = itemView.findViewById(R.id.artworkName)
        val artworkDescription: TextView = itemView.findViewById(R.id.artworkDescription)
        val exposureIndicator: View = itemView.findViewById(R.id.exposureIndicator)
        val exposureStatus: TextView = itemView.findViewById(R.id.exposureStatus)
        val exposureStatusLayout: ViewGroup = itemView.findViewById(R.id.exposureStatusLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artwork, parent, false)
        return ArtworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtworkViewHolder, position: Int) {
        val artwork = artworks[position]
        
        // Set artwork details
        holder.artworkName.text = artwork.name
        holder.artworkDescription.text = artwork.description
        
        // Load artwork image
        if (artwork.imageUrl.isNotEmpty()) {
            Picasso.get()
                .load(artwork.imageUrl)
                .placeholder(R.drawable.ic_palette)
                .error(R.drawable.ic_palette)
                .into(holder.artworkImage)
        } else {
            holder.artworkImage.setImageResource(R.drawable.ic_palette)
        }
        
        // Set exposure status
        val isExposed = artwork.isExposed ?: false
        if (isExposed) {
            holder.exposureStatus.text = "On Display"
            holder.exposureIndicator.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            )
            holder.exposureStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
            )
        } else {
            holder.exposureStatus.text = "Not on Display"
            holder.exposureIndicator.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
            )
            holder.exposureStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
            )
        }
    }

    override fun getItemCount(): Int = artworks.size

    fun updateArtworks(newArtworks: List<Artwork>) {
        artworks = newArtworks
        notifyDataSetChanged()
    }
}