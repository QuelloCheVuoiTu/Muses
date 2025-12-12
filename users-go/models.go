package main

import (
	"errors"
	"regexp"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// User represents a user entity
// swagger:model User
type User struct {
	// The unique identifier of the user
	ID primitive.ObjectID `json:"_id,omitempty" bson:"_id,omitempty"`

	// The fist name of the user (required)
	Firstname string `json:"firstname,omitempty" bson:"firstname,omitempty"`

	// The last name of the user (required)
	Lastname string `json:"lastname,omitempty" bson:"lastname,omitempty"`

	// Username is the user's display name (required)
	Username string `json:"username,omitempty" bson:"username,omitempty"`

	// Email of the user (required)
	Email string `json:"email,omitempty" bson:"email,omitempty"`

	// Birthday is the user's date of birth (required)
	Birthday *time.Time `json:"birthday,omitempty" bson:"birthday,omitempty"`

	// Country is the user's country of origin (required)
	Country string `json:"country,omitempty" bson:"country,omitempty"`

	// AvatarURL points to a profile picture (optional)
	AvatarURL string `json:"avatar_url,omitempty" bson:"avatar_url,omitempty"`

	// Preferences contains user preferences (optional)
	Preferences []string `json:"preferences,omitempty" bson:"preferences,omitempty"`

	// RangePreferences indicates the user's preference range (optional)
	RangePreferences float32 `json:"range_preferences,omitempty" bson:"range_preferences,omitempty"`

	// Timestamps
	CreatedAt time.Time `json:"created_at,omitempty" bson:"created_at,omitempty"`
	UpdatedAt time.Time `json:"updated_at,omitempty" bson:"updated_at,omitempty"`
}

// Validate validates the user data
func (u *User) Validate() error {
	// Required fields
	if strings.TrimSpace(u.Firstname) == "" {
		return errors.New("firstname is required")
	}
	if strings.TrimSpace(u.Lastname) == "" {
		return errors.New("lastname is required")
	}
	if strings.TrimSpace(u.Username) == "" {
		return errors.New("username is required")
	}
	if strings.TrimSpace(u.Email) == "" {
		return errors.New("email is required")
	}
	if u.Birthday == nil || u.Birthday.IsZero() {
		return errors.New("birthday is required")
	}
	if strings.TrimSpace(u.Country) == "" {
		return errors.New("country is required")
	}

	// Length constraints for names
	if len(u.Firstname) > 50 {
		return errors.New("firstname must be less than 50 characters")
	}
	if len(u.Lastname) > 50 {
		return errors.New("lastname must be less than 50 characters")
	}

	// Validate email
	if len(u.Email) > 256 {
		return errors.New("email must be less than 256 characters")
	}
	// simple email regex: local@domain.tld
	var emailRe = regexp.MustCompile(`^[^\s@]+@[^\s@]+\.[^\s@]+$`)
	if !emailRe.MatchString(u.Email) {
		return errors.New("email is not a valid address")
	}

	// Validate birthday is not in the future
	now := time.Now().UTC()
	if u.Birthday.After(now) {
		return errors.New("birthday cannot be in the future")
	}

	// Country length constraint
	if len(u.Country) > 100 {
		return errors.New("country must be less than 100 characters")
	}

	// AvatarURL length constraint (basic)
	if len(u.AvatarURL) > 2048 {
		return errors.New("avatar_url must be less than 2048 characters")
	}

	// RangePreferences constraint and default value
	if u.RangePreferences == 0 {
		u.RangePreferences = 1
	}
	if u.RangePreferences < 0 || u.RangePreferences > 15 {
		return errors.New("range_preferences must be between 0 and 15")
	}

	return nil
}
