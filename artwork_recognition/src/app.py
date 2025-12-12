from flask import Flask,request, jsonify, make_response
from werkzeug.exceptions import BadRequest,UnsupportedMediaType
from flask_pymongo import PyMongo
from bson import ObjectId
import requests
from helpers import async_http_requests,RequestSchema
from logging.handlers import RotatingFileHandler
from datetime import datetime
import os
import json
from random import choice
import logging
from typing import get_args
import sys
from random import randint

ARTWORK_SVC_NAME = os.getenv("ARTWORK_SVC_NAME")
MISSION_SVC_NAME = os.getenv("MISSION_SVC_NAME")
QUEST_SVC_NAME = os.getenv("QUEST_SVC_NAME")

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

	return jsonify({"message": "Artwork recognition service is online"}), 200


@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""

	return jsonify({"message": "Artwork recognition service is online"}), 200

@app.route("/<user_id>", methods=["POST"])
def recognize(user_id:str):
	user_id = user_id.strip()
	if not user_id:
		return jsonify({"message":"Id path param is required"}),400
	
	try:
		body = request.get_json()
		task_id = body["task_id"]
		mission_id = body["mission_id"]
		quest_id = body["quest_id"]
	except KeyError:
		return jsonify({"message":"Excpected json body with the required parameters 'task_id', 'mission_id', 'quest_id'"}),400
	except UnsupportedMediaType:
		return jsonify({"message":"Excpected json body"},415)

	request_map = dict(
		mission = RequestSchema(
			method="GET",
			url=f"http://{MISSION_SVC_NAME}/{mission_id}"
		),
		quest = RequestSchema(
			method="GET",
			url=f"http://{QUEST_SVC_NAME}/{quest_id}"
		)
	)

	response_map = async_http_requests(request_map)

	if any(map(lambda v: isinstance(v,Exception),response_map.values())):
		return jsonify({"message":"Server Connection Lost, retry later"},503)
	
	if any(map(lambda v: v.status_code == 404 or v.status_code >= 400 ,response_map.values())):
		return jsonify({"message":"Data non-coherent, could not link the identifier received to the user's missions"}),404
	
	mission_repsonse = response_map["mission"]
	quest_repsonse = response_map["quest"]
	mission = mission_repsonse.json()["mission"]
	quest = quest_repsonse.json()["quest"]
	if user_id != mission["user_id"]:
		return jsonify({"message":"Not authorized"}),401
	if quest_id not in map(lambda m: m["step_id"],mission["steps"]):
		return jsonify({"message":"Quest not found inside defined mission"}),404
	if task_id not in quest["tasks"]:
		return jsonify({"message":"Task not found inside defined quest"}),404
	
	for i,s in enumerate(mission["steps"]):
		if s["completed"]:
			continue
		if s["step_id"] != quest_id:
			return jsonify({"message":"Cannot complete task because this quest must not be done yet"}),406
		break
	#Check for user location

	#Complete task,quests and missions accordingly
	r = requests.post(f"http://{QUEST_SVC_NAME}/complete/{quest_id}",json={"task_id":task_id})
	if not r.ok:
		AppLogger.error(f"Task completion returned error: {r.content.decode(errors="ignore")}")
		if r.status_code == 404:
			return jsonify({"message":"Task not found"}),404
		return jsonify({"message":"Could not complete task"}),500
	body = r.json()
	tasks_completed = int(body["completed_tasks"] )
	tot_tasks = int(body["tot_tasks"])
	if tasks_completed != tot_tasks:
		return "", 204
	r = requests.post(f"http://{MISSION_SVC_NAME}/complete/{mission_id}",json={"step_id":quest_id})
	if not r.ok:
		AppLogger.error(f"Quest completion returned error: {r.content.decode(errors="ignore")}")
		if r.status_code == 400:
			return "", 204
		return jsonify({"message":"Could not complete quest"}),500
	return "", 204

# Gestione errori globali
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint non trovato'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Errore interno del server'}), 500

if __name__ == '__main__':
	if not (QUEST_SVC_NAME and MISSION_SVC_NAME):
		AppLogger.critical("QUEST_SVC_NAME and MISSION_SVC_NAME environment variables are mandatory for the service usage")
	app.run( host='0.0.0.0', port=8000)
