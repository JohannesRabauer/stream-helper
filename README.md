# stream-helper

Local-first Spring Boot + Thymeleaf application for planning, promoting, and post-processing live-coding streams with AI.

## Features

- Project-based workspace with file-backed storage (`schemaVersion: 1`)
- Markdown notes with edit + rendered preview
- Instruction layering (Global + Project + Category)
- Brand profile constraints
- AI generation for:
  - topics
  - guest ideas
  - YouTube descriptions
  - LinkedIn posts
  - social posts
  - hashtags
  - YouTube tags (500 char max enforcement)
  - chapter generation
  - detailed summaries
  - thumbnail prompts and creation flow
- Post-stream transcription:
  - local file upload
  - YouTube URL via `yt-dlp`
  - speaker diarization when available
- Versioned drafts + final selection for generated outputs
- Raw-folder persistence + ZIP export with manifest

## One-command Docker Compose startup

### Option 1: Local AI mode (Ollama + local Whisper ASR)

```bash
docker compose --profile ollama up --build
```

App URL: `http://localhost:8080`

### Option 2: OpenAI mode (no Ollama runtime required)

Set your key first:

```bash
export OPENAI_API_KEY=your_key_here
```

Then run:

```bash
docker compose --profile openai up --build
```

App URL: `http://localhost:8080`

## Local dev (without Docker)

```bash
mvn spring-boot:run
```

Data is stored in `./data` by default.

## Tests

```bash
mvn test
```
