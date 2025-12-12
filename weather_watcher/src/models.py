from dataclasses import dataclass,field
from typing import Literal
from helpers import DictSerializable,SERIALIZABLE_TYPES

@dataclass
class Node(DictSerializable):
    name: str
    ip: str
    longitude: float
    latitude: float

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return self.__dict__

    @staticmethod
    def deserialize(data:dict)->'Node':
        data_model =  {
            "name": str,
            "ip": str,
            "longitude": float,
            "latitude": float

        }
        Node.validate_fields(data_model,data)
        return Node(**data)


# {
        # "nodeId": 117442,
        # "timestamp": "2025-09-17T15:19:47.014966668Z",
        # "value": 0.4,
        # "sensor": {
            # "id": 395487,
            # "minThreshold": 0.195,
            # "maxThreshold": 1.861,
            # "unitOfMeasure": "PERCENT",
            # "type": "HUMIDITY",
            # "nodeId": 117442
        # }
@dataclass
class NodeInfo(DictSerializable):
    node_id:str
    timstamp:str
    value:float
    measures:dict[str,dict[str,any]]


    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return self.__dict__

    @staticmethod
    def deserialize(data:dict)->'Node':
        data_model =  {
            "node_id": str,
            "timstamp": str,
            "value": float,
            "measures": dict

        }
        NodeInfo.validate_fields(data_model,data)
        return NodeInfo(**data)
    
DictSerializable.register(NodeInfo)
DictSerializable.register(Node)
