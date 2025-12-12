from dataclasses import dataclass,field
from typing import Literal
from helpers import DictSerializable,SERIALIZABLE_TYPES
from typing import get_args
QUEST_STATUSES = Literal["COMPLETE","PENDING","IN_PROGRESS","STOPPED"]
ALLOWED_TRANSITIONS = dict(
    COMPLETE = ["PENDING","COMPLETE"],
    PENDING = ["COMPLETE","IN_PROGRESS","PENDING"],
    IN_PROGRESS = ["COMPLETE","STOPPED","IN_PROGRESS"],
    STOPPED = ["IN_PROGRESS","STOPPED"]
)
@dataclass
class Task(DictSerializable):
    completed: bool
    title: str
    description: str

    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return dict(
            completed = self.completed,
            title = self.title,
            description = self.description,
        )

    @staticmethod
    def deserialize(data:dict)->'Task':
        data_model =  {
            "completed":bool,
            "title":str,
            "description":str,
        }
        Task.validate_fields(data_model,data)
        return Task(**data)
    
@dataclass
class Quest(DictSerializable):
    title: str
    status: QUEST_STATUSES
    description: str
    tasks: dict[str,Task]
    subject_id: str 
    
    def _serialize(self)->dict[str,SERIALIZABLE_TYPES]:
        return self.__dict__
    
    @staticmethod
    def deserialize(data:dict)->'Quest':
        data_model =  {
            "title" : str,
            "status" : str,
            "tasks" : dict,
            "description": str,
            "subject_id":str
        }
        Quest.validate_fields(data_model,data)
        if not data["status"] in get_args(QUEST_STATUSES):
            raise Exception("Status Unknown")
        data["tasks"] = dict([(k,Task.deserialize(v) )for k,v in data["tasks"].items()])
        return Quest(**data)
    
    def transition_status(self,status: QUEST_STATUSES):
        if not status in get_args(QUEST_STATUSES):
            raise Exception("Status unknown")
        if not status in ALLOWED_TRANSITIONS[self.status]:
            raise Exception("Status transition not allowed")
        self.status = status

    def complete(self):
        if not all(map(lambda t: t.completed,self.tasks.values())):
            raise Exception("Cannot complete quest if all tasks are not completed")
        self.transition_status("COMPLETE")

    def start(self):
        self.transition_status("IN_PROGRESS")

    def stop(self):
        self.transition_status("STOPPED")

    def reset(self):
        for t in self.tasks.values():
            t.completed = False
        self.transition_status("PENDING")

    def get_progress_state(self) ->tuple[int,int]:
        tot_tasks = len(self.tasks.keys())
        tot_completed = len([t for t in self.tasks.values() if t.completed])
        return tot_completed,tot_tasks
    
    def complete_step(self,step_id:str):
        if self.status != "IN_PROGRESS":
            raise Exception("Cannot complete step before starting the quest")
        task = self.tasks.get(step_id,None)
        if not task:
            raise Exception("Task not found")
        task.completed = True
        try:
            self.complete()
        except Exception:
            pass
        


DictSerializable.register(Quest)
DictSerializable.register(Task)
