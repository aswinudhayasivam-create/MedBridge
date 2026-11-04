"""Small in-memory fixed-window rate limiter (per client IP)."""
import threading
import time
from collections import defaultdict

from flask import request

from .errors import ApiError

_lock = threading.Lock()
_hits = defaultdict(list)


def limit(max_requests, window_seconds):
    """Raise 429 when this client exceeds max_requests inside the window."""
    now = time.time()
    key = request.headers.get("X-Forwarded-For", request.remote_addr or "unknown").split(",")[0].strip()
    with _lock:
        bucket = _hits[key]
        cutoff = now - window_seconds
        while bucket and bucket[0] < cutoff:
            bucket.pop(0)
        if len(bucket) >= max_requests:
            raise ApiError(429, "rate_limited", "Too many attempts. Please wait a minute and try again.")
        bucket.append(now)


def reset():
    with _lock:
        _hits.clear()
