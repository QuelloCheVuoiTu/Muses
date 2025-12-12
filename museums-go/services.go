package main

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type MuseumService struct {
	client     *mongo.Client
	collection *mongo.Collection
}

func NewMuseumService(client *mongo.Client) *MuseumService {
	config := LoadConfig()
	return &MuseumService{
		client:     client,
		collection: client.Database(config.DBName).Collection(config.CollName),
	}
}

// POST - .../museums
// CreateMuseum creates a new museum entry in the database
func (s *MuseumService) CreateMuseum(museum *Museum) (*mongo.InsertOneResult, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := museum.Validate(); err != nil {
		return nil, err
	}

	// Set timestamps
	now := time.Now().UTC()
	museum.CreatedAt = now
	museum.UpdatedAt = now

	// Insert the new user into the database
	mongo_response, err := s.collection.InsertOne(ctx, museum)
	if err != nil {
		return nil, err
	}

	// Return the result of the insertion
	return mongo_response, nil
}

// GET - .../museums
// GetAllMuseums retrieves all museum entries from the database
func (s *MuseumService) GetAllMuseums() ([]Museum, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	cursor, err := s.collection.Find(ctx, bson.M{})
	if err != nil {
		return nil, errors.New("error while connecting to db")
	}
	defer cursor.Close(ctx)

	var museums []Museum
	for cursor.Next(ctx) {
		var museum Museum
		if err := cursor.Decode(&museum); err != nil {
			return nil, fmt.Errorf("error while decoding docs -> %s", err.Error())
		}
		museums = append(museums, museum)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return museums, nil
}

// GET - .../museums/search
// GetAllMuseums retrieves all museum entries from the database and eventually filters by types or search term
// The latitude/longitude pointers are optional — pass nil to ignore a bound.
func (s *MuseumService) GetMuseumsWithSearch(types []string, name string, minLat, maxLat, minLon, maxLon *float64) ([]Museum, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	filter := bson.M{}

	// types filter (partial, case-insensitive)
	if len(types) > 0 {
		regexes := make([]interface{}, 0, len(types))
		for _, t := range types {
			pattern := regexp.QuoteMeta(t)
			regexes = append(regexes, primitive.Regex{Pattern: pattern, Options: "i"})
		}
		filter["types"] = bson.M{"$in": regexes}
	}

	// name filter (partial, case-insensitive)
	if name = strings.TrimSpace(name); name != "" {
		pattern := regexp.QuoteMeta(name)
		filter["name"] = primitive.Regex{Pattern: pattern, Options: "i"}
	}

	// location bounding box filters (inclusive). We build an AND for the ranges using nested bson.M
	// Apply latitude range if either minLat/maxLat is provided
	locFilters := bson.M{}
	if minLat != nil {
		locFilters["location.latitude"] = bson.M{"$gte": *minLat}
	}
	if maxLat != nil {
		// If we've already set a map for location.latitude, merge the operator
		if existing, ok := locFilters["location.latitude"].(bson.M); ok {
			existing["$lte"] = *maxLat
			locFilters["location.latitude"] = existing
		} else {
			locFilters["location.latitude"] = bson.M{"$lte": *maxLat}
		}
	}
	// Apply longitude range if either minLon/maxLon is provided
	if minLon != nil {
		locFilters["location.longitude"] = bson.M{"$gte": *minLon}
	}
	if maxLon != nil {
		if existing, ok := locFilters["location.longitude"].(bson.M); ok {
			existing["$lte"] = *maxLon
			locFilters["location.longitude"] = existing
		} else {
			locFilters["location.longitude"] = bson.M{"$lte": *maxLon}
		}
	}

	// If we have any location constraints, merge them into the main filter
	if len(locFilters) > 0 {
		for k, v := range locFilters {
			filter[k] = v
		}
	}

	cursor, err := s.collection.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var museums []Museum
	for cursor.Next(ctx) {
		var museum Museum
		if err := cursor.Decode(&museum); err != nil {
			return nil, err
		}
		museums = append(museums, museum)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return museums, nil
}

// GET - .../museums/{id}
// GetMuseum retrieves a single museum entry by its ID
func (s *MuseumService) GetMuseum(id primitive.ObjectID) (*Museum, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var museum Museum
	err := s.collection.FindOne(ctx, bson.M{"_id": id}).Decode(&museum)
	if err != nil {
		return nil, err
	}
	return &museum, nil
}

// PUT - .../museums/{id}
// UpdateMuseum updates an existing museum entry by its ID
func (s *MuseumService) UpdateMuseum(id primitive.ObjectID, museumUpdate *Museum) (*Museum, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := museumUpdate.Validate(); err != nil {
		return nil, err
	}

	// Build update document from the provided struct while omitting empty fields.
	// We marshal the struct to BSON and unmarshal into a bson.M so `omitempty` tags are respected.
	raw, err := bson.Marshal(museumUpdate)
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

	// Fetch and return the updated museum entity
	return s.GetMuseum(id)
}

// DELETE - .../museums/{id}
// DeleteMuseum removes a museum entry from the database by its ID
func (s *MuseumService) DeleteMuseum(id primitive.ObjectID) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	result, err := s.collection.DeleteOne(ctx, bson.M{"_id": id})
	if err != nil {
		return err
	}

	if result.DeletedCount == 0 {
		return mongo.ErrNoDocuments
	}

	return nil
}
