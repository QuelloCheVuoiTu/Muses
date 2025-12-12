package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"net/http"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

func LinkUserToAuthService(user_id string, external_id primitive.ObjectID) error {
	// Load config to access user role and auth service URI
	config := LoadConfig()

	// Prepare the JSON payload.
	payload := map[string]string{
		"user_id":     user_id,
		"external_id": external_id.Hex(),
	}

	// Marshal the data into JSON.
	jsonPayload, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	// Create a new POST request.
	req, err := http.NewRequest("POST", config.AuthSvcUri, bytes.NewBuffer(jsonPayload))
	if err != nil {
		return err
	}

	// Set the Content-Type header.
	req.Header.Set("Content-Type", "application/json")

	// Create an HTTP client.
	client := &http.Client{}

	// Send the request.
	resp, err := client.Do(req)
	if err != nil {
		return err
	}

	// Always close the response body.
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return errors.New("failed to link user with auth service")
	}

	return nil
}

func DeleteUserFromAuthService(auth_id string) error {
	// Load config to access auth service URI
	config := LoadConfig()

	// Create a new DELETE request.
	req, err := http.NewRequest("DELETE", config.AuthSvcDeleteUri+auth_id, nil)
	if err != nil {
		return err
	}

	// Create an HTTP client.
	client := &http.Client{}

	// Send the request.
	resp, err := client.Do(req)
	if err != nil {
		return err
	}

	// Always close the response body.
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return errors.New("failed to delete user from auth service")
	}

	return nil
}
