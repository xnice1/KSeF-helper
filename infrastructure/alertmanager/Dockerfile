FROM prom/alertmanager:v0.33.0 AS alertmanager

FROM python:3.13-alpine
COPY --from=alertmanager /bin/alertmanager /bin/alertmanager
COPY entrypoint.py /entrypoint.py
ENTRYPOINT ["python3", "/entrypoint.py"]
