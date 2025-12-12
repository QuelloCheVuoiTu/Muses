from dataclasses import dataclass,field
from typing import Literal
from helpers import DictSerializable,SERIALIZABLE_TYPES
from typing import get_args
import os
import requests
from random import choice
from datetime import datetime
from typing import Union
REWARD_SVC_NAME = os.getenv('REWARD_SVC_NAME',"museum_rewards")

class OutOfStockException(Exception):
    pass

class RewardNotFoundException(Exception):
    pass

class ExpiredRewardException(Exception):
    pass

class RewardAlreadyUsedException(Exception):
    pass


@dataclass
class ReductionType(DictSerializable):
    REDUCTION_TYPES=["flat","percentage"]
    type: str
    amount: float

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        self.validate_costraints()
        return dict(
            type=self.type,
            amount=self.amount
        )

    @staticmethod
    def deserialize(data:dict)->'ReductionType':
        data_model =  {
            "type": str,
            "amount": Union[float,int]
        }
        ReductionType.validate_fields(data_model,data)
        reduction = ReductionType(**data)
        reduction.validate_costraints()
        return reduction

    def validate_costraints(self):
        if self.amount <= 0:
            raise ValueError("Amount cannot be negative or 0")
        if not self.type in ReductionType.REDUCTION_TYPES:
            raise ValueError("Reduction type can only be percentage or flat")
        if self.type == "percentage" and not (self.amount > 0 and self.amount <=100):
            raise ValueError("Percentage reduction can only amount to valuse between 1 and 100")

@dataclass
class Reward(DictSerializable):
    subject: str
    description: str
    reduction: ReductionType
    stock: int 
    museum_id: str
    expiration_date: datetime
    created_at: datetime

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        self.validate_constraints()
        return self.__dict__

    @staticmethod
    def deserialize(data:dict)->'Reward':
        data = data.copy()
        if "_id" in data:
            del(data["_id"])
        data_model =  {
            "subject": str,
            "description": str,
            "reduction": ReductionType,
            "stock": int, 
            "museum_id": str,
            "expiration_date": datetime,
            "created_at": datetime
        }
        reduction = data.get("reduction")
        if not reduction:
            raise ValueError("Reduction parameter is missing")
        reduction = ReductionType.deserialize(reduction)
        data["reduction"] = reduction
        Reward.validate_fields(data_model,data)
        reward = Reward(**data)
        reward.validate_constraints()
        return reward

    def validate_constraints(self):
        if self.stock < 0:
            raise ValueError("Stock parameter cannot be negative")
        if self.created_at > self.expiration_date:
            raise TimeoutError("Cannot create a reward already expired")
    
    def use(self):
        if self.stock == 0:
            raise OutOfStockException("Reward already used")
        if self.expiration_date < datetime.now():
            raise ExpiredRewardException("Reward expired")
        self.stock -= 1

DictSerializable.register(Reward)
