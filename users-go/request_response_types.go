package main

import "time"

// CreateUserRequest represents the request body for creating a user.
// swagger:model CreateUserRequest
type CreateUserRequest struct {
	// The fist name of the user (required)
	Firstname string `json:"firstname,omitempty" example:"john"`

	// The last name of the user (required)
	Lastname string `json:"lastname,omitempty" example:"doe"`

	// Username is the user's display name (required)
	Username string `json:"username" example:"john_doe"`

	// Email of the user (required)
	Email string `json:"email" example:"john@example.com"`

	// Birthday is the user's date of birth (required)
	Birthday *time.Time `json:"birthday,omitempty" example:"1990-01-01T00:00:00Z"`

	// Country is the user's country of origin (required)
	Country string `json:"country,omitempty" example:"USA"`

	// AvatarURL points to a profile picture (optional)
	AvatarURL string `json:"avatar_url,omitempty" example:"http://example.com/avatar.jpg"`
}

// UpdateUserRequest represents the request body for updating a user.
// swagger:model UpdateUserRequest
type UpdateUserRequest struct {
	// The fist name of the user (required)
	Firstname string `json:"firstname,omitempty" example:"john"`

	// The last name of the user (required)
	Lastname string `json:"lastname,omitempty" example:"doe"`

	// Username is the user's display name (required)
	Username string `json:"username" example:"john_doe_updated"`

	// Email of the user (required)
	Email string `json:"email" example:"john@example.com"`

	// Birthday is the user's date of birth (required)
	Birthday *time.Time `json:"birthday,omitempty" example:"1990-01-01T00:00:00Z"`

	// Country is the user's country of origin (required)
	Country string `json:"country,omitempty" example:"USA"`

	// AvatarURL points to a profile picture (optional)
	AvatarURL string `json:"avatar_url,omitempty" example:"http://example.com/avatar.jpg"`
}

// PreferencesUpdateRequest represents the PATCH request body for updating preferences.
// swagger:model PreferencesUpdateRequest
type PreferencesUpdateRequest struct {
	// array of preference tags
	// example: ["art","history"]
	Preferences []string `json:"preferences" example:"art,history"`
}

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
