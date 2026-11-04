"""Hash-chained, server-signed custody events, and Merkle roots for anchoring.

Honest limits:
- Tamper-EVIDENT, not tamper-proof. Anyone who controls the database AND the signing key
  can rewrite history and re-sign it. Anchoring a root somewhere we do not control is what
  would make that detectable; the local demo chain is controlled by us so it does not.
- Proves a record existed unchanged. It says nothing about whether the content was true.
- Events must carry no personal data.
"""
import hashlib
import hmac
import json

from flask import current_app


def _keys():
    """AUDIT_SIGNING_KEY is 'kid:secret' or 'kid:secret,oldkid:oldsecret'. First key signs.
    Rotation: put the new key first and keep old ones so old events still verify."""
    raw = current_app.config.get("AUDIT_SIGNING_KEY") or ""
    pairs = []
    for part in raw.split(","):
        if ":" in part:
            kid, secret = part.split(":", 1)
            if kid.strip() and secret:
                pairs.append((kid.strip()[:12], secret))
    if not pairs:  # development fallback only; production config check rejects this
        pairs = [("dev", "dev-audit-key-not-secret")]
    return pairs


def canonical_hash(ev):
    note_h = hashlib.sha256((ev.note or "").encode()).hexdigest()
    body = {
        "d": ev.donation_id, "l": ev.label, "a": ev.actor_id, "r": ev.actor_role,
        "t": ev.created_at.replace(microsecond=0).isoformat(), "n": note_h, "p": ev.prev_hash,
    }
    return hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _sign(secret, h):
    return hmac.new(secret.encode(), h.encode(), hashlib.sha256).hexdigest()


def seal(ev):
    ev.hash = canonical_hash(ev)
    kid, secret = _keys()[0]
    ev.sig = f"{kid}.{_sign(secret, ev.hash)}"


def verify_chain(events):
    """Return {ok, checked, problem}. Checks hashes, links and signatures."""
    keys = dict(_keys())
    prev = None
    for i, ev in enumerate(events):
        if ev.prev_hash != prev:
            return {"ok": False, "checked": i, "problem": f"event {i + 1}: chain link broken"}
        if canonical_hash(ev) != ev.hash:
            return {"ok": False, "checked": i, "problem": f"event {i + 1}: content does not match its hash"}
        kid, _, sig = (ev.sig or "").partition(".")
        secret = keys.get(kid)
        if secret is None:
            return {"ok": False, "checked": i, "problem": f"event {i + 1}: unknown signing key '{kid}'"}
        if not hmac.compare_digest(_sign(secret, ev.hash), sig):
            return {"ok": False, "checked": i, "problem": f"event {i + 1}: bad signature"}
        prev = ev.hash
    return {"ok": True, "checked": len(events), "problem": None}


def merkle_root(hashes):
    if not hashes:
        raise ValueError("no hashes")
    layer = [bytes.fromhex(h) for h in hashes]
    while len(layer) > 1:
        if len(layer) % 2:
            layer.append(layer[-1])
        layer = [hashlib.sha256(layer[i] + layer[i + 1]).digest() for i in range(0, len(layer), 2)]
    return layer[0].hex()
