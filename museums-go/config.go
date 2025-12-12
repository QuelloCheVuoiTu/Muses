package main

import "os"

type Config struct {
	MongoHost        string
	MongoPort        string
	DBName           string
	MongoURI         string
	CollName         string
	Port             string
	AuthSvcUri       string
	AuthSvcDeleteUri string
}

func LoadConfig() *Config {
	// Read env vars into locals so we can build MongoURI
	mongoHost := getEnv("MONGO_HOST", "localhost")
	mongoPort := getEnv("MONGO_PORT", "27017")
	dbName := getEnv("DB_NAME", "museums_db")
	authSvcName := getEnv("AUTH_SVC_NAME", "")

	// Set AuthSvcUri only if authSvcName is provided
	authSvcUri := ""
	authSvcDeleteUri := ""
	if authSvcName != "" {
		authSvcUri = "http://" + authSvcName + "/linkentity"
		authSvcDeleteUri = "http://" + authSvcName + "/delete/"
	}

	return &Config{
		MongoHost:        mongoHost,
		MongoPort:        mongoPort,
		DBName:           dbName,
		MongoURI:         "mongodb://" + mongoHost + ":" + mongoPort + "/" + dbName,
		CollName:         getEnv("COLL_NAME", "museums"),
		Port:             getEnv("PORT", "8000"),
		AuthSvcUri:       authSvcUri,
		AuthSvcDeleteUri: authSvcDeleteUri,
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
