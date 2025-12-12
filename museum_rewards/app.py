from flask import Flask, request, jsonify
from flask_pymongo import PyMongo,MongoClient
from bson import ObjectId
import requests
from datetime import datetime
from typing import Union
import os
from helpers import validate_reward_data, REWARD_SCHEMA,validata_data_types
import json
#NEED A CRONJOB FOR EXPIRED REWAEDS CLEANUP
app = Flask(__name__)

# Configurazione MongoDB - supporta sia localhost che Docker
MONGO_HOST = os.getenv('MONGO_HOST', 'ollama-gemma')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'rewards_db')
MUSEO_SVC_NAME = os.getenv('MUSEO_SVC_NAME',"museo")


app.config["MONGO_URI"] = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}"
mongo = PyMongo(app)

# Collezione musei
rewards = mongo.db.rewards

# Helper function per convertire ObjectId in string
def serialize_reward(reward):
    if reward:
        reward['_id'] = str(reward['_id'])
        return reward
    return None

def ping_db() -> bool:
	"""
	Ping the MongoDB server to check readiness.
	Returns True if the ping is successful, False otherwise.
	"""
	# If the `collection` collection hasn't been initialized, we can't reach the DB.
	if rewards is None:
		print("ping_db: collection collection not initialized")
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
			print("ping_db: ping returned non-ok result: %s", res)
		return ok
	except Exception as e:
		# Any exception (timeout, connection error, etc.) is treated as a failed ping
		print("ping_db: exception while pinging db with temp client: %s", e)
		return False
	finally:
		if temp_client:
			try:
				temp_client.close()
			except Exception:
				pass


# GET ALL - Recupera tutti i musei
@app.route('/', methods=['GET'])
def get_all_rewards():
    try:
        # ['subject','description', 'used','expiration_date','data']
        # Parametri di query per filtraggio
        subject = request.args.get('subject')
        description = request.args.get('description')
        data = request.args.get('data')
        expiration_date = request.args.get('expiration_date')
        limit = request.args.get('limit',type=int,default=1)

        # Costruzione della query
        query = {}
        if subject:
            query['subject'] = {'$regex': subject, '$options': 'i'}
        if description:
            query["description"] = description
        if data:
            query['data'] = data
        if expiration_date:
            query["expiration_date"] = expiration_date
        # Recupero dati ,limit=limit
        reward_list = list(rewards.find(query))
        
        # Serializzazione
        serialized_rewards = [serialize_reward(reward) for reward in reward_list]
        
        return jsonify({
            'rewards': serialized_rewards,
            'count': len(serialized_rewards)
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
        
        # Validazione dati
        is_valid, error_msg = validate_reward_data(data)
        if not is_valid and error_msg!= "Campo obbligatorio mancante: museum_id":
            return jsonify({'error': error_msg}), 400
        
        # Preparazione documento
        reward_doc = {
            'subject': data['subject'],
            'description': data['description'],
            'data': data['data'],
            'stock': data['stock'],
            'museum_id': museum_id,
            'expiration_date': datetime.fromisoformat(data['expiration_date'].rsplit(".",1)[0]),
            'created_at': datetime.now().isoformat()
        }
        
        # Inserimento nel database
        result = rewards.insert_one(reward_doc)
        
        # Recupero del documento creato
        created_reward = rewards.find_one({'_id': result.inserted_id})
        
        return jsonify({
            'message': 'Reward creato con successo',
            'reward': serialize_reward(created_reward)
        }), 201
    except KeyError:
        return jsonify({'error': "Request body incomplete"}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
def _get_museum_rewards(museum_id) -> list:
    # Costruzione della query
    query = dict(museum_id=museum_id)
    # Recupero dati
    reward_list = list(rewards.find(query))
    # Serializzazione
    return [serialize_reward(reward) for reward in reward_list]

# GET - Ottieni rewards del museo
@app.route('/museum/<museum_id>', methods=['GET'])
def get_museum_rewards(museum_id):
    try:
        serialized_rewards = _get_museum_rewards(museum_id)
        
        return jsonify({
            'rewards': serialized_rewards,
            'count': len(serialized_rewards)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    
# GET - Ottieni rewards dai musei di un tipo
@app.route('/type/<type>', methods=['GET'])
def get_rewards_by_type(type):
    try:
        # Interrogo servizio di museo
        resp = requests.get(f"http://{MUSEO_SVC_NAME}/getmuseumsbytype/{type}")
        content = resp.content.decode("utf-8")
        try:
            content:dict = json.loads(content)
        except Exception:
            raise Exception("Internal communication error with museum service")
        museums = content.get("museums",[])
        
        rewards = []
        for museum in museums:
            rewards.extend(_get_museum_rewards(museum["_id"]))
        
        return jsonify({
            'rewards': rewards,
            'count': len(rewards)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/use/<reward_id>', methods=['POST'])
def edit_reward(reward_id):
    try:
        reward = list(rewards.find_one({'_id': ObjectId(reward_id)}))
        if not reward:
            return jsonify({
                'status': 'error',
                'message': 'Reward non trovata'
            }), 404
        
        reward = serialize_reward(reward[0])
        stock = reward['stock']
        if isinstance(stock,int) and stock > 0:
            reward['stock']-=1
        elif stock <1:
            return jsonify({
            'message':"Stock terminated for this reward"
        }), 400

        return jsonify({
            'status': 'success',
            'data': reward
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero del reward: {str(e)}'
        }), 500
    
# POST - Modifica un reward
@app.route('/<reward_id>', methods=['POST'])
def edit_reward(reward_id):
    try:
        data = request.get_json()
        validata_data_types(data)
        if not data:
            return jsonify({'error': 'Dati JSON richiesti'}), 400
        # Verifica che l'ID sia un ObjectId valido
        if not ObjectId.is_valid(reward_id):
            return jsonify({
                'status': 'error',
                'message': 'ID non valido'
            }), 400
        reward = rewards.find_one({'_id': ObjectId(reward_id)})
        reward.update(data)
        validate_reward_data(reward)
        if not reward:
            return jsonify({
                'status': 'error',
                'message': 'Reward non trovata'
            }), 404
        
        reward = serialize_reward(reward)
        return jsonify({
            'status': 'success',
            'data': reward
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'reward: {str(e)}'
        }), 500
    
@app.route('/<reward_id>', methods=['GET'])
def getrewardbyid(reward_id):
    try:
        reward = list(rewards.find_one({'_id': ObjectId(reward_id)}))
        if not reward:
            return jsonify({
                'status': 'error',
                'message': 'Reward non trovata'
            }), 404
        
        reward = serialize_reward(reward[0])

        return jsonify({
            'status': 'success',
            'data': reward
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'reward: {str(e)}'
        }), 500
    
@app.route('/<reward_id>', methods=['DELETE'])
def deleterewardbyid(reward_id):
    try:
        reward = rewards.delete_one({'_id': ObjectId(reward_id)})
        if not reward:
            return jsonify({
                'status': 'error',
                'message': 'Reward non trovata'
            }), 404        
        return jsonify({
            'status': 'success'
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'reward: {str(e)}'
        }), 500
    
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
			return jsonify({"message": "Museum reward service is ready"}), 200
		print("Readiness check failed: cannot reach database")
		return jsonify({"message": "Museum reward service not ready: cannot reach database"}), 503
	except Exception as e:
		print("Readiness check error: %s", e)
		return jsonify({"message": "Museum reward service not ready"}), 503

# Gestione errori globali
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint non trovato'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Errore interno del server'}), 500

if __name__ == '__main__':
    
    app.run(debug=True, host='0.0.0.0', port=8000)