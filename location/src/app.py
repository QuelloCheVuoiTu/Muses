import asyncio
import logging
import os
import sys

from flask import Flask, request, jsonify

from helpers import setup_db_connection, ping_db, edit_user, get_mobile_token
from services import request_location_from_device


# Configure logging
DEBUG_MODE = os.getenv("DEBUG_MODE", "true")
LOG_FILE = os.getenv("LOG_FILE", "location_logs.log")

Rootlogger = logging.getLogger()
if Rootlogger.hasHandlers():
    Rootlogger.handlers.clear()
Rootlogger.setLevel(logging.DEBUG if DEBUG_MODE == "true" else logging.WARNING)

AppLogger = logging.getLogger("Auth")

# Clear existing handlers to prevent duplicate logs
if AppLogger.hasHandlers():
    AppLogger.handlers.clear()

AppLogger.setLevel(logging.DEBUG if DEBUG_MODE == "true" else logging.WARNING)

# Create file handler
file_handler = logging.FileHandler(LOG_FILE)

# Create console handler
stream_handler = logging.StreamHandler(sys.stdout)

# Create formatter and add it to the handlers
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
file_handler.setFormatter(formatter)
stream_handler.setFormatter(formatter)

# Add the handlers to the logger
AppLogger.addHandler(file_handler)
AppLogger.addHandler(stream_handler)
Rootlogger.addHandler(stream_handler)

# Print debug level
AppLogger.info("Debug level set to %s", "DEBUG" if DEBUG_MODE == "true" else "WARNING")

# Initialize Flask app
app = Flask(__name__)

# Dictionary of per-user queues
user_queues: dict[str, asyncio.Queue] = {}


# Get the queue used to send location to GET method
def get_user_queue(user_id: str) -> asyncio.Queue:
    """Return (or create) a queue for the given user."""

    if user_id not in user_queues:
        user_queues[user_id] = asyncio.Queue()

    return user_queues[user_id]


# Health endpoint
@app.route("/health", methods=["GET"])
def health():
    """Simple health check function."""

    return jsonify({"message": "Location service is online"}), 200


# Readiness endpoint
@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""
     
	# Ensure the DB connection is set up in the app context
	try:
		# ping_db will return False if users collection isn't initialized or ping fails
		if ping_db():
			return jsonify({"message": "Location service is ready"}), 200
		AppLogger.error("Readiness check failed: cannot reach database")
		return jsonify({"message": "Location service not ready: cannot reach database"}), 503
	except Exception as e:
		AppLogger.error("Readiness check error: %s", e)
		return jsonify({"message": "Location service not ready"}), 503


# Save the token from the Android application
@app.route("/savetoken", methods=["PATCH"])
def save_token():
    """Receive JSON payload with a 'token' field and save it to the module-level variable."""

    data = request.get_json(silent=True)
    if not data or 'token' not in data:
        return jsonify({"error": "Missing 'token' in JSON body"}), 400

    user_id = data["userId"]
    token = data['token']

    # Print the received data to stdout for verification
    AppLogger.debug(f"Received user ID: {user_id}")
    AppLogger.debug(f"Received token: {token}")

    # TODO: Instead of using global variables, patch the user in the DB with the token
    reg_success = edit_user(user_id, {"token_mobile": token})
    if not reg_success:
        AppLogger.debug("Edit cancelled: Could not save token mobile")
        return jsonify({"message": "Could not save token mobile"}), 400

    return jsonify({"message": "Token saved"}), 200


# Send notification to the Android app
# @app.route("/notify", methods=["GET"])
# def notify():
#     """Send a simple push notification to the Android app."""

#     # Ensure a token is available
#     if not token:
#         return jsonify({"success": False, "error": "No token saved"}), 400

#     result = send_fcm_notification(token, 'Test notification!',
#                                    'Hello from python firebase admin SDK')

#     # Return the result from the notification service with an appropriate status
#     if isinstance(result, dict) and result.get("success"):
#         return jsonify(result), 200
#     else:
#         # If result is a dict with an error, return it with 500; otherwise return generic error
#         if isinstance(result, dict):
#             return jsonify(result), 500
#         return jsonify({
#             "success": False,
#             "error": "Unknown error sending notification"
#         }), 500


# Get location from the mobile device
@app.route("/<id>", methods=["GET"])
async def get_location(id):
    """Retrieve the location from the mobile device.

    Flask passes the path parameter as a keyword argument; accept `id` and use it
    as the user identifier.
    """

    # Use the route parameter provided by Flask
    user_id = id

    if not user_id:
        return jsonify({"error": "Missing 'id' in path"}), 400
    AppLogger.debug(f"ID from path: {user_id}")

    # Retrieve token from user using user_id
    token = get_mobile_token(user_id)

    AppLogger.debug(f"Retrieved token: {token}")

    # Ensure a token is available
    if not token:
        return jsonify({"success": False, "error": "No token saved"}), 400

    result = request_location_from_device(token)

    # Return the result from the notification service with an appropriate status
    if isinstance(result, dict) and result.get("success"):
        # Request successfully sent, now retieve location from response

        # Get the queue associated to the specified user
        queue = get_user_queue(user_id)
        try:
            data = await asyncio.wait_for(queue.get(), timeout=10)
        except asyncio.TimeoutError:
            return jsonify({"error": "Timeout waiting for data"}), 504

        if not data or 'latitude' not in data or 'longitude' not in data:
            return jsonify({"error": "Missing parts in JSON body"}), 400

        AppLogger.debug(f"Received data: {data}")

        latitude = data["latitude"]
        longitude = data["longitude"]

        return jsonify({"latitude": latitude, "longitude": longitude}), 200
    else:
        # If result is a dict with an error, return it with 500; otherwise return generic error
        if isinstance(result, dict):
            return jsonify(result), 500
        return jsonify({
            "success": False,
            "error": "Unknown error sending notification"
        }), 500


# Receive user location from app
@app.route("/savelocation", methods=["POST"])
async def save_location():
    """Receive the user location from the mobile device and put it into a queue for the user."""

    data = request.get_json(silent=True)
    if not data or 'latitude' not in data or 'longitude' not in data:
        return jsonify({"error": "Missing parts in JSON body"}), 400

    queue_id = data["userId"]
    AppLogger.debug(f"Received user ID: {queue_id}")

    latitude = data["latitude"]
    longitude = data["longitude"]
    AppLogger.debug(f"Received location: lat={latitude}, long={longitude}")

    # Get the queue for the user and put the location in it
    queue = get_user_queue(queue_id)
    await queue.put(data)

    return jsonify({"message": "Location correctly received"}), 200


# Error handlers
@app.errorhandler(404)
def not_found(error):
    """404 error handler."""

    return jsonify({'error': f'Endpoint not found: {error}'}), 404


@app.errorhandler(Exception)
def internal_error(error):
    """500 error handler."""

    return jsonify({'error': f'Internal server error: {error}'}), 500


# Main
if __name__ == '__main__':
	with app.app_context():
		setup_db_connection()

	app.run(debug=DEBUG_MODE, host='0.0.0.0', port=9000)
