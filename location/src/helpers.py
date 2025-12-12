import os
import logging

from flask import current_app, jsonify
from flask_pymongo import PyMongo
from pymongo import MongoClient
from bson import ObjectId


# Configurazione MongoDB - supporta sia localhost che Docker
MONGO_HOST = os.getenv('MONGO_HOST', 'localhost')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'users_db')

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

	Returns:
	 	bool: True if the ping is successful, False otherwise.
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
		result = users.update_one({"_id": ObjectId(user_id)}, {'$set': data})

		if result.matched_count == 0:
			logger.debug("edit_user: no matching user for id %s", user_id)
			return False
	except Exception as e:
		logger.debug("edit_user: exception: %s", e)
		return False
	
	return True


def get_mobile_token(user_id: str):
	"""
	Retrieve the 'token_mobile' field for a user by ID.

	Args:
		user_id (str): The user's ObjectId as a string.

	Returns:
		Optional[str]: The token_mobile value if present, otherwise None.
	"""
	try:
		user = users.find_one({"_id": ObjectId(user_id)}, {"token_mobile": 1})
		if not user:
			logger.debug("get_mobile_token: user not found %s", user_id)
			return None
		return user.get("token_mobile")
	except Exception as e:
		logger.debug("get_mobile_token: exception: %s", e)
		return None



