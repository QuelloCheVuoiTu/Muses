"""
Mission Tracker service Flask app.
"""

import os
import json
import logging
import sys
from typing import get_args
from flask import Flask, request, jsonify, make_response
from werkzeug.exceptions import BadRequest,UnsupportedMediaType
from models import Mission,Step,MISSION_STATUSES
from data_source import setup_db_connection, get, edit, find, delete,insert
from data_source import ping_db
import requests
from threading import Thread
STEP_MANAGER_SVC_NAME = os.getenv("STEP_MANAGER_SVC_NAME", "quest-manager")
DEBUG_MODE = os.getenv("DEBUG_MODE", "true")
LOG_FILE = os.getenv("LOG_FILE", "/var/log/svc_logs.log")
LOG_LEVEL = logging.DEBUG if DEBUG_MODE == "true" else logging.WARNING

app = Flask(__name__)

# Configure logging
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

AppLogger.info("Debug level set to %s", "DEBUG" if DEBUG_MODE == "true" else "WARNING")

def get_db_mission(id:str)->Mission:
	mission = get(id)
	if not mission:
		raise KeyError() 
	return Mission.deserialize(mission)
	

@app.route("/health", methods=["GET"])
def health():
	"""Simple health check function."""

	return jsonify({"message": "Mission tracker service is online"}), 200


@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""
	# Ensure the DB connection is set up in the app context
	try:
		# ping_db will return False if users collection isn't initialized or ping fails
		if ping_db():
			return jsonify({"message": "Mission tracker service is ready"}), 200
		AppLogger.error("Readiness check failed: cannot reach database")
		return jsonify({"message": "Mission tracker service not ready: cannot reach database"}), 503
	except Exception as e:
		AppLogger.error("Readiness check error: %s", e)
		return jsonify({"message": "Mission tracker service not ready"}), 503


@app.route("/", methods=["GET"])
def get_all_missions():
	missions = find({})
	resp = make_response(jsonify({"missions":missions,"count":len(missions)}),200)
	resp.headers["Content-Type"]="application/json"
	return resp

@app.route("/<u_id>", methods=["POST"])
def generate_mission(u_id:str):
	u_id = u_id.strip()
	if not u_id:
		return jsonify({"message":"Parameter 'user_id' expected in the request"}),400
	r = requests.post(f"http://{STEP_MANAGER_SVC_NAME}/{u_id}")
	if not r.ok:
		return jsonify({"message":"something went wrong with steps generation"}),500
	try:
		# expected list of string with step ids
		step_ids = r.json()
		if not step_ids:
			return jsonify({"message":"Cannot create mission with no quests"}),400
		mission_doc = dict(
				user_id=u_id,
				status="PENDING",
				steps=[dict(
					step_id=s_id,
					completed=False
				) for s_id in step_ids]
			)
		m = Mission.deserialize(mission_doc)
	except json.JSONDecodeError:
		return jsonify({"message":"Bad request, body is not a json string"}),400
	except Exception as e :
		return jsonify({"message":"Something went wrong with data parsing","error":str(e)}),400
	try:
		res = insert(m)
	except Exception:
		return jsonify({"message":"Something went wrong with mission registration"}),500

	return jsonify({"mission":str(res.inserted_id)}),201

@app.route("/<id>", methods=["GET"])
def get_mission(id:str):
	mission = get(id)
	if not mission:
		return jsonify({"error":"Mission not found"}),404
	step_list = mission["steps"]
	completed_steps = len([s for s in step_list if s["completed"]])
	resp = make_response(jsonify({"mission":mission,"steps_completed":completed_steps,"tot_steps":len(step_list)}),200)
	resp.headers["Content-Type"]="application/json"
	return resp

