# Saigon.Quest Content Server

A separate Ktor backend responsible for accepting authenticated file uploads and serving them statically with long‑term caching. It maintains its own ArangoDB database for upload records.

## Features
- Validates API bearer tokens by calling the main API backend (`/auth/validate`).
- Stores files under a local `static/` directory with UUID‑prefixed filenames.
- Persists upload metadata in ArangoDB (`saigon_quest_uploads`):
  - `userId`, `originalFileName`, `uploadedFilePath`, `createdAt`.
- Serves static files at `/static/...` with gzip and 1‑year cache headers.

## Module Layout
- Server entry: `content/src/main/kotlin/ContentServer.kt`
- DB config: `content/src/main/kotlin/database/ArangoConfig.kt`
- Repo: `content/src/main/kotlin/repository/UploadRepository.kt`
- Config: `content/src/main/resources/application.conf`

## Dependencies
- Requires the main API backend running and reachable at `API_BACKEND_URL`.
- Requires ArangoDB running locally (defaults match backend):
  - host `localhost`, port `8529`, db `saigon_quest_uploads`, user `saigonquest`, pass `saigonquest`.

## Setup ArangoDB
Use the existing setup script (from module `:backend`) which now also provisions the uploads DB and collection:

```
./backend/setup_arango_db.sh
```

This creates:
- DB `saigon_quest`
- DB `saigon_quest_uploads`
- Collection `content_uploads` in `saigon_quest_uploads`
- Grants RW access to user `saigonquest`.

## Run Locally
- Start API backend (for token validation):
```
./gradlew :backend:run
```
- Start content server (pointing at API backend URL):
```
API_BACKEND_URL=http://localhost:8080 ./gradlew :content:run
```
- Default port: `8090` (configurable via `PORT` env var in `application.conf`).

### Frontend Integration
- The web app expects the content server base URL from env var `CONTENT_SERVER_URL` at build time.
- Example build/run with both backends locally:
```
CONTENT_SERVER_URL=http://localhost:8090 ./gradlew :browser:run
```
If not provided, the UI falls back to `http://localhost:8090`.

## API
### POST `/upload`
- Multipart form fields:
  - `file`: the binary file content
  - `API_USER_BEARER_TOKEN`: bearer token acquired from API backend
- Behavior:
  1) Validates token via `GET {API_BACKEND_URL}/auth/validate`.
  2) Saves file to `./static/{UUID}_{sanitizedOriginalName}`.
  3) Creates a `ContentUpload` record in ArangoDB.
  4) Returns the created record.
- Example:
```
curl -F "file=@/path/to/image.png" \
     -F "API_USER_BEARER_TOKEN=$TOKEN" \
     http://localhost:8090/upload
```
- Response (201):
```
{
  "key": "...",
  "createdAt": "...",
  "userId": "...",
  "originalFileName": "image.png",
  "uploadedFilePath": "/static/3f..._image.png"
}
```

### GET `/static/{filename}`
- Serves uploaded file contents.
- Adds `Cache-Control: public, max-age=31536000, immutable`.
- Gzip compression enabled.

## Configuration
- `API_BACKEND_URL`: Required. Base URL of API backend (e.g., `http://localhost:8080`).
- `PORT`: Optional. Overrides default port in Ktor `application.conf`.
- ArangoDB connection defaults are defined in `database/ArangoConfig.kt` and can be adjusted or wired to env vars if needed.
 - Frontend build: `CONTENT_SERVER_URL` to point uploads (default `http://localhost:8090`).

## Notes / Security
- Filenames are sanitized and prefixed with a UUID to avoid collisions and path traversal.
- No separate auth system: tokens are validated explicitly with the API backend.
- Consider adding file size limits, MIME/type allowlists, and anti‑abuse rate limits for production.

## Build
```
./gradlew :content:assemble
```

## Troubleshooting
- Ensure the API backend is running and `API_BACKEND_URL` is set correctly; otherwise `/upload` will return Unauthorized.
- Ensure ArangoDB is running and the uploads DB exists: `./backend/setup_arango_db.sh`.
- Check server logs for errors: `build/install/content/bin/content` run output or Gradle run logs.
