"""
Authentication service helpers module.

This module provides utility functions for user management, database operations,
and error handling in the authentication service.
"""

import os
import logging

from flask import current_app, jsonify
from flask_pymongo import PyMongo
from pymongo import MongoClient
from bson import ObjectId
from werkzeug.security import generate_password_hash, check_password_hash


# Configurazione MongoDB - supporta sia localhost che Docker
MONGO_HOST = os.getenv('MONGO_HOST', 'localhost')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'auth_db')

logger = logging.getLogger(__name__)
users = None


def setup_db_connection():
	"""
	Set up the database connection for MongoDB using Flask-PyMongo.
	"""
	global users
	current_app.config["MONGO_URI"] = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}"
	mongo = PyMongo(current_app)
	users = mongo.db.users


def ping_db() -> bool:
	"""
	Ping the MongoDB server to check readiness.
	Returns True if the ping is successful, False otherwise.
	"""
	# If the `users` collection hasn't been initialized, we can't reach the DB.
	if users is None:
		logger.debug("ping_db: users collection not initialized")
		return False

	# Create a short-lived MongoClient with 5s driver timeouts so only this
	# operation is affected. This avoids changing timeouts for the app-wide client.
	temp_client = None
	try:
		timeout_ms = 5000
		uri = (
			f"mongodb://{MONGO_HOST}:{MONGO_PORT}/"
			f"?connectTimeoutMS={timeout_ms}"
			f"&serverSelectionTimeoutMS={timeout_ms}"
			f"&socketTimeoutMS={timeout_ms}"
		)
		temp_client = MongoClient(uri)
		
		# Use the admin database to send a ping command.
		res = temp_client.admin.command('ping')
		ok = bool(res and res.get('ok'))
		if not ok:
			logger.debug("ping_db: ping returned non-ok result: %s", res)
		return ok
	except Exception as e:
		# Any exception (timeout, connection error, etc.) is treated as a failed ping
		logger.debug("ping_db: exception while pinging db with temp client: %s", e)
		return False
	finally:
		if temp_client:
			try:
				temp_client.close()
			except Exception:
				pass


def save_user(username: str, password: str, role: str) -> bool:
	"""
	Save a new user to the database if the user does not already exist.

	Args:
		username (str): The username of the user.
		password (str): The password of the user.
		role (str): The role of the user.

	Returns:
		bool: True if user was saved successfully, False if user already exists.
	"""
	try:
		if not validate_user(username, password, role):
			raise KeyError()
	except KeyError:
		doc = {
			"username": username,
			"password": generate_password_hash(password),
			"role": role
		}
		users.insert_one(doc)
		return True
	return False


def find_users(user_filter: dict[str]):
	"""
	Find users based on the given filter.

	Args:
		user_filter (dict): The filter criteria for finding users.

	Returns:
		list: List of serialized user objects.
	"""
	return list(map(serialize_obj, users.find(user_filter)))


def delete_user(user_id: str) -> bool:
	"""
	Delete a user by ID.

	Args:
		user_id (str): The ID of the user to delete.

	Returns:
		bool: True if deleted successfully, False otherwise.
	"""
	try:
		users.delete_one({"_id": ObjectId(user_id)})
		return True
	except Exception:
		return False


def get_user(user_id: str):
	"""
	Get a user by ID.

	Args:
		user_id (str): The ID of the user.

	Returns:
		dict or None: The serialized user object or None if not found.
	"""
	return serialize_obj(list(users.find({"_id": ObjectId(user_id)}))[0])


def get_user_by_name_and_role(username: str, role: str):
	"""
	Get a user by username and role.

	Args:
		username (str): The username of the user.
		role (str): The role of the user.

	Returns:
		dict or None: The serialized user object or None if not found.
	"""
	return serialize_obj(list(users.find({"username": username, "role": role}))[0])


def edit_user(user_id: str, data: dict) -> bool:
	"""
	Edit a user by ID with the provided data.

	Args:
		user_id (str): The ID of the user to edit.
		data (dict): The data to update.

	Returns:
		bool: True if updated successfully, False otherwise.
	"""
	try:
		users.update_one({"_id": ObjectId(user_id)}, {'$set': data})
	except Exception as e:
		logger.debug(e)
		return False
	return True


def validate_user(username: str, password: str, role: str):
	"""
	Validate user credentials.

	Args:
		username (str): The username.
		password (str): The password.
		role (str): The role.

	Returns:
		dict: The serialized user object if valid.

	Raises:
		KeyError: If user not found or password incorrect.
	"""
	user_list = list(users.find({"username": username, "role": role}))
	user_found = None
	for user in user_list:
		if check_password_hash(user['password'], password):
			user_found = user
			break
	if not user_found:
		raise KeyError(f"User {username} does not exist")
	return serialize_obj(user_found)


def internal_server_error_handler(func):
	"""
	Decorator to handle internal server errors.

	Args:
		func: The function to decorate.

	Returns:
		function: The decorated function.
	"""
	def handler(*args, **kwargs):
		try:
			return func(*args, **kwargs)
		except Exception:
			return 500, jsonify({
				"message": "Internal Server Error"
			})
	return handler


def serialize_obj(obj):
	"""
	Serialize MongoDB object by converting ObjectId to string.

	Args:
		obj: The object to serialize.

	Returns:
		The serialized object or None.
	"""
	if obj:
		obj['_id'] = str(obj['_id'])
		return obj
	return None
