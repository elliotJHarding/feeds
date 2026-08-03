"""Dump the feeds device as Google's Home Graph stored it (devices:sync +
devices:query), authenticating with the service-account key held in the
cluster secret. Secrets stay in process memory - nothing is written to disk.

Setup (once):  python3 -m venv .venv && .venv/bin/pip install google-auth requests
Run from the feeds repo root (needs ./kubeconfig and psql):
    tools/googlehome/.venv/bin/python tools/googlehome/hg_dump.py
"""
import base64
import json
import subprocess
import sys

import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

KUBE = ["kubectl", "--kubeconfig", "./kubeconfig", "-n", "default"]


def secret_value(name, key):
    out = subprocess.check_output(
        KUBE + ["get", "secret", name, "-o", f"jsonpath={{.data.{key.replace('.', '\\.')}}}"])
    return base64.b64decode(out).decode()


def agent_user_id():
    url = secret_value("feeds-database-secret", "url")
    user = secret_value("feeds-database-secret", "username")
    password = secret_value("feeds-database-secret", "password")
    out = subprocess.check_output(
        ["psql", f"postgresql://{user}@{url}", "-t", "-A",
         "-c", "select uuid from family_group;"],
        env={"PGPASSWORD": password, "PATH": "/opt/homebrew/bin:/usr/bin:/bin"})
    ids = [line for line in out.decode().splitlines() if line.strip()]
    if len(ids) != 1:
        print(f"expected exactly one family_group, got: {ids}")
        sys.exit(1)
    return ids[0].strip()


def main():
    sa_info = json.loads(secret_value("feeds-app-secret", "googlehome-sa-key"))
    credentials = service_account.Credentials.from_service_account_info(
        sa_info, scopes=["https://www.googleapis.com/auth/homegraph"])
    credentials.refresh(Request())
    headers = {"Authorization": f"Bearer {credentials.token}",
               "Content-Type": "application/json"}

    agent = agent_user_id()
    print(f"agentUserId: {agent}\n")

    sync = requests.post("https://homegraph.googleapis.com/v1/devices:sync",
                         headers=headers, json={"agentUserId": agent})
    print(f"--- devices:sync HTTP {sync.status_code}")
    print(json.dumps(sync.json(), indent=2))

    device_ids = [d["id"] for d in sync.json().get("payload", {}).get("devices", [])]
    if device_ids:
        query = requests.post(
            "https://homegraph.googleapis.com/v1/devices:query",
            headers=headers,
            json={"agentUserId": agent,
                  "inputs": [{"payload": {"devices": [{"id": i} for i in device_ids]}}]})
        print(f"\n--- devices:query HTTP {query.status_code}")
        print(json.dumps(query.json(), indent=2))


if __name__ == "__main__":
    main()
