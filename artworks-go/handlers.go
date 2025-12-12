package main

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/readpref"
)

type ArtworkHandler struct {
	artworkService *ArtworkService
}

func NewArtworkHandler(artworkService *ArtworkService) *ArtworkHandler {
	return &ArtworkHandler{artworkService: artworkService}
}

// Write a JSON error response
func (h *ArtworkHandler) writeErrorResponse(w http.ResponseWriter, message string, statusCode int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(ErrorResponse{Error: message})
}

// splitCommaSeparated splits a comma-separated string and trims whitespace.
func splitCommaSeparated(s string) []string {
	parts := strings.Split(s, ",")
	for i := range parts {
		parts[i] = strings.TrimSpace(parts[i])
	}
	return parts
}

// HealthCheck godoc
// @Summary Health check endpoint
// @Description Check if the service is running
// @Tags Health
// @Produce plain
// @Security BearerAuth
// @Success 200 {string} string "OK"
// @Router /health [get]
func (h *ArtworkHandler) HealthCheckHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Artwork system is online\n"))
}

// ReadyCheck godoc
// @Summary Service readiness probe
// @Description Deeper readiness check that verifies the service can reach required dependencies (e.g. MongoDB). Returns 200 when ready, 503 when not.
// @Tags Health
// @Produce plain
// @Success 200 {string} string "Artwork system is ready"
// @Failure 503 {string} string "Service Unavailable"
// @Router /ready [get]
func (h *ArtworkHandler) ReadyCheckHandler(w http.ResponseWriter, r *http.Request) {
	// Quick nil checks
	if h == nil || h.artworkService == nil || h.artworkService.client == nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: service not initialized\n"))
		return
	}

	// Ping MongoDB with a short timeout
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := h.artworkService.client.Ping(ctx, readpref.Primary()); err != nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: cannot reach mongodb\n"))
		return
	}

	w.Header().Set("Content-Type", "text/plain")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Artwork system is ready\n"))
}

// CreateArtwork godoc
// @Summary Create a new artwork
// @Description Create a new artwork entity
// @Tags Artworks
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param artwork body Artwork true "Artwork object"
// @Success 201 {object} Artwork
// @Failure 400 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /artworks [post]
func (h *ArtworkHandler) CreateArtworkHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	var artwork Artwork
	if err := json.NewDecoder(r.Body).Decode(&artwork); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	result, err := h.artworkService.CreateArtwork(&artwork)
	if err != nil {
		h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(result)
}

// GetAllArtworks godoc
// @Summary Get all artworks
// @Description Retrieve all artworks from the database
// @Tags Artworks
// @Produce json
// @Security BearerAuth
// @Success 200 {array} Artwork
// @Failure 500 {object} ErrorResponse
// @Router /artworks [get]
func (h *ArtworkHandler) GetAllArtworksHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	artworks, err := h.artworkService.GetAllArtworks()
	if err != nil {
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(artworks)
}

// GetAllArtworks godoc
// @Summary Get artworks by types
// @Description Retrieve all artworks, optionally filtered by types and/or search term
// @Tags Artworks
// @Produce json
// @Security BearerAuth
// @Param types query []string false "Filter by artwork types (comma-separated or repeated). Example: types=art,history or types=art&types=history" collectionFormat(multi)
// @Param name query string false "Partial name match"
// @Success 200 {array} Artwork
// @Failure 500 {object} ErrorResponse
// @Router /artworks/search [get]
func (h *ArtworkHandler) GetArtworksWithSearchHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	// Parse query parameter `types`. Support both repeated params (?types=a&types=b)
	// and comma-separated values (?types=a,b,c).
	var types []string
	queryValues := r.URL.Query()["types"]
	for _, v := range queryValues {
		if v == "" {
			continue
		}
		parts := splitCommaSeparated(v)
		for _, p := range parts {
			if p != "" {
				types = append(types, p)
			}
		}
	}

	// Read optional name query parameter for partial name matching
	name := r.URL.Query().Get("name")

	// Read optional museum query parameter for museum matching
	museum := r.URL.Query().Get("museum")

	// Call service with types and name
	artworks, err := h.artworkService.GetArtworksWithSearch(types, name, museum)
	if err != nil {
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(artworks)
}

// GetArtwork godoc
// @Summary Get a artwork by ID
// @Description Get a single artwork by its ObjectID
// @Tags Artworks
// @Produce json
// @Security BearerAuth
// @Param id path string true "Artwork ID" Format(ObjectID)
// @Success 200 {object} Artwork
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /artworks/{id} [get]
func (h *ArtworkHandler) GetArtworkHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid artwork ID format", http.StatusBadRequest)
		return
	}

	artwork, err := h.artworkService.GetArtwork(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Artwork not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(artwork)
}

// UpdateArtwork godoc
// @Summary Update a artwork
// @Description Update an existing artwork's information
// @Tags Artworks
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param id path string true "Artwork ID" Format(ObjectID)
// @Param artwork body Artwork true "Updated artwork object"
// @Success 200 {object} Artwork
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /artworks/{id} [put]
func (h *ArtworkHandler) UpdateArtworkHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid artwork ID format", http.StatusBadRequest)
		return
	}

	var artworkUpdate Artwork
	if err := json.NewDecoder(r.Body).Decode(&artworkUpdate); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	updatedArtwork, err := h.artworkService.UpdateArtwork(id, &artworkUpdate)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Artwork not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		}
		return
	}

	json.NewEncoder(w).Encode(updatedArtwork)
}

// DeleteArtwork godoc
// @Summary Delete a artwork
// @Description Delete a artwork by their ID
// @Tags Artworks
// @Produce json
// @Security BearerAuth
// @Param id path string true "Artwork ID" Format(ObjectID)
// @Success 200 {object} SuccessResponse
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /artworks/{id} [delete]
func (h *ArtworkHandler) DeleteArtworkHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid artwork ID format", http.StatusBadRequest)
		return
	}

	err = h.artworkService.DeleteArtwork(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Artwork not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(map[string]string{"message": "Artwork deleted successfully"})
}
