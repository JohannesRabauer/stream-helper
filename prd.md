# PRD: Live-Coding Session Assistant

## 1. Product Overview
Build a **Spring Boot + Thymeleaf** application that helps a live-coding creator plan, promote, and post-process streaming sessions using either:

1. **Local AI** (Ollama via Docker)
2. **Cloud AI API** (for example OpenAI)

The product is **project-based**, **local-first**, and stores project data in open, portable files.

## 2. Confirmed v1 Scope Decisions
1. v1 is **planning + content generation + copy/export**, not direct publishing to social platforms.
2. v1 is **single-user local**.
3. v1 focuses on **content/planning assets** (no scheduling/reminders workflow).
4. Guest recommendation in v1 uses **curated internal contact/project data**, not web scraping/discovery.
5. Topic/guest ranking in v1 uses **notes/preferences/manual signals**, not external analytics integration.

## 3. Functional Requirements

### 3.1 Projects and workspace model
- All assets are grouped under **Projects**.
- A project can contain multiple sessions/episodes and generated outputs.
- User can create, rename, delete, and browse projects.

### 3.2 Markdown notes
- Project notes are Markdown files.
- Notes support:
  - **Edit view** (raw markdown)
  - **Rendered view** (preview)

### 3.3 Local-first storage and schema
- Project storage is file-based and is the source of truth (no hidden proprietary-only storage).
- Define a **versioned on-disk schema** (`schemaVersion: 1`) from v1.
- Canonical project structure includes at least:
  - `/notes`
  - `/sessions`
  - `/outputs`
  - `/transcripts`
  - `/thumbnails`
  - `/config`
- Export supports:
  - raw project folder copy
  - one-click zip package including manifest

### 3.4 AI provider support
- Configurable AI providers:
  - Ollama endpoint + model
  - OpenAI API key + model
- Provider configurable by user/workspace.

### 3.5 Instruction layering and prompt composition
- Prompt instructions exist at three scopes:
  1. **Global**
  2. **Project-specific**
  3. **Category-specific** (YouTube description, summary, social posts, thumbnail prompt, etc.)
- Precedence is **Category > Project > Global**.
- App shows the composed/effective prompt preview for transparency.

### 3.6 Brand profile constraints
- Add structured brand profile settings (separate from free-text instructions), such as:
  - preferred colors
  - text style limits
  - required/excluded words
  - thumbnail title length guidance
- Brand profile can be applied to social text and thumbnail generation flows.

### 3.7 Topic and guest assistant
- Generate ranked topic suggestions with rationale.
- Suggest guests from curated internal sources with explicit fit reasoning.

### 3.8 Promotion content assistant
- Generate:
  - YouTube description
  - LinkedIn post
  - social micro-posts (tweet-size)
  - hashtags
  - YouTube tags as one comma-separated string, max 500 chars
- For descriptions and social posts, generate **3 strategy variants + 1 recommended pick**.
- Enforce platform-specific constraints at generation time (character limits, formatting rules, etc.).

### 3.9 Post-stream assistant
- Accept video input from:
  - local file upload
  - YouTube URL
- Transcription default is **local-first** with optional cloud fallback.
- Use project default language, with optional auto-detect for transcript tasks.
- Transcript includes timestamps and speaker labels when possible.
- Generate YouTube chapters using enforceable defaults (ascending time, first chapter at 00:00, title formatting).
- Generate a detailed summary of technical and discussion content.

### 3.10 Thumbnail assistant
- Generate at least 3 thumbnail prompt variants and recommend one.
- Support two creation paths:
  - built-in image generation (configured provider)
  - exportable prompt package for external design tools
- Support iterative refinement and version history.

### 3.11 Drafting, versioning, and finalization
- Every generation is stored as a **versioned draft**.
- User explicitly marks a version as **final/canonical**.
- Default retention: keep all versions; optional manual archive/compress.

### 3.12 Quality validation
- Before finalization/export, run automatic checks for:
  - YouTube tags <= 500 characters
  - duplicate hashtag detection
  - chapter timestamp ordering/validity
  - platform length/format constraints
- Validation reports actionable fix hints.

