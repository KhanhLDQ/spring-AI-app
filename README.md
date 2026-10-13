# Kefir AI assistant (Spring AI demo)

A shop assistant built to show what Spring AI does: streaming chat, tool calling, RAG over uploaded PDFs, chat memory and a semantic cache, with an Inspector that traces every step.

Spring Boot 4.1 + Spring AI 2.0, Java 25, Postgres/pgvector, React (built into the jar).

## Run it (macOS, Linux, Windows)

Needs JDK 25 and a running Docker (Rancher Desktop with the dockerd/moby backend, or Docker Desktop). No Node needed: the build downloads its own.

| Step | macOS / Linux | Windows (PowerShell) |
|---|---|---|
| 1. Setup (once) | `scripts/setup.sh` | `scripts\setup.ps1` |
| 2. Add API keys | edit `.env`: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` | same |
| 3. Run | `scripts/run.sh` | `scripts\run.ps1` |

Open http://localhost:8080 and choose **Login as User** or **Login as Admin**.

`scripts/run.sh mock` (`run.ps1 mock`) runs with a scripted fake model and fake embeddings: no keys, no cost. Use it to rehearse the flow. The real-model profile is `demo`.

On Windows clone into a short path (for example `C:\work\spring-AI-app`).

## Develop

```
docker compose up -d
(set -a; source .env; set +a; ./mvnw spring-boot:run)   # backend on :8080, mock profile
npm --prefix frontend install && npm --prefix frontend run dev   # UI on :5173, proxies /api
./mvnw verify                                            # tests use Testcontainers
```

`./mvnw -DskipTests package -Dskip.installnodenpm -Dskip.npm` builds the jar without rebuilding the UI.

## Configuration

Only environment variables (see `.env.example`): `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `DB_PASSWORD`, `JWT_SIGNING_KEY`. Profiles: `mock` (default), `dev` (real models, spend capped by the budget setting), `demo` (real models).
