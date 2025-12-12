from dataclasses import dataclass,field
from typing import Literal
from helpers import DictSerializable,SERIALIZABLE_TYPES
from typing import get_args
import os
import requests
from random import choice
from datetime import datetime
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
class Reward(DictSerializable):
    reward_id : str
    user_id : str
    used : bool

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return self.__dict__

    @staticmethod
    def deserialize(data:dict)->'Reward':
        data = data.copy()
        if "_id" in data:
            del(data["_id"])
        data_model =  {
            "reward_id" : str,
            "user_id" : str,
            "used" : bool,
        }
        Reward.validate_fields(data_model,data)
        return Reward(**data)

    def use(self):
        if self.used:
            raise RewardAlreadyUsedException("Reward already used")
        resp = requests.get(f"http://{REWARD_SVC_NAME}/redeem/{self.reward_id}")
        status_code = resp.status_code
        if status_code == 417:
            raise ExpiredRewardException("Reward Expired")
        if not resp.ok:
            raise Exception("Internal Server Error")
        self.used = True

    @staticmethod
    def generate(user_id:str ):
        resp = requests.get(f"http://{REWARD_SVC_NAME}/assign")
        status_code = resp.status_code
        if status_code == 410:
            raise OutOfStockException("Reward is out of stock")
        elif status_code >= 500:
            raise Exception("Internal Server Error")
        body = resp.json()
        reward = body.get("reward")
        if not reward:
            raise Exception("No reward found")   
        return Reward(reward_id=reward,user_id=user_id,used=False)
    
    def retrieve_reward(self)->dict:
        resp = requests.get(f"http://{REWARD_SVC_NAME}/{self.reward_id}")
        status_code = resp.status_code
        if status_code == 404:
            raise FileNotFoundError("Reward Not Found")
        if not resp.ok:
            raise Exception("Internal Server Error")
        rew = resp.json()["reward"]
        ret_dict = dict(
            subject = rew["subject"],
            description = rew["description"],
            reduction_type = rew["reduction"]["type"],
            amount = rew["reduction"]["amount"],
            museum_id = rew["museum_id"]
        )
        return ret_dict

DictSerializable.register(Reward)
