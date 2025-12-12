import os
import logging

from flask import current_app, jsonify
from flask_pymongo import PyMongo
from pymongo import MongoClient
from pymongo.errors import ConnectionFailure, OperationFailure
from pymongo.write_concern import WriteConcern
from pymongo.read_concern import ReadConcern
from pymongo.results import InsertOneResult
from bson import ObjectId
from helpers import DictSerializable
from typing import TypeVar,Union
from functools import partial
# Configurazione MongoDB - supporta sia localhost che Docker
MONGO_HOST = os.getenv('MONGO_HOST', 'localhost')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'rewards_db')
MONGO_COL = os.getenv('COLLECTION_NAME', 'rewards')

logger = logging.getLogger(__name__)
collection = None
mongo = None

def setup_db_connection():
	"""
	Set up the database connection for MongoDB using Flask-PyMongo.
	"""
	global collection
	global mongo
	mongo = MongoClient(f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}")
	db = mongo.get_database(MONGO_DB)
	collection = db.get_collection(MONGO_COL,write_concern=WriteConcern(fsync=True))


def ping_db() -> bool:
	"""
	Ping the MongoDB server to check readiness.
	Returns True if the ping is successful, False otherwise.
	"""
	# If the `collection` collection hasn't been initialized, we can't reach the DB.
	if collection is None:
		logger.debug("ping_db: collection collection not initialized")
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


def insert(object: DictSerializable) -> InsertOneResult:
	"""
	Save a new object to the database if the object does not already exist.

	Args:
		object (DictSerializable): Serializable object to insert into db
	Returns:
		bool: True if object was saved successfully, False if object already exists.
	"""
	return collection.insert_one(object.serialize())


def find(filter: dict[str],limit:int=0) ->list[dict]:
	"""
	Find collection based on the given filter.

	Args:
		filter (dict): The filter criteria for finding collection.

	Returns:
		list: List of serialized object objects.
	"""
	result = collection.find(filter) if limit <=0 else collection.find(filter).limit(limit)
	return list(map(serialize_obj, result))

def count(filter: dict[str]) ->int:
	"""
	Count collection docs based on the given filter.

	Args:
		filter (dict): The filter criteria for finding collection.

	Returns:
		list: List of serialized object objects.
	"""
	return collection.count_documents(filter)


def delete(id: str) -> bool:
	"""
	Delete a object by ID.

	Args:
		id (str): The ID of the object to delete.

	Returns:
		bool: True if deleted successfully, False otherwise.
	"""
	try:
		collection.delete_one({"_id": ObjectId(id)})
		return True
	except Exception:
		return False


def get(id: str)->Union[dict,None]:
	"""
	Get a object by ID.

	Args:
		id (str): The ID of the object.

	Returns:
		dict or None: The serialized object object or None if not found.
	"""
	try:
		return serialize_obj(list(collection.find({"_id": ObjectId(id)}))[0])
	except IndexError:
		return None

def edit(id: str, data: dict,session=None) -> bool:
	"""
	Edit a object by ID with the provided data.

	Args:
		id (str): The ID of the object to edit.
		data (dict): The data to update.

	Returns:
		bool: True if updated successfully, False otherwise.
	"""
	try:
		collection.update_one({"_id": ObjectId(id)}, {'$set': data},session=session)
	except Exception as e:
		logger.debug(e)
		return False
	return True

def safe_edit(id:str,data:dict)->bool:
	with mongo.start_session() as session:
		try:
			fnc = partial(edit,id,data)
			# Uses the with_transaction method to start a transaction, execute the callback, and commit (or abort on error).
			session.with_transaction(fnc)
			return True
		except (ConnectionFailure, OperationFailure) as e:
			return False

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
