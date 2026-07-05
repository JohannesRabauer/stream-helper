# PRD: Stream Helper (As-Built)

## 1. Product Overview
Stream Helper is a local-first Spring Boot + Thymeleaf application for live-coding creators. It supports planning, promotional asset generation, transcription, and post-stream wrap-up in project-scoped workspaces backed by plain files.

The app runs in two provider modes:
1. Ollama + local Whisper ASR
2. OpenAI for text/image/transcription

## 2. Product Goals
1. Keep stream prep and post-processing in one project workflow.
2. Save all work as portable on-disk files, with no database dependency.
3. Provide repeatable AI generation with instruction layering and reusable context.
4. Preserve history with versioned artifacts and explicit final selection.

## 3. Target User and Context
- Single user operating locally.
- Typical flow: plan stream -> prepare title/description/thumbnail/social -> transcribe recording -> generate chapters and summary -> export project ZIP.

## 4. In-Scope Functionality (Current)

### 4.1 Project and Workspace Management
- Create, list, rename, and delete projects.
- Each project has:
  - metadata (`project.json`)
  - notes
  - config
  - generated outputs by category
  - export folder

### 4.2 Workflow UI
Six stage tabs in one project cockpit:
1. Pre-stream planning
2. Description
3. Thumbnail
4. Social Media (Announcements)
5. Transcription
6. Post-stream wrap-up

Each stage has:
- autosaved working draft (except transcription stage)
- action buttons mapped to AI/transcription endpoints
- inline latest result panel
- stage-local history panel

### 4.3 Notes and Definitions
- Project notes drawer with Markdown edit + rendered preview.
- Autosave for project notes.
- LLM definitions drawer with:
  - global definitions (cross-project)
  - project definitions
  - stage definitions (applied to all categories in that stage)

### 4.4 AI Output Generation
Supported generation categories:
- Topic ideas (3 variants)
- Guest ideas (3 variants)
- YouTube titles (15 distinct options, exactly one recommended)
- YouTube descriptions (3 variants)
- LinkedIn posts (3 variants)
- Social posts (3 variants, normalized to <= 280 chars)
- Hashtags (1 variant, normalized and deduplicated)
- YouTube tags (1 variant, normalized to <= 500 chars)
- Thumbnail ideas (target output: 10 idea blocks)
- Thumbnail prompts (3 variants)
- Chapters (1 variant from latest transcript)
- Summary (1 variant from latest transcript)

### 4.5 Thumbnail Creation
Two paths:
1. External prompt package (text artifact)
2. Built-in image generation (OpenAI image API only)

Built-in path stores:
- binary PNG in outputs
- artifact marker entry in thumbnail assets history

### 4.6 Artifact Versioning and Refinement
- Every generation result is persisted as an artifact version.
- User can:
  - mark any artifact final
  - edit supported categories inline (edit saves a new version)
  - refine any artifact by prompt (thread lineage stored)
- History and inline views show badges:
  - recommended
  - final

### 4.7 Transcription
Input modes:
1. local file upload (multipart)
2. YouTube URL (yt-dlp download)

Provider behavior:
- `WHISPER_LOCAL`: calls Whisper webservice endpoint
- `OPENAI`: calls `/v1/audio/transcriptions`
- OpenAI mode chunks oversized audio using ffmpeg (segmenting) and merges offset timestamps

Transcription output:
- timestamped line format `[start - end] speaker: text`
- stored as transcript artifact
- optional host/guest relabeling of first two diarized speakers

Progress tracking:
- per-project progress snapshot endpoint
- running/completed/failed state, percent, stage, message, timestamps

### 4.8 Export
- Project ZIP export endpoint.
- Includes `manifest.json` plus all project files except previous exports.

## 5. Prompt Composition and Context Rules (Current)
- Instruction blocks included in composed prompt:
  - global instruction
  - project instruction
  - project category instruction
  - workflow notes + selected prior artifacts
  - merged brand profile
  - participants block
  - precedence text

- Context category set is target-dependent and excludes transcript for most generation flows.
- Chapters and summary require a stored transcript; they fail if none exists.

## 6. Validation and Normalization Rules (Current)
- YouTube tags: comma list trimmed to <= 500 chars; empty and overflow checks.
- Hashtags: adds `#` prefix and removes duplicates; duplicate detection in validation.
- Social posts: checks `<= 280`.
- Chapters:
  - prepends `00:00 Introduction` if missing
  - validates timestamp format and ascending order
  - validates first chapter starts at `00:00`

## 7. Non-Goals / Out of Scope (Current)
- Direct publishing to social platforms.
- Multi-user collaboration/authentication/permissions.
- Scheduling/reminders/calendar automation.
- Analytics integrations and external ranking systems.

## 8. Acceptance Criteria (As-Built)
1. User can create/open/rename/delete projects from UI and API.
2. Workflow has six tabs with stage-local autosaved drafts and history.
3. Project notes support edit + preview with autosave.
4. LLM definitions support global/project/stage text instructions.
5. AI generation endpoints persist versioned artifacts and return category-scoped results.
6. YouTube title endpoint returns 15 options with one recommended.
7. Social post outputs in returned variants are capped at 280 chars.
8. Chapters and summary can be generated from latest stored transcript.
9. Transcription supports local files and YouTube URLs with progress reporting.
10. Finalization and refinement flows keep artifact lineage and final marker behavior.
11. ZIP export returns downloadable archive with manifest.
12. Deployment can run via Docker Compose in Ollama or OpenAI profile.
