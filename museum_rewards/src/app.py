from flask import Flask, request, jsonify,Response
from flask_pymongo import PyMongo,MongoClient
from bson import ObjectId
import requests
from datetime import datetime
from data_source import *
import os
import json
from models import Reward,OutOfStockException,ExpiredRewardException,RewardNotFoundException,RewardAlreadyUsedException
from random import choices,choice,random
from logging.handlers import RotatingFileHandler
import logging
from time import sleep
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

@app.route('/', methods=['GET'])
def get_all_rewards():
    try:
        serialized_rewards = find({})
        AppLogger.debug(f"Found rewards: {serialized_rewards}")
        return jsonify({
            'rewards': serialized_rewards
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/redeem/<reward_id>', methods=['GET'])
def redeem_reward(reward_id):
    try:
        reward = get(reward_id)
        if not reward:
            return build_error("Reward not found",404)
        # reward["expiration_date"] = datetime.fromisoformat(reward["expiration_date"])
        # reward["created_at"] = datetime.fromisoformat(reward["created_at"])
        reward = Reward.deserialize(reward)
        return "",204
    except TimeoutError:
        return build_error("Reward Expired",417)
    except Exception:
        return build_error("Internal Server Error",500)

@app.route('/assign', methods=['GET'])
def assign_reward():
    try:
        m_type = request.args.get('type')
        limit = 1
        query=dict(expiration_date={"$gte": datetime.now()},stock={"$gte":0})
        if m_type:
            #TODO: implement algorithm for museum ids retrieval based on museum type for reward choice
            pass
        #Wait random amount of time to prevent and reduce collisions (avoid transaction usage)
        ran_wait = random()*2
        sleep(ran_wait)
        AppLogger.debug(f"Assign query to mongo {query}")
        serialized_rewards = find(query,limit=20)
        if not serialized_rewards:
            return build_error("No reward found",410)
        AppLogger.debug(f"Found assignable rewards {serialized_rewards}")
        stocks = [s["stock"] for s in serialized_rewards]
        tot_rew = sum(stocks)
        chosen_reward = choices(serialized_rewards,weights=[s/tot_rew for s in stocks],k=limit)[0]
        AppLogger.debug(f"Chosen reward: {chosen_reward}")
        # chosen_reward["expiration_date"] = datetime.fromisoformat(chosen_reward["expiration_date"])
        # chosen_reward["created_at"] = datetime.fromisoformat(chosen_reward["created_at"])
        reward = Reward.deserialize(chosen_reward)
        reward.use()
        edit(chosen_reward["_id"],reward.serialize())
        return jsonify({
            'reward': chosen_reward["_id"]
        }), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# PUT - Crea un nuovo reward
@app.route('/<museum_id>', methods=['PUT'])
def create_reward(museum_id):
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'Dati JSON richiesti'}), 400
        
        reward_doc = {
            'subject': data['subject'],
            'description': data['description'],
            'reduction': data['reduction'],
            'stock': data['stock'],
            'museum_id': museum_id,
            'expiration_date': datetime.fromisoformat(data['expiration_date'].rsplit(".",1)[0]),
            'created_at': datetime.now()
        }
        reward = Reward.deserialize(reward_doc)
        ins_id = str(insert(reward).inserted_id)
        return jsonify({
            'message': 'Reward creato con successo',
            'reward': ins_id
        }), 201
    except KeyError:
        return jsonify({'error': "Request body incomplete"}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
# GET - Ottieni rewards del museo
@app.route('/museum/<museum_id>', methods=['GET'])
def get_museum_rewards(museum_id):
    try:
        serialized_rewards = find({"museum_id":museum_id})
        if not serialized_rewards:
            return build_error("Not reward found for the museum",404)
        return jsonify({
            'rewards': serialized_rewards,
            'count': len(serialized_rewards)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# POST - Modifica un reward
@app.route('/<reward_id>', methods=['POST'])
def edit_reward(reward_id):
    try:
        reward_id = reward_id.strip()
        if not reward_id:
            return build_error("Reward id is missing",400)
        reward = get(reward_id)
        if not reward:
            return build_error("Reward not found",404)
        data = request.get_json()
        if not data:
            return jsonify({'error': 'Dati JSON richiesti'}), 400
        if "creation_date" in data or "exipiration_date" in data:
            return build_error("Cannot alter creation or expiration date",400)
        reward.update(data)
        # reward["expiration_date"] = datetime.fromisoformat(data['expiration_date'].rsplit(".",1)[0])
        # reward["created_at"] = datetime.fromisoformat(reward["created_at"])
        AppLogger.debug(f"Reward dict edited: {reward}")
        try:
            Reward.deserialize(reward)
        except ValueError as e:
            AppLogger.warning(str(e))
            return build_error(str(e),406)
        except TimeoutError:
            AppLogger.warning(str(e))
            return build_error(str(e),403)
        except Exception as e:
            ex = str(e)
            if ex.startswith("Data not suitable"):
                AppLogger.warning(ex)
                return build_error(str(e),400)
            AppLogger.critical(e)
            return build_error('Internal server error',500)
        edit(reward_id,reward)
        return "", 204
    except Exception as e:
        return jsonify({
            'message': 'Internal server error'
        }), 500
    
@app.route('/<reward_id>', methods=['GET'])
def getrewardbyid(reward_id):
    try:
        reward = get(reward_id)
        if not reward:
            return jsonify({
                'message': 'Reward non trovata'
            }), 404
        return jsonify({
            'reward': reward
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'reward: {str(e)}'
        }), 500
    
@app.route('/<reward_id>', methods=['DELETE'])
def deleterewardbyid(reward_id):
    try:
        res = delete(reward_id)
        return jsonify({
            'status': 'success'
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'reward: {str(e)}'
        }), 500

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