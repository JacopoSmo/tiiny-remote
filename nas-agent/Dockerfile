# Tiiny Remote NAS agent: runs your NAS automations around the clock. Standard library only.
FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 DATA_DIR=/data PORT=8765
RUN useradd --uid 10001 --create-home agent && mkdir -p /data && chown agent /data
COPY agent.py /app/agent.py
USER agent
EXPOSE 8765
VOLUME ["/data"]
HEALTHCHECK --interval=60s --timeout=5s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8765/health',timeout=4)"
CMD ["python", "-I", "/app/agent.py"]
