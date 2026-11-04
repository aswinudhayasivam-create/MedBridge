"""Label text suggestions from a photo, using local Tesseract. SUGGESTIONS ONLY.

OCR is error-prone and says nothing about whether a medicine is genuine. The donor must
review and confirm every field. The image is never stored.
"""
import calendar
import os
import re
import subprocess
import tempfile
from datetime import date

MONTHS = {m.lower(): i for i, m in enumerate(calendar.month_abbr) if m}


def run_tesseract(path):
    try:
        out = subprocess.run(["tesseract", path, "stdout", "-l", "eng", "--psm", "6"],
                             capture_output=True, text=True, timeout=25)
    except FileNotFoundError:
        raise RuntimeError("not_installed")
    except subprocess.TimeoutExpired:
        raise RuntimeError("timeout")
    if out.returncode != 0:
        raise RuntimeError("failed")
    return out.stdout


def ocr_bytes(data):
    fd, path = tempfile.mkstemp(suffix=".img")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        return run_tesseract(path)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def _last_day(year, month):
    if year < 100:
        year += 2000
    if not (2000 <= year <= 2100 and 1 <= month <= 12):
        return None
    return date(year, month, calendar.monthrange(year, month)[1])


def parse_label(text):
    text = text or ""
    up = text.upper()
    out = {}
    m = re.search(r"(?:EXP(?:IRY)?\.?(?:\s*DATE)?|USE\s*BEFORE|BEST\s*BEFORE)\s*[:\-]?\s*(\d{1,2})\s*[/\-. ]\s*(\d{2,4})", up)
    d = None
    if m:
        d = _last_day(int(m.group(2)), int(m.group(1)))
    else:
        m = re.search(r"(?:EXP(?:IRY)?\.?|USE\s*BEFORE)\s*[:\-]?\s*([A-Z]{3})[A-Z]*[\s,.\-/]*(\d{2,4})", up)
        if m and m.group(1).lower() in MONTHS:
            d = _last_day(int(m.group(2)), MONTHS[m.group(1).lower()])
    if d:
        out["expiry_date"] = d.isoformat()
    m = re.search(r"(?:B\.?\s*NO\.?|BATCH(?:\s*NO\.?)?|LOT)\s*[:\-]?\s*([A-Z0-9\-]{3,20})", up)
    if m:
        out["batch_number"] = m.group(1)
    m = re.search(r"(?:MFD\.?\s*BY|MFG\.?\s*BY|MANUFACTURED\s*BY)\s*[:\-]?\s*([^\n]{3,80})", text, re.I)
    if m:
        out["manufacturer"] = m.group(1).strip()
    m = re.search(r"(\d+(?:\.\d+)?\s?(?:mg|mcg|g|ml|iu))\b", text, re.I)
    if m:
        out["strength"] = m.group(1).replace(" ", "")
    skip = re.compile(r"\b(exp|mfg|mfd|batch|b\.?no|lot|mrp|price|tablets?|capsules?|store|keep|schedule|rx)\b", re.I)
    for line in text.splitlines():
        line = line.strip()
        letters = re.sub(r"[^A-Za-z]", "", line)
        if len(letters) >= 4 and len(letters) / max(len(line), 1) > 0.6 and not skip.search(line):
            out["medicine_name"] = line[:80]
            break
    return out
