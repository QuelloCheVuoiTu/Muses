package main

import "os"

type Config struct {
	MongoHost string
	MongoPort string
	DBName    string
	MongoURI  string
	CollName  string
	Port      string
}

func LoadConfig() *Config {
	// Read env vars into locals so we can build MongoURI
	mongoHost := getEnv("MONGO_HOST", "localhost")
	mongoPort := getEnv("MONGO_PORT", "27017")
	dbName := getEnv("DB_NAME", "opere_db")

	return &Config{
		MongoHost: mongoHost,
		MongoPort: mongoPort,
		DBName:    dbName,
		MongoURI:  "mongodb://" + mongoHost + ":" + mongoPort + "/" + dbName,
		CollName:  getEnv("COLL_NAME", "opere"),
		Port:      getEnv("PORT", "4000"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
