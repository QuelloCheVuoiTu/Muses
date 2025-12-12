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
    


DictSerializable.register(Node)
