"""Anchor new custody events to the LOCAL DEMO chain.

Run from backend/:   python anchor.py
Needs `npx hardhat node` running in ../chain. Computes a Merkle root over the hashes of
events not yet anchored, publishes the root, and records it.

Local demo chain: we control it, so this shows the mechanism and proves nothing to a
third party. A real deployment would anchor to a network we do not control.
"""
import json
import os
import subprocess
import sys

from medbridge import create_app
from medbridge.audit import merkle_root
from medbridge.db import Session
from medbridge.models import Anchor, DonationEvent

CHAIN_DIR = os.path.join(os.path.dirname(__file__), "..", "chain")


def pending_events():
    last = Session.query(Anchor).order_by(Anchor.id.desc()).first()
    q = Session.query(DonationEvent).order_by(DonationEvent.id.asc())
    if last:
        q = q.filter(DonationEvent.id > last.last_event_id)
    return q.all()


def make_anchor(publish=None):
    """publish(root, count) -> dict with tx/id. Injected in tests."""
    events = pending_events()
    if not events:
        return None
    root = merkle_root([e.hash for e in events])
    ref = None
    label = None
    if publish:
        res = publish(root, len(events))
        ref, label = res["tx"], "local-demo"
        if res.get("stored") != root:
            raise RuntimeError("the chain stored a different root than we sent")
    a = Anchor(root=root, event_count=len(events), first_event_id=events[0].id,
               last_event_id=events[-1].id, chain_label=label, chain_ref=ref)
    Session.add(a)
    Session.commit()
    return a


def publish_local(root, count):
    out = subprocess.run(["npx", "hardhat", "run", "scripts/anchor.js", "--network", "localhost"],
                         cwd=CHAIN_DIR, capture_output=True, text=True, timeout=120,
                         env={**os.environ, "ANCHOR_ROOT": root, "ANCHOR_COUNT": str(count)})
    if out.returncode != 0:
        raise RuntimeError("local chain not reachable: " + (out.stderr or out.stdout)[-300:])
    return json.loads(out.stdout.strip().splitlines()[-1])


if __name__ == "__main__":
    app = create_app()
    with app.app_context():
        a = make_anchor(publish_local if "--no-chain" not in sys.argv else None)
        print("nothing new to anchor" if a is None else f"anchored {a.event_count} events, root {a.root}, ref {a.chain_ref}")
