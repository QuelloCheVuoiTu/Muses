"""RBAC module for role-based access control."""

import os
import json
import re
from pathlib import Path

from flask import Request


ALLOWED_ROLES = os.getenv("ALLOWED_ROLES", "ADMIN,M_ADMIN,USER")
ALLOWED_ROLES = ALLOWED_ROLES.split(",")
ID_SUBSTITUTION_PLACEHOLDER = os.getenv("ID_SUBSTITUTION_PLACEHOLDER", "__ID__")
RBAC_RULES_PATH = os.getenv("RBAC_RULES_PATH", "/etc/config/RBAC_RULES.json")
RBAC_FILE = Path(RBAC_RULES_PATH)
# RBAC_RULES_TEMPLATE = {
#     "admin" : str,
#     "role_binding":dict(str=dict(str=list[str]))
# }


if not RBAC_FILE.exists() or not RBAC_FILE.is_file():
	RBAC_RULES=None
else:
	with RBAC_FILE.open(encoding='utf-8') as f:
		RBAC_RULES:dict = json.load(f)


def _substitute_id(p:str,u_id:str):
	return p.replace(ID_SUBSTITUTION_PLACEHOLDER,u_id)


def rbac_validation(request:Request,role:str,external_id:str = "") -> bool:
	"""Validate RBAC for the given request and role."""

	if not RBAC_RULES:
		return True

	admin_role = RBAC_RULES.get("admin", None)
	if role == admin_role:
		return True
	
	if not external_id and RBAC_RULES.get("require_link","false") == "true":
		return False

	if not ('Original-Route' in request.headers and 'Original-Method' in request.headers):
		return False

	path = request.headers.get('Original-Route')
	method = request.headers.get('Original-Method')
	role_bindings = RBAC_RULES.get("role_binding", None)
	if not role_bindings:
		return True

	rb = role_bindings.get(role, None)
	if not rb:
		return False

	path_list:list[str] = rb.get(method, None)
	if not path_list:
		return False

	path_list = list(map(lambda p: _substitute_id(p, external_id), path_list))
	return any(re.match(p, path) is not None for p in path_list)
