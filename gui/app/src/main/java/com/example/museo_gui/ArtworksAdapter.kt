package com.example.museo_gui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.museo_gui.models.*

class ArtworksAdapter(private val artworks: List<Artwork>) :
    RecyclerView.Adapter<ArtworksAdapter.ArtworkViewHolder>() {

    class ArtworkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artworkImage: ImageView = view.findViewById(R.id.artworkImage)
        val artworkName: TextView = view.findViewById(R.id.artworkName)
        val artworkType: TextView = view.findViewById(R.id.artworkType)
        val artworkDescription: TextView = view.findViewById(R.id.artworkDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artwork, parent, false)
        return ArtworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtworkViewHolder, position: Int) {
        val artwork = artworks[position]

        holder.artworkName.text = artwork.name
        holder.artworkType.text = artwork.type
        holder.artworkDescription.text = artwork.description

        Glide.with(holder.itemView.context)
            .load(artwork.imageurl)
            .placeholder(R.drawable.ic_museum)
            .error(R.drawable.ic_museum)
            .centerCrop()
            .into(holder.artworkImage)
    }

    override fun getItemCount() = artworks.size
}