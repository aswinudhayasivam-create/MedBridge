# Single-service image: builds the React app, serves it and the API with gunicorn.
# Not deployed anywhere. Set MB_ENV, JWT_SECRET, DATABASE_URL, AUDIT_SIGNING_KEY etc. as host env vars (never in the image).
FROM node:20-slim AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr libpq5 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY backend/requirements.txt backend/requirements-postgres.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-postgres.txt
COPY backend/ ./
COPY --from=web /web/dist /app/static
ENV FRONTEND_DIST=/app/static PORT=8000
# Migrations run before start; tables are never created at startup outside development.
CMD ["sh", "-c", "alembic upgrade head && gunicorn -w 1 -b 0.0.0.0:${PORT} wsgi:app"]
