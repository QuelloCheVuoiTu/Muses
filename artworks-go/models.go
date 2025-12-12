package main

import (
	"errors"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// SuccessResponse is used in delete responses
// swagger:model SuccessResponse
type SuccessResponse struct {
	// Message contains a success message
	Message string `json:"message"`
}

// ErrorResponse represents an error response
// swagger:model ErrorResponse
type ErrorResponse struct {
	// Error contains the error message
	Error string `json:"error"`
}

// Artwork represents an artwork entity
// swagger:model Artwork
type Artwork struct {
	// The unique identifier of the artwork
	ID primitive.ObjectID `json:"_id,omitempty" bson:"_id,omitempty"`

	// Name of the artwork
	Name string `json:"name,omitempty" bson:"name,omitempty"`

	// Short description of the artwork
	Description string `json:"description,omitempty" bson:"description,omitempty"`

	// URL to the museum image
	ImageURL string `json:"imageurl,omitempty" bson:"imageurl,omitempty"`

	// Parent is an optional external reference to another document (ObjectID)
	Museum primitive.ObjectID `json:"museum,omitempty" bson:"museum,omitempty"`

	// IsExposed indicates if the artwork is currently exposed
	IsExposed *bool `json:"is_exposed,omitempty" bson:"is_exposed,omitempty"`

	// Types/categories of the museum
	Types []string `json:"types,omitempty" bson:"types,omitempty"`

	// Creation timestamp
	CreatedAt time.Time `json:"created_at,omitempty" bson:"created_at,omitempty"`

	// Last update timestamp
	UpdatedAt time.Time `json:"updated_at,omitempty" bson:"updated_at,omitempty"`
}

// Validate checks if the Artwork struct has valid data
func (a *Artwork) Validate() error {
	if strings.TrimSpace(a.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(a.Description) == "" {
		return errors.New("description is required")
	}
	if strings.TrimSpace(a.ImageURL) == "" {
		return errors.New("image URL is required")
	}
	if a.IsExposed == nil {
		return errors.New("is_exposed is required")
	}
	// TODO: Should we validate if the museum exists?
	if a.Museum.IsZero() {
		return errors.New("museum is required")
	}
	if len(a.Types) == 0 {
		return errors.New("types are required")
	}
	return nil
}
