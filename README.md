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

Edit `.env` with your following required values:

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

> Note: This project uses functions available only in PostgreSQL 18+, so don't try downgrading the image unless you know what you're doing!

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
| `COMPOSE_PROFILES`        | No       | Set to `criteria` to include the local criteria checker sidecar |
| `COMPLEXITY_AP_TARGET`    | No       | AP target for AI complexity estimation     |
| `COMPLEXITY_ACCURACY_SHIFT` | No    | Accuracy shift for AI complexity estimation |
| `COMPLEXITY_TRANSFORM_OFFSET` | No  | Curve transform offset parameter           |
| `COMPLEXITY_TRANSFORM_SCALE` | No   | Curve transform scale parameter            |
| `COMPLEXITY_TRANSFORM_BASE` | No    | Curve transform base parameter             |
| `CRITERIA_CHECKER_URL`    | No       | Override for the criteria checker sidecar URL |
| `MAP_ZIP_CACHE_PATH`      | No       | Folder where downloaded BeatSaver map zips are kept, so the complexity script and the criteria check never fetch the same map twice. Defaults to `./data/map-zips` |
| `COMPLEXITY_MODEL_URL`    | No       | Override for the complexity model sidecar URL, the service that turns a map file into per-note accuracies for the complexity script |
| `DISCORD_OAUTH_CLIENT_ID` | OAuth    | Discord application client ID              |
| `DISCORD_OAUTH_CLIENT_SECRET` | OAuth | Discord application client secret         |
| `DISCORD_OAUTH_REDIRECT_URI` | OAuth | Backend callback URL for Discord (`{api}/v1/auth/discord/callback`) |
| `BEATLEADER_OAUTH_CLIENT_ID` | OAuth | BeatLeader application client ID           |
| `BEATLEADER_OAUTH_CLIENT_SECRET` | OAuth | BeatLeader application client secret   |
| `BEATLEADER_OAUTH_REDIRECT_URI` | OAuth | Backend callback URL for BeatLeader (`{api}/v1/auth/beatleader/callback`) |
| `STEAM_OPENID_REALM`      | OAuth    | Public backend origin used as the Steam OpenID realm |
| `STEAM_OPENID_RETURN_TO`  | OAuth    | Backend callback URL for Steam (`{realm}/v1/auth/steam/callback`) |
| `OAUTH_ALLOWED_RETURN_ORIGINS` | OAuth | Comma-separated frontend origins the OAuth flow may redirect back to |
| `ACCSABER_DOMAINS`        | No   | Comma-separated site domains used for CORS, auth cookie scoping, OG tags, and OpenAPI server URLs. Defaults to `accsaber.com,accsaberreloaded.com`. |
| `KOFI_VERIFICATION_TOKEN` | No   | Ko-fi webhook verification token (from `ko-fi.com/manage/webhooks`).|
| `CDN_STORAGE_PATH`        | No   | Where encoded WebP files are written. Defaults to `./data/cdn` locally, `/var/cdn` in prod. |
| `CDN_BASE_URL`            | No   | Public URL prefix returned in `avatarUrl`/`coverUrl`/`iconUrl`/`backgroundUrl`. |
| `CDN_VIPS_BINARY`         | No   | Path to the `vips` CLI. Override if not on `$PATH`. |
| `CDN_WEBP_QUALITY`        | No   | WebP encode quality (0-100). Default 80. |
| `CDN_WEBP_EFFORT`         | No   | WebP encode effort (0-6, higher = smaller file, slower). Default 4. |
| `CDN_AVATAR_MAX_DIMENSION` | No  | Pixel cap for mirrored player avatars. Default 256. |
| `CDN_COVER_MAX_DIMENSION` | No   | Pixel cap for mirrored map covers. Default 1024. |
| `CDN_AVIF_QUALITY`        | No   | AVIF encode quality (0-100). Default 60. |
| `CDN_AVIF_EFFORT`         | No   | AVIF encode effort (0-9, higher is smaller and slower). Default 4. |
| `CDN_PNG_COMPRESSION`     | No   | PNG compression level (0-9). Default 6. |
| `RATE_LIMIT_TRUSTED_IPS`  | No   | Comma separated IPs that skip the 400 requests per 60 seconds limit. Empty means nobody is exempt. |
| `SONGSUGGEST_OUTPUT_PATH` | No   | Where the weekly SongSuggest leaderboard export gets written. |
| `IMAGE_TAG`               | No   | Which published backend image the production compose file pulls. Defaults to `latest`; set it to a `v` tag to pin or roll back. |
| `WS_START_DELAY_SECONDS`  | No   | How long to wait after boot before connecting the BeatLeader and ScoreSaber sockets. Defaults to 0 locally and 60 in production, which keeps a freshly started container quiet until the one it is replacing has gone, so the same score never gets ingested twice. |
| `BACKFILL_STARTUP_DELAY_SECONDS` | No | How long to wait after boot before the startup gap-fill runs. Gives the app a moment to come up and start serving before a couple of days of backfill lands on it. |
| `IMPERSONATION_ENABLED`   | No   | Turns on the staff endpoint that mints a player token for any account, so you can browse as that player. Only works when the `staging` profile is active, and the app refuses to start if you enable it anywhere else. Defaults to off. |
| `IMPERSONATION_TOKEN_TTL` | No   | How many seconds an impersonation token stays valid. Defaults to 3600. |
| `RELEASE_CHANNEL`         | No   | The label shown next to the version, like `BETA` or `RC`. It comes back from `/v1/health/ping` so the site can show which build people are on. Leave it blank once a version is a proper release and the label disappears. Defaults to `BETA`. |
| `MISSION_ASSIGN_ACTIVE_DAYS` | No | How recently somebody has to have played before the nightly roll hands them daily and weekly missions. Defaults to 90 days. Anyone outside the window picks missions up the next time they log in, and keeping the window tight is what stops the completion rate on the stats page being watered down by people who never saw the mission. |
| `CDN_UPLOAD_MAX_DIMENSION`| No   | Pixel cap for **uploaded** content (campaign bg/icon, item icon, user-uploaded avatar). Default 4096. Effectively unconstrained; admin/user controls dimension. |
| `CDN_MAX_UPLOAD_BYTES`    | No   | Hard ceiling on uploaded file size in bytes. Default 10485760 (10 MB). |
| `CDN_BACKFILL_DELAY_MS`   | No   | Throttle between tasks in the avatar/cover backfill loop. Default 0 (no throttle). Set higher to gentle the system or upstream APIs. |
| `CDN_ENCODE_TIMEOUT_MS`   | No   | Per-encode timeout for the vips subprocess. Default 30000 (30s). |
| `ITEM_FILES_STORAGE_PATH` | No   | Directory downloadable item files (sabers/pedestals) are served from. |
| `ITEM_FILES_SIGNING_KEY`  | Downloads | Base64 Ed25519 private key (PKCS#8 DER) used to sign each item-file download per player. Generate with `openssl genpkey -algorithm ed25519 -outform DER \| base64 -w0`. Required only to serve downloadable items; use a separate key per environment. |

> **OAuth** vars are required to run the player/staff OAuth login flow. Leave blank to disable OAuth - username/password staff login still works.

### Important Note on AI Usage
> *AccSaber Reloaded's infrastructure, flow and core features are human-made. Tedious tasks were automated with the help of AI (tests, DTOs, some methods). The codebase is manually reviewed and edited, and all creative input is human-generated. I firmly believe that human creativity and intuition are irreplaceable in software development.*
> *If you do not agree with this approach and you are a vibe-coder OR you take a firm No-AI stance, please refrain from using or contributing to this project.*
> 
