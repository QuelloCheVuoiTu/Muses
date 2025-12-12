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

// Coordinates represents geographical coordinates
// swagger:model Coordinates
type Coordinates struct {
	// Longitude in decimal degrees
	Longitude float64 `json:"longitude,omitempty" bson:"longitude,omitempty"`
	// Latitude in decimal degrees
	Latitude float64 `json:"latitude,omitempty" bson:"latitude,omitempty"`
}

// Museum represents a museum entity
// swagger:model Museum
type Museum struct {
	// The unique identifier of the museum
	ID primitive.ObjectID `json:"_id,omitempty" bson:"_id,omitempty"`

	// Name of the museum
	Name string `json:"name,omitempty" bson:"name,omitempty"`

	// Short description of the museum
	Description string `json:"description,omitempty" bson:"description,omitempty"`

	// Location coordinates of the museum
	Location Coordinates `json:"location" bson:"location,omitempty"`

	// Opening hours
	Hours string `json:"hours,omitempty" bson:"hours,omitempty"`

	// Entry price information
	Price string `json:"price,omitempty" bson:"price,omitempty"`

	// URL to the museum image
	ImageURL string `json:"imageurl,omitempty" bson:"imageurl,omitempty"`

	// Types/categories of the museum
	Types []string `json:"types,omitempty" bson:"types,omitempty"`

	// Parent is an optional external reference to another document (ObjectID)
	Parent primitive.ObjectID `json:"parent,omitempty" bson:"parent,omitempty"`

	// Creation timestamp
	CreatedAt time.Time `json:"created_at,omitempty" bson:"created_at,omitempty"`

	// Last update timestamp
	UpdatedAt time.Time `json:"updated_at,omitempty" bson:"updated_at,omitempty"`
}

// Validate checks if the Museum struct has valid data
func (m *Museum) Validate() error {
	if strings.TrimSpace(m.Name) == "" {
		return errors.New("name is required")
	}
	if strings.TrimSpace(m.Description) == "" {
		return errors.New("description is required")
	}
	if m.Location.Latitude == 0 && m.Location.Longitude == 0 {
		return errors.New("location is required")
	}
	if strings.TrimSpace(m.Hours) == "" {
		return errors.New("hours are required")
	}
	if strings.TrimSpace(m.Price) == "" {
		return errors.New("price is required")
	}
	if strings.TrimSpace(m.ImageURL) == "" {
		return errors.New("image URL is required")
	}
	if len(m.Types) == 0 {
		return errors.New("types are required")
	}
	return nil
}
