from flask import Flask, request, jsonify,Response
from flask_pymongo import PyMongo,MongoClient
from bson import ObjectId
import requests
from datetime import datetime
from data_source import *
import os
import json
from models import Reward,OutOfStockException,ExpiredRewardException,RewardNotFoundException,RewardAlreadyUsedException
from random import choice
from logging.handlers import RotatingFileHandler
import logging
import sys

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

def build_error(message:str,code:int,error:str = None)->tuple[Response,int]:
    resp_dict = {"message":message.strip()}
    if error and error.strip():
        resp_dict["error"] = error.strip()
    return jsonify(resp_dict),code 

# GET ALL - Recupera tutti i musei
@app.route('/', methods=['GET'])
def get_all_rewards():
    try:
        rewards = find()
        AppLogger.debug(f"Found rewards: {rewards}")
        return jsonify({"rewards":rewards,"count":len(rewards)})
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
# GET ALL - Recupera tutti i musei
@app.route('/<reward_id>', methods=['GET'])
def getrewardbyid(reward_id):
    try:
        reward = get(reward_id)
        AppLogger.debug(f"Found reward: {reward}")
        if not reward:
            return build_error("Reward not found",404)
        return jsonify({
            'reward': reward
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
@app.route('/details/<reward_id>', methods=['GET'])
def getreward_details(reward_id):
    try:
        reward = get(reward_id)
        if not reward:
            return build_error("Reward not found",404)
        AppLogger.debug(f"Found reward: {reward}")
        if not reward:
            return build_error("Reward not found",404)
        reward = Reward.deserialize(reward)
        return jsonify({
            'reward': reward.retrieve_reward()
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
@app.route('/<reward_id>', methods=['POST'])
def use_reward(reward_id):
    try:
        reward = get(reward_id)
        AppLogger.debug(f"Using reward: {reward}")
        if not reward:
            return build_error("Reward not found",404)
        reward = Reward.deserialize(reward)
        reward.use()
        edit(reward_id,reward.serialize())
        return jsonify({"reward":reward.reward_id}), 200
    except RewardAlreadyUsedException as e:
        AppLogger.error(str(e))
        return build_error(str(e),400)
    except ExpiredRewardException as e:
        AppLogger.warning(str(e))
        return build_error(str(e),417)
    except Exception as e:
        AppLogger.critical(str(e))
        return jsonify({'error': str(e)}), 500

@app.route('/generate/<user_id>', methods=['GET'])
def generate_reward(user_id):
    try:
        reward = Reward.generate(user_id=user_id)
        AppLogger.debug(f"Generated reward: {reward}")
        ins_id = str(insert(reward).inserted_id)
        return jsonify({
            'message': 'Reward successfully created',
            'reward_id': ins_id
        }), 201
    except OutOfStockException as e:
        AppLogger.warning(str(e))
        return build_error(str(e),503)
    except Exception as e:
        return jsonify(
           {
            'message': 'Internal server error',
            'error' : str(e)
        } 
        ),500
    

# GET - Recupera i reward di uno user
@app.route('/owned/<user_id>', methods=['GET'])
def get_user_reward(user_id):
    try:
        reward_list = list(find({'user_id': user_id}))
        AppLogger.debug(f"Found user rewards: {reward_list}")
        if not reward_list:
            return jsonify(
            {
            'message': f'No rewards found for user {user_id}',
            } 
            ),404
        return jsonify(
            {
            'rewards': reward_list,
            'count' : len(reward_list)
            } 
            ),200
        
    except Exception as e:
        return jsonify(
           {
            'message': 'Internal server error',
            'error' : str(e)
        } 
        ),500

# integrate getbyId. This is for quest status: checkStatus, completeTask, Deactivate,  

# Endpoint per verificare lo stato dell'API
@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({"message": "Auth service is online"}), 200

@app.route("/ready", methods=["GET"])
def ready():
	"""Readiness probe: returns 200 if DB is reachable, 503 otherwise."""
	# Ensure the DB connection is set up in the app context
	try:
		# ping_db will return False if users collection isn't initialized or ping fails
		if ping_db():
			return jsonify({"message": "User rewards service is ready"}), 200
		print("Readiness check failed: cannot reach database")
		return jsonify({"message": "User rewards service not ready: cannot reach database"}), 503
	except Exception as e:
		print("Readiness check error: %s", e)
		return jsonify({"message": "User rewards service not ready"}), 503

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
    app.run(debug=True, host='0.0.0.0', port=8000)