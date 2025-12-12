import requests
from dataclasses import dataclass
from asyncio import run,TaskGroup,Task

@dataclass
class RequestSchema():
    method:str
    url:str
    headers:dict=None
    data:any=None
    json:any=None

def get_urls():
    return ["url1","url2"]

def load_url(url, timeout):
    return requests.get(url, timeout = timeout)

def async_http_requests(requests:dict[str,RequestSchema])->dict[str,requests.Response]:
    return run(_async_http_requests(requests))
    
async def request_task(r:RequestSchema):
        return requests.request(method=r.method,url=r.url,headers=r.headers,data=r.data,json=r.json)

async def _async_http_requests(request_list:dict[str,RequestSchema])->dict[str,requests.Response]:
    tasks:dict[str,Task] = dict()
    results = dict()
    async with TaskGroup() as tg:
        for k,r in request_list.items():
            tasks[k]=tg.create_task(request_task(r))
    for k,v in tasks.items():
        try:
            results[k] = v.result()
        except Exception as e:
            results[k] = e
    
    return results