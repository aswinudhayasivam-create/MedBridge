"""Production entry: gunicorn wsgi:app"""
from medbridge import create_app

app = create_app()
