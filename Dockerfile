FROM python:3.12-slim AS builder

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
RUN pip install poetry==1.8.3

WORKDIR /app
COPY pyproject.toml poetry.lock ./
RUN poetry config virtualenvs.in-project true \
    && poetry install --only main --no-root --no-interaction

FROM python:3.12-slim AS runtime

ENV PYTHONUNBUFFERED=1 PATH="/app/.venv/bin:$PATH"
WORKDIR /app

COPY --from=builder /app/.venv /app/.venv
COPY alembic.ini ./
COPY alembic ./alembic
COPY app ./app
COPY entrypoint.sh ./
RUN chmod +x entrypoint.sh

EXPOSE 8000
ENTRYPOINT ["./entrypoint.sh"]
