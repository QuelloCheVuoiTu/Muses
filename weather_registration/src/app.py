from flask import Flask,request, jsonify, make_response
from werkzeug.exceptions import BadRequest,UnsupportedMediaType
from flask_pymongo import PyMongo
from bson import ObjectId
from models import *
import requests
from logging.handlers import RotatingFileHandler
from datetime import datetime
from data_source import *
import os
import json
from random import choice
import logging
from typing import get_args
from models import QUEST_STATUSES
import sys
from random import randint

# Configure logging
DEBUG_MODE = os.getenv("DEBUG_MODE", "true")
LOG_FILE = os.getenv("LOG_FILE", "/var/log/svc_logs.log")
LOG_LEVEL = logging.DEBUG if DEBUG_MODE == "true" else logging.WARNING
Rootlogger = logging.getLogger()
AppLogger = logging.getLogger("App")

# Clear existing handlers to prevent duplicate logs
if AppLogger.hasHandlers():
    AppLogger.handlers.clear()
if Rootlogger.hasHandlers():
    Rootlogger.handlers.clear()

AppLogger.setLevel(LOG_LEVEL)
Rootlogger.setLevel(LOG_LEVEL)

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

app = Flask(__name__)


@app.route("/health", methods=["GET"])
def health():
	"""Simple health check function."""

	return jsonify({"message": "Quest tracker service is online"}), 200


@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""
	# Ensure the DB connection is set up in the app context
	try:
		# ping_db will return False if users collection isn't initialized or ping fails
		if ping_db():
			return jsonify({"message": "Quest tracker service is ready"}), 200
		AppLogger.error("Readiness check failed: cannot reach database")
		return jsonify({"message": "Quest tracker service not ready: cannot reach database"}), 503
	except Exception as e:
		AppLogger.error("Readiness check error: %s", e)
		return jsonify({"message": "Quest tracker service not ready"}), 503

def message_error(message:str):
	return jsonify({"message":message})

@app.route('/', methods=['PUT'])
def save_node():
	try:
		body = request.get_json()
		node = Node.deserialize(body)		
	except UnsupportedMediaType:
		return message_error("Expected json body"),415
	except Exception as e:
		return message_error(str(e)),400
	try:
		ins = insert(node)
	except Exception:
		return message_error("Internal server error"),500
	return jsonify({"id":str(ins.inserted_id)}),201

@app.route('/<id>', methods=['DELETE'])
def delete_node(id:str):
	try:
		id = id.strip()
		if not id:
			raise Exception("Expected id")
		delete(id)	
	except UnsupportedMediaType:
		return message_error("Expected json body"),415
	except Exception as e:
		return message_error(str(e)),400
	return "",204

# Gestione errori globali
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint non trovato'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Errore interno del server'}), 500

if __name__ == '__main__':
	with app.app_context():
		setup_db_connection()
	app.run( host='0.0.0.0', port=8000)
    