from flask import Flask, request, jsonify
from pymongo import MongoClient
from bson import ObjectId
import json
import os
from datetime import datetime

app = Flask(__name__)

# Configurazione MongoDB
MONGO_HOST = os.getenv('MONGO_HOST', 'ollama-gemma')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'user_preferences')
MONGO_URI = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}"
DB_NAME = 'user_preferences'
COLLECTION_NAME = 'preferences'

# Connessione a MongoDB
try:
    client = MongoClient(MONGO_URI)
    db = client[DB_NAME]
    collection = db[COLLECTION_NAME]
    print(f"Connesso a MongoDB: {MONGO_URI}")
except Exception as e:
    print(f"Errore connessione MongoDB: {e}")
    client = None

class JSONEncoder(json.JSONEncoder):
    """Encoder personalizzato per gestire ObjectId di MongoDB"""
    def default(self, obj):
        if isinstance(obj, ObjectId):
            return str(obj)
        return super().default(obj)

app.json_encoder = JSONEncoder

@app.route('/health', methods=['GET'])
def health_check():
    """Endpoint per verificare lo stato dell'applicazione"""
    try:
        # Test connessione MongoDB
        client.admin.command('ping')
        db_status = "connected"
    except Exception as e:
        db_status = f"error: {str(e)}"
    
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "database": db_status
    })

@app.route('/addpreference', methods=['POST'])
def save_preference():
    """Salva una nuova preferenza utente"""
    try:
        # Verifica che la richiesta contenga JSON
        if not request.is_json:
            return jsonify({"error": "Content-Type deve essere application/json"}), 400
        
        data = request.get_json()
        
        # Validazione campi obbligatori
        if not data:
            return jsonify({"error": "Dati JSON non validi"}), 400
        
        if 'iduser' not in data:
            return jsonify({"error": "Campo 'iduser' obbligatorio"}), 400
        
        if 'preferenza' not in data:
            return jsonify({"error": "Campo 'preferenza' obbligatorio"}), 400
        
        # Validazione che i campi non siano vuoti
        if not data['iduser'] or not data['preferenza']:
            return jsonify({"error": "I campi 'iduser' e 'preferenza' non possono essere vuoti"}), 400
        
        # Creazione documento da salvare
        preference_doc = {
            "iduser": data['iduser'],
            "preferenza": data['preferenza'],
            "timestamp": datetime.now()
        }
        
        # Salvataggio nel database
        result = collection.insert_one(preference_doc)
        
        return jsonify({
            "message": "Preferenza salvata con successo",
            "id": str(result.inserted_id),
            "iduser": data['iduser']
        }), 201
        
    except Exception as e:
        return jsonify({"error": f"Errore interno del server: {str(e)}"}), 500

@app.route('/getpreference/<iduser>', methods=['GET'])
def get_preferences(iduser):
    """Recupera tutte le preferenze di un utente specifico"""
    try:
        # Validazione parametro
        if not iduser:
            return jsonify({"error": "ID utente non valido"}), 400
        
        # Recupero preferenze dal database
        preferences = list(collection.find({"iduser": iduser}))
        
        # Conversione ObjectId in string per la serializzazione JSON
        for pref in preferences:
            pref['_id'] = str(pref['_id'])
            # Conversione timestamp in string se presente
            if 'timestamp' in pref:
                pref['timestamp'] = pref['timestamp'].isoformat()
        
        return jsonify({
            "iduser": iduser,
            "preferences": preferences,
            "count": len(preferences)
        }), 200
        
    except Exception as e:
        return jsonify({"error": f"Errore interno del server: {str(e)}"}), 500

@app.route('/getallpreferences', methods=['GET'])
def get_all_preferences():
    """Recupera tutte le preferenze (utile per debug/admin)"""
    try:
        # Recupero tutte le preferenze
        preferences = list(collection.find())
        
        # Conversione ObjectId in string
        for pref in preferences:
            pref['_id'] = str(pref['_id'])
            if 'timestamp' in pref:
                pref['timestamp'] = pref['timestamp'].isoformat()
        
        return jsonify({
            "count": len(preferences),
            "preferences": preferences
        }), 200
        
    except Exception as e:
        return jsonify({"error": f"Errore interno del server: {str(e)}"}), 500

@app.route('/delpreference/<iduser>', methods=['DELETE'])
def delete_user_preferences(iduser):
    """Elimina tutte le preferenze di un utente specifico"""
    try:
        if not iduser:
            return jsonify({"error": "ID utente non valido"}), 400
        
        # Eliminazione preferenze
        result = collection.delete_many({"iduser": iduser})
        
        return jsonify({
            "message": f"Eliminate {result.deleted_count} preferenze per l'utente {iduser}",
            "deleted_count": result.deleted_count
        }), 200
        
    except Exception as e:
        return jsonify({"error": f"Errore interno del server: {str(e)}"}), 500

@app.errorhandler(404)
def not_found(error):
    return jsonify({"error": "Endpoint non trovato"}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({"error": "Errore interno del server"}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7000, debug=True)