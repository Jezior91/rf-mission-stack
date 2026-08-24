"""
RF Mission Stack — Rate Limiting & Security Middleware
"""
import time
import os
from collections import defaultdict
from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware

# Rate limit: max N requests per window (seconds) per IP
RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "100"))
RATE_LIMIT_WINDOW   = int(os.getenv("RATE_LIMIT_WINDOW",   "60"))

_counters: dict = defaultdict(list)


class RateLimitMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        # /health i /docs nie są limitowane
        if request.url.path in ("/health", "/docs", "/openapi.json", "/redoc"):
            return await call_next(request)

        ip = request.client.host if request.client else "unknown"
        now = time.time()
        window_start = now - RATE_LIMIT_WINDOW

        # Wyczyść stare wpisy
        _counters[ip] = [t for t in _counters[ip] if t > window_start]

        if len(_counters[ip]) >= RATE_LIMIT_REQUESTS:
            raise HTTPException(
                status_code=429,
                detail={
                    "error": "rate_limit_exceeded",
                    "limit": RATE_LIMIT_REQUESTS,
                    "window_seconds": RATE_LIMIT_WINDOW,
                    "retry_after": int(RATE_LIMIT_WINDOW - (now - _counters[ip][0]))
                }
            )

        _counters[ip].append(now)
        response = await call_next(request)
        response.headers["X-RateLimit-Limit"] = str(RATE_LIMIT_REQUESTS)
        response.headers["X-RateLimit-Remaining"] = str(RATE_LIMIT_REQUESTS - len(_counters[ip]))
        response.headers["X-RateLimit-Reset"] = str(int(window_start + RATE_LIMIT_WINDOW))
        return response
