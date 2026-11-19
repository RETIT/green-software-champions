# Green Software Champions

[![Build](https://github.com/retit/green-software-champions/actions/workflows/build.yml/badge.svg)](https://github.com/retit/green-software-champions/actions/workflows/build.yml)

A gamified leaderboard for the carbon footprint of your teams' cloud accounts.
It fetches the monthly CO₂-equivalent emissions of each team's cloud projects,
ranks the teams, shows their history and awards achievements for reductions.

- **Leaderboard** of all teams for a month, lowest emissions first, with the
  change against the previous month and a company-wide summary
- **History charts** of the last 13 months per team, plus a world-map view
- **Achievements**, evaluated once every team has data for a month:

  | Achievement           | Awarded for                                                     | Requires                                               |
  |-----------------------|-----------------------------------------------------------------|--------------------------------------------------------|
  | 🏆 Reduction Champion | the largest reduction in % vs. the previous month (monthly)     | ≥ 1 % of the company's emissions in the previous month |
  | 💪 Most CO₂e Saved    | the most kg saved vs. the previous month (monthly)              | –                                                      |
  | 📉 Big Saver          | ≥ 20 % below the average of the previous 3 months               | data for each of the previous 3 months                 |
  | 🌱 Green Streak       | 3, 6 or 12 months in a row with ≥ 2 % reduction                 | –                                                      |
  | ⭐ Personal Best      | ≥ 5 % below the team's best month so far                        | data for at least 6 earlier months                     |
  | 📅 Annual Improver    | ≥ 10, 25 or 50 % below the previous calendar year (in December) | data for every month of both years                     |

  Months of different length are compared by their daily averages. Admins can
  also grant custom achievements ("shoutouts") through the API.

**Cloud providers:** AWS is supported through the AWS Sustainability API. The
GCP provider, based on the Carbon Footprint export to BigQuery, and the Azure
provider are under development.

**Tech stack:** Quarkus 3 (Java 25), H2 (embedded, file-based) with Flyway,
Keycloak via OIDC, and a Lit + TypeScript frontend built with Vite and served by
Quarkus through Quinoa.

## Prerequisites

| Tool                 | Needed for                    | Notes                                                    |
|----------------------|-------------------------------|----------------------------------------------------------|
| JDK 25 or newer      | everything                    |                                                          |
| Maven 3.9            | everything                    |                                                          |
| Node.js and npm      | dev mode, `mvn package`       | Quinoa runs `npm install` and the Vite build; CI uses Node 26 |
| Docker or Podman     | dev mode only                 | Quarkus Dev Services start Keycloak in a container       |

`mvn test` needs neither Node.js nor a container runtime.

## Quick start: dev mode with sample data

```bash
git clone https://github.com/retit/green-software-champions.git
cd green-software-champions
mvn quarkus:dev
```

Then open <http://localhost:8080>. On the first start this:

- starts a Keycloak container (Dev Services) with the realm `green-software`
  and the users `developer` / `developer` and `admin` / `admin`,
- creates the H2 database in `./data/greensoft.mv.db`,
- creates the four sample teams from
  [`sample-teams.yaml`](src/main/resources/sample-teams.yaml),
- seeds 14 months of generated sample emissions for them and evaluates the
  achievements, as no cloud provider is enabled.

Security checks are off in dev mode, so you don't need to log in, neither in the
UI nor for the API. Code changes in Java and TypeScript are reloaded live.

| URL                                   | What                               |
|---------------------------------------|------------------------------------|
| <http://localhost:8080>               | the dashboard                      |
| <http://localhost:8080/q/dev-ui>      | Quarkus Dev UI (incl. Keycloak)    |
| <http://localhost:8080/q/swagger-ui>  | REST API docs (dev mode only)      |
| <http://localhost:8080/q/health>      | health checks                      |

The database is kept across restarts. To start over, stop Quarkus and run
`rm -rf data`.

### Using Podman

On Linux with rootless Podman, enable the user socket and point
Testcontainers (used by Dev Services) at it before starting Quarkus:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
mvn quarkus:dev
```

Ryuk, the Testcontainers cleanup container, doesn't work with rootless Podman;
the repository already disables it in
[`testcontainers.properties`](src/main/resources/testcontainers.properties).

## Using real AWS data

The sample data is only seeded while no provider is enabled. As soon as one is,
the application fetches the last 12 months from it at every startup (startup
waits for this) and again at 02:00 on the 25th of every month. AWS publishes a
month's data around the 21st of the following month.

1. **Credentials.** The application uses `AWS_ACCESS_KEY_ID` and
   `AWS_SECRET_ACCESS_KEY` if both are set, otherwise the AWS SDK's
   [default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)
   (profiles, SSO, IAM roles, …). The identity needs the IAM action
   `sustainability:GetEstimatedCarbonEmissions`. A member account of an AWS
   Organization only sees its own data; to query several accounts, use the
   management account.

2. **Teams.** Tell the application which AWS accounts belong to which team,
   either by setting the account IDs of the sample teams …

   ```bash
   export ALPHA_AWS_PROJECT=123456789012   # Team Alpha
   export BETA_AWS_PROJECT=234567890123    # Team Beta
   export DELTA_AWS_PROJECT=345678901234   # Team Delta
   ```

   … or by writing your own [teams file](#teams-file) and pointing dev mode
   at it, which replaces the sample teams:

   ```bash
   export QUARKUS_CONFIG_LOCATIONS=$PWD/my-teams.yaml
   ```

3. **Start** with a fresh database, so no sample data is left over:

   ```bash
   rm -rf data
   AWS_ENABLED=true mvn quarkus:dev
   ```

Instead of exporting variables, you can put them into a `.env` file in the
project root, which Quarkus reads at startup and git ignores. That works for
all application settings, but not for `DOCKER_HOST`, which has to be a real
environment variable.

## Using real GCP data

> [!NOTE]
> The GCP provider is under development.

## Using real Azure data

> [!NOTE]
> The Azure provider is under development.

## Running the packaged application

```bash
mvn package
```

This builds the frontend and the application into `target/quarkus-app/`. Run
it with `java -jar target/quarkus-app/quarkus-run.jar`; the whole
`quarkus-app` directory is needed. For a single self-contained jar, add
`-Dquarkus.package.jar.type=uber-jar` and run
`target/green-software-champions-1.0.0-SNAPSHOT-runner.jar`. CI builds that jar
for every push to `main` and every pull request and keeps it as a build
artifact for 30 days.

Outside dev mode, the teams file, the database and Keycloak have to be
configured:

```bash
export TEAMS_FILE=/etc/green-champions/teams.yaml   # see "Teams file"
export DB_FILE=/var/lib/green-champions/greensoft   # H2 file, without .mv.db
export KEYCLOAK_URL=https://keycloak.example.com    # without /realms/…
export KEYCLOAK_SECRET=<client secret>
export AWS_ENABLED=true
java -jar target/quarkus-app/quarkus-run.jar
```

`DB_FILE` must be absolute or start with `./` or `~/`. H2 creates the file
and its directories on the first start, and only one process can open it at a
time.

Keycloak needs a realm (`green-software` by default) with a confidential
client (`green-champions` by default) and the realm roles `user`, for reading
the leaderboard, and `admin`, for the admin API.

> [!NOTE]
> The login of the dashboard is under development. Until it's available, use
> dev mode to see the dashboard with real data. Outside dev mode, the API
> accepts requests with a Keycloak access token.

## Configuration

All settings are in [`application.yaml`](src/main/resources/application.yaml)
and can be overridden with these environment variables:

| Variable                                       | Default            | Description                                                          |
|------------------------------------------------|--------------------|----------------------------------------------------------------------|
| `TEAMS_FILE`                                   | –                  | Path of the teams file; required outside dev mode                    |
| `DB_FILE`                                      | `./data/greensoft` in dev mode | Path of the H2 database file without `.mv.db`; required outside dev mode |
| `KEYCLOAK_URL`                                 | –                  | Keycloak base URL; required outside dev mode                         |
| `KEYCLOAK_SECRET`                              | –                  | Client secret; required outside dev mode                             |
| `KEYCLOAK_REALM`                               | `green-software`   | Keycloak realm                                                       |
| `KEYCLOAK_CLIENT_ID`                           | `green-champions`  | Keycloak client                                                      |
| `AWS_ENABLED`                                  | `false`            | Fetch carbon data from AWS                                           |
| `AWS_REGION`                                   | `us-east-1`        | Region of the AWS Sustainability API endpoint                        |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`   | –                  | Static AWS credentials; otherwise the default credentials chain      |
| `CARBON_SYNC_CRON`                             | `0 0 2 25 * ?`     | When the scheduled sync runs (Quartz cron, server time zone)         |
| `CARBON_SYNC_MONTHS`                           | `12`               | Months fetched per sync, ending with the previous month              |
| `CARBON_HISTORY_MONTHS`                        | `13`               | Months shown in the history charts                                   |

The GCP and Azure providers, which are under development, are configured with
`GCP_ENABLED`, `GCP_DATASET` and `CLOUD_GCP_CREDENTIALS_JSON` or
`CLOUD_GCP_CREDENTIALS_FILE`, and with `AZURE_ENABLED`,
`CLOUD_AZURE_TENANT_ID`, `CLOUD_AZURE_CLIENT_ID` and
`CLOUD_AZURE_CLIENT_SECRET`.

### Teams file

The teams and their cloud projects are configured in a YAML file with the same
structure as [`sample-teams.yaml`](src/main/resources/sample-teams.yaml):

```yaml
teams:
  definitions:
    - name: "Team Alpha"                 # required, identifies the team
      description: "Frontend development team"
      color: "#4CAF50"                   # required
      icon: "🌱"                         # default: 🌍
      projects:
        - provider: AWS                  # AWS, AZURE or GCP
          project-id: "123456789012"     # AWS account ID
          project-name: "Alpha AWS Account"  # default: the project ID
```

The file is applied at every startup: new teams and projects are created and
existing teams are updated. Teams are matched by name, so renaming one creates
a new team. Teams and projects removed from the file stay in the database.

## Admin API

The admin endpoints require the `admin` role (except in dev mode, where no
login is needed). They are documented in the Swagger UI.

```bash
# Fetch the last CARBON_SYNC_MONTHS months again now
curl -X POST http://localhost:8080/api/admin/sync

# Fetch a single month
curl -X POST http://localhost:8080/api/admin/sync/2026-10

# Grant a custom achievement to the team with ID 1
curl -X POST http://localhost:8080/api/admin/teams/1/achievements \
  -H 'Content-Type: application/json' \
  -d '{"title": "Cleanup Hero", "description": "Deleted 40 idle VMs", "badgeIcon": "🧹"}'
```

The team IDs are listed by `GET /api/leaderboard/teams`. Outside dev mode, add
`-H "Authorization: Bearer $TOKEN"` with an access token of a Keycloak user
with the `admin` role.

## Tests

```bash
mvn test
```

The tests use an in-memory H2 database and the sample teams. Keycloak, the
cloud providers and the scheduler are disabled, so no network, container
runtime or Node.js is needed. Only the tests of the GCP provider enable it,
against a fake of the BigQuery API and Google's token endpoint that the
application under test serves itself. As it only serves the fake once started,
these tests turn off the check of the export and the sync at startup, and call
the check themselves.

## Troubleshooting

- **Dev Services don't start Keycloak:** check that Docker or Podman is
  running and, for Podman, that `DOCKER_HOST` is exported. Dev Services are
  also skipped if `quarkus.oidc.auth-server-url` is set, e.g. through
  `QUARKUS_OIDC_AUTH_SERVER_URL`.
- **"Database may be already in use":** another process, e.g. a second dev
  mode or the packaged application, has the H2 file open. Stop it, or give
  one of them a different `DB_FILE`.
- **Startup takes long with a provider enabled:** startup waits for the
  initial sync of `CARBON_SYNC_MONTHS` months.
- **A month is missing for a team:** a month is only stored once every
  provider of the team has published it, and achievements are only evaluated
  once every team has it. Before the 25th, the previous month is often not
  available yet.

## Project layout

```
src/main/java/io/retit/greensoftware/
  client/               cloud provider clients (AWS SDK, REST clients of Azure and BigQuery)
  config/               typed configuration (teams, providers, sync, history)
  resource/             REST endpoints (/api/leaderboard, /api/admin)
  scheduler/            startup sync or sample data seeding, monthly sync
  service/              sync, achievements, leaderboard, team setup, dev sample data
src/main/resources/
  application.yaml      configuration of all profiles
  sample-teams.yaml     teams of dev mode and tests
  db/migration/         Flyway schema
src/main/webui/         Lit + TypeScript frontend (Vite)
```

## License

[Apache License 2.0](LICENSE)
