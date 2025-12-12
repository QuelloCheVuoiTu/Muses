package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/readpref"
)

type MuseumHandler struct {
	museumService *MuseumService
}

func NewMuseumHandler(museumService *MuseumService) *MuseumHandler {
	return &MuseumHandler{museumService: museumService}
}

// Write a JSON error response
func (h *MuseumHandler) writeErrorResponse(w http.ResponseWriter, message string, statusCode int) {
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
// @Success 200 {string} string "Museum system is online"
// @Router /health [get]
func (h *MuseumHandler) HealthCheckHandler(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Museum system is online\n"))
}

// ReadyCheck godoc
// @Summary Service readiness probe
// @Description Deeper readiness check that verifies the service can reach required dependencies (e.g. MongoDB). Returns 200 when ready, 503 when not.
// @Tags Health
// @Produce plain
// @Success 200 {string} string "Museum system is ready"
// @Failure 503 {string} string "Service Unavailable"
// @Router /ready [get]
func (h *MuseumHandler) ReadyCheckHandler(w http.ResponseWriter, r *http.Request) {
	// Quick nil checks
	if h == nil || h.museumService == nil || h.museumService.client == nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: service not initialized\n"))
		return
	}

	// Ping MongoDB with a short timeout
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := h.museumService.client.Ping(ctx, readpref.Primary()); err != nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: cannot reach mongodb\n"))
		return
	}

	w.Header().Set("Content-Type", "text/plain")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Museum system is ready\n"))
}

// CreateMuseum godoc
// @Summary Create a new museum
// @Description Create a new museum entity
// @Tags Museums
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param museum body Museum true "Museum object"
// @Success 201 {object} Museum
// @Failure 400 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /museums [post]
func (h *MuseumHandler) CreateMuseumHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	var museum Museum
	if err := json.NewDecoder(r.Body).Decode(&museum); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	result, err := h.museumService.CreateMuseum(&museum)
	if err != nil {
		h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		return
	}

	// Link the user to the authentication service
	if LoadConfig().AuthSvcUri != "" {
		// Retrieve the InsertedID from the result and store it in a variable
		insertedID := result.InsertedID.(primitive.ObjectID)

		// Retrieve the userid header
		headerUserID := r.Header.Get("userid")

		err = LinkUserToAuthService(headerUserID, insertedID)
		if err != nil {
			// Rollback user creation in case of failure
			h.museumService.DeleteMuseum(insertedID)

			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
			return
		}
	}

	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(result)
}

// GetAllMuseums godoc
// @Summary Get all museums
// @Description Retrieve all museums from the database
// @Tags Museums
// @Produce json
// @Security BearerAuth
// @Success 200 {array} Museum
// @Failure 500 {object} ErrorResponse
// @Router /museums [get]
func (h *MuseumHandler) GetAllMuseumsHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	museums, err := h.museumService.GetAllMuseums()
	if err != nil {
		log.Printf("/ method returned an error: %s", err.Error())
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(museums)
}

// GetAllMuseums godoc
// @Summary Get museums by types and/or location
// @Description Retrieve all museums, optionally filtered by types, search term or by a bounding box (min/max latitude and longitude)
// @Tags Museums
// @Produce json
// @Security BearerAuth
// @Param types query []string false "Filter by museum types (comma-separated or repeated). Example: types=art,history or types=art&types=history" collectionFormat(multi)
// @Param name query string false "Partial name match"
// @Param minLat query number false "Minimum latitude of bounding box (inclusive)"
// @Param maxLat query number false "Maximum latitude of bounding box (inclusive)"
// @Param minLon query number false "Minimum longitude of bounding box (inclusive)"
// @Param maxLon query number false "Maximum longitude of bounding box (inclusive)"
// @Success 200 {array} Museum
// @Failure 500 {object} ErrorResponse
// @Router /museums/search [get]
func (h *MuseumHandler) GetMuseumsWithSearchHandler(w http.ResponseWriter, r *http.Request) {
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

	// Optional bounding box parameters
	var minLatPtr, maxLatPtr, minLonPtr, maxLonPtr *float64
	if v := r.URL.Query().Get("minLat"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err != nil {
			h.writeErrorResponse(w, "Invalid minLat value", http.StatusBadRequest)
			return
		}
		minLatPtr = &f
	}
	if v := r.URL.Query().Get("maxLat"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err != nil {
			h.writeErrorResponse(w, "Invalid maxLat value", http.StatusBadRequest)
			return
		}
		maxLatPtr = &f
	}
	if v := r.URL.Query().Get("minLon"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err != nil {
			h.writeErrorResponse(w, "Invalid minLon value", http.StatusBadRequest)
			return
		}
		minLonPtr = &f
	}
	if v := r.URL.Query().Get("maxLon"); v != "" {
		f, err := strconv.ParseFloat(v, 64)
		if err != nil {
			h.writeErrorResponse(w, "Invalid maxLon value", http.StatusBadRequest)
			return
		}
		maxLonPtr = &f
	}

	// Call service with types, name and optional bounding box
	museums, err := h.museumService.GetMuseumsWithSearch(types, name, minLatPtr, maxLatPtr, minLonPtr, maxLonPtr)
	if err != nil {
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(museums)
}

// GetMuseum godoc
// @Summary Get a museum by ID
// @Description Get a single museum by its ObjectID
// @Tags Museums
// @Produce json
// @Security BearerAuth
// @Param id path string true "Museum ID" Format(ObjectID)
// @Success 200 {object} Museum
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /museums/{id} [get]
func (h *MuseumHandler) GetMuseumHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid museum ID format", http.StatusBadRequest)
		return
	}

	museum, err := h.museumService.GetMuseum(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Museum not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(museum)
}

// UpdateMuseum godoc
// @Summary Update a museum
// @Description Update an existing museum's information
// @Tags Museums
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param id path string true "Museum ID" Format(ObjectID)
// @Param museum body Museum true "Updated museum object"
// @Success 200 {object} Museum
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /museums/{id} [put]
func (h *MuseumHandler) UpdateMuseumHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid museum ID format", http.StatusBadRequest)
		return
	}

	var museumUpdate Museum
	if err := json.NewDecoder(r.Body).Decode(&museumUpdate); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	updatedMuseum, err := h.museumService.UpdateMuseum(id, &museumUpdate)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Museum not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		}
		return
	}

	json.NewEncoder(w).Encode(updatedMuseum)
}

// DeleteMuseum godoc
// @Summary Delete a museum
// @Description Delete a museum by their ID
// @Tags Museums
// @Produce json
// @Security BearerAuth
// @Param id path string true "Museum ID" Format(ObjectID)
// @Success 200 {object} SuccessResponse
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /museums/{id} [delete]
func (h *MuseumHandler) DeleteMuseumHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	headerUserID := r.Header.Get("userid")

	// If present, delete the user from the authentication service
	if LoadConfig().AuthSvcDeleteUri != "" {
		err := DeleteUserFromAuthService(headerUserID)
		if err != nil {
			h.writeErrorResponse(w, "Could not delete user from auth service", http.StatusBadRequest)
			return
		}
	}

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid museum ID format", http.StatusBadRequest)
		return
	}

	err = h.museumService.DeleteMuseum(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "Museum not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	// TODO: Eventually, delete or disassociate artworks linked to this museum

	json.NewEncoder(w).Encode(map[string]string{"message": "Museum deleted successfully"})
}
