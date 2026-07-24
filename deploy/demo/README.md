# CloudMold internal Demo release

This deployment surface is intentionally limited to an internal, disposable
Demo. It runs the monolith, the management frontend and Redis against a
separately prepared Demo MySQL database. It does not run Flink or StarRocks and
it must not contain the only copy of any data.

## Preflight

1. Build backend and frontend images on CI or a release workstation and tag
   both with their full Git commit SHA.
2. Restore or clone a complete Yudao Demo database, verify the committed
   CloudMold SQL manifest, apply every required migration in release order, and
   record the backup ID and schema checksum. Duplicate version labels exist in
   the historical SQL set, so use the full relative filename from
   `cloudmold-migrations.sha256` instead of treating the numeric prefix as a
   globally unique migration ID. The repository base SQL alone is not a complete
   Mall/Member/WMS schema and must not be used as a fresh release database.
3. Copy `.env.example` to `.env`, replace the database connection, passwords,
   and both image tags. Never commit `.env`.
4. Keep `DEMO_BIND_ADDRESS=127.0.0.1` unless a TLS reverse proxy and an approved
   security-group change are already in place.
5. Back up the Demo database and Redis volume before an upgrade.
6. Use at least 4 vCPU / 8 GiB for the complete stack. A 2 vCPU / 2 GiB host is
   limited to the frontend or one lightweight service by the cloud runbook.

## Validate and start

Run the read-only database gate before creating any application container. It
first rejects added, missing, or modified CloudMold SQL assets, then checks
MySQL 8, the complete Yudao module baseline, every table created by the ordered
CloudMold migrations, representative migration columns, indexes and
constraints, and the V82 business-navigation zero-row checks:

```bash
export CLOUDMOLD_DB_HOST=replace-with-private-db-host
export CLOUDMOLD_DB_NAME=ruoyi-vue-pro
export CLOUDMOLD_DB_USERNAME=replace-with-demo-db-user
export CLOUDMOLD_DB_PASSWORD=replace-with-demo-db-password
deploy/demo/database-preflight.sh
```

The password is passed to the MySQL client through `MYSQL_PWD`; it is not
printed or placed on the process command line. Unset it after validation.

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env pull backend frontend
docker compose --env-file .env up -d
docker compose --env-file .env ps
curl --fail http://127.0.0.1:48080/v3/api-docs
curl --fail http://127.0.0.1:8080/
```

The Compose stack never creates or migrates the database. Validate the complete
schema against a cloned database before deployment; application startup keeps
automatic Flowable schema updates disabled.

The backend image uses the already verified executable artifact as its build
context:

```bash
docker build --file deploy/demo/backend.Dockerfile \
  --build-arg VCS_REF="$BACKEND_GIT_SHA" \
  --tag "cloudmold/backend:$BACKEND_GIT_SHA" \
  yudao-server/target
```

## Roll back

Keep the previous backend and frontend commit-SHA tags. If smoke tests fail,
restore those two values in `.env` and run:

```bash
docker compose --env-file .env up -d --no-deps backend frontend
```

For a schema failure, stop the stack and restore the pre-release database dump.
Do not attempt a destructive production rollback from this Demo runbook.
