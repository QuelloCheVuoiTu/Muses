from flask import Flask, request, jsonify
from pymongo import MongoClient
from bson import ObjectId
import json
import os

app = Flask(__name__)

# Configurazione MongoDB
MONGO_HOST = os.getenv('MONGO_HOST', 'ollama-gemma')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'opere_db')
MONGO_URI = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}"

client = MongoClient(MONGO_URI)
db = client[MONGO_DB]
collection = db.opere

# Converter personalizzato per ObjectId
class JSONEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, ObjectId):
            return str(obj)
        return super().default(obj)

app.json_encoder = JSONEncoder

def validate_opera_data(data):
    """Valida i dati dell'opera secondo il formato richiesto"""
    required_fields = ['name', 'description', 'imageurl', 'museum', 'type']
    
    for field in required_fields:
        if field not in data:
            return False, f"Campo '{field}' mancante"
    
    if not isinstance(data['name'], str) or not data['name'].strip():
        return False, "Il nome dell'opera deve essere una stringa non vuota"
    
    if not isinstance(data['description'], str) or not data['description'].strip():
        return False, "La descrizione deve essere una stringa non vuota"
    
    if not isinstance(data['imageurl'], str) or not data['imageurl'].strip():
        return False, "L'URL dell'immagine deve essere una stringa non vuota"
    
    if not isinstance(data['museum'], str) or not data['museum'].strip():
        return False, "Il museo deve essere una stringa non vuota"
    
    if not isinstance(data['type'], str) or not data['type'].strip():
        return False, "Il tipo dell'opera deve essere una stringa non vuota"
    
    return True, "OK"

def serialize_opera(opera):
    """Serializza un'opera convertendo ObjectId in stringa"""
    if opera:
        opera['_id'] = str(opera['_id'])
    return opera

# GET ALL - Ottieni tutte le opere
@app.route('/getopere', methods=['GET'])
def get_all_opere():
    try:
        # Parametri di query per filtraggio
        museum_filter = request.args.get('museum')
        type_filter = request.args.get('type')
        name_search = request.args.get('name')
        
        # Costruzione della query
        query = {}
        if museum_filter:
            query['museum'] = {'$regex': museum_filter, '$options': 'i'}
        if type_filter:
            query['type'] = {'$regex': type_filter, '$options': 'i'}
        if name_search:
            query['name'] = {'$regex': name_search, '$options': 'i'}
        
        opere = list(collection.find(query))
        for opera in opere:
            opera = serialize_opera(opera)
        return jsonify({
            'status': 'success',
            'data': opere,
            'count': len(opere)
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero delle opere: {str(e)}'
        }), 500

# GET BY ID - Ottieni un'opera specifica
@app.route('/getopera/<opera_id>', methods=['GET'])
def get_opera_by_id(opera_id):
    try:
        # Verifica che l'ID sia un ObjectId valido
        if not ObjectId.is_valid(opera_id):
            return jsonify({
                'status': 'error',
                'message': 'ID non valido'
            }), 400
        
        opera = collection.find_one({'_id': ObjectId(opera_id)})
        
        if not opera:
            return jsonify({
                'status': 'error',
                'message': 'Opera non trovata'
            }), 404
        
        opera = serialize_opera(opera)
        return jsonify({
            'status': 'success',
            'data': opera
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dell\'opera: {str(e)}'
        }), 500
    
# GET - Ottieni tutte le opere per un tipo specifico
@app.route('/getoperebytype/<type_name>', methods=['GET'])
def get_opere_by_type(type_name):
    try:
        # Parametro opzionale per filtrare anche per museo
        museum_filter = request.args.get('museum')
        
        query = {'type': {'$regex': type_name, '$options': 'i'}}
        if museum_filter:
            query['museum'] = {'$regex': museum_filter, '$options': 'i'}
        
        opere = list(collection.find(query))
        for opera in opere:
            opera = serialize_opera(opera)
        
        return jsonify({
            'status': 'success',
            'data': opere,
            'count': len(opere),
            'type': type_name
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero delle opere per il tipo: {str(e)}'
        }), 500
    
# GET BY NAME SEARCH - Ricerca opere per nome (ricerca parziale)
@app.route('/getoperabyname/<search_term>', methods=['GET'])
def search_opere_by_name(search_term):
    try:
        # Parametri opzionali per filtraggio aggiuntivo
        museum_filter = request.args.get('museum')
        type_filter = request.args.get('type')
        author_filter = request.args.get('author')
        limit = request.args.get('limit', 10)
        
        # Validazione parametri
        try:
            limit = int(limit)
            if limit <= 0:
                limit = 10
        except ValueError:
            limit = 10
        
        search_term = search_term.strip()
        
        if not search_term:
            return jsonify({
                'status': 'error',
                'message': 'Termine di ricerca richiesto'
            }), 400
        
        # Query base per la ricerca nel nome
        query = {'name': {'$regex': search_term, '$options': 'i'}}
        
        # Aggiunta filtri opzionali
        if museum_filter:
            query['museum'] = {'$regex': museum_filter, '$options': 'i'}
        if type_filter:
            query['type'] = {'$regex': type_filter, '$options': 'i'}
        if author_filter:
            query['author'] = {'$regex': author_filter, '$options': 'i'}
        
        # Recupero dati con limite
        opere = list(collection.find(query).limit(limit))
        for opera in opere:
            opera = serialize_opera(opera)
        
        # Conta totale risultati
        total_count = collection.count_documents(query)
        
        return jsonify({
            'status': 'success',
            'data': opere,
            'count': len(opere),
            'total_found': total_count,
            'search_term': search_term
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nella ricerca: {str(e)}'
        }), 500

# POST - Crea una nuova opera
@app.route('/addopera', methods=['POST'])
def create_opera():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                'status': 'error',
                'message': 'Nessun dato fornito'
            }), 400
        
        # Validazione dei dati
        is_valid, message = validate_opera_data(data)
        if not is_valid:
            return jsonify({
                'status': 'error',
                'message': message
            }), 400
        
        # Crea l'opera
        opera = {
            'name': data['name'].strip(),
            'description': data['description'].strip(),
            'imageurl': data['imageurl'].strip(),
            'museum': data['museum'].strip(),
            'type': data['type'].strip()
        }
        
        result = collection.insert_one(opera)
        opera['_id'] = str(result.inserted_id)
        
        return jsonify({
            'status': 'success',
            'message': 'Opera creata con successo',
            'data': opera
        }), 201
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nella creazione dell\'opera: {str(e)}'
        }), 500

# PUT - Aggiorna un'opera esistente
@app.route('/modifyopera/<opera_id>', methods=['PUT'])
def update_opera(opera_id):
    try:
        # Verifica che l'ID sia un ObjectId valido
        if not ObjectId.is_valid(opera_id):
            return jsonify({
                'status': 'error',
                'message': 'ID non valido'
            }), 400
        
        data = request.get_json()
        
        if not data:
            return jsonify({
                'status': 'error',
                'message': 'Nessun dato fornito'
            }), 400
        
        # Validazione dei dati
        is_valid, message = validate_opera_data(data)
        if not is_valid:
            return jsonify({
                'status': 'error',
                'message': message
            }), 400
        
        # Verifica che l'opera esista
        existing_opera = collection.find_one({'_id': ObjectId(opera_id)})
        if not existing_opera:
            return jsonify({
                'status': 'error',
                'message': 'Opera non trovata'
            }), 404
        
        # Aggiorna l'opera
        updated_opera = {
            'name': data['name'].strip(),
            'description': data['description'].strip(),
            'imageurl': data['imageurl'].strip(),
            'museum': data['museum'].strip(),
            'type': data['type'].strip()
        }
        
        result = collection.update_one(
            {'_id': ObjectId(opera_id)},
            {'$set': updated_opera}
        )
        
        if result.modified_count == 0:
            return jsonify({
                'status': 'error',
                'message': 'Nessuna modifica effettuata'
            }), 400
        
        # Recupera l'opera aggiornata
        updated_opera = collection.find_one({'_id': ObjectId(opera_id)})
        updated_opera = serialize_opera(updated_opera)
        
        return jsonify({
            'status': 'success',
            'message': 'Opera aggiornata con successo',
            'data': updated_opera
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nell\'aggiornamento dell\'opera: {str(e)}'
        }), 500

# DELETE - Elimina un'opera
@app.route('/deleteopera/<opera_id>', methods=['DELETE'])
def delete_opera(opera_id):
    try:
        # Verifica che l'ID sia un ObjectId valido
        if not ObjectId.is_valid(opera_id):
            return jsonify({
                'status': 'error',
                'message': 'ID non valido'
            }), 400
        
        # Verifica che l'opera esista
        existing_opera = collection.find_one({'_id': ObjectId(opera_id)})
        if not existing_opera:
            return jsonify({
                'status': 'error',
                'message': 'Opera non trovata'
            }), 404
        
        # Elimina l'opera
        result = collection.delete_one({'_id': ObjectId(opera_id)})
        
        if result.deleted_count == 0:
            return jsonify({
                'status': 'error',
                'message': 'Errore nell\'eliminazione dell\'opera'
            }), 500
        
        return jsonify({
            'status': 'success',
            'message': 'Opera eliminata con successo'
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nell\'eliminazione dell\'opera: {str(e)}'
        }), 500

# GET - Ottieni tutti i tipi di opere distinti
@app.route('/gettypes', methods=['GET'])
def get_opera_types():
    try:
        types = collection.distinct('type')
        return jsonify({
            'status': 'success',
            'data': sorted(types),
            'count': len(types)
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero dei tipi: {str(e)}'
        }), 500

# GET - Ottieni tutte le opere per un museo specifico
@app.route('/getoperebymuseum/<museum_name>', methods=['GET'])
def get_opere_by_museum(museum_name):
    try:
        # Parametro opzionale per filtrare anche per tipo
        type_filter = request.args.get('type')
        
        query = {'museum': {'$regex': museum_name, '$options': 'i'}}
        if type_filter:
            query['type'] = {'$regex': type_filter, '$options': 'i'}
        
        opere = list(collection.find(query))
        for opera in opere:
            opera = serialize_opera(opera)
        
        return jsonify({
            'status': 'success',
            'data': opere,
            'count': len(opere),
            'museum': museum_name
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore nel recupero delle opere per il museo: {str(e)}'
        }), 500

# Route di health check
@app.route('/health', methods=['GET'])
def health_check():
    try:
        # Testa la connessione al database
        client.admin.command('ping')
        return jsonify({
            'status': 'success',
            'message': 'Applicazione e database funzionanti',
            'database': 'opere_db',
            'collection': 'opere'
        }), 200
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': f'Errore di connessione al database: {str(e)}'
        }), 500

# Gestione errori globali
@app.errorhandler(404)
def not_found(error):
    return jsonify({
        'status': 'error',
        'message': 'Endpoint non trovato'
    }), 404

@app.errorhandler(405)
def method_not_allowed(error):
    return jsonify({
        'status': 'error',
        'message': 'Metodo non consentito'
    }), 405

@app.errorhandler(500)
def internal_error(error):
    return jsonify({
        'status': 'error',
        'message': 'Errore interno del server'
    }), 500

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=4000)