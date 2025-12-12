from abc import ABCMeta,abstractmethod
from typing import Literal,Union
SERIALIZABLE_TYPES = Union[str,int,dict,bool,float,list]
class DictSerializable(metaclass=ABCMeta):
    def serialize(self,data:dict[str,SERIALIZABLE_TYPES]=None)-> dict[str,SERIALIZABLE_TYPES]:
        if data==None:
            data:dict = self._serialize() 
        ret_dict:dict[str,Union[str,int,dict,bool,float]] = dict()
        for k,v in data.items():
            if isinstance(v,dict):
                val = self.serialize(v)
            elif isinstance(v,SERIALIZABLE_TYPES):
                val = v
                if isinstance(v,list) and any(map(lambda x: isinstance(x,DictSerializable),v)):
                    val = [vl.serialize() if isinstance(vl,DictSerializable) else vl for vl in v]      
            elif isinstance(v,DictSerializable):
                val = v.serialize()
            else:
                continue
            ret_dict[k] = val
        return ret_dict
    
    @abstractmethod
    def _serialize(self)->dict:
        """Make dictionary that represents the object

        Returns:
            dict: dictionary representing the object
        """
        pass
    
    @abstractmethod
    def deserialize(data:dict)->'DictSerializable':
        pass

    @classmethod
    def __subclasshook__(cls, C):
        if cls is DictSerializable:
            if any("_serialize" in B.__dict__ and "deserialize" in B.__dict__ for B in C.__mro__):
                return True
        return NotImplemented