@app.route("/complete/<id>", methods=["POST"])
def complete_step(id:str):
	try:
		mission = get_db_mission(id)
		body = request.get_json()
		step_id = body.get("step_id",None)
		if not step_id:
			raise BadRequest()
		AppLogger.info(f"Completing step {step_id}")
		mission.complete_step(step_id)
		if not edit(id,mission.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update mission"}),500
		comp_steps,tot_steps = mission.get_progress_state()
		return jsonify({"tot_steps":tot_steps,"completed_steps":comp_steps}),200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except KeyError:
		return jsonify({"error":"Mission not found"}),404
	except PermissionError:
		return jsonify({"error":"Cannot complete step"}),403
	except ReferenceError:
		return jsonify({"error":"Cannot complete step, not in order"}),406
	except ConnectionError:
		return jsonify({"error":"Could not start a new quest"}),503
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500
	
@app.route("/stop/<id>", methods=["POST"])
def stop(id:str):
	try:
		mission = get_db_mission(id)
		AppLogger.info(f"Stopping mission {id}")
		mission.stop_mission()
		if not edit(id,mission.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update mission"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except KeyError:
		return jsonify({"error":"Mission not found"}),404
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500

@app.route("/start/<id>", methods=["POST"])
def start(id:str):
	try:
		mission = get_db_mission(id)
		AppLogger.info(f"Starting mission {id}")
		mission.start_mission()
		if not edit(id,mission.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update mission"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except KeyError:
		return jsonify({"error":"Mission not found"}),404
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500

@app.route("/reset/<id>", methods=["POST"])
def reset(id:str):
	try:
		mission = get_db_mission(id)
		AppLogger.info(f"Resetting mission {id}")
		mission.reset_mission()
		if not edit(id,mission.serialize()):
			return jsonify({"error":"Internal Server Error","message":"Could not update mission"}),500
		return "",200
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except KeyError:
		return jsonify({"error":"Mission not found"}),404
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500


@app.route("/status/<id>", methods=["POST"])
def update_status(id:str):
	mission = get(id)
	if not mission:
		return jsonify({"error":"Mission not found"}),404
	mission = Mission.deserialize(mission)
	try:
		body = request.get_json()
		status = body.get("status",None)
		if not status:
			raise BadRequest()
		AppLogger.info(f"Transitioning status from {mission.status} to {status}")
		match status:
			case "COMPLETED":
				mission.complete_mission()
			case "PENDING":
				mission.reset_mission()
			case "IN_PROGRESS":
				mission.start_mission()
			case "STOPPED":
				mission.stop_mission()
	except UnsupportedMediaType:
		return jsonify({"error":"Bad request format, json payload expected"}),400
	except json.JSONDecodeError:
		return json({"message":"Internal server error"}),500
	except Exception as e:
		ex= str(e)
		if "Status" or "steps are not completed" in ex or "Cannot complete task" in ex:
			AppLogger.error("Could not transition status: "+ ex)
			return jsonify({"message":"Could not update status","error":ex}),400
		return json({"message":"Could not update step status","error":ex}),500
	if not edit(id,mission.serialize()):
		return jsonify({"error":"Internal Server Error","message":"Could not update mission"}),500
		
	return "",200

@app.route("/status", methods=["GET"])
def get_mission_by_status():
	status = request.args.get("status",None)
	if not status:
		return "No query specified",400
	missions = find({"status":status})
	return jsonify(missions,200)


@app.route("/user/<user_id>", methods=["GET"])
def get_mission_by_user(user_id):
	missions = find({"user_id":user_id})
	return jsonify(missions,200)

# Error handlers
@app.errorhandler(404)
def not_found(arg):
	"""404 error handler."""

	return jsonify({'error': 'Endpoint not found'}), 404

# Error handlers
@app.errorhandler(401)
def not_found():
	"""401 error handler."""

	return jsonify({'error': 'Something went wrong with authentication'}), 401


@app.errorhandler(Exception)
def internal_error(error):
	"""500 error handler."""

	if DEBUG_MODE == "true":
		return jsonify({'error': f'Internal server error: {error}'}), 500

	return jsonify({'error': 'Internal server error'}), 500


if __name__ == '__main__':
	with app.app_context():
		setup_db_connection()
	app.run( host='0.0.0.0', port=8000)
