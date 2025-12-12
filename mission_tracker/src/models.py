from dataclasses import dataclass,field
from typing import Literal,get_args
import requests
from helpers import DictSerializable,SERIALIZABLE_TYPES
import os
from threading import Thread
MISSION_STATUSES = Literal["COMPLETE","PENDING","IN_PROGRESS","STOPPED"]
ALLOWED_TRANSITIONS = dict(
    COMPLETE = ["PENDING","COMPLETE"],
    PENDING = ["COMPLETE","IN_PROGRESS","PENDING"],
    IN_PROGRESS = ["COMPLETE","STOPPED","IN_PROGRESS"],
    STOPPED = ["IN_PROGRESS","STOPPED"]
)
STEP_MANAGER_SVC_NAME = os.getenv("STEP_MANAGER_SVC_NAME", "quest-manager")
REWARD_SVC_NAME = os.getenv("REWARD_SVC_NAME", None)

@dataclass
class Step(DictSerializable):
    step_id: str
    completed: bool

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return dict(
            step_id = self.step_id,
            completed = self.completed
        )

    @staticmethod
    def deserialize(data:dict)->'Step':
        if not ("step_id" in data and "completed" in data):
            raise Exception("Data not suitable for class deserialization")
        step_id = data["step_id"]
        completed = data["completed"]
        if not isinstance(completed,bool):
            raise Exception("Status not correct")
        if not (isinstance(step_id,str) and step_id):
            raise Exception("Step id not correct")
        return Step(step_id=step_id,completed=completed)
    
@dataclass
class Mission(DictSerializable):
    user_id:str
    status: MISSION_STATUSES
    steps:list[Step]
    
    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return dict(
            status = self.status,
            steps = self.steps,
            user_id = self.user_id
        )
    
    @staticmethod
    def deserialize(data:dict)->'Mission':
        if not ("status" in data and "steps" in data and "user_id" in data):
            raise Exception("Data not suitable for class deserialization")
        status = data["status"]
        steps = data["steps"]
        user_id = data["user_id"]
        steps = [Step.deserialize(s) for s in steps]
        if not status in get_args(MISSION_STATUSES):
            raise Exception("Status unknown")
        if not (isinstance(steps,list) and steps and all(map(lambda x: isinstance(x,Step),steps))):
            raise Exception("Steps not suitable for mission creation")
        if not user_id:
            raise Exception("User id not suitable for mission creation")
        return Mission(status=status,steps=steps,user_id=user_id)
    
    def transition_status(self,status: MISSION_STATUSES):
        if not status in get_args(MISSION_STATUSES):
            raise Exception("Status unknown")
        if not status in ALLOWED_TRANSITIONS[self.status]:
            raise Exception("Status transition not allowed")
        self.status = status

    def complete_mission(self):
        if not all(map(lambda s: s.completed,self.steps)):
            raise InterruptedError("Cannot complete mission if all steps are not completed")
        self.transition_status("COMPLETE")
        if REWARD_SVC_NAME:
            t = Thread(target=lambda : requests.get(f"http://{REWARD_SVC_NAME}/generate/{self.user_id}"))
            t.start()

    def start_mission(self):
        first_task = self.steps[0]
        r = requests.post(f"http://{STEP_MANAGER_SVC_NAME}/start/{first_task.step_id}")
        if not r.ok:
            raise Exception(r.json().get("error","Unknown error"))
        self.transition_status("IN_PROGRESS")

    def stop_mission(self):
        for t in self.steps:
           r = requests.post(f"http://{STEP_MANAGER_SVC_NAME}/stop/{t.step_id}")
           if not r.ok:
               raise Exception(r.json().get("error","Unknown error"))
        self.transition_status("STOPPED")

    def reset_mission(self):
        for t in self.steps:
            r = requests.post(f"http://{STEP_MANAGER_SVC_NAME}/reset/{t.step_id}")
            if not r.ok:
                raise Exception(r.json().get("error","Unknown error"))
            t.completed = False

    def get_progress_state(self) -> tuple[int,int]:
        tot = len(self.steps)
        tot_completed = len([s for s in self.steps if s.completed])
        return tot_completed,tot
    
    def complete_step(self,step_id:str):
        if self.status != "IN_PROGRESS":
             raise Exception("Cannot complete task before starting the mission")
        found:int = -1
        for i,s in enumerate(self.steps):
            if s.completed:
                continue
            if s.step_id != step_id:
                raise ReferenceError("Cannot complete task before completing the previous ones")
            found = i
            break
        if found < 0:
            raise Exception("Step not found")
        r = requests.get(f"http://{STEP_MANAGER_SVC_NAME}/{step_id}")
        if not r.ok:
            raise Exception(r.json().get("error","Unknown error"))
        body = r.json()
        tasks_completed = body["tasks_completed"]
        tot_tasks = body["tot_tasks"]
        if tasks_completed != tot_tasks:
            raise PermissionError()
        self.steps[found].completed = True
        try:    
            self.complete_mission()
        except InterruptedError:
            r = requests.post(f"http://{STEP_MANAGER_SVC_NAME}/start/{self.steps[found+1].step_id}")
            if not r.ok:
                raise ConnectionError(r.json().get("error","Unknown error"))
        
        
        

DictSerializable.register(Step)
DictSerializable.register(Mission)
