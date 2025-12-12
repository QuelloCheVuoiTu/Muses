package main

import (
	"context"
	"regexp"
	"strings"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type ArtworkService struct {
	client     *mongo.Client
	collection *mongo.Collection
}

func NewArtworkService(client *mongo.Client) *ArtworkService {
	config := LoadConfig()
	return &ArtworkService{
		client:     client,
		collection: client.Database(config.DBName).Collection(config.CollName),
	}
}

// POST - .../artworks
// CreateArtwork creates a new artwork entry in the database
func (s *ArtworkService) CreateArtwork(artwork *Artwork) (*mongo.InsertOneResult, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := artwork.Validate(); err != nil {
		return nil, err
	}

	// Set timestamps
	now := time.Now().UTC()
	artwork.CreatedAt = now
	artwork.UpdatedAt = now

	return s.collection.InsertOne(ctx, artwork)
}

// GET - .../artworks
// GetAllArtworks retrieves all artwork entries from the database
func (s *ArtworkService) GetAllArtworks() ([]Artwork, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	cursor, err := s.collection.Find(ctx, bson.M{})
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var artworks []Artwork
	for cursor.Next(ctx) {
		var artwork Artwork
		if err := cursor.Decode(&artwork); err != nil {
			return nil, err
		}
		artworks = append(artworks, artwork)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return artworks, nil
}

// GET - .../artworks/search
// GetArtworksWithSearch retrieves all artwork entries that match the search criteria
// based on types, name and museum (partial, case-insensitive). If no criteria are provided,
// it returns all artworks.
func (s *ArtworkService) GetArtworksWithSearch(types []string, name string, museum string) ([]Artwork, error) {
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

	// museum filter (exact match on ObjectID). This is an external key so matching must be exact.
	if museum = strings.TrimSpace(museum); museum != "" {
		objID, err := primitive.ObjectIDFromHex(museum)
		if err != nil {
			return nil, err
		}
		// Artwork.Museum uses the bson tag `parent`, so filter on that field.
		filter["museum"] = objID
	}

	cursor, err := s.collection.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var artworks []Artwork
	for cursor.Next(ctx) {
		var artwork Artwork
		if err := cursor.Decode(&artwork); err != nil {
			return nil, err
		}
		artworks = append(artworks, artwork)
	}

	if err := cursor.Err(); err != nil {
		return nil, err
	}

	return artworks, nil
}

// GET - .../artworks/{id}
// GetArtwork retrieves a single artwork entry by its ID
func (s *ArtworkService) GetArtwork(id primitive.ObjectID) (*Artwork, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var artwork Artwork
	err := s.collection.FindOne(ctx, bson.M{"_id": id}).Decode(&artwork)
	if err != nil {
		return nil, err
	}
	return &artwork, nil
}

// PUT - .../artworks/{id}
// UpdateArtwork updates an existing artwork entry by its ID
func (s *ArtworkService) UpdateArtwork(id primitive.ObjectID, artworkUpdate *Artwork) (*Artwork, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := artworkUpdate.Validate(); err != nil {
		return nil, err
	}

	// Build update document from the provided struct while omitting empty fields.
	// We marshal the struct to BSON and unmarshal into a bson.M so `omitempty` tags are respected.
	raw, err := bson.Marshal(artworkUpdate)
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

	// Fetch and return the updated artwork entity
	return s.GetArtwork(id)
}

// DELETE - .../artworks/{id}
// DeleteArtwork removes a artwork entry from the database by its ID
func (s *ArtworkService) DeleteArtwork(id primitive.ObjectID) error {
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
