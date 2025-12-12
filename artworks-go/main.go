package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/rs/cors"

	// Swagger
	_ "artworks/docs"

	// Imports for logger and middleware
	"artworks/logger"
	"artworks/middleware"

	stdlog "log"

	"github.com/sirupsen/logrus"
)

// @title MuSES - Artwork Management API
// @version 1.0
// @description Simple REST API in order to properly manage an artwork in the system.

// @license.name AGPL-3.0
// @license.url https://www.gnu.org/licenses/agpl-3.0.html

// @host it.unisannio.muses
// @BasePath /artworks
// @schemes https

// Security definitions
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Enter 'Bearer <token>'

func main() {
	// Initialize logger (uses LOG_FILE and LOG_LEVEL env vars if set)
	logFile := getEnv("LOG_FILE", "./artworks.log")
	logLevel := getEnv("LOG_LEVEL", "info")
	if logLevel == "" {
		logLevel = "info"
	}
	appLogger := logger.Init(logFile, logLevel)
	appLogger.WithField("level", appLogger.Level.String()).Info("Logger configured")

	// Redirect standard library logger output into logrus so other packages' logs
	// go through the same writer/rotator and formatting.
	stdlog.SetOutput(appLogger.Writer())
	stdlog.SetFlags(0)

	// Startup message as debug-level (logger already set to debug if LOG_LEVEL=debug)
	appLogger.Info("Starting the server...")

	// Load configuration from environment variables
	config := LoadConfig()

	// Initialize database connection
	db, err := InitDB(config.MongoURI)
	if err != nil {
		appLogger.WithField("error", err).Fatal("Failed to connect to database")
	}
	defer db.Disconnect(context.Background())

	// Initialize artwork service
	artworkService := NewArtworkService(db)

	// Initialize handlers
	artworkHandler := NewArtworkHandler(artworkService)

	// Setup routes
	router := SetupRoutes(artworkHandler)

	// Wrap router with logging middleware. The middleware returns an http.Handler,
	// so assign it to a separate variable instead of overwriting the *mux.Router.
	handler := middleware.LoggingMiddleware(appLogger)(router)

	// TODO: Eventually remove
	// Configure rs/cors
	c := cors.New(cors.Options{
		// For development you can allow all origins:
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Content-Type", "Authorization", "Accept"},
		AllowCredentials: false,
		// If you need credentials set AllowCredentials: true and specify exact AllowedOrigins.
	})

	// Create HTTP server
	server := &http.Server{
		Addr:         ":" + config.Port,
		Handler:      c.Handler(handler),
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Start server in a goroutine
	go func() {
		appLogger.WithFields(logrus.Fields{"port": config.Port}).Info("Server is running")
		appLogger.WithField("swagger", "http://localhost:"+config.Port+"/swagger/").Info("API docs")
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			appLogger.WithField("error", err).Fatal("Server failed to start")
		}
	}()

	// Wait for interrupt signal to gracefully shutdown the server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down server...")

	// Graceful shutdown with timeout
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		appLogger.WithField("error", err).Fatal("Server forced to shutdown")
	}
	appLogger.Info("Server exited")
}
