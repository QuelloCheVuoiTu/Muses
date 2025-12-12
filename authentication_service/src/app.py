"""
Authentication service Flask app.
"""

import os
import json
import logging
import sys
from logging.handlers import RotatingFileHandler

from flask import Flask, request, jsonify, make_response

from auth import validate_registration, validate_token, verify_login
from rbac import rbac_validation
from helpers import setup_db_connection, get_user, edit_user, find_users, delete_user
from helpers import ping_db

DEBUG_MODE = os.getenv("DEBUG_MODE", "true")
LOG_FILE = os.getenv("LOG_FILE", "/var/log/auth_logs.log")


app = Flask(__name__)

# Configure logging
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
file_handler = RotatingFileHandler(LOG_FILE,"a", maxBytes=5*1024*1024,backupCount=2)
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

AppLogger.info("Debug level set to %s", "DEBUG" if DEBUG_MODE == "true" else "WARNING")


@app.route("/health", methods=["GET"])
def health():
	"""Simple health check function."""

	return jsonify({"message": "Auth service is online"}), 200


@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""
	# Ensure the DB connection is set up in the app context
	try:
		# ping_db will return False if users collection isn't initialized or ping fails
		if ping_db():
			return jsonify({"message": "Auth service is ready"}), 200
		AppLogger.error("Readiness check failed: cannot reach database")
		return jsonify({"message": "Auth service not ready: cannot reach database"}), 503
	except Exception as e:
		AppLogger.error("Readiness check error: %s", e)
		return jsonify({"message": "Auth service not ready"}), 503


@app.route("/login", methods=["GET"])
def login():
	"""Login endpoint."""

	authorization_header = request.authorization
	if not authorization_header:
		return jsonify({"message": "Authorization required"}), 401

	username = authorization_header.get("username", None)
	password = authorization_header.get("password", None)
	AppLogger.debug("Username: %s", username)

	if not username or not password:
		return jsonify({"message": "Authorization required"}), 401

	declared_role = request.headers.get("RBAC-Name", None)
	AppLogger.debug("Declared Role: %s", declared_role)
	if not declared_role:
		return jsonify({"message": "Wrong authorization origin"}), 401

	try:
		user_id, external_id, token = verify_login(username, password, declared_role)
	except KeyError:
		return jsonify({"message": "User not authorized"}), 401

	response = make_response(jsonify({"token": token}), 200)
	response.headers["RBAC-Name"] = declared_role
	response.headers["UserID"] = user_id
	if external_id:
		response.headers["EntityID"] = external_id
	return response


@app.route("/validate", methods=["GET"])
def validate():
	"""Token validation endpoint."""

	authorization_header = request.authorization
	if not authorization_header or authorization_header.type != "bearer":
		return jsonify(
			{"message": "Bearer schema for token authentication is required"}), 401

	token = authorization_header.token
	AppLogger.debug("Token: %s", token)
	if not token:
		return jsonify({"message": "Token for validation is required"}), 401

	try:
		user_id, role = validate_token(token)
		user = get_user(user_id)
	except IndexError:
		return jsonify({"message": "User not valid"}), 401
	except Exception:
		return jsonify({"message": "Token not valid"}), 401
	
	external_id = user["external_id"] if "external_id" in user else ""
	AppLogger.debug("user_id, role and external_id: %s : %s : %s", user_id, role, external_id)

	if not rbac_validation(request, role, external_id):
		AppLogger.debug("User denied request: %s %s : %s",
						request.headers.get('Original-Route', 'no-path-header'),
						request.headers.get('Original-Method', 'no-path-header'),
						role)
		return jsonify({"message": "User not authorized to call this API"}), 401

	AppLogger.debug("User granted request: %s : %s", request.headers.get('Original-Route', 'no-path-header'), role)

	response = make_response("", 204)
	response.headers["RBAC-Name"] = role
	response.headers["UserID"] = user_id
	if external_id:
		response.headers["EntityID"] = external_id
	return response


