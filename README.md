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

**Sample data:** to try it without a cloud account, dev mode generates sample
emissions for the teams and evaluates their achievements, as long as no cloud
provider is enabled (see [Sample data](#sample-data)).

**Cloud providers:** AWS is supported through the AWS Sustainability API. The
GCP provider, based on the Carbon Footprint export to BigQuery, and the Azure
provider are under development.

**Tech stack:** Quarkus 3 (Java 25), H2 (embedded, file-based) with Flyway,
login via OpenID Connect (e.g. Keycloak), and a Lit + TypeScript frontend built
with Vite and served by Quarkus through Quinoa.

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
- seeds 14 months of generated [sample data](#sample-data) for them and
  evaluates the achievements, as no cloud provider is enabled.

Security checks are off in dev mode, so you don't need to log in, neither in the
UI nor for the API. To try the login, start dev mode with
`QUARKUS_SECURITY_AUTH_ENABLED_IN_DEV_MODE=true mvn quarkus:dev` and log in as
`developer` or `admin`. Code changes in Java and TypeScript are reloaded live.

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

## Sample data

Dev mode seeds sample emissions at every start in which no cloud provider is
enabled, i.e. none of `AWS_ENABLED`, `GCP_ENABLED` and `AZURE_ENABLED` is
`true`, also not in the `.env` file. The packaged application and the tests
never seed any.

- **Teams:** every team in the database, the sample teams or those of your own
  [teams file](#teams-file), with values only for the providers each team has
  projects with. So you can see your own teams on the dashboard before
  connecting a cloud account.
- **Months:** `CARBON_HISTORY_MONTHS` + 1, 14 by default, ending with the
  previous month, so that the oldest month in the charts can be compared with
  the one before.
- **Values:** 40 to 220 kg CO₂e per provider in the latest month, with a trend
  between 1.5 % growth and 5 % reduction per month and some noise on top. They
  are random, but derived from the team name, so a team gets the same curve at
  every start.
- **Achievements** are evaluated like after a sync, for every month that all
  teams have data for.

Existing entries are never changed: each start only adds the missing months,
e.g. a new team's or, at the turn of the month, the new previous month, which
continues each team's curve. To generate all of it anew, e.g. after changing
the projects of a team, stop Quarkus and run `rm -rf data`.

Once a provider is enabled, nothing is seeded any more, but the sample data
already in the database stays: a sync overwrites the sample values of the
months it fetches, but neither the older months nor the achievements already
awarded for them. So delete the database before switching to real data.

## Using real AWS data

The [sample data](#sample-data) is only seeded while no provider is enabled. As soon as one is,
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

Outside dev mode, the teams file, the database and the identity provider have
to be configured:

```bash
export TEAMS_FILE=/etc/green-champions/teams.yaml   # see "Teams file"
export DB_FILE=/var/lib/green-champions/greensoft   # H2 file, without .mv.db
export OIDC_ISSUER_URL=https://keycloak.example.com/realms/green-software
export OIDC_CLIENT_SECRET=<client secret>
export AWS_ENABLED=true
java -jar target/quarkus-app/quarkus-run.jar
```

`DB_FILE` must be absolute or start with `./` or `~/`. H2 creates the file
and its directories on the first start, and only one process can open it at a
time.

### Identity provider (OIDC)

The dashboard redirects to an OpenID Connect provider for the login and keeps
the session in an encrypted cookie. `OIDC_ISSUER_URL` is the provider's issuer,
to which `/.well-known/openid-configuration` is appended. The provider needs:

- a client (`OIDC_CLIENT_ID`, `green-champions` by default) with a secret
  (`OIDC_CLIENT_SECRET`) for the authorization code flow, with the
  application's URL, e.g. `https://champions.example.com/*`, as redirect URI
  and `https://champions.example.com/` as post-logout redirect URI,
- the roles `user`, for the dashboard, and `admin`, for the admin API, in its
  tokens (see [Other providers](#other-providers)).

Users without the role `user` can log in, but are told that they have no
access. The admin API only accepts access tokens, not the dashboard's session
(see [Admin API](#admin-api)).

Behind a reverse proxy that terminates TLS, also set
`QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=true` and
`QUARKUS_HTTP_PROXY_ENABLE_FORWARDED_HOST=true`, so that the application sends
the identity provider its public `https://` URL as redirect URI. Only set them
behind a proxy that overwrites the `X-Forwarded-*` headers.

#### Keycloak

Keycloak needs a realm, e.g. `green-software`, whose URL
`https://keycloak.example.com/realms/green-software` is `OIDC_ISSUER_URL`, with:

- the client, with *Client authentication* and the *Standard flow* enabled,
  and the URLs above as *Valid redirect URI* and *Valid post logout redirect
  URI*,
- the realm roles `user` and `admin`.

Nothing else needs to be configured. Use a realm of its own, as every access
token of the realm with the role `admin` is accepted by the admin API (see
`OIDC_AUDIENCE` below).

#### Other providers

The application is developed against Keycloak; the settings below follow the
other providers' documentation and are untested. The application reads the
roles from the access token, from the claims `groups` (e.g. Okta, authentik),
`realm_access.roles` (Keycloak) and `roles` (the app roles of Microsoft Entra
ID). Depending on the provider, also set:

| Setting                                  | When                                                                                                                                                                                                                       |
|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `QUARKUS_OIDC_ROLES_SOURCE=idtoken`      | The access token of the login isn't meant for this application, so it can't be verified: Entra ID (it's for Microsoft Graph), Okta's org authorization server, Auth0 (opaque). The provider must put the roles in the ID token. |
| `QUARKUS_OIDC_ROLES_ROLE_CLAIM_PATH`     | The roles are in another claim, e.g. one an Auth0 Action adds. Replaces the three claims above.                                                                                                                            |
| [roles mapping](#roles-mapping)          | The roles have other names, e.g. groups of the whole organisation or the group IDs of Entra ID.                                                                                                                             |
| `QUARKUS_OIDC_AUTHENTICATION_SCOPES`     | Further scopes, comma-separated: `offline_access` for refresh tokens (all but Keycloak, which issues them anyway), `profile` for the user name (Entra ID), `groups` (Okta).                                               |
| `OIDC_AUDIENCE`                          | Other applications' tokens come from the same issuer, e.g. all apps of an Entra ID tenant. See below.                                                                                                                     |
| `QUARKUS_OIDC_TOKEN_PRINCIPAL_CLAIM`     | The tokens have neither `upn` nor `preferred_username`, so the dashboard would show the `sub` claim as user name, e.g. with Auth0: set it to `email`.                                                                      |
| `QUARKUS_OIDC_END_SESSION_PATH`          | The provider's discovery document has no `end_session_endpoint`, needed for the logout.                                                                                                                                    |

Without a refresh token, the login session ends with the ID token. The
dashboard then reloads, which leads through the provider's login back to it
without asking for the password while the provider's own session is active.
Don't request `offline_access` from Keycloak, whose offline tokens outlive the
Keycloak session.

**Audience:** the admin API accepts any access token of the issuer with the
role `admin`, unless `OIDC_AUDIENCE` lists the audiences (`aud` claim),
comma-separated, of which the tokens must have one. With Entra ID, the issuer
is the whole tenant, so a token for another application of the tenant with an
app role `admin` would be accepted: set `OIDC_AUDIENCE` to the application's
client ID, the audience of its ID tokens and of the access tokens for its own
API. Once set, every token the application gets must match it: the ID tokens,
whose audience is otherwise checked against the client ID, the access tokens of
scripts and, as long as the roles are read from it, the access token of the
login.

**Not supported:** ZITADEL's roles claim is an object rather than a list,
which Quarkus can't read, and providers without roles, e.g. Google, can't tell
users from admins.

#### Roles mapping

If the provider's roles or groups have other names, map them to `user` and
`admin` in a config file, `config/application.yaml` in the directory the
application is started in:

```yaml
quarkus:
  http:
    auth:
      roles-mapping:
        # name or ID of the provider's role or group: roles of the application
        "GSC-Users": user
        "GSC-Admins": admin,user
        "3fa85f64-5717-4562-b3fc-2c963f66afa6": user   # Entra ID group ID
```

## Configuration

All settings are in [`application.yaml`](src/main/resources/application.yaml)
and can be overridden with these environment variables:

| Variable                                       | Default            | Description                                                          |
|------------------------------------------------|--------------------|----------------------------------------------------------------------|
| `TEAMS_FILE`                                   | –                  | Path of the teams file; required outside dev mode                    |
| `DB_FILE`                                      | `./data/greensoft` in dev mode | Path of the H2 database file without `.mv.db`; required outside dev mode |
| `OIDC_ISSUER_URL`                              | –                  | Issuer URL of the identity provider; required outside dev mode       |
| `OIDC_CLIENT_SECRET`                           | –                  | Client secret; required outside dev mode                             |
| `OIDC_CLIENT_ID`                               | `green-champions`  | Client ID                                                            |
| `OIDC_AUDIENCE`                                | –                  | Required audience of access tokens, see [Other providers](#other-providers) |
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
`-H "Authorization: Bearer $TOKEN"` with an access token of the identity
provider for a user or service account with the `admin` role. The admin API
doesn't accept the session cookie of the dashboard, so that no other website
can make a logged-in admin's browser call it (CSRF); without a token, it
redirects to the login.

## Tests

```bash
mvn test
```

The tests use an in-memory H2 database and the sample teams. Keycloak, the
cloud providers and the scheduler are disabled, so no network, container
runtime or Node.js is needed. Only `SecurityTest` enables OIDC, against a fake
OIDC server (WireMock), and logs in through its login form with HtmlUnit. Only the tests of the GCP provider enable it,
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
- **No sample data in dev mode:** a cloud provider is enabled, e.g. through
  `AWS_ENABLED=true` in the `.env` file, so dev mode syncs instead.
- **Sample data next to real data:** the database still holds the sample data
  of an earlier dev mode start. Stop Quarkus and run `rm -rf data`.
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
