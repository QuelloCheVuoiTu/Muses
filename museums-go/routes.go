package main

import (
	"github.com/gorilla/mux"
	httpSwagger "github.com/swaggo/http-swagger"
)

func SetupRoutes(museumHandler *MuseumHandler) *mux.Router {
	r := mux.NewRouter()

	// Swagger documentation endpoint
	r.PathPrefix("/swagger/").Handler(httpSwagger.WrapHandler)

	// Health check endpoint
	r.HandleFunc("/health", museumHandler.HealthCheckHandler).Methods("GET")
	r.HandleFunc("/ready", museumHandler.ReadyCheckHandler).Methods("GET")

	// CRUD endpoints for museums
	r.HandleFunc("/", museumHandler.CreateMuseumHandler).Methods("POST")
	r.HandleFunc("/", museumHandler.GetAllMuseumsHandler).Methods("GET")
	r.HandleFunc("/search", museumHandler.GetMuseumsWithSearchHandler).Methods("GET")
	r.HandleFunc("/{id}", museumHandler.GetMuseumHandler).Methods("GET")
	r.HandleFunc("/{id}", museumHandler.UpdateMuseumHandler).Methods("PUT")
	r.HandleFunc("/{id}", museumHandler.DeleteMuseumHandler).Methods("DELETE")

	return r
}
