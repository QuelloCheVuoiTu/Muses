# MuSES Admin Android Application

## Overview
This is the Android admin application for MuSES (Museum Smart Experience System). It provides a clean login interface and admin dashboard for museum administrators with QR code scanning capabilities for reward validation.

## Features
- **Clean Login Flow**: Secure authentication with token management
- **Token Persistence**: Auth tokens are saved and used automatically for API calls
- **Modern UI**: Material Design 3 with iOS-inspired styling using XML layouts
- **QR Code Scanning**: Camera-based QR code scanning for reward validation
- **Reward Validation**: Complete workflow from scan to validation with API integration
- **Admin Dashboard**: Simple dashboard for museum administration

## Architecture
- **MVVM Pattern**: Using ViewModels for business logic
- **Repository Pattern**: Clean data layer with AuthRepository and RewardRepository
- **XML Layouts**: Traditional Android layouts for all screens
- **Coroutines**: Asynchronous operations for network calls

## Key Components

### Authentication
- `AuthTokenManager`: Manages token persistence using SharedPreferences
- `AuthRepository`: Handles login API calls with Basic Auth
- `LoginActivity`: Traditional Activity with XML layout for login UI

### QR Code & Rewards
- `QRScanActivity`: Camera-based QR code scanner using CameraX and MLKit
- `RewardDetailsActivity`: Displays reward information and handles validation
- `RewardRepository`: Handles reward API calls (getRewardDetails, useReward)
- `Reward`, `RewardResponse`, `UseRewardResponse`: Data models for reward system

### Network Layer
- `ApiService`: Retrofit interface for API communication
- `RetrofitInstance`: Configured with SSL bypass for development and automatic token injection

## QR Code Workflow

1. **Main Screen**: User taps "SCAN QR CODE" button
2. **QR Scan**: Camera opens and scans QR code containing reward ID
3. **Fetch Details**: App makes GET request to `/user/rewards/details/{reward_id}`
4. **Display Reward**: Shows reward details (subject, description, amount, reduction_type)
5. **Validation**: User taps "VALIDATE" button
6. **Use Reward**: App makes POST request to `/user/rewards/{reward_id}`
7. **Result**: Shows success/error message based on response code:
   - 200: Reward validated successfully
   - 400: Reward already used
   - 417: Reward expired

## API Endpoints

### Authentication
- `GET /auth/login`: Login with Basic Auth, returns Bearer token

### Rewards
- `GET /user/rewards/details/{reward_id}`: Get reward details
- `POST /user/rewards/{reward_id}`: Validate/use reward

## Network Configuration
- Base URL: `https://muses.dev.services.ding.unisannio.it/`
- RBAC Role: `M_ADMIN` (Museum Admin)
- SSL verification disabled for development (self-signed certificates)

## Build Configuration
- **minSdk**: 24
- **targetSdk**: 36
- **compileSdk**: 36
- **Kotlin**: 2.0.21
- **AGP**: 8.13.0

### Dependencies
- CameraX for camera functionality
- MLKit Barcode Scanning for QR code detection
- Retrofit 2.9.0 with Gson converter
- Lifecycle components
- Material Design Components

## Permissions Required
- `CAMERA`: For QR code scanning
- `INTERNET`: For API calls
- `ACCESS_NETWORK_STATE`: For network status

## Usage
1. Launch the app
2. If no token is saved, user is redirected to login
3. Enter admin credentials
4. Upon successful login, token is saved and user sees dashboard
5. Tap "SCAN QR CODE" to scan reward QR codes
6. Review reward details and tap "VALIDATE" to process
7. View validation results
8. Use logout to clear token and return to login

## Security Notes
⚠️ **Development Configuration**: The app currently bypasses SSL certificate validation for development purposes. This should be removed for production deployment.

## Data Models

### Reward Response Structure
```json
{
    "reward": {
        "amount": int,
        "description": String,
        "museum_id": String,
        "reduction_type": String,
        "subject": String
    }
}
```

### Use Reward Response
- Returns HTTP status code (200, 400, 417) indicating validation result