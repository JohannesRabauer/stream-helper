# Stream Helper Specification (Current Implementation)

## 1. Purpose
This document specifies the implemented behavior of Stream Helper as of the current codebase. It is an as-built technical specification, not a future roadmap.

## 2. System Overview

### 2.1 Stack
- Java 21
- Spring Boot 3.5.x
- Spring MVC + Thymeleaf
- Flexmark for Markdown rendering
- File-system persistence (no relational database)

### 2.2 Runtime Modes
`docker-compose.yml` supports two app profiles:
1. `ollama`: app + ollama + whisper ASR + ollama model init
2. `openai`: app only, configured for OpenAI APIs

### 2.3 Core Boundaries
- Server-rendered pages: `/projects`, `/projects/{projectId}`
- JSON APIs under `/api/...`
- All persistent data in `stream-helper.storage.data-dir` (default `./data`)

## 3. Configuration

## 3.1 Application Properties
Main keys (`application.properties`):
- `stream-helper.storage.data-dir`
- `stream-helper.ai.provider` (`ollama` or `openai`)
- `stream-helper.ai.timeout-seconds`
- `stream-helper.ai.ollama.base-url`, `.model`
- `stream-helper.ai.openai.base-url`, `.api-key`, `.chat-model`, `.image-model`
- `stream-helper.transcription.provider` (`whisper-local` or `openai`)
- `stream-helper.transcription.whisper-base-url`
- `stream-helper.transcription.openai.base-url`, `.api-key`, `.model`
- `stream-helper.yt-dlp.command`
- `stream-helper.ffmpeg.command`

## 3.2 Docker Environment Inputs
From `.env.example` and compose:
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_CHAT_MODEL`
- `OPENAI_IMAGE_MODEL`
- `OPENAI_TRANSCRIPTION_MODEL`
- `OLLAMA_MODEL`
- `WHISPER_MODEL`
- `APP_PORT`

## 4. Domain Model

### 4.1 Core Types
- `ProjectMetadata`: `id`, `name`, `schemaVersion`, `createdAt`, `updatedAt`
- `ProjectConfig`:
  - `schemaVersion`
  - `defaultLanguage`
  - `directives` (`projectInstruction`, `categoryInstructions`)
  - `brandProfile`
  - `currentWorkflowStage`
  - `hostDisplayName`, `guestDisplayName`
  - `workspaceDrafts` map
- `GlobalConfig`:
  - `schemaVersion`
  - `globalInstruction`
  - `categoryInstructions` map (stored, currently not used in prompt composition)
  - `brandProfile`
- `BrandProfile`:
  - `preferredColors[]`
  - `requiredWords[]`
  - `bannedWords[]`
  - `thumbnailMaxWords`
- `ArtifactVersion`:
  - `id`, `category`, `strategy`, `content`
  - `parentArtifactId`, `threadId`, `refinementPrompt`
  - `recommended`, `finalVersion`, `createdAt`
- `VariantResult`:
  - `category`
  - `effectivePromptPreview`
  - `variants[]`
  - `validationIssues[]`

### 4.2 Generation Categories
`TOPIC_IDEA`, `GUEST_IDEA`, `YOUTUBE_TITLES`, `YOUTUBE_DESCRIPTION`, `LINKEDIN_POST`, `SOCIAL_POST`, `HASHTAGS`, `YOUTUBE_TAGS`, `TRANSCRIPT`, `CHAPTERS`, `SUMMARY`, `THUMBNAIL_PROMPT`, `THUMBNAIL_ASSET`, `THUMBNAIL_IDEA`.

## 5. Storage Specification

## 5.1 Root Layout
Under data dir:
- `projects/`
- `global/`

## 5.2 Global Files
- `global/global-config.json`

## 5.3 Project Layout
For each project id:
- `project.json`
- `notes/`
- `sessions/`
- `outputs/`
- `transcripts/`
- `thumbnails/`
- `config/project-config.json`
- `exports/` (created lazily on export)

## 5.4 Notes
- Stored as `notes/{noteId}.md`
- If note id not provided, generated `note-{8char}`

## 5.5 Artifacts
- Stored per category in `outputs/{category-lower}/`
- Each artifact version: `{artifactId}.json`
- Final pointer file: `final.txt` with selected artifact id
- Binary assets (for thumbnails) saved in same category folder with random id and extension

## 5.6 Manifest
`buildProjectManifest(projectId)` returns:
- `schemaVersion`
- `projectId`
- `name`
- `createdAt`
- `updatedAt`
- `defaultLanguage`
- `categories` (all enum names)

## 6. Prompt Composition Specification

`InstructionComposer.compose(projectId, category)` builds system prompt with:
1. global instruction
2. project instruction
3. project category instruction
4. workflow notes and selected prior artifact context
5. merged brand profile (global + project)
6. participants section (host/guest if present)
7. precedence note text

Context selection uses category-dependent allowlists (`relevantContextCategories`). Transcript context is intentionally excluded from most flows.

Project notes source:
- use `project-notes` when present
- else build fallback from legacy stage note ids (`pre-stream-notes`, `description-notes`, etc.)

## 7. AI Client Specification

## 7.1 Provider Dispatch
`ActiveAiClient.generateText()`:
- OLLAMA -> POST `{baseUrl}/api/generate`
- OPENAI -> POST `{baseUrl}/v1/chat/completions`

## 7.2 OpenAI Image
`generateImagePng(prompt)`:
- only when provider is OPENAI
- POST `{baseUrl}/v1/images/generations`
- expects `data[0].b64_json`
- decodes to PNG bytes

## 7.3 Error Contract
- Missing OpenAI API key throws `AiClientException`.
- API/request failures throw `AiClientException`.

## 8. Generation and Validation Behavior

## 8.1 Variant Strategies
- Topic ideas: 3 strategies
- Guest ideas: 3 strategies
- YouTube description: 3 strategies
- LinkedIn: 3 strategies
- Social posts: 3 strategies
- Hashtags: default
- YouTube tags: default
- Thumbnail prompts: 3 strategies
- Chapters: default
- Summary: default

### 8.2 YouTube Titles
Special flow:
- one AI call requesting exactly 15 lines with one recommended marker
- parser extracts normalized options
- deduplicates by lowercase title
- ensures exactly one recommended (fallback first item)
- stores each title as separate artifact

## 8.3 Social Post Normalization
After generation, each variant is truncated to 280 characters (with ellipsis when trimmed) and re-saved as `*-normalized` artifact version; returned result uses normalized variants.

## 8.4 Output Validation Rules
`OutputValidationService`:
- `YOUTUBE_TAGS`:
  - normalize by comma tokenization and trim
  - enforce max 500 chars by truncating token list
  - validate empty and overflow
- `HASHTAGS`:
  - normalize to `#tag` format with dedupe
  - validate empty and duplicate
- `SOCIAL_POST`:
  - validate empty and >280 chars
- `CHAPTERS`:
  - normalize prepend `00:00 Introduction` when missing
  - validate line format, ascending timestamps, first starts at `00:00`

## 9. Artifact Lifecycle

### 9.1 Create
Every generated result is persisted with new artifact id and timestamp.

### 9.2 Finalize
`markFinal(projectId, category, artifactId)`:
- toggles `finalVersion` for all versions in category
- writes selected id to `final.txt`

### 9.3 Edit
`saveArtifactEdit(...)`:
- creates new artifact with `-edited` strategy variant
- links `parentArtifactId` and `threadId`
- immediately marks edited version as final

### 9.4 Refine
`refineArtifact(...)`:
- reads source artifact
- generates rewritten content using refinement prompt
- stores new artifact with `-refined` strategy
- preserves thread lineage and stores `refinementPrompt`

## 10. Transcription Specification

## 10.1 Inputs
- Multipart upload: `/transcripts/file`
- YouTube URL: `/transcripts/youtube`

## 10.2 YouTube Download
`YouTubeAudioDownloader`:
- runs `yt-dlp` with retries, JS runtimes (`node,deno`), extractor args
- converts to mp3 output
- timeout: 15 minutes
- maps known failure signatures to user-friendly messages

## 10.3 Provider Paths
- `WHISPER_LOCAL`:
  - POST multipart to `{whisperBaseUrl}/asr?output=json&task=transcribe...`
  - optional `language` and `diarize=true`
- `OPENAI`:
  - POST multipart to `{baseUrl}/v1/audio/transcriptions`
  - model from config
  - verbose segment output parsing

## 10.4 OpenAI Large Audio Chunking
- hard limit: 25 MiB
- if above limit:
  - split with ffmpeg into ~540s mp3 chunks
  - transcribe each chunk
  - offset segment timestamps by chunk start
  - merge
- ffmpeg chunking timeout: 20 minutes

## 10.5 Transcript Output Format
`toPlainTranscript(entries)` line format:
`[MM:SS - MM:SS] Speaker: text`
or with hours when needed.

## 10.6 Participant Relabeling
If host/guest names are configured:
- first non-unknown diarized speaker alias -> host
- second non-unknown -> guest
- applied before plain transcript serialization

## 10.7 Progress Tracking
`TranscriptionProgressService` stores per-project snapshots:
- `active`, `failed`, `percent`, `stage`, `message`
- `startedAt`, `updatedAt`, `completedAt`

Lifecycle:
- `start()` -> active snapshot
- `update()` -> monotonic percent while active
- `complete()` -> percent 100, completed
- `fail()` -> failed terminal snapshot
- `get()` -> idle snapshot if none exists

## 11. Web/API Specification

## 11.1 Page Routes
- `GET /` -> redirect `/projects`
- `GET /projects` -> projects page
- `POST /projects` -> create project then redirect to project page
- `GET /projects/{projectId}` -> project cockpit page
- `POST /projects/{projectId}/notes` -> note save redirect flow (server-rendered path)

## 11.2 Project API (`/api`)
- `GET /projects`
- `POST /projects`
- `GET /projects/{projectId}`
- `PUT /projects/{projectId}` rename
- `DELETE /projects/{projectId}`
- `GET /projects/{projectId}/config`
- `PUT /projects/{projectId}/config`
- `GET /config/global`
- `PUT /config/global`
- `GET /projects/{projectId}/notes`
- `GET /projects/{projectId}/notes/{noteId}`
- `POST /projects/{projectId}/notes`
- `DELETE /projects/{projectId}/notes/{noteId}`
- `GET /projects/{projectId}/artifacts/{category}`
- `PUT /projects/{projectId}/artifacts/{category}/{artifactId}` (edit -> new version)
- `GET /projects/{projectId}/export` (zip binary)

## 11.3 Assistant API (`/api/projects/{projectId}`)
- `POST /topic-ideas`
- `POST /guest-ideas`
- `POST /youtube-description`
- `POST /youtube-titles`
- `POST /linkedin-post`
- `POST /social-posts`
- `POST /hashtags`
- `POST /youtube-tags`
- `POST /transcripts/file` (multipart)
- `POST /transcripts/youtube`
- `GET /transcripts/progress`
- `POST /chapters`
- `POST /summary`
- `POST /thumbnail-prompts`
- `POST /thumbnail-ideas`
- `POST /thumbnails/create`
- `POST /artifacts/{category}/{artifactId}/finalize`
- `POST /artifacts/{category}/{artifactId}/refine`
- `POST /effective-prompt/{category}`

## 11.4 Request DTO Validation
- `CreateProjectRequest.name`: `@NotBlank`
- `RenameProjectRequest.name`: `@NotBlank`
- `GenerationRequest.brief`: `@NotBlank`
- `RefineArtifactRequest.prompt`: `@NotBlank`
- `ThumbnailCreateRequest.prompt`: `@NotBlank`
- `YouTubeTranscriptionRequest.youtubeUrl`: `@NotBlank`

## 11.5 Error Mapping
`ApiExceptionHandler`:
- `NotFoundException` -> `404` `{error: NOT_FOUND, ...}`
- `StorageException`, `AiClientException`, `TranscriptionException` -> `400` `{error: REQUEST_FAILED, ...}`
- validation exceptions -> `400` `{error: VALIDATION_ERROR, ...}`
- `MaxUploadSizeExceededException` -> `413` `{error: UPLOAD_TOO_LARGE, ...}`

## 12. Frontend Behavior (`project.js`, `projects.js`)

## 12.1 Workflow Areas
Area-to-category mapping:
- pre-stream: `TOPIC_IDEA`, `GUEST_IDEA`
- description: `YOUTUBE_TITLES`, `YOUTUBE_DESCRIPTION`, `YOUTUBE_TAGS`
- thumbnail: `THUMBNAIL_IDEA`, `THUMBNAIL_PROMPT`, `THUMBNAIL_ASSET`
- social-announcements: `LINKEDIN_POST`, `SOCIAL_POST`, `HASHTAGS`
- transcription: `TRANSCRIPT`
- post-stream: `CHAPTERS`, `SUMMARY`

## 12.2 Autosave
- Project drafts (`workspaceDrafts`) autosave via `/api/projects/{id}/config`
- Project notes autosave via `/api/projects/{id}/notes` with fixed note id `project-notes`
- Global definitions autosave via `/api/config/global`

## 12.3 Inline Result and History
- Latest result panel renders variants and validation issues.
- History panel loads artifacts per category and groups by category.
- Editable categories support in-place textarea editing; each save creates new artifact version.

## 12.4 Refinement UX
- Each artifact card can send refine prompt to `/refine`.
- Thread turns shown by `refinementPrompt` lineage per `threadId`.

## 12.5 Finalization UX
- Mark final button calls `/finalize`.
- History refresh updates final badges.

## 12.6 Transcription UX
- Polls `/transcripts/progress` every 1.2s while active.
- Displays progress bar, stage, message, percent.

## 12.7 Legacy Migration
On load, script migrates old `promotion` draft to:
- `description`
- `thumbnail`
- `social-announcements`
and maps old workflow stage `promotion` to `description`.

## 13. Export Specification

`ProjectExportService.exportProjectZip(projectId)`:
1. ensure project exists
2. create `exports/` in project dir
3. create `export-{timestamp}.zip`
4. add generated `manifest.json`
5. add all regular project files except files under `exports/`

Response:
- `application/octet-stream`
- content disposition attachment with zip file name

## 14. Security and Operational Notes
- API keys are provided via config/env; no credential store abstraction.
- No authn/authz layer; intended for trusted local use.
- Large uploads allowed (up to 8GB servlet/tomcat settings).
- Docker image includes `ffmpeg`, `yt-dlp`, and `nodejs` for transcription/download flows.

## 15. Known Gaps / Current Constraints
1. No direct social publishing integrations.
2. No user/account model.
3. Brand profile is part of config model and prompt composition, but no dedicated UI editor for brand fields.
4. Global category instruction map exists in `GlobalConfig` but only global free-text instruction is currently surfaced in UI and prompt composition.
5. `defaultLanguage` is persisted in project config and manifest but not auto-applied by current UI transcription requests.
