"""Outgoing mail. Development uses a database outbox that sends nothing to anyone."""
import smtplib
from email.message import EmailMessage

from flask import current_app

from .db import Session
from .models import Outbox


class MailDisabled(Exception):
    pass


def mail_available():
    return current_app.config.get("MAIL_BACKEND", "outbox") != "disabled"


def send_mail(to_address, subject, body):
    backend = current_app.config.get("MAIL_BACKEND", "outbox")
    if backend == "disabled":
        raise MailDisabled("Email is turned off on this server.")
    if backend == "outbox":
        Session.add(Outbox(to_address=to_address, subject=subject, body=body))
        return
    msg = EmailMessage()
    msg["From"] = current_app.config["MAIL_FROM"]
    msg["To"] = to_address
    msg["Subject"] = subject
    msg.set_content(body)
    cfg = current_app.config
    with smtplib.SMTP(cfg["SMTP_HOST"], cfg["SMTP_PORT"], timeout=15) as s:
        s.starttls()
        if cfg["SMTP_USER"]:
            s.login(cfg["SMTP_USER"], cfg["SMTP_PASSWORD"])
        s.send_message(msg)
