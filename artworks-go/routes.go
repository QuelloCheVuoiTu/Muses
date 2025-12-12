package main

import (
	"github.com/gorilla/mux"
	httpSwagger "github.com/swaggo/http-swagger"
)

func SetupRoutes(artworkHandler *ArtworkHandler) *mux.Router {
	r := mux.NewRouter()

	// Swagger documentation endpoint
	r.PathPrefix("/swagger/").Handler(httpSwagger.WrapHandler)

	// Health check endpoint
	r.HandleFunc("/health", artworkHandler.HealthCheckHandler).Methods("GET")
	r.HandleFunc("/ready", artworkHandler.ReadyCheckHandler).Methods("GET")

	// CRUD endpoints for artworks
	r.HandleFunc("/", artworkHandler.CreateArtworkHandler).Methods("POST")
	r.HandleFunc("/", artworkHandler.GetAllArtworksHandler).Methods("GET")
	r.HandleFunc("/search", artworkHandler.GetArtworksWithSearchHandler).Methods("GET")
	r.HandleFunc("/{id}", artworkHandler.GetArtworkHandler).Methods("GET")
	r.HandleFunc("/{id}", artworkHandler.UpdateArtworkHandler).Methods("PUT")
	r.HandleFunc("/{id}", artworkHandler.DeleteArtworkHandler).Methods("DELETE")

	return r
}
