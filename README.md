# green-software-champions

[![Build](https://github.com/retit/green-software-champions/actions/workflows/build.yml/badge.svg)](https://github.com/retit/green-software-champions/actions/workflows/build.yml)
This repository contains the "Green Software Champions" application — a leaderboard that aggregates CO2-equivalent data from cloud providers for teams and awards achievements.

Quick checklist (important for local development)
- Docker/Podman: required for Quarkus Dev Services (Postgres, Keycloak)
- Podman rootless: enable the user socket with `systemctl --user enable --now podman.socket`
- Place local environment variables in `.env` (read by `mvn quarkus:dev`)

Getting started (development)
1. Ensure Docker or Podman is running.
2. (Optional for rootless Podman) Enable the socket:

```bash
systemctl --user enable --now podman.socket
```

3. If using Podman: set the socket path in your local `.env` (example):

```bash
# .env (do not commit)
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock
```

4. Start Quarkus Dev:

```bash
mvn quarkus:dev
```

GCP Service Account: credentials via ENV (no file required)
-------------------------------------------------------
The application supports two ways to provide GCP service account credentials:

- `cloud.gcp.credentials-file` — path to a JSON key file (e.g. `/secrets/gsc-billing-sa.json`)
- `cloud.gcp.credentials-json` — full JSON content of the service account key in an environment variable

Configuration maps to the following environment variables (SmallRye/Quarkus mapping):

- `cloud.gcp.credentials-file` → `CLOUD_GCP_CREDENTIALS_FILE`
- `cloud.gcp.credentials-json` → `CLOUD_GCP_CREDENTIALS_JSON`

Example: set JSON key in an ENV variable and start Quarkus (local):

```bash
export CLOUD_GCP_CREDENTIALS_JSON="$(cat /path/to/gsc-billing-sa.json)"
mvn quarkus:dev
```

Alternative (one-liner):

```bash
CLOUD_GCP_CREDENTIALS_JSON="$(cat ~/.config/gsc/gsc-billing-sa.json)" mvn quarkus:dev
```

Why `credentials-json`?
- Useful when you don't want to mount a file into a container (e.g. in CI).
- The app loads the JSON string in-memory and creates Google ServiceAccountCredentials without writing a temporary file.

Example: GitHub Actions (secret contains the JSON content)

```yaml
- name: Setup GCP credentials
  env:
	GCP_SA_KEY: ${{ secrets.GCP_SA_KEY }}
  run: |
	# secure temporary file (optional) and set ENV for subsequent steps
	echo "$GCP_SA_KEY" > $HOME/gcp-key.json
	chmod 600 $HOME/gcp-key.json
	echo "CLOUD_GCP_CREDENTIALS_JSON=$(cat $HOME/gcp-key.json)" >> $GITHUB_ENV
```

Security notes
- Never commit service account keys into the Git repository.
- Use CI secrets or container secrets where possible.
- Restrict the service account roles following the principle of least privilege (e.g. `roles/billing.viewer` for Billing API access).

Implementation details
- The application checks credentials in this order when accessing GCP:
  1. Application Default Credentials / metadata server (when running on GCP)
  2. `cloud.gcp.credentials-json` (if set)
  3. `cloud.gcp.credentials-file` (if set)

Support & troubleshooting
- If Quarkus does not start Dev Services, check whether `quarkus.datasource.jdbc.url` or `quarkus.oidc.auth-server-url` are set in the active profile — that prevents Dev Services from starting.
- For Podman: if Testcontainers reports errors related to `ryuk`, check `testcontainers.properties` or set `ryuk.container.disabled=true` (only if necessary).

If you want, I can also add a short quickstart section with macOS/Windows commands or provide a small example script.
