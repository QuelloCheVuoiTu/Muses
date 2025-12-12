import os
from pymongo import MongoClient
from pymongo.collection import ObjectId
from pymongo.results import InsertOneResult,UpdateResult
from logging import getLogger,DEBUG,WARNING,FileHandler
MONGO_HOST = os.getenv('MONGO_HOST', 'mongo')
MONGO_PORT = os.getenv('MONGO_PORT', '27017')
MONGO_DB = os.getenv('MONGO_DB', 'userdb')
DEBUG_MODE = os.getenv('DEBUG_MODE', 'true')
COLLECTION_NAME = "users"
MONGO_URI = f"mongodb://{MONGO_HOST}:{MONGO_PORT}/"
AUTH_SVC_NAME = os.getenv('AUTH_SVC_NAME', 'auth')
USER_ROLE = os.getenv("USER_ROLE","USER")

logger = getLogger()
logger.setLevel(DEBUG if DEBUG_MODE == "true" else WARNING)
logger.addHandler(FileHandler("/var/log/svc_logs.log"))
users = None

def setup_db():
    global users
    # Connessione MongoDB
    try:
        client = MongoClient(MONGO_URI)
        db = client.get_database(MONGO_DB)
        users = db.get_collection(COLLECTION_NAME)
        logger.info("Connessione a MongoDB stabilita con successo")
    except Exception as e:
        logger.error(f"Errore di connessione a MongoDB: {e}")
        exit(1)

def serialize_obj(obj):
    if obj:
        obj["_id"] = str(obj["_id"])
        return obj
    return None

def add_user(user_data:dict)->InsertOneResult:
    try:
        return users.insert_one(user_data)
    except Exception:
        return None

def edit_user(user_id:str,edit_data:dict)->UpdateResult:
    try:
        return users.update_one({"_id":ObjectId(user_id)},edit_data)
    except Exception:
        return None

def delete_user(user_id:str)->bool:
    try:
        users.delete_one({"_id":ObjectId(user_id)})
        return True
    except Exception:
        return False
    
def find_users(filter:dict)->list:
    return list(map(serialize_obj,users.find(filter)))