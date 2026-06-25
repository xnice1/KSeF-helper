import json
import os


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


config = f"""global:
  resolve_timeout: 5m
  smtp_smarthost: {json.dumps(required("ALERT_SMTP_SMARTHOST"))}
  smtp_from: {json.dumps(required("ALERT_EMAIL_FROM"))}
  smtp_auth_username: {json.dumps(required("ALERT_SMTP_USERNAME"))}
  smtp_auth_password: {json.dumps(required("ALERT_SMTP_PASSWORD"))}
  smtp_require_tls: {os.environ.get("ALERT_SMTP_REQUIRE_TLS", "true").lower()}

route:
  receiver: staging-email
  group_by: [alertname]
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h

receivers:
  - name: staging-email
    email_configs:
      - to: {json.dumps(required("ALERT_EMAIL_TO"))}
        send_resolved: true
"""

with open("/tmp/alertmanager.yml", "w", encoding="utf-8") as output:
    output.write(config)

os.execv(
    "/bin/alertmanager",
    [
        "/bin/alertmanager",
        "--config.file=/tmp/alertmanager.yml",
        "--storage.path=/alertmanager",
    ],
)
