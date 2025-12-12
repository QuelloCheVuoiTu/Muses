
"""
Authentication and JWT utility functions for the authentication service.

Handles token validation, login verification, registration, and credential validation.
"""

import os
import re
import logging
from datetime import datetime, timezone, timedelta
from uuid import uuid4

import jwt as jt

from helpers import validate_user, save_user


JWK = os.getenv("OCT", str(uuid4()))
JWT_ISSUER = os.getenv("ISSUER", "it.unisannio.muses")
EXPIRATION_DELTA = os.getenv("EXPIRATION_DELTA_SECONDS", "86400")
PASSWORD_ALLOWED_PATTERN = re.compile(r'[a-zA-Z0-9_$%()=?\^*+\[@#\]\.:,;]{8,18}')
USERNAME_ALLOWED_PATTERN = re.compile(r'[a-zA-Z0-9_]{5,20}')

logger = logging.getLogger(__name__)


try:
	EXPIRATION_DELTA = int(EXPIRATION_DELTA)
except Exception:
	logger.warning("Expiration delta format not valid, must be a valid integer")
	EXPIRATION_DELTA = 86400


def validate_token(jwt_token: str) -> tuple[str, str]:
	"""
	Validate a JWT token and extract user_id and role.

	Args:
		jwt_token (str): The JWT token to validate.

	Returns:
		tuple[str, str]: The user_id and role from the token.

	Raises:
		Exception: If the token is invalid or missing required fields.
	"""

	try:
		payload: dict = jt.decode(jwt=jwt_token,
								  key=JWK,
								  algorithms="HS256",
								  issuer=JWT_ISSUER)
	except Exception as exc:
		raise Exception("Token not valid") from exc

	# Validate user
	user_id = payload.get("user_id", None)
	if not user_id:
		raise Exception("Token not valid")

	role = payload.get("role", None)
	if not role:
		raise Exception("Token not valid")

	return user_id, role


def verify_login(username: str, password: str, role: str) -> tuple[str, str,str]:
	"""
	Verify user credentials and generate a JWT token if valid.

	Args:
		username (str): The username.
		password (str): The password.
		role (str): The user's role.

	Returns:
		tuple[str, str]: The user_id and the generated JWT token.
	"""

	# Verify user is in db
	user_found = validate_user(username, password, role)

	exp = datetime.now(tz=timezone.utc) + timedelta(seconds=EXPIRATION_DELTA)
	payload = {
		"exp": exp,
		"user_id": user_found["_id"],
		"role": role,
		"iss": JWT_ISSUER
	}

	return user_found["_id"], user_found.get("external_id",None) ,jt.encode(payload=payload, key=JWK, algorithm="HS256")


def validate_registration(username: str, password: str, role: str) -> tuple[bool, str]:
	"""
	Validate registration data and attempt to save a new user.

	Args:
		username (str): The username to register.
		password (str): The password to register.
		role (str): The user's role.

	Returns:
		tuple[bool, str]: (True, "") if successful, (False, reason) otherwise.
	"""

	if role == "ADMIN":
		return False, ""

	is_valid, msg = validate_username_password(username=username, password=password)
	if not is_valid:
		return False, msg

	# Save function on db from utils
	if not save_user(username, password, role):
		return False, "User already exists"

	return True, ""


def validate_username_password(username: str, password: str) -> tuple[bool, str]:
	"""
	Validate username and password against allowed patterns.

	Args:
		username (str): The username to validate.
		password (str): The password to validate.

	Returns:
		tuple[bool, str]: (True, "") if valid, (False, reason) otherwise.
	"""

	user_match = re.fullmatch(pattern=USERNAME_ALLOWED_PATTERN, string=username)
	if not user_match:
		return False, (
			"Username must be at least 5 characters and maximum 20 characters long, "
			"it can only contains letters, numbers and the character _ "
		)

	pass_match = re.fullmatch(pattern=PASSWORD_ALLOWED_PATTERN, string=password)
	if not pass_match:
		return False, (
			"Password must be at least 8 characters and maximum 18 characters long, "
			"it can only contains letters, numbers and the characters: _$%()=?^*+[@#].:,; "
		)

	return True, ""
