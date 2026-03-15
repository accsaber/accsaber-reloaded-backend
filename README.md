# AccSaber Backend

REST API for the AccSaber Reloaded platform, an accuracy-based leaderboard system for Beat Saber. Built with Java 25, Spring Boot 4 and PostgreSQL.

## Requirements

- Docker and Docker Compose (v2)
- Git

That's it. Everything runs in containers.

## Quick Start (Development)

```bash
# 1. Clone and configure
git clone https://github.com/Tikugato/accsaber-reloaded-backend.git accsaber-backend
cd accsaber-backend
cp .env.example .env
```

Edit `.env` with your values:

```
POSTGRES_DB=accsaber
POSTGRES_USER=accsaber
POSTGRES_PASSWORD=your_password_here
SPRING_PROFILES_ACTIVE=dev
JWT_SECRET=your_jwt_secret_here
SERVICE_API_KEY=your_api_key_here
```

```bash
# 2. Start everything
docker compose up --build
```

The API will be available at `http://localhost:8080`. Swagger docs at `http://localhost:8080/v1/docs`.

Dev mode exposes all ports for local debugging:

| Service    | URL                        |
|------------|----------------------------|
| API        | http://localhost:8080       |
| Swagger    | http://localhost:8080/v1/docs |
| PostgreSQL | localhost:5433              |
| Prometheus | http://localhost:9090       |
| Grafana    | http://localhost:3000       |

### Test Data
1. Create an administrator account via directly in the database.
2. Manually insert some data via the ranking workflow (`/v1/ranking/maps/import`, `/v1/batches`, `/v1/batches/{id}/release`) & milestones (`/v1/admin/milestones`).
3. Scores and players will sync automatically from ScoreSaber and BeatLeader!
> Note: I do not recommend running a full import of ALL ranked maps in dev mode, as the backfill is slow and intensive. Use the admin endpoints to create a smaller test dataset.

## Monitoring

Prometheus and Grafana are included in both compose files. Grafana comes pre-provisioned with:
- **System Health** dashboard - JVM metrics, HTTP request rates, database connections
- **Player Activity** dashboard - score submissions, player counts

Access Grafana at port 3000 (default login: `admin` / your `GRAFANA_ADMIN_PASSWORD`).

The backend exposes metrics at `/actuator/prometheus` and health details at `/actuator/health`.

## Project Structure

```
src/main/java/com/accsaber/backend/
  config/           Spring configuration (security, async, caching, health indicators)
  controller/       REST endpoints grouped by access tier and domain
  service/          Business logic grouped by domain (score/, map/, stats/, milestone/, campaign/, player/, staff/, infra/)
  repository/       Data access interfaces grouped by domain
  model/
    entity/         JPA entities grouped by domain
    dto/            Request/response DTOs grouped by domain
  exception/        Custom exceptions and global error handler
  util/             Static utility classes

src/main/resources/
  application.yml           Base config
  application-dev.yml       Dev overrides
  application-prod.yml      Prod overrides
  logback-spring.xml        Logging config
  db/migration/             Flyway SQL migrations
```

## Configuration Reference

| Environment Variable       | Required | Description                                |
|---------------------------|----------|--------------------------------------------|
| `POSTGRES_DB`             | Yes      | Database name                              |
| `POSTGRES_USER`           | Yes      | Database username                          |
| `POSTGRES_PASSWORD`       | Yes      | Database password                          |
| `JWT_SECRET`              | Yes      | Secret key for signing JWT tokens          |
| `SERVICE_API_KEY`         | Yes      | API key for authenticated score submission |
| `SPRING_PROFILES_ACTIVE`  | No       | `dev` or `prod` (prod compose hardcodes this) |
| `GRAFANA_ADMIN_PASSWORD`  | No       | Grafana admin password (default: `admin`)  |