@app.route("/register", methods=["POST"])
def register():
	"""User registration endpoint."""

	data = request.get_data(as_text=True)
	try:
		data: dict = json.loads(data)
	except json.JSONDecodeError:
		return jsonify(
			{"message": f"Registration body is not valid, received {data}"}), 400

	username = data.get("username", None)
	password = data.get("password", None)
	AppLogger.debug("Username: %s", username)
	if not username or not password:
		return jsonify(
			{"message": f"Registration body is not complete, received {data}"}), 400

	declared_role = request.headers.get("RBAC-Request", None)
	if not declared_role:
		return jsonify({"message": "Wrong registration origin"}), 401

	reg_success, message = validate_registration(username=username,
													password=password,
													role=declared_role)
	if not reg_success:
		return jsonify({"message": message}), 400

	return jsonify({"message": "Registration completed successfully"}), 200


@app.route("/linkentity", methods=["POST"])
def linkentity():
	"""Link user to external entity."""

	data = request.get_data(as_text=True)
	try:
		data: dict = json.loads(data)
	except json.JSONDecodeError:
		AppLogger.debug("Linking body is not valid, received %s", data)
		return jsonify( {"message": f"Linking body is not valid, received {data}"}), 400

	user_id = data.get("user_id", None)
	external_id = data.get("external_id", None)
	AppLogger.debug("User ID, external_id: %s:%s", user_id, external_id)
	if not (user_id and external_id):
		AppLogger.debug("Linking body is not valid, received %s", data)
		return jsonify( {"message": f"Linking body is not complete, received {data}"}), 400

	try:
		user = get_user(user_id)
	except IndexError:
		return jsonify({"message": "Entity not found"}), 404

	reg_success = edit_user(user["_id"], {"external_id": external_id})
	if not reg_success:
		AppLogger.debug("Edit cancelled")
		return jsonify({"message": "Edit cancelled"}), 400

	return jsonify({"message": "Registration completed successfully"}), 200


@app.route("/linkentity/<external_id>", methods=["GET"])
def verifyentity(external_id):
	"""Verify if an entity is linked."""

	if not external_id:
		return jsonify( {"message": f"No user with external_id {external_id} found"}), 404

	users = find_users({"external_id": external_id})
	if not users:
		return jsonify({"message": f"No user with external_id {external_id} found"}), 404

	return "", 200


@app.route("/unlinkedentity", methods=["GET"])
def getunlinkedentities():
	"""Get all unlinked entities."""

	users = find_users({"external_id": {"$exists": False}})
	return jsonify({
		"count": len(users),
		"entities": [u["_id"] for u in users]
	}), 200


@app.route("/delete/<entity_id>", methods=["DELETE"])
def delete(entity_id):
	"""Delete an entity by ID."""

	if not delete_user(entity_id):
		return jsonify({"message": "Entity not found"}), 404

	return jsonify({"message": f"Entity {entity_id} deleted"}), 200


@app.route("/delete/bulk", methods=["DELETE"])
def bulkdelete():
	"""Bulk delete entities."""

	data: dict = request.get_json(force=True)
	entities_id = data.get("entities", None)
	if not entities_id:
		return jsonify({
			"message":
			"Wrong body structure, 'entities' field is either missing or is an empty list"
		}), 400

	deleted = 0
	for entity in entities_id:
		if delete_user(entity):
			deleted += 1

	return jsonify({"message": f"Deleted {deleted} entities"}), 200


# Error handlers
@app.errorhandler(404)
def not_found():
	"""404 error handler."""

	return jsonify({'error': 'Endpoint not found'}), 404


@app.errorhandler(Exception)
def internal_error(error):
	"""500 error handler."""

	if DEBUG_MODE == "true":
		return jsonify({'error': f'Internal server error: {error}'}), 500

	return jsonify({'error': 'Internal server error'}), 500


if __name__ == '__main__':
	with app.app_context():
		setup_db_connection()

	app.run(debug=True, host='0.0.0.0', port=8000)