### 3.13 One-command Docker Compose runtime
- The full application stack must build and start with a single Docker Compose command.
- Deployment must support two provider modes:
  - **Ollama mode** (local AI): app + Ollama service
  - **OpenAI mode** (API AI): app configured for OpenAI without requiring Ollama runtime
- Provider selection must be configuration-driven (for example env variables or compose profiles), without code changes.
- Startup flow must not require manual build/run steps outside Docker Compose.

## 4. Use Cases

| ID | Use Case | Outcome |
|---|---|---|
| UC-1 | Manage projects | Stream work is organized per project |
| UC-2 | Write markdown notes | Edit and rendered preview are both available |
| UC-3 | Generate topics | Ranked topic options with rationale |
| UC-4 | Find guests | Curated guest recommendations with fit reasoning |
| UC-5 | Generate platform descriptions | YouTube and LinkedIn copy variants plus recommendation |
| UC-6 | Generate social micro-posts | 3 short variants + recommended pick |
| UC-7 | Generate hashtags and YouTube tags | Valid, constraint-compliant outputs |
| UC-8 | Transcribe stream content | Timestamped transcript with speaker labels where possible |
| UC-9 | Create YouTube chapters | Constraint-compliant chapter list |
| UC-10 | Produce detailed summary | Deep recap of technical and narrative content |
| UC-11 | Generate thumbnail prompts | Multiple prompt variants + recommended option |
| UC-12 | Create/refine thumbnail drafts | Built-in or external-tool flow with versioning |
| UC-13 | Apply instruction and brand controls | Consistent, steerable output behavior |
| UC-14 | Finalize and export project | Final artifacts can be copied/shared outside app |

## 5. Acceptance Criteria

| ID | Acceptance Criteria |
|---|---|
| AC-1 | User can create, rename, and delete projects. |
| AC-2 | All notes and generated outputs are linked to a project and session. |
| AC-3 | Markdown notes support edit and rendered views. |
| AC-4 | File-based storage is source of truth and uses a documented `schemaVersion` field. |
| AC-5 | User can export project as raw folder and as zip+manifest package. |
| AC-6 | User can configure Ollama and OpenAI providers and select model. |
| AC-7 | Instruction precedence is Category > Project > Global and effective prompt preview is visible. |
| AC-8 | Structured brand profile rules can be configured and applied to relevant generations. |
| AC-9 | Topic generation returns ranked suggestions with rationale. |
| AC-10 | Guest recommendations use curated internal data and include explicit fit explanations. |
| AC-11 | YouTube and LinkedIn generation returns 3 variants and one recommended version. |
| AC-12 | Social micro-post generation returns at least 3 variants and one recommended version. |
| AC-13 | Platform constraints are enforced automatically in generated outputs. |
| AC-14 | YouTube tags output is a comma-separated string with total length <= 500 characters. |
| AC-15 | Post-stream input supports local file upload and YouTube URL. |
| AC-16 | Transcription runs local-first with optional cloud fallback. |
| AC-17 | Transcript includes timestamps and speaker diarization when available. |
| AC-18 | Chapter generation enforces ascending timestamps and chapter formatting defaults. |
| AC-19 | Detailed summary covers technical topics, key decisions, and outcomes. |
| AC-20 | Thumbnail flow supports prompt generation, recommendation, creation path selection, and iterative refinement. |
| AC-21 | All generations are stored as versioned drafts and user can mark a final version. |
| AC-22 | System retains version history by default with manual archive/compress option. |
| AC-23 | Automatic validation checks run before finalization/export and provide clear fix hints. |
| AC-24 | Provider failures show actionable error messages and allow retry without losing input. |
| AC-25 | v1 includes no direct social publishing and no scheduling/reminder automation. |
| AC-26 | A single Docker Compose command builds and starts the full app in Ollama mode. |
| AC-27 | A single Docker Compose command builds and starts the full app in OpenAI mode. |
| AC-28 | AI provider mode can be switched via configuration (env/profile) without source code edits. |

## 6. Non-Functional Requirements
- Spring Boot + Thymeleaf server-rendered UI.
- Local-first behavior for core project content.
- Secure credential handling (no plain-text secret logging).
- Async status indicators for long-running jobs (transcription, summarization, image generation).
- Deterministic and reproducible output generation based on stored prompt inputs and instruction layers.
- Container-first local deployment: compile/build/start via one Docker Compose command per provider mode.
