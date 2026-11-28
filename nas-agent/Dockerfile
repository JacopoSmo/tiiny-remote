# Tiiny Remote NAS agent: runs your NAS automations around the clock. Standard library only (plus Python's time-zone data).
FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 DATA_DIR=/data PORT=8765
# Time zones: the slim image may come without the system's zone database; Python's own copy is the fallback.
# openssl makes the agent's own certificate once (the encrypted link to the app); openssh-client reads the Tiiny's
# temperatures with the agent's own key (agent 2.1).
RUN apt-get update && apt-get install -y --no-install-recommends openssl openssh-client && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir tzdata && useradd --uid 10001 --create-home agent && mkdir -p /data && chown agent /data
COPY agent.py /app/agent.py
USER agent
EXPOSE 8765
VOLUME ["/data"]
HEALTHCHECK --interval=60s --timeout=5s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8765/health',timeout=4)"
CMD ["python", "-I", "/app/agent.py"]
