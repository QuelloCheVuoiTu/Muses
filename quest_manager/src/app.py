from flask import Flask,request, jsonify, make_response
from werkzeug.exceptions import BadRequest,UnsupportedMediaType
from flask_pymongo import PyMongo
from bson import ObjectId
from models import Quest,Task
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

QUEST_BUILDER_SVC_NAME = os.getenv("QUEST_BUILDER_SVC_NAME", "quest-builder")
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

def get_db_quest(id:str)->Quest:
	quest = get(id)
	del(quest["_id"])
	if not quest:
		raise KeyError() 
	return Quest.deserialize(quest)


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

@app.route('/', methods=['GET'])
def get_all_quests():
	quests = find({})
	AppLogger.debug(f"Found quests: {quests}")
	resp = make_response(jsonify({"quests":quests,"count":len(quests)}),200)
	resp.headers["Content-Type"]="application/json"
	return resp

@app.route("/complete/<id>", methods=["POST"])
def complete_step(id:str):
	try:
		quest = get_db_quest(id)
		body = request.get_json()
		task_id = body.get("task_id",None)
		if not task_id:
			raise BadRequest()
		AppLogger.info(f"Completing task {task_id}")
		quest.complete_step(task_id)
		if not edit(id,quest.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update quest"}),500
		comp,tot = quest.get_progress_state()
		return jsonify({"tot_tasks":tot,"completed_tasks":comp}),200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except KeyError:
		jsonify({"error":"Quest not found"}),404
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except Exception as e:
		ex= str(e)
		if ex == "Task not found":
			AppLogger.error(ex)
			return jsonify({"message":"Task not found","error":ex}),404
		if "Status" or "steps are not completed" in ex or "Cannot complete task" or "tasks are not completed" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500
	
@app.route("/stop/<id>", methods=["POST"])
def stop(id:str):
	try:
		quest = get_db_quest(id)
		AppLogger.info(f"Stopping quest {id}")
		quest.stop()
		if not edit(id,quest.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update quest"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except KeyError:
		jsonify({"error":"Mission not found"}),404
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" or "tasks are not completed"in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500

@app.route("/start/<id>", methods=["POST"])
def start(id:str):
	try:
		quest = get_db_quest(id)
		AppLogger.info(f"Starting quest {id}")
		quest.start()
		if not edit(id,quest.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update quest"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except KeyError:
		jsonify({"error":"Mission not found"}),404
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" or "tasks are not completed" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500

@app.route("/reset/<id>", methods=["POST"])
def reset(id:str):
	try:
		quest = get_db_quest(id)
		AppLogger.info(f"Resetting quest {id}")
		quest.reset()
		if not edit(id,quest.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update quest"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except KeyError:
		jsonify({"error":"Mission not found"}),404
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" or "tasks are not completed" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500

@app.route('/<id>', methods=['POST'])
def generate_quest(id:str):
	# n_quests = randint(2,6)
	n_quests = 1
	max_tasks = randint(15,17)
	r = requests.get(f"http://{QUEST_BUILDER_SVC_NAME}/quest-builder/questing/quest/multiple?user_id={id}&n_quests={n_quests}&max_tasks={max_tasks}")
	if not r.ok:
		AppLogger.warning(f"Quest generation failed, returned: {r.content.decode(errors='ignore')} {r.status_code}")
		return jsonify({"message":"Something went wrong with quest generation"}),500
	quest_ids = []
	try:
		serialized_quests:list[dict] = r.json()
		AppLogger.debug(f"Received generated quests: {serialized_quests}")
		if not serialized_quests:
			return jsonify({"message":"Quest must have at least a task"}),400
		for q in serialized_quests:
			q["subject_id"]= q["museumId"]
			q["status"]="PENDING"
			del(q["museumId"])
			task_list = q["tasks"] 
			q["tasks"] = dict()
			for t in task_list:
				tid = t["artworkId"]
				q["tasks"][tid]=dict(
					completed=False,
					title = t["title"],
					description = t["description"]
				)
			AppLogger.debug(f"Deserializing quest: {q}")
			q = Quest.deserialize(q)
			AppLogger.debug(f"Quest: {q}")
			res = insert(q)
			quest_ids.append(str(res.inserted_id))
	except json.UnsupportedMediaType as e:
		AppLogger.error("Something went wrong with quest generation "+ str(e))
		return jsonify({"message":"Something went wrong with quest generation"}),500
	except Exception as e:
		AppLogger.error("Something went wrong with serialization or registration: "+ str(e))
		return jsonify({"message":"Something went wrong with quest registration","error":str(e)}),500
	
	return jsonify(quest_ids),200

@app.route('/<id>', methods=['GET'])
def get_quest(id:str):
	quest = get(id)
	if not quest:
		AppLogger.warning("Quest not found")
		return jsonify({"error":"Quest not found"}),404
	AppLogger.debug("Found quest: "+ str(quest))
	task_list = quest["tasks"].values()
	tasks_completed = len([t for t in task_list if t["completed"]])

	resp = make_response(jsonify({"quest":quest,"tasks_completed":tasks_completed,"tot_tasks":len(task_list)}),200)
	resp.headers["Content-Type"]="application/json"
	return resp

@app.route('/status/<id>', methods=['POST'])
def update_quest_status(id:str):
	quest = get(id)
	if not quest:
		AppLogger.warning("Quest not found")
		return jsonify({"error":"Quest not found"}),404
	del(quest["_id"])
	AppLogger.debug("Found quest: "+ str(quest))
	try:
		quest:Quest = Quest.deserialize(quest)
		body = request.get_json()
		status:str = body.get("status",None)
		task_id:str = body.get("task_id",None)
		if not status and not task_id:
			raise UnsupportedMediaType()
		if task_id:
			quest.complete_step(task_id)
		if status:
			status = status.upper()
			quest.transition_status(status)
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except KeyError:
		return jsonify({"error":"Bad Request, 'status' field excpected in request json"}),400
	except Exception as e:
		ex= str(e)
		if "Status" or "tasks are not completed" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		AppLogger.error("Could not complete quest registration: "+ ex)
		return jsonify({"error":"Internal server error"}),500
	if not edit(id,quest.serialize()):
		return jsonify({"error":"Internal Server Error","message":"Could not update quest"}),500
	return "",200

@app.route('/status', methods=['GET'])
def get_quest_by_status():
	status = request.args.get("status",None)
	subject = request.args.get("subject",None)
	full = request.args.get("full",None)
	query = dict()
	if status:
		status = status.upper()
		query["status"] = status
	if subject:
		query["subject_id"] = subject
	if full:
		full = full.lower()
	resp_dict = dict()
	if full == "true":
		quests = find(query)
		resp_dict["count"] = len(quests)
		resp_dict["quests"] = quests
	else:
		resp_dict["count"] = count(query)
		
	return jsonify(resp_dict),200

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
    