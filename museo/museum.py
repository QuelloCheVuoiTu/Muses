from flask import Flask, request, jsonify
from flask_pymongo import PyMongo
from bson import ObjectId
from datetime import datetime
import os

app = Flask(__name__)

# Configurazione MongoDB - supporta sia localhost che Docker
MONGO_HOST = os.getenv('MONGO_HOST', 'ollama-gemma')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'museums_db')

app.config["MONGO_URI"] = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/{MONGO_DB}"
mongo = PyMongo(app)

# Collezione musei
museums = mongo.db.museums

# Helper function per convertire ObjectId in string
def serialize_museum(museum):
    if museum:
        museum['_id'] = str(museum['_id'])
        return museum
    return None

# Validation helper
def validate_museum_data(data):
    required_fields = ['name', 'description', 'location', 'hours', 'price', 'rating', 'imageurl', 'type']
    
    for field in required_fields:
        if field not in data:
            return False, f"Campo obbligatorio mancante: {field}"
    
    # Validazione location
    if 'location' in data:
        location = data['location']
        if not isinstance(location, dict) or 'longitude' not in location or 'latitude' not in location:
            return False, "Location deve contenere longitude e latitude"
        
        try:
            float(location['longitude'])
            float(location['latitude'])
        except (ValueError, TypeError):
            return False, "Longitude e latitude devono essere numeri validi"
    
    # Validazione rating
    try:
        rating = float(data['rating'])
        if not (0 <= rating <= 5):
            return False, "Rating deve essere tra 0 e 5"
    except (ValueError, TypeError):
        return False, "Rating deve essere un numero valido"
    
    # Validazione type
    if 'type' in data:
        if not isinstance(data['type'], str) or not data['type'].strip():
            return False, "Type deve essere una stringa non vuota"
    
    return True, ""

# GET ALL - Recupera tutti i musei
@app.route('/getmuseums', methods=['GET'])
def get_all_museums():
    try:
        # Parametri di query per filtraggio
        parent = request.args.get('parent')
        rating_min = request.args.get('rating_min')
        name_search = request.args.get('name')
        type_filter = request.args.get('type')
        
        # Costruzione della query
        query = {}
        if parent:
            query['parent'] = parent
        if rating_min:
            try:
                query['rating'] = {'$gte': float(rating_min)}
            except ValueError:
                return jsonify({'error': 'rating_min deve essere un numero valido'}), 400
        if name_search:
            query['name'] = {'$regex': name_search, '$options': 'i'}
        if type_filter:
            query['type'] = {'$regex': type_filter, '$options': 'i'}
        
        # Recupero dati
        museum_list = list(museums.find(query))
        
        # Serializzazione
        serialized_museums = [serialize_museum(museum) for museum in museum_list]
        
        return jsonify({
            'museums': serialized_museums,
            'count': len(serialized_museums)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# GET BY ID - Recupera un museo specifico
@app.route('/getmuseum/<museum_id>', methods=['GET'])
def get_museum(museum_id):
    try:
        # Validazione ObjectId
        if not ObjectId.is_valid(museum_id):
            return jsonify({'error': 'ID museo non valido'}), 400
        
        museum = museums.find_one({'_id': ObjectId(museum_id)})
        
        if not museum:
            return jsonify({'error': 'Museo non trovato'}), 404
        
        return jsonify(serialize_museum(museum)), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# GET - Ottieni tutti i musei per un tipo specifico
@app.route('/getmuseumsbytype/<type_name>', methods=['GET'])
def get_museums_by_type(type_name):
    try:
        # Parametri opzionali per filtraggio aggiuntivo
        rating_min = request.args.get('rating_min')
        name_search = request.args.get('name')
        parent = request.args.get('parent')
        
        # Query base per il tipo
        query = {'type': {'$regex': type_name, '$options': 'i'}}
        
        # Aggiunta filtri opzionali
        if rating_min:
            try:
                query['rating'] = {'$gte': float(rating_min)}
            except ValueError:
                return jsonify({'error': 'rating_min deve essere un numero valido'}), 400
        
        if name_search:
            query['name'] = {'$regex': name_search, '$options': 'i'}
        
        if parent:
            query['parent'] = parent
        
        # Recupero dati
        museum_list = list(museums.find(query))
        
        # Serializzazione
        serialized_museums = [serialize_museum(museum) for museum in museum_list]
        
        return jsonify({
            'museums': serialized_museums,
            'count': len(serialized_museums),
            'type': type_name
        }), 200
        
    except Exception as e:
        return jsonify({'error': f'Errore nel recupero dei musei per il tipo: {str(e)}'}), 500
    
# GET BY NAME PARTIAL - Recupera musei con nome che contiene la stringa specificata
@app.route('/getmuseumbyname/<search_term>', methods=['GET'])
def search_museums_by_name(search_term):
    try:
        # Parametri opzionali per filtraggio aggiuntivo
        rating_min = request.args.get('rating_min')
        type_filter = request.args.get('type')
        parent = request.args.get('parent')
        limit = request.args.get('limit', 10)  # Limite default di 10 risultati
        
        # Validazione parametri
        try:
            limit = int(limit)
            if limit <= 0:
                limit = 10
        except ValueError:
            limit = 10
        
        search_term = search_term.strip()
        
        if not search_term:
            return jsonify({'error': 'Termine di ricerca richiesto'}), 400
        
        # Query base per la ricerca nel nome
        query = {'name': {'$regex': search_term, '$options': 'i'}}
        
        # Aggiunta filtri opzionali
        if rating_min:
            try:
                query['rating'] = {'$gte': float(rating_min)}
            except ValueError:
                return jsonify({'error': 'rating_min deve essere un numero valido'}), 400
        
        if type_filter:
            query['type'] = {'$regex': type_filter, '$options': 'i'}
        
        if parent:
            query['parent'] = parent
        
        # Recupero dati con limite
        museum_list = list(museums.find(query).limit(limit))
        
        # Serializzazione
        serialized_museums = [serialize_museum(museum) for museum in museum_list]
        
        return jsonify({
            'museums': serialized_museums,
            'count': len(serialized_museums),
            'search_term': search_term,
            'total_found': museums.count_documents(query)
        }), 200
        
    except Exception as e:
        return jsonify({'error': f'Errore nella ricerca: {str(e)}'}), 500

# POST - Crea un nuovo museo
@app.route('/addmuseum', methods=['POST'])
def create_museum():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'Dati JSON richiesti'}), 400
        
        # Validazione dati
        is_valid, error_msg = validate_museum_data(data)
        if not is_valid:
            return jsonify({'error': error_msg}), 400
        
        # Preparazione documento
        museum_doc = {
            'name': data['name'],
            'description': data['description'],
            'location': {
                'longitude': float(data['location']['longitude']),
                'latitude': float(data['location']['latitude'])
            },
            'hours': data['hours'],
            'price': data['price'],
            'rating': float(data['rating']),
            'imageurl': data['imageurl'],
            'type': data['type'].strip(),
            'created_at': datetime.utcnow()
        }
        
        # Aggiunta campo parent se presente
        if 'parent' in data and data['parent']:
            museum_doc['parent'] = data['parent']
        
        # Inserimento nel database
        result = museums.insert_one(museum_doc)
        
        # Recupero del documento creato
        created_museum = museums.find_one({'_id': result.inserted_id})
        
        return jsonify({
            'message': 'Museo creato con successo',
            'museum': serialize_museum(created_museum)
        }), 201
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# PUT - Aggiorna un museo esistente
@app.route('/modifymuseum/<museum_id>', methods=['PUT'])
def update_museum(museum_id):
    try:
        # Validazione ObjectId
        if not ObjectId.is_valid(museum_id):
            return jsonify({'error': 'ID museo non valido'}), 400
        
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'Dati JSON richiesti'}), 400
        
        # Verifica esistenza museo
        existing_museum = museums.find_one({'_id': ObjectId(museum_id)})
        if not existing_museum:
            return jsonify({'error': 'Museo non trovato'}), 404
        
        # Validazione dati
        is_valid, error_msg = validate_museum_data(data)
        if not is_valid:
            return jsonify({'error': error_msg}), 400
        
        # Preparazione aggiornamento
        update_doc = {
            'name': data['name'],
            'description': data['description'],
            'location': {
                'longitude': float(data['location']['longitude']),
                'latitude': float(data['location']['latitude'])
            },
            'hours': data['hours'],
            'price': data['price'],
            'rating': float(data['rating']),
            'imageurl': data['imageurl'],
            'type': data['type'].strip(),
            'updated_at': datetime.utcnow()
        }
        
        # Gestione campo parent
        if 'parent' in data and data['parent']:
            update_doc['parent'] = data['parent']
        else:
            # Rimozione campo parent se non presente nei nuovi dati
            museums.update_one(
                {'_id': ObjectId(museum_id)},
                {'$unset': {'parent': ""}}
            )
        
        # Aggiornamento nel database
        museums.update_one(
            {'_id': ObjectId(museum_id)},
            {'$set': update_doc}
        )
        
        # Recupero documento aggiornato
        updated_museum = museums.find_one({'_id': ObjectId(museum_id)})
        
        return jsonify({
            'message': 'Museo aggiornato con successo',
            'museum': serialize_museum(updated_museum)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# DELETE - Elimina un museo
@app.route('/deletemuseum/<museum_id>', methods=['DELETE'])
def delete_museum(museum_id):
    try:
        # Validazione ObjectId
        if not ObjectId.is_valid(museum_id):
            return jsonify({'error': 'ID museo non valido'}), 400
        
        # Verifica esistenza museo
        existing_museum = museums.find_one({'_id': ObjectId(museum_id)})
        if not existing_museum:
            return jsonify({'error': 'Museo non trovato'}), 404
        
        # Eliminazione
        result = museums.delete_one({'_id': ObjectId(museum_id)})
        
        if result.deleted_count == 1:
            return jsonify({'message': 'Museo eliminato con successo'}), 200
        else:
            return jsonify({'error': 'Errore durante l\'eliminazione'}), 500
            
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Endpoint per recuperare tutti i tipi di arte disponibili
@app.route('/gettypes', methods=['GET'])
def get_art_types():
    try:
        # Recupera tutti i tipi distinti di arte
        types = museums.distinct('type')
        return jsonify({
            'types': sorted(types),
            'count': len(types)
        }), 200
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# Endpoint per verificare lo stato dell'API
@app.route('/health', methods=['GET'])
def health_check():
    try:
        # Test connessione database
        mongo.db.command('ping')
        db_status = 'Connected'
    except Exception as e:
        db_status = f'Disconnected: {str(e)}'
    
    return jsonify({
        'status': 'OK',
        'timestamp': datetime.utcnow().isoformat(),
        'database': db_status,
        'mongo_uri': app.config["MONGO_URI"]
    }), 200

# Gestione errori globali
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint non trovato'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Errore interno del server'}), 500

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8000)