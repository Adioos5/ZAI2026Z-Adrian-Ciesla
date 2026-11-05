FROM python:3.12-alpine
WORKDIR /app
COPY sensor_server.py .
EXPOSE 8000
# -u: unbuffered output so readings show up in `docker compose logs` immediately.
CMD ["python", "-u", "sensor_server.py", "--host", "0.0.0.0", "--port", "8000"]
