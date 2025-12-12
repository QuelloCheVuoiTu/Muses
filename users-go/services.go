package main

import (
	"context"
	"errors"
	"regexp"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

var ErrEmailOrUsernameAlreadyExists = errors.New("email or username already taken")

type UserService struct {
	client     *mongo.Client
	collection *mongo.Collection
}

func NewUserService(client *mongo.Client) *UserService {
	config := LoadConfig()
	return &UserService{
		client:     client,
		collection: client.Database(config.DBName).Collection(config.CollName),
	}
}

// POST - .../users
// CreateUser creates a new user entry in the database
func (s *UserService) CreateUser(user *User) (*mongo.InsertOneResult, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := user.Validate(); err != nil {
		return nil, err
	}

	// Check for existing email and username (case-insensitive) using the search logic
	existingUsers, err := s.GetUsersWithSearch(user.Email, user.Username)
	if err != nil {
		return nil, err
	}
	if len(existingUsers) > 0 {
		return nil, ErrEmailOrUsernameAlreadyExists
	}

	// Set timestamps
	now := time.Now().UTC()
	user.CreatedAt = now
	user.UpdatedAt = now

	// Insert the new user into the database
	mongo_response, err := s.collection.InsertOne(ctx, user)
	if err != nil {
		return nil, err
	}

	// Return the result of the insertion
	return mongo_response, nil
}

// GET - .../users
// GetAllUsers retrieves all user entries from the database
func (s *UserService) GetAllUsers() ([]User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	cursor, err := s.collection.Find(ctx, bson.M{})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var users []User
	for cursor.Next(ctx) {
		var user User
		if err := cursor.Decode(&user); err != nil {
			return nil, err
		}
		users = append(users, user)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return users, nil
}

// GET - .../users/search
// GetUsersWithSearch retrieves all user entries from the database and eventually filters them
// by email and/or username if provided. Both filters are exact matches but case-insensitive.
func (s *UserService) GetUsersWithSearch(email string, username string) ([]User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	var filter bson.M

	// Build exact, case-insensitive conditions for email and username.
	// If both are provided we use $or so we match either the email OR the username.
	var conditions []bson.M

	if email = strings.TrimSpace(email); email != "" {
		pattern := regexp.QuoteMeta(email)
		conditions = append(conditions, bson.M{"email": primitive.Regex{Pattern: "^" + pattern + "$", Options: "i"}})
	}

	if username = strings.TrimSpace(username); username != "" {
		pattern := regexp.QuoteMeta(username)
		conditions = append(conditions, bson.M{"username": primitive.Regex{Pattern: "^" + pattern + "$", Options: "i"}})
	}

	switch len(conditions) {
	case 0:
		// empty filter -> all documents
		filter = bson.M{}
	case 1:
		// single condition -> use it directly
		filter = conditions[0]
	default:
		// multiple conditions -> match any (OR)
		filter = bson.M{"$or": conditions}
	}

	cursor, err := s.collection.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var users []User
	for cursor.Next(ctx) {
		var user User
		if err := cursor.Decode(&user); err != nil {
			return nil, err
		}
		users = append(users, user)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return users, nil
}

// GET - .../users/{id}
// GetUser retrieves a single user entry by its ID
func (s *UserService) GetUser(id primitive.ObjectID) (*User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var user User
	err := s.collection.FindOne(ctx, bson.M{"_id": id}).Decode(&user)
	if err != nil {
		return nil, err
	}
	return &user, nil
}

// PUT - .../users/{id}
// UpdateUser updates an existing user entry by its ID
func (s *UserService) UpdateUser(id primitive.ObjectID, userUpdate *User) (*User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := userUpdate.Validate(); err != nil {
		return nil, err
	}

	// Check for existing email or username (case-insensitive), excluding the current user being updated
	existingUsers, err := s.GetUsersWithSearch(userUpdate.Email, userUpdate.Username)
	if err != nil {
		return nil, err
	}
	for _, u := range existingUsers {
		if u.ID != id {
			return nil, ErrEmailOrUsernameAlreadyExists
		}
	}

	// Build update document from the provided struct while omitting empty fields.
	// We marshal the struct to BSON and unmarshal into a bson.M so `omitempty` tags are respected.
	raw, err := bson.Marshal(userUpdate)
	if err != nil {
		return nil, err
	}
	var updateDoc bson.M
	if err := bson.Unmarshal(raw, &updateDoc); err != nil {
		return nil, err
	}

	// Ensure we don't overwrite created_at and always set updated_at
	updateDoc["updated_at"] = time.Now().UTC()

	update := bson.M{"$set": updateDoc}
	result, err := s.collection.UpdateOne(ctx, bson.M{"_id": id}, update)
	if err != nil {
		return nil, err
	}

	if result.MatchedCount == 0 {
		return nil, mongo.ErrNoDocuments
	}

	// Fetch and return the updated user entity
	return s.GetUser(id)
}

// DELETE - .../users/{id}
// DeleteUser removes a user entry from the database by its ID
func (s *UserService) DeleteUser(id primitive.ObjectID) error {
	// Set a timeout to avoid hanging operations
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Perform the deletion from the database
	result, err := s.collection.DeleteOne(ctx, bson.M{"_id": id})
	if err != nil {
		return err
	}

	// If no documents were deleted, return an error
	if result.DeletedCount == 0 {
		return mongo.ErrNoDocuments
	}

	// Successful deletion, no error to return
	return nil
}

// GET - .../users/{id}/preferences
// GetUserPreferences retrieves the preference tags for a specific user
func (s *UserService) GetUserPreferences(id primitive.ObjectID) ([]string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var user User
	err := s.collection.FindOne(ctx, bson.M{"_id": id}).Decode(&user)
	if err != nil {
		return nil, err
	}

	return user.Preferences, nil
}

// PATCH - .../users/{id}/preferences
// UpdateUserPreferences adds a set of preferences to the user's existing preferences
func (s *UserService) UpdateUserPreferences(id primitive.ObjectID, prefs []string) (*User, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Insert preferences into a set to ensure uniqueness
	prefSet := NewSetFromSlice(prefs)

	// Convert back to slice and replace the stored preferences
	uniquePrefs := SetToSlice(prefSet)

	// Update the user's preferences in the database
	update := bson.M{
		"$set": bson.M{
			"preferences": uniquePrefs,
			"updated_at":  time.Now().UTC(),
		},
	}

	// Perform the update operation
	result, err := s.collection.UpdateOne(ctx, bson.M{"_id": id}, update)
	if err != nil {
		return nil, err
	}
	if result.MatchedCount == 0 {
		return nil, mongo.ErrNoDocuments
	}

	return s.GetUser(id)
}
