# Multi-stage Dockerfile for the Go REST API
# Builder stage
FROM golang:1.24.4 AS builder

WORKDIR /src

# Use Go modules; copy only go.mod and go.sum first to leverage cache
COPY go.mod go.sum ./
RUN go mod download

# Copy source and build static binary
COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -trimpath -ldflags "-s -w" -o /app/server .

# Final stage: small runtime image with ca-certificates
FROM alpine:3.19
RUN apk add --no-cache ca-certificates

WORKDIR /app

# Copy the statically built binary from builder
COPY --from=builder /app/server ./server

# Expose the port the app runs on
EXPOSE 2000

ENTRYPOINT ["/app/server"]
