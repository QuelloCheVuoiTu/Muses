package main

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/gorilla/mux"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/readpref"
)

type UserHandler struct {
	userService *UserService
}

func NewUserHandler(userService *UserService) *UserHandler {
	return &UserHandler{userService: userService}
}

// Write a JSON error response
func (h *UserHandler) writeErrorResponse(w http.ResponseWriter, message string, statusCode int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(ErrorResponse{Error: message})
}

// HealthCheck godoc
// @Summary Service liveness probe
// @Description Lightweight check to confirm the HTTP server and application process are running.
// @Tags Health
// @Produce plain
// @Success 200 {string} string "User system is online"
// @Router /health [get]
func (h *UserHandler) HealthCheckHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("User system is online\n"))
}

// ReadyCheck godoc
// @Summary Service readiness probe
// @Description Deeper readiness check that verifies the service can reach required dependencies (e.g. MongoDB). Returns 200 when ready, 503 when not.
// @Tags Health
// @Produce plain
// @Success 200 {string} string "User system is ready"
// @Failure 503 {string} string "Service Unavailable"
// @Router /ready [get]
func (h *UserHandler) ReadyCheckHandler(w http.ResponseWriter, r *http.Request) {
	// Quick nil checks
	if h == nil || h.userService == nil || h.userService.client == nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: service not initialized\n"))
		return
	}

	// Ping MongoDB with a short timeout
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	if err := h.userService.client.Ping(ctx, readpref.Primary()); err != nil {
		w.Header().Set("Content-Type", "text/plain")
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("unready: cannot reach mongodb\n"))
		return
	}

	w.Header().Set("Content-Type", "text/plain")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("User system is ready\n"))
}

// CreateUser godoc
// @Summary Create a new user
// @Description Create a new user entity
// @Tags Users
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param user body CreateUserRequest true "User object"
// @Success 201 {object} User
// @Failure 400 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users [post]
func (h *UserHandler) CreateUserHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	var user User
	if err := json.NewDecoder(r.Body).Decode(&user); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	result, err := h.userService.CreateUser(&user)
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
			h.userService.DeleteUser(insertedID)

			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
			return
		}
	}

	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(result)
}

// GetAllUsers godoc
// @Summary Get all users
// @Description Retrieve all users from the database
// @Tags Users
// @Produce json
// @Security BearerAuth
// @Success 200 {array} User
// @Failure 500 {object} ErrorResponse
// @Router /users [get]
func (h *UserHandler) GetAllUsersHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	users, err := h.userService.GetAllUsers()
	if err != nil {
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(users)
}

// GetUsersWithSearch godoc
// @Summary Search users
// @Description Retrieve users, optionally filtered by exact email and/or username. The filter performs an exact (whole-string) match and is case-insensitive.
// @Tags Users
// @Produce json
// @Security BearerAuth
// @Param email query string false "Exact email match (case-insensitive)"
// @Param username query string false "Exact username match (case-insensitive)"
// @Success 200 {array} User
// @Failure 500 {object} ErrorResponse
// @Router /users/search [get]
func (h *UserHandler) GetUsersWithSearchHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	// Read optional email query parameter for exact email matching
	email := r.URL.Query().Get("email")

	// Read optional username query parameter for exact username matching
	username := r.URL.Query().Get("username")

	// Call service with email
	users, err := h.userService.GetUsersWithSearch(email, username)
	if err != nil {
		h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	json.NewEncoder(w).Encode(users)
}

// GetUser godoc
// @Summary Get a user by ID
// @Description Get a single user by its ObjectID
// @Tags Users
// @Produce json
// @Security BearerAuth
// @Param id path string true "User ID" Format(ObjectID)
// @Success 200 {object} User
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users/{id} [get]
func (h *UserHandler) GetUserHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid user ID format", http.StatusBadRequest)
		return
	}

	user, err := h.userService.GetUser(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "User not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(user)
}

// UpdateUser godoc
// @Summary Update a user
// @Description Update an existing user's information
// @Tags Users
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param id path string true "User ID" Format(ObjectID)
// @Param user body UpdateUserRequest true "Updated user object"
// @Success 200 {object} User
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users/{id} [put]
func (h *UserHandler) UpdateUserHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid user ID format", http.StatusBadRequest)
		return
	}

	var userUpdate User
	if err := json.NewDecoder(r.Body).Decode(&userUpdate); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	updatedUser, err := h.userService.UpdateUser(id, &userUpdate)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "User not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		}
		return
	}

	json.NewEncoder(w).Encode(updatedUser)
}

// DeleteUser godoc
// @Summary Delete a user
// @Description Delete a user by their ID
// @Tags Users
// @Produce json
// @Security BearerAuth
// @Param id path string true "User ID" Format(ObjectID)
// @Success 200 {object} SuccessResponse
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users/{id} [delete]
func (h *UserHandler) DeleteUserHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	// TODO: Should we remove user from auth service AFTER deleting from DB?
	headerUserID := r.Header.Get("userid")

	// If present, delete the user from the authentication service
	if LoadConfig().AuthSvcDeleteUri != "" {
		err := DeleteUserFromAuthService(headerUserID)
		if err != nil {
			h.writeErrorResponse(w, "Could not delete user from auth service", http.StatusBadRequest)
			return
		}
	}

	// Parse the user ID from the URL
	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid user ID format", http.StatusBadRequest)
		return
	}

	// Call the service to delete the user
	err = h.userService.DeleteUser(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "User not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(map[string]string{"message": "User deleted successfully"})
}

// GetUserPreferences godoc
// @Summary Get a user's preferences
// @Description Retrieve the preference tags for a specific user.
// @Tags Users
// @Produce json
// @Security BearerAuth
// @Param id path string true "User ID" Format(ObjectID)
// @Success 200 {object} PreferencesUpdateRequest
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users/{id}/preferences [get]
func (h *UserHandler) GetUserPreferencesHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid user ID format", http.StatusBadRequest)
		return
	}

	preferences, err := h.userService.GetUserPreferences(id)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "User not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, "Internal server error", http.StatusInternalServerError)
		}
		return
	}

	json.NewEncoder(w).Encode(map[string][]string{"preferences": preferences})
}

// UpdateUserPreferences godoc
// @Summary Update a user's preferences
// @Description Update the preference tags for a specific user.
// @Tags Users
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param id path string true "User ID" Format(ObjectID)
// @Param preferences body PreferencesUpdateRequest true "Preferences object"
// @Success 200 {object} User
// @Failure 400 {object} ErrorResponse
// @Failure 404 {object} ErrorResponse
// @Failure 500 {object} ErrorResponse
// @Router /users/{id}/preferences [patch]
func (h *UserHandler) UpdateUserPreferencesHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	params := mux.Vars(r)
	id, err := primitive.ObjectIDFromHex(params["id"])
	if err != nil {
		h.writeErrorResponse(w, "Invalid user ID format", http.StatusBadRequest)
		return
	}

	var req struct {
		Preferences []string `json:"preferences"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeErrorResponse(w, "Invalid JSON format", http.StatusBadRequest)
		return
	}

	updatedUser, err := h.userService.UpdateUserPreferences(id, req.Preferences)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			h.writeErrorResponse(w, "User not found", http.StatusNotFound)
		} else {
			h.writeErrorResponse(w, err.Error(), http.StatusBadRequest)
		}
		return
	}

	json.NewEncoder(w).Encode(updatedUser)
}
