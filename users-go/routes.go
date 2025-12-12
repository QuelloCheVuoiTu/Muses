package main

import (
	"github.com/gorilla/mux"
	httpSwagger "github.com/swaggo/http-swagger"
)

func SetupRoutes(userHandler *UserHandler) *mux.Router {
	r := mux.NewRouter()

	// Swagger documentation endpoint
	r.PathPrefix("/swagger/").Handler(httpSwagger.WrapHandler)

	// Health check endpoint
	r.HandleFunc("/health", userHandler.HealthCheckHandler).Methods("GET")

	// Ready check endpoint
	r.HandleFunc("/ready", userHandler.ReadyCheckHandler).Methods("GET")

	// CRUD endpoints for users
	r.HandleFunc("/", userHandler.CreateUserHandler).Methods("POST")
	r.HandleFunc("/", userHandler.GetAllUsersHandler).Methods("GET")
	r.HandleFunc("/search", userHandler.GetUsersWithSearchHandler).Methods("GET")
	r.HandleFunc("/{id}", userHandler.GetUserHandler).Methods("GET")
	r.HandleFunc("/{id}", userHandler.UpdateUserHandler).Methods("PUT")
	r.HandleFunc("/{id}", userHandler.DeleteUserHandler).Methods("DELETE")
	r.HandleFunc("/{id}/preferences", userHandler.GetUserPreferencesHandler).Methods("GET")
	r.HandleFunc("/{id}/preferences", userHandler.UpdateUserPreferencesHandler).Methods("PATCH")

	return r
}
