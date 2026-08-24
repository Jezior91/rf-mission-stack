"""JWT Auth — ETAP Role System"""
import os
from datetime import datetime, timedelta
from typing import Optional
from dataclasses import dataclass
from jose import JWTError, jwt
from passlib.context import CryptContext

SECRET_KEY    = os.environ.get("SECRET_KEY", "CHANGE_ME")
ALGORITHM     = os.environ.get("JWT_ALGORITHM", "HS256")
ACCESS_EXPIRE = int(os.environ.get("ACCESS_TOKEN_EXPIRE_MINUTES", "30"))
REFRESH_EXPIRE= int(os.environ.get("REFRESH_TOKEN_EXPIRE_DAYS", "7"))

# ETAP Role Hierarchy: observer < operator < commander < meta-will
ROLE_LEVELS = {"observer": 1, "operator": 2, "commander": 3, "meta-will": 4}

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

@dataclass
class TokenData:
    sub: str
    role: str
    token_type: str = "access"

def hash_password(password: str) -> str:
    return pwd_context.hash(password)

def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)

def create_access_token(sub: str, role: str) -> str:
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_EXPIRE)
    return jwt.encode(
        {"sub": sub, "role": role, "type": "access", "exp": expire},
        SECRET_KEY, algorithm=ALGORITHM
    )

def create_refresh_token(sub: str, role: str) -> str:
    expire = datetime.utcnow() + timedelta(days=REFRESH_EXPIRE)
    return jwt.encode(
        {"sub": sub, "role": role, "type": "refresh", "exp": expire},
        SECRET_KEY, algorithm=ALGORITHM
    )

def verify_token(token: str) -> Optional[TokenData]:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return TokenData(
            sub=payload["sub"],
            role=payload.get("role", "observer"),
            token_type=payload.get("type", "access")
        )
    except JWTError:
        return None

def require_role(user_role: str, min_role: str) -> bool:
    """Check if user has at least min_role level."""
    return ROLE_LEVELS.get(user_role, 0) >= ROLE_LEVELS.get(min_role, 99)

# Convenience: standalone token endpoint (mount in FastAPI if needed)
from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

router = APIRouter(prefix="/auth", tags=["auth"])

# In-memory user store for demo — replace with DB in production
DEMO_USERS = {
    "admin": {"hashed": hash_password("admin123"), "role": "meta-will"},
    "operator1": {"hashed": hash_password("op1pass"), "role": "operator"},
    "observer1": {"hashed": hash_password("obs1pass"), "role": "observer"},
}

class LoginRequest(BaseModel):
    username: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    role: str
    expires_in: int

class RefreshRequest(BaseModel):
    refresh_token: str

@router.post("/login", response_model=TokenResponse)
def login(req: LoginRequest):
    user = DEMO_USERS.get(req.username)
    if not user or not verify_password(req.password, user["hashed"]):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")
    return TokenResponse(
        access_token=create_access_token(req.username, user["role"]),
        refresh_token=create_refresh_token(req.username, user["role"]),
        role=user["role"],
        expires_in=ACCESS_EXPIRE * 60
    )

@router.post("/refresh", response_model=TokenResponse)
def refresh(req: RefreshRequest):
    data = verify_token(req.refresh_token)
    if data is None or data.token_type != "refresh":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    user = DEMO_USERS.get(data.sub)
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)
    return TokenResponse(
        access_token=create_access_token(data.sub, user["role"]),
        refresh_token=create_refresh_token(data.sub, user["role"]),
        role=user["role"],
        expires_in=ACCESS_EXPIRE * 60
    )
