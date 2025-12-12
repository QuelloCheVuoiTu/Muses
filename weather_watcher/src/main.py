import requests
from data_source import *
from models import *
import os 

RADIUS = os.getenv("RADIUS", 10)
ONLY_LATEST = os.getenv("ONLY_LATEST", "true")

def retrieve_weather_data(ip:str,lat:float,long:float,radius:int,types:list[str],only_latest:bool=True):
    #types=WIND_SPEED&types=HUMIDITY&types=TEMPERATURE&types=CUMULATIVE_RAINFALL
    url = f"http://{ip}/fmms/sens-readings/sensor-readings?lat={lat}&lon={long}&radius={radius}&{'&'.join(types)}&onlyLatest={'true' if only_latest else 'false'}"
    r = requests.get(url=url)
    return r.json()

if __name__ == "__main__":
    setup_db_connection()
    nodes = setup_db_connection_node()
    node_dict:dict[str,Node] = dict() 
    for n in nodes.find({}):
        node_dict[n["_id"]] = Node.deserialize(n)
    for k,node in node_dict.items():
        data = retrieve_weather_data(node.ip,node.latitude,node.longitude,int(RADIUS),types=["WIND_SPEED","HUMIDITY","TEMPERATURE","CUMULATIVE_RAINFALL"],only_latest=ONLY_LATEST=="true")
        edit({})
    