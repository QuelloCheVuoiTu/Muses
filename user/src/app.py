from flask import Flask, request, jsonify
from data_source import setup_db,find_users,DEBUG_MODE,add_user,USER_ROLE,AUTH_SVC_NAME,delete_user
from logging import getLogger,DEBUG,WARNING,FileHandler
import requests as rq

AUTH_SVC_URI = f"http://{AUTH_SVC_NAME}"
setup_db()
app = Flask(__name__)

# Configurazione logging
logger = getLogger()
logger.setLevel(DEBUG if DEBUG_MODE == "true" else WARNING)
logger.addHandler(FileHandler("/var/log/svc_logs.log"))

@app.route('/health', methods=['GET'])
def health_check():
    """Endpoint per verificare lo stato dell'applicazione"""
    return jsonify({"status": "healthy", "message": "App is running"}), 200

@app.route('/', methods=['POST'])
def add_sys_user():
    """Aggiunge un nuovo utente al database"""
    try:
        # Ottieni i dati dal request JSON
        data = request.get_json()
        
        # Validazione dei campi richiesti
        if not data:
            return jsonify({"error": "Nessun dato fornito"}), 400
        
        required_fields = ['username', 'password', 'mail']
        for field in required_fields:
            if field not in data:
                return jsonify({"error": f"Campo '{field}' mancante"}), 400
            if not data[field]:
                return jsonify({"error": f"Campo '{field}' non può essere vuoto"}), 400
        
        # Controlla se l'utente esiste già
        existing_user = find_users({
            "$or": [
                {"username": data['username']},
                {"mail": data['mail']}
            ]
        })
        
        if existing_user:
            return jsonify({"error": "Username o email già esistenti"}), 409
        
        # Crea l'oggetto utente
        user = {
            "username": data['username'],
            "password": data['password'],
            "mail": data['mail']
        }
        
        # Inserisci l'utente nel database
        result = add_user(user)
        link_data = {
            "role": USER_ROLE,
            "username": data["username"],
            "external_id":str(result.inserted_id)
        }
        resp = rq.post(url=f"{AUTH_SVC_URI}/linkentity",json=link_data,headers={
            "Content-Type":"application/json"
        })
        logger.debug(f"Auth response: {resp.text}")
        if not resp.ok:
            delete_user(result.inserted_id)
            return jsonify(
                {"message": "Registration encountered a problem"}
            ),503
        # Prepara la risposta
        response = {
            "message": "Utente aggiunto con successo",
            "user_id": str(result.inserted_id),
            "user": {
                "username": user['username'],
                "mail": user['mail']
            }
        }
        
        logger.info(f"Utente aggiunto: {user['username']}")
        return jsonify(response), 201
        
    except Exception as e:
        logger.error(f"Errore nell'aggiunta dell'utente: {e}")
        return jsonify({"error": "Errore interno del server"}), 500

@app.route('/', methods=['GET'])
def get_all_users():
    """Recupera tutti gli utenti dal database"""
    try:
        # Recupera tutti gli utenti dal database
        users_cursor = find_users({})
        
        # Converte i risultati in una lista
        users = []
        for user in users_cursor:
            users.append({
                "id": str(user['_id']),
                "username": user['username'],
                "password": user['password'],
                "mail": user['mail']
            })
        
        response = {
            "message": "Utenti recuperati con successo",
            "total_users": len(users),
            "users": users
        }
        
        logger.info(f"Recuperati {len(users)} utenti")
        return jsonify(response), 200
        
    except Exception as e:
        logger.error(f"Errore nel recupero degli utenti: {e}")
        return jsonify({"error": "Errore interno del server"}), 500

@app.errorhandler(404)
def not_found(error):
    return jsonify({"error": "Endpoint non trovato"}), 404

@app.errorhandler(405)
def method_not_allowed(error):
    return jsonify({"error": "Metodo non consentito"}), 405

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=2000, debug=True)