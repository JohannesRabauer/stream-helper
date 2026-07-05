const workflowAreas = {
  "pre-stream": {
    draftKey: "pre-stream",
    draftInputId: "draft-pre-stream",
    categories: ["TOPIC_IDEA", "GUEST_IDEA"]
  },
  description: {
    draftKey: "description",
    draftInputId: "draft-description",
    categories: ["YOUTUBE_TITLES", "YOUTUBE_DESCRIPTION", "YOUTUBE_TAGS"]
  },
  thumbnail: {
    draftKey: "thumbnail",
    draftInputId: "draft-thumbnail",
    categories: ["THUMBNAIL_IDEAS", "THUMBNAIL_PROMPTS", "THUMBNAILS"]
  },
  "social-announcements": {
    draftKey: "social-announcements",
    draftInputId: "draft-social-announcements",
    categories: ["LINKEDIN_POST", "SOCIAL_POST", "HASHTAGS"]
  },
  transcription: {
    categories: ["TRANSCRIPT"]
  },
  "post-stream": {
    draftKey: "post-stream",
    draftInputId: "draft-post-stream",
    categories: ["CHAPTERS", "SUMMARY"]
  }
};

const categoryLabels = {
  TOPIC_IDEA: "Topic ideas",
  GUEST_IDEA: "Guest ideas",
  YOUTUBE_TITLES: "YouTube titles",
  YOUTUBE_DESCRIPTION: "YouTube descriptions",
  LINKEDIN_POST: "LinkedIn posts",
  SOCIAL_POST: "Social posts",
  HASHTAGS: "Hashtags",
  YOUTUBE_TAGS: "YouTube tags",
  TRANSCRIPT: "Transcripts",
  CHAPTERS: "Chapters",
  SUMMARY: "Summaries",
  THUMBNAIL_IDEAS: "Thumbnail ideas",
  THUMBNAIL_PROMPTS: "Thumbnail prompts",
  THUMBNAILS: "Thumbnail images"
};

const categoryToArea = {
  TOPIC_IDEA: "pre-stream",
  GUEST_IDEA: "pre-stream",
  YOUTUBE_TITLES: "description",
  YOUTUBE_DESCRIPTION: "description",
  YOUTUBE_TAGS: "description",
  THUMBNAIL_IDEAS: "thumbnail",
  THUMBNAIL_PROMPTS: "thumbnail",
  THUMBNAILS: "thumbnail",
  LINKEDIN_POST: "social-announcements",
  SOCIAL_POST: "social-announcements",
  HASHTAGS: "social-announcements",
  TRANSCRIPT: "transcription",
  CHAPTERS: "post-stream",
  SUMMARY: "post-stream"
};

const guidanceConfig = {
  TOPIC_IDEA: {
    placeholder: "e.g. lean more technical, include Spring Boot, avoid clickbait",
    chips: ["More technical", "Punchier", "Include keyword…", "Shorter"]
  },
  GUEST_IDEA: {
    placeholder: "e.g. similar background, different perspective, industry expert",
    chips: ["Similar background", "Different perspective", "Industry expert", "First-time guest"]
  },
  YOUTUBE_TITLES: {
    placeholder: "e.g. lean technical, include keyword Spring Boot, more engaging",
    chips: ["Include keyword", "Lean technical", "More hooks", "Avoid clickbait"]
  },
  YOUTUBE_DESCRIPTION: {
    placeholder: "e.g. more SEO, add timestamps note, include relevant links",
    chips: ["Add timestamps", "More SEO focus", "Include links", "Shorter version"]
  },
  YOUTUBE_TAGS: {
    placeholder: "e.g. fewer/broader or niche/technical, industry-specific focus",
    chips: ["Broader reach", "More niche", "Technical focus", "Fewer tags"]
  },
  LINKEDIN_POST: {
    placeholder: "e.g. more professional, personal story, include call-to-action",
    chips: ["More professional", "Personal story", "Add CTA", "Shorter version"]
  },
  SOCIAL_POST: {
    placeholder: "e.g. more engaging, shorter, include emoji, multiple variations",
    chips: ["More engaging", "Shorter", "Include emoji", "Multiple variations"]
  },
  HASHTAGS: {
    placeholder: "e.g. broader/narrower, industry-specific, mix of popular and niche",
    chips: ["Broader reach", "Niche/technical", "Industry-specific", "Fewer hashtags"]
  },
  CHAPTERS: {
    placeholder: "e.g. add timestamps, more granular sections, include key moments",
    chips: ["Add timestamps", "More granular", "Fewer chapters", "Shorter titles"]
  },
  SUMMARY: {
    placeholder: "e.g. focus on key takeaways, shorter version, include timestamps",
    chips: ["Key takeaways", "Shorter", "More detailed", "Include timestamps"]
  },
  THUMBNAIL_IDEAS: {
    placeholder: "e.g. high contrast, show a face, big bold text",
    chips: ["High contrast", "Show a face", "Big bold text", "Minimalist"]
  },
  THUMBNAIL_PROMPTS: {
    placeholder: "e.g. professional, cinematic lighting, bright colors",
    chips: ["Professional", "Cinematic", "Bright colors", "Minimalist"]
  },
  THUMBNAILS: {
    placeholder: "e.g. add text overlay, different style",
    chips: ["Add text", "Different style", "Higher quality"]
  }
};

let projectConfig = JSON.parse(JSON.stringify(initialProjectConfig || {}));
let globalConfig = JSON.parse(JSON.stringify(initialGlobalConfig || {}));
let projectNotes = {
  markdown: initialProjectNoteMarkdown || "",
  html: initialProjectNoteHtml || ""
};
let configSaveTimer = null;
let globalConfigSaveTimer = null;
const noteSaveTimers = new Map();
let projectNotesMode = "edit";
const editableArtifactCategories = new Set([
  "TOPIC_IDEA",
  "GUEST_IDEA",
  "YOUTUBE_TITLES",
  "YOUTUBE_DESCRIPTION",
  "LINKEDIN_POST",
  "SOCIAL_POST",
  "HASHTAGS",
  "YOUTUBE_TAGS",
  "CHAPTERS",
  "SUMMARY",
  "THUMBNAIL_IDEAS",
  "THUMBNAIL_PROMPTS"
]);

const spinnerFrames = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];
let transcriptionProgressPollTimer = null;
let transcriptionRequestInFlight = false;
let transcriptionAwaitingActiveState = false;
const timestampFormatter = createTimestampFormatter();
const journeyOrder = ["pre-stream", "description", "thumbnail", "social-announcements", "transcription", "post-stream"];
const journeyMeta = {
  "pre-stream": {title: "Step 1 · Plan", nextHint: "Capture a clear stream brief, then generate topic and guest ideas."},
  description: {title: "Step 2 · Title & description", nextHint: "Generate titles, description, and tags from your chosen angle."},
  thumbnail: {title: "Step 3 · Thumbnail", nextHint: "Draft visual concepts, then turn the best one into prompts or assets."},
  "social-announcements": {title: "Step 4 · Announce", nextHint: "Create your LinkedIn post, short social posts, and hashtags."},
  transcription: {title: "Step 5 · Transcribe", nextHint: "Upload media or a YouTube URL to unlock chapters and summary."},
  "post-stream": {title: "Step 6 · Wrap-up", nextHint: "Generate chapters and summary once a transcript is available."}
};
const journeyStatusLabels = {
  "not-started": "Not started",
  "in-progress": "In progress",
  ready: "Ready",
  locked: "Locked"
};
const areaCompletionState = {};

function checkImageProviderSupport() {
  return typeof imageProviderAvailable !== 'undefined' && imageProviderAvailable === true;
}

function initializeImageProviderCheck() {
  const providerCheckDiv = document.getElementById("image-provider-check");
  if (!providerCheckDiv) return;

  const hasImageSupport = checkImageProviderSupport();
  const actionButtons = providerCheckDiv.querySelector(".asset-actions");
  
  if (!hasImageSupport) {
    if (actionButtons) {
      actionButtons.style.display = "none";
    }
    const note = document.createElement("div");
    note.className = "muted";
    note.style.padding = "0.5rem 0";
    note.style.fontSize = "0.9em";
    note.textContent = "Image generation requires OpenAI API configuration";
    providerCheckDiv.appendChild(note);
  }
}

document.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button || button.disabled || document.body.classList.contains("calm-ui")) {
    return;
  }
  const ripple = document.createElement("span");
  ripple.className = "ripple";
  const rect = button.getBoundingClientRect();
  const size = Math.max(rect.width, rect.height);
  ripple.style.width = ripple.style.height = `${size}px`;
  ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
  ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
  button.appendChild(ripple);
  setTimeout(() => ripple.remove(), 600);
});

function exportProject() {
  window.location.href = `/api/projects/${projectId}/export`;
}

async function renameProject(button) {
  await withButtonLoading(button, async () => {
    const input = document.getElementById("projectRenameInput");
    const titleNode = document.getElementById("projectTitle");
    if (!input || !titleNode) {
      return;
    }
    const nextName = input.value.trim();
    if (!nextName) {
      showStatus("Project name is required.", "warning");
      return;
    }
    const currentName = titleNode.textContent.trim();
    if (nextName === currentName) {
      showStatus("Project name is unchanged.", "warning");
      return;
    }
    const updated = await apiJson(`/api/projects/${projectId}`, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({name: nextName})
    });
    const savedName = (updated?.name || nextName).trim();
    titleNode.textContent = savedName;
    input.value = savedName;
    document.title = `${savedName} · Stream Helper`;
    toggleProjectRenameEditor(false);
    showStatus("Project renamed.", "success");
  });
}

function toggleProjectRenameEditor(forceOpen) {
  const editor = document.getElementById("projectRenameEditor");
  const input = document.getElementById("projectRenameInput");
  const titleNode = document.getElementById("projectTitle");
  const button = document.getElementById("projectTitleEditButton");
  if (!editor || !input || !titleNode || !button) {
    return;
  }
  const open = typeof forceOpen === "boolean" ? forceOpen : editor.hidden;
  editor.hidden = !open;
  button.setAttribute("aria-expanded", String(open));
  if (open) {
    input.value = titleNode.textContent.trim();
    input.focus();
    input.select();
  }
}

function cancelProjectRename() {
  const input = document.getElementById("projectRenameInput");
  const titleNode = document.getElementById("projectTitle");
  if (input && titleNode) {
    input.value = titleNode.textContent.trim();
  }
  toggleProjectRenameEditor(false);
}

function selectWorkflowTab(areaKey, button) {
  if (areaKey === "post-stream" && !hasTranscriptAvailable()) {
    showStatus("Create a transcript in Step 5 before opening Wrap-up.", "warning");
    const transcriptionButton = document.querySelector('.tab-button[data-tab="transcription"]');
    if (transcriptionButton) {
      selectWorkflowTab("transcription", transcriptionButton);
    }
    return;
  }
  document.querySelectorAll("[data-tab-panel]").forEach((panel) => {
    panel.hidden = panel.dataset.tabPanel !== areaKey;
    panel.classList.toggle("active", panel.dataset.tabPanel === areaKey);
  });
  document.querySelectorAll(".tab-button").forEach((tabButton) => {
    const isActive = tabButton.dataset.tab === areaKey;
    tabButton.classList.toggle("active", isActive);
    tabButton.setAttribute("aria-selected", String(isActive));
    tabButton.setAttribute("aria-current", isActive ? "page" : "false");
  });
  if (button) {
    button.classList.add("active");
  }
  projectConfig.currentWorkflowStage = areaKey;
  scheduleProjectConfigSave();
  updateJourneyRail();
}

function hasTranscriptAvailable() {
  return Boolean(areaCompletionState.transcription?.hasArtifacts);
}

function getAreaJourneyState(areaKey) {
  if (areaKey === "post-stream" && !hasTranscriptAvailable()) {
    return "locked";
  }
  const completion = areaCompletionState[areaKey];
  if (completion?.hasFinal) {
    return "ready";
  }
  if (completion?.hasArtifacts || getDraftValue(areaKey)) {
    return "in-progress";
  }
  return "not-started";
}

function resolveNextJourneyArea() {
  for (const areaKey of journeyOrder) {
    const state = getAreaJourneyState(areaKey);
    if (state !== "ready" && state !== "locked") {
      return areaKey;
    }
  }
  return null;
}

function updateJourneyRail() {
  document.querySelectorAll(".tab-button[data-tab]").forEach((tabButton) => {
    const areaKey = tabButton.dataset.tab;
    const state = getAreaJourneyState(areaKey);
    tabButton.dataset.stepState = state;
    tabButton.setAttribute("aria-disabled", state === "locked" ? "true" : "false");
    const statusNode = tabButton.querySelector(".journey-step-status");
    if (statusNode) {
      statusNode.textContent = journeyStatusLabels[state] || journeyStatusLabels["not-started"];
    }
  });
  const helperNode = document.getElementById("journeyNextStep");
  if (!helperNode) {
    return;
  }
  const nextArea = resolveNextJourneyArea();
  if (!nextArea) {
    helperNode.textContent = "Episode kit complete — review final picks and export your ZIP.";
    return;
  }
  const nextMeta = journeyMeta[nextArea];
  helperNode.textContent = nextMeta?.nextHint || "Pick a stage and continue.";
}

async function runStageBriefAction(areaKey, endpoint, button) {
  await withButtonLoading(button, async () => {
    const block = button.closest('.asset-block');
    const category = block?.dataset.category;
    const guidanceTextarea = block?.querySelector('.asset-guidance .guidance-textarea');
    const guidance = guidanceTextarea?.value?.trim() || "";
    
    const data = await apiJson(`/api/projects/${projectId}/${endpoint}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({brief: guidance})
    });
    renderResult(data);
    await reloadAreaHistory(areaKey);
  });
}

async function runStageTextAction(areaKey, endpoint, button) {
  await withButtonLoading(button, async () => {
    const input = getDraftValue(areaKey);
    const data = await apiJson(`/api/projects/${projectId}/${endpoint}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({text: input})
    });
    renderResult(data);
    await reloadAreaHistory(areaKey);
  });
}

async function createThumbnailFromStage(areaKey, builtIn, button) {
  await withButtonLoading(button, async () => {
    const input = getDraftValue(areaKey);
    if (!input) {
      showStatus("Please add promotion guidance before creating thumbnail assets.", "warning");
      return;
    }
    const data = await apiJson(`/api/projects/${projectId}/thumbnails/create`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({prompt: input, builtIn})
    });
    renderResult(data);
    await reloadAreaHistory(areaKey);
  });
}

async function transcribeFile(button) {
  await withButtonLoading(button, async () => {
    const fileInput = document.getElementById("transcriptionFile");
    if (!fileInput.files.length) {
      showStatus("Please choose a media file first.", "warning");
      return;
    }
    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    const language = document.getElementById("transcriptionLanguage").value.trim();
    if (language) {
      formData.append("language", language);
    }
    formData.append("diarize", document.getElementById("transcriptionDiarize").checked.toString());
    await runTranscriptionAction(
        "Uploading media and starting transcription...",
        () => apiJson(`/api/projects/${projectId}/transcripts/file`, {
          method: "POST",
          body: formData
        })
    );
  });
}

async function transcribeYoutube(button) {
  await withButtonLoading(button, async () => {
    const youtubeUrl = document.getElementById("youtubeUrl").value.trim();
    if (!youtubeUrl) {
      showStatus("Please enter a YouTube URL.", "warning");
      return;
    }
    const language = document.getElementById("transcriptionLanguage").value.trim();
    await runTranscriptionAction(
        "Starting YouTube download and transcription...",
        () => apiJson(`/api/projects/${projectId}/transcripts/youtube`, {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({
            youtubeUrl,
            language: language || null,
            diarize: document.getElementById("transcriptionDiarize").checked
          })
        })
    );
  });
}

async function runTranscriptionAction(initialMessage, requestFn) {
  transcriptionRequestInFlight = true;
  transcriptionAwaitingActiveState = true;
  startTranscriptionProgressMonitor(initialMessage);
  let responseData = null;
  let errorMessage = "";
  try {
    responseData = await requestFn();
  } catch (error) {
    errorMessage = error?.message || "Transcription failed.";
  } finally {
    transcriptionRequestInFlight = false;
    transcriptionAwaitingActiveState = false;
    await refreshTranscriptionProgressSnapshot();
    stopTranscriptionProgressPolling();
  }
  if (responseData) {
    renderResult(responseData);
    await reloadAreaHistory("transcription");
    await renderTranscriptInTaskCard();
    showTranscriptionProgressState({
      active: false,
      failed: false,
      percent: 100,
      stage: "completed",
      message: "Transcription completed."
    });
    return;
  }
  showTranscriptionProgressState({
    active: false,
    failed: true,
    percent: readCurrentTranscriptionPercent(),
    stage: "failed",
    message: errorMessage
  });
}

async function loadEffectivePrompt(button) {
  await withButtonLoading(button, async () => {
    const category = document.getElementById("categorySelect").value;
    const data = await apiJson(`/api/projects/${projectId}/effective-prompt/${category}`, {method: "POST"});
    document.getElementById("effectivePromptBox").textContent = data.effectivePrompt || JSON.stringify(data, null, 2);
    showStatus(`Loaded LLM debug preview for ${formatCategoryLabel(category)}.`, "success");
  });
}

async function finalizeArtifact(category, artifactId) {
  await apiJson(`/api/projects/${projectId}/artifacts/${category}/${artifactId}/finalize`, {method: "POST"});
  showStatus(`Marked ${formatCategoryLabel(category)} version as final.`, "success");
  await reloadAreaHistory(categoryToArea[category]);
}

let currentRefineContext = {category: null, artifactId: null};

function showRefinePopover(category, artifactId) {
  currentRefineContext = {category, artifactId};
  const popover = document.getElementById("refinePopover");
  const overlay = document.getElementById("refinePopoverOverlay");
  const input = document.getElementById("refinePopoverInput");
  if (popover && overlay && input) {
    input.value = "";
    input.focus();
    popover.hidden = false;
    overlay.hidden = false;
  }
}

function closeRefinePopover() {
  const popover = document.getElementById("refinePopover");
  const overlay = document.getElementById("refinePopoverOverlay");
  if (popover && overlay) {
    popover.hidden = true;
    overlay.hidden = true;
  }
  currentRefineContext = {category: null, artifactId: null};
}

async function submitRefinement() {
  const {category, artifactId} = currentRefineContext;
  if (!category || !artifactId) {
    return;
  }
  const input = document.getElementById("refinePopoverInput");
  if (!input) {
    return;
  }
  const prompt = input.value.trim();
  if (!prompt) {
    showStatus("Please enter a refinement prompt.", "warning");
    return;
  }
  const button = document.getElementById("refinePopoverSubmit");
  await withButtonLoading(button, async () => {
    try {
      const data = await apiJson(`/api/projects/${projectId}/artifacts/${category}/${artifactId}/refine`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({prompt})
      });
      showStatus("Refinement submitted. New version created.", "success");
      renderResult(data);
      await reloadAreaHistory(categoryToArea[category]);
      closeRefinePopover();
    } catch (e) {
      console.error("Refinement failed:", e);
    }
  });
}

async function apiJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : {};
  } catch (_) {
    data = {raw: text};
  }
  if (!response.ok) {
    const message = data.message || `HTTP ${response.status}`;
    showStatus(message, "error");
    throw new Error(message);
  }
  return data;
}

async function apiJsonQuiet(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : {};
  } catch (_) {
    data = {raw: text};
  }
  if (!response.ok) {
    throw new Error(data.message || `HTTP ${response.status}`);
  }
  return data;
}

function renderResult(data) {
  const areaKey = categoryToArea[data.category];
  if (areaKey) {
    if (areaKey !== "transcription") {
      renderAssetBlockResult(data);
    }
    reloadAreaHistory(areaKey).catch(console.error);
  }
  showStatus("Saved a new version.", "success");
}

function renderAssetBlockResult(data) {
  const category = data.category;
  const block = document.querySelector(`.asset-block[data-category="${category}"]`);
  if (!block) return;
  
  const panel = block.querySelector(`#inline-result-${category}`);
  const cards = block.querySelector(`#inline-result-cards-${category}`);
  const rawBox = block.querySelector(`#inline-result-json-${category}`);
  const currentPickContainer = block.querySelector(`#asset-current-${category}`);
  
  if (!panel || !cards || !rawBox || !currentPickContainer) return;
  
  panel.hidden = false;
  rawBox.textContent = JSON.stringify(data, null, 2);
  cards.innerHTML = "";
  currentPickContainer.innerHTML = "";
  
  if (!data.variants || !Array.isArray(data.variants)) {
    return;
  }
  
  // Find current pick: final > recommended > latest
  const finalVariant = data.variants.find(v => v.final);
  const recommendedVariant = data.variants.find(v => v.recommended);
  const currentPick = finalVariant || recommendedVariant || data.variants[0];
  
  // Render current pick in asset-current container
  if (currentPick) {
    renderCurrentPickForBlock(currentPickContainer, currentPick, category);
    showTier1Actions(block, category, currentPick);
  }
  
  const threadTurnsById = buildRefinementTurnsByThread(data.variants);
  
  data.variants.forEach((variant, index) => {
    const card = document.createElement("article");
    card.className = "variant-card";
    card.style.animationDelay = `${index * 0.07}s`;
    const editable = isEditableArtifactCategory(data.category);
    const threadTurns = resolveRefinementTurnsForArtifact(threadTurnsById, variant);
    card.innerHTML = `
      <div class="variant-card-header">
        <div>
          <h4>${escapeHtml(variant.strategy || "variant")}</h4>
          <div class="muted artifact-timestamp">Saved ${escapeHtml(formatTimestamp(variant.createdAt))}</div>
        </div>
        <div class="inline-actions artifact-badges">
          ${renderArtifactBadges(variant)}
        </div>
      </div>
      ${editable
        ? `<textarea class="artifact-editor" rows="10" aria-label="Editable ${escapeHtml(formatCategoryLabel(data.category))} text"></textarea>
           <div class="artifact-save-state muted">Autosaves as a new version when you pause typing.</div>`
        : `<pre>${escapeHtml(previewContent(variant.content || "", data.category, 2400))}</pre>`}
      <div class="inline-actions artifact-actions">
        <button type="button" class="secondary-button artifact-copy-button">Copy content</button>
        <button type="button" class="secondary-button artifact-final-button">Mark final</button>
      </div>
      ${renderRefinePanel(data.category, threadTurns)}
    `;
    const copyButton = card.querySelector(".artifact-copy-button");
    const finalButton = card.querySelector(".artifact-final-button");
    const textarea = card.querySelector(".artifact-editor");
    if (textarea) {
      setupArtifactEditor({
        textarea,
        artifact: variant,
        category: data.category,
        areaKey: categoryToArea[data.category],
        timestampNode: card.querySelector(".artifact-timestamp"),
        badgesNode: card.querySelector(".artifact-badges"),
        saveStateNode: card.querySelector(".artifact-save-state"),
        source: "result"
      });
    }
    copyButton?.addEventListener("click", () => copyToClipboard(textarea ? textarea.value : (variant.content || "")));
    finalButton?.addEventListener("click", async () => {
      await finalizeArtifact(data.category, variant.id);
    });
    bindRefinementControls({
      container: card,
      artifact: variant,
      category: data.category,
      areaKey: categoryToArea[data.category]
    });
    cards.appendChild(card);
  });
}

function buildVersionTree(artifacts) {
  if (!Array.isArray(artifacts) || artifacts.length === 0) {
    return [];
  }

  const byId = new Map();
  const roots = [];
  const machineVersions = new Set(["-normalized", "-edited", "-refined"]);

  artifacts.forEach((artifact) => {
    byId.set(artifact.id, { ...artifact, children: [] });
  });

  artifacts.forEach((artifact) => {
    const isMachineVersion = machineVersions.some((suffix) => artifact.id?.endsWith(suffix));
    if (isMachineVersion && artifact.parentArtifactId) {
      const parent = byId.get(artifact.parentArtifactId);
      if (parent) {
        parent.children.push(byId.get(artifact.id));
      }
    } else if (!artifact.parentArtifactId || !byId.has(artifact.parentArtifactId)) {
      roots.push(byId.get(artifact.id));
    }
  });

  roots.sort((a, b) => Date.parse(b.createdAt || "") - Date.parse(a.createdAt || ""));
  roots.forEach((root) => {
    root.children.sort((a, b) => Date.parse(a.createdAt || "") - Date.parse(b.createdAt || ""));
  });

  return roots;
}

function renderVersionRow(artifact, isNested, level, category) {
  const row = document.createElement("div");
  const isMachineVersion = artifact.id?.endsWith("-normalized") || artifact.id?.endsWith("-edited") || artifact.id?.endsWith("-refined");
  
  let badgeText = artifact.strategy || "version";
  if (isMachineVersion) {
    const suffix = artifact.id?.split("-").pop();
    badgeText = suffix === "normalized" ? "auto-trimmed" : suffix;
  }
  
  row.className = isNested ? "version-row version-row-nested" : "version-row";
  if (isNested) {
    row.style.marginLeft = `${level * 1.2}em`;
  }
  
  row.innerHTML = `
    <div class="version-chip">v${artifact.id?.slice(-3) || "?"}</div>
    <div class="version-badge">${escapeHtml(badgeText)}</div>
    <div class="version-preview">${escapeHtml(previewContent(artifact.content || "", category, 80))}</div>
    <div class="inline-actions version-actions">
      <button type="button" class="secondary-button version-copy-button">Copy</button>
      <button type="button" class="secondary-button version-make-current-button" data-artifact-id="${artifact.id}" data-category="${category}">Make current</button>
    </div>
  `;
  
  const copyButton = row.querySelector(".version-copy-button");
  copyButton?.addEventListener("click", () => {
    copyToClipboard(artifact.content || "");
  });
  
  const makeCurrent = row.querySelector(".version-make-current-button");
  makeCurrent?.addEventListener("click", async () => {
    await makeVersionCurrent(category, artifact.id);
  });
  
  return row;
}

async function makeVersionCurrent(category, artifactId) {
  try {
    await finalizeArtifact(category, artifactId);
    await reloadAreaHistory(categoryToArea[category]);
  } catch (error) {
    console.error("Error making version current:", error);
  }
}

function renderAssetBlockVersions(category, artifacts) {
  const block = document.querySelector(`.asset-block[data-category="${category}"]`);
  if (!block) return;
  
  const summaryNode = block.querySelector(`#versions-summary-${category}`);
  const timelineContainer = block.querySelector(`#asset-versions-${category}`);
  
  if (!summaryNode || !timelineContainer) return;
  
  const count = Array.isArray(artifacts) ? artifacts.length : 0;
  summaryNode.textContent = `Versions (${count})`;
  timelineContainer.innerHTML = "";
  
  if (count === 0) {
    timelineContainer.innerHTML = `<p class="muted">No versions yet.</p>`;
    return;
  }
  
  const versionTree = buildVersionTree(artifacts);
  
  versionTree.forEach((root) => {
    const rootRow = renderVersionRow(root, false, 0, category);
    timelineContainer.appendChild(rootRow);
    
    if (root.children && root.children.length > 0) {
      root.children.forEach((child) => {
        const childRow = renderVersionRow(child, true, 1, category);
        timelineContainer.appendChild(childRow);
      });
    }
  });
}

async function reloadAreaHistory(areaKey) {
  const area = workflowAreas[areaKey];
  if (!area) {
    return;
  }
  const results = await Promise.all(
      area.categories.map(async (category) => ({
        category,
        artifacts: await apiJson(`/api/projects/${projectId}/artifacts/${category}`, {method: "GET"})
      }))
  );
  renderAreaHistory(areaKey, results);
  updateAreaCompletionState(areaKey, results);
}

function updateAreaCompletionState(areaKey, categoryResults) {
  const artifacts = (categoryResults || [])
      .flatMap((result) => Array.isArray(result.artifacts) ? result.artifacts : []);
  areaCompletionState[areaKey] = {
    hasArtifacts: artifacts.length > 0,
    hasFinal: artifacts.some((artifact) => Boolean(artifact?.finalVersion))
  };
  updateJourneyRail();
}

function renderAreaHistory(areaKey, categoryResults) {
  categoryResults.forEach(({category, artifacts}) => {
    renderAssetBlockVersions(category, artifacts || []);
  });
  updateAreaCompletionState(areaKey, categoryResults);
}

function setupArtifactEditor({textarea, artifact, category, areaKey, timestampNode, badgesNode, saveStateNode, source}) {
  textarea.value = artifact.content || "";
  textarea.dataset.artifactId = artifact.id;
  textarea.dataset.lastSavedContent = artifact.content || "";
  autoResizeTextarea(textarea);
  textarea.addEventListener("input", () => {
    autoResizeTextarea(textarea);
    if (saveStateNode) {
      saveStateNode.textContent = "Editing… autosave pending.";
    }
    scheduleArtifactSave({textarea, artifact, category, areaKey, timestampNode, badgesNode, saveStateNode, source});
  });
  textarea.addEventListener("blur", () => {
    if (!textarea._saving && textarea.value !== textarea.dataset.lastSavedContent && !textarea._artifactSaveTimer) {
      saveArtifactEdit({textarea, artifact, category, areaKey, timestampNode, badgesNode, saveStateNode, source})
          .catch(console.error);
    }
  });
}

function scheduleArtifactSave(context) {
  clearTimeout(context.textarea._artifactSaveTimer);
  context.textarea._artifactSaveTimer = setTimeout(() => {
    context.textarea._artifactSaveTimer = null;
    saveArtifactEdit(context).catch(console.error);
  }, 900);
}

async function saveArtifactEdit({textarea, artifact, category, areaKey, timestampNode, badgesNode, saveStateNode, source}) {
  const content = textarea.value;
  if (content === textarea.dataset.lastSavedContent) {
    if (saveStateNode) {
      saveStateNode.textContent = "All changes saved.";
    }
    return;
  }
  if (textarea._saving) {
    textarea._needsSave = true;
    return;
  }
  textarea._saving = true;
  if (saveStateNode) {
    saveStateNode.textContent = "Saving a new version…";
  }
  try {
    const saved = await apiJson(`/api/projects/${projectId}/artifacts/${category}/${textarea.dataset.artifactId}`, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({content})
    });
    artifact.id = saved.id;
    artifact.content = saved.content || "";
    artifact.strategy = saved.strategy;
    artifact.createdAt = saved.createdAt;
    artifact.finalVersion = saved.finalVersion;
    textarea.dataset.artifactId = saved.id;
    textarea.dataset.lastSavedContent = saved.content || "";
    if (timestampNode) {
      timestampNode.textContent = `Saved ${formatTimestamp(saved.createdAt)}`;
    }
    if (badgesNode) {
      badgesNode.innerHTML = renderArtifactBadges(saved);
    }
    if (saveStateNode) {
      saveStateNode.textContent = "Saved as a new version.";
    }
    if (source === "result" && areaKey) {
      reloadAreaHistory(areaKey).catch(console.error);
    }
  } catch (error) {
    if (saveStateNode) {
      saveStateNode.textContent = "Save failed. Keep editing and try again.";
    }
    showStatus("Failed to save edited text.", "error");
    throw error;
  } finally {
    textarea._saving = false;
    if (textarea._needsSave) {
      textarea._needsSave = false;
      scheduleArtifactSave({textarea, artifact, category, areaKey, timestampNode, badgesNode, saveStateNode, source});
    }
  }
}

function renderArtifactBadges(artifact) {
  const badges = [];
  if (artifact.recommended) {
    badges.push('<span class="badge">Recommended</span>');
  }
  if (artifact.finalVersion) {
    badges.push('<span class="badge final">Final</span>');
  }
  return badges.join("");
}

function isRefinableArtifactCategory(category) {
  return Boolean(category);
}

function renderRefinePanel(category, threadTurns) {
  if (!isRefinableArtifactCategory(category)) {
    return "";
  }
  return `
    <section class="artifact-refine-panel">
      <h5>Refinement chat</h5>
      ${renderRefinementTurns(threadTurns)}
      <label class="artifact-refine-label">Refine with prompt</label>
      <textarea class="artifact-refine-input" rows="3" placeholder="Ask the LLM to refine this result (tone, length, format, emphasis, etc.)."></textarea>
      <div class="inline-actions artifact-refine-actions">
        <button type="button" class="secondary-button artifact-refine-button">Send refine prompt</button>
      </div>
    </section>
  `;
}

function renderRefinementTurns(threadTurns) {
  if (!Array.isArray(threadTurns) || threadTurns.length === 0) {
    return '<p class="muted artifact-refine-empty">No refinement prompts yet.</p>';
  }
  const items = threadTurns.map((turn) => `
    <li>
      <div class="artifact-refine-turn-meta">Prompt • ${escapeHtml(formatTimestamp(turn.createdAt))}</div>
      <div class="artifact-refine-turn-prompt">${escapeHtml(turn.prompt)}</div>
    </li>
  `);
  return `<ol class="artifact-refine-turns">${items.join("")}</ol>`;
}

function resolveRefinementTurnsForArtifact(turnsByThread, artifact) {
  const turns = turnsByThread.get(artifact?.threadId || artifact?.id) || [];
  const artifactTimestamp = Date.parse(artifact?.createdAt || "");
  if (!Number.isFinite(artifactTimestamp)) {
    return turns;
  }
  return turns.filter((turn) => {
    const turnTimestamp = Date.parse(turn.createdAt || "");
    return !Number.isFinite(turnTimestamp) || turnTimestamp <= artifactTimestamp;
  });
}

function buildRefinementTurnsByThread(artifacts) {
  const turnsByThread = new Map();
  if (!Array.isArray(artifacts)) {
    return turnsByThread;
  }
  artifacts.forEach((artifact) => {
    const threadId = artifact?.threadId || artifact?.id;
    if (!threadId) {
      return;
    }
    if (!turnsByThread.has(threadId)) {
      turnsByThread.set(threadId, []);
    }
    const prompt = (artifact.refinementPrompt || "").trim();
    if (prompt) {
      turnsByThread.get(threadId).push({
        prompt,
        createdAt: artifact.createdAt || ""
      });
    }
  });
  turnsByThread.forEach((turns) => {
    turns.sort((left, right) => Date.parse(left.createdAt || "") - Date.parse(right.createdAt || ""));
  });
  return turnsByThread;
}

function bindRefinementControls({container, artifact, category, areaKey}) {
  if (!container || !artifact?.id || !isRefinableArtifactCategory(category)) {
    return;
  }
  const promptInput = container.querySelector(".artifact-refine-input");
  const sendButton = container.querySelector(".artifact-refine-button");
  if (!promptInput || !sendButton) {
    return;
  }
  sendButton.addEventListener("click", async () => {
    const prompt = promptInput.value.trim();
    if (!prompt) {
      showStatus("Add a refinement prompt first.", "warning");
      return;
    }
    await withButtonLoading(sendButton, async () => {
      const data = await apiJson(`/api/projects/${projectId}/artifacts/${category}/${artifact.id}/refine`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({prompt})
      });
      promptInput.value = "";
      renderResult(data);
      if (areaKey) {
        await reloadAreaHistory(areaKey);
      }
    });
  });
}

async function copyToClipboard(text) {
  if (!text) {
    showStatus("Nothing to copy.", "warning");
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    showStatus("Copied to clipboard.", "success");
  } catch (_) {
    showStatus("Clipboard copy failed.", "error");
  }
}

function showStatus(message, kind = "success", timeoutMs = 4000) {
  const icons = {success: "✓", warning: "⚠", error: "✕"};
  const bar = document.getElementById("statusBar");
  bar.hidden = false;
  bar.style.animation = "";
  bar.className = `status-bar ${kind}`;
  bar.textContent = `${icons[kind] ?? ""} ${message}`;
  clearTimeout(bar._dismissTimer);
  bar._dismissTimer = setTimeout(() => {
    bar.style.animation = "fadeOut 0.4s ease forwards";
    setTimeout(() => {
      bar.hidden = true;
      bar.style.animation = "";
    }, 400);
  }, timeoutMs);
}

async function withButtonLoading(button, fn) {
  if (!button) {
    return fn();
  }
  const original = button.textContent;
  button.disabled = true;
  let frame = 0;
  const timer = setInterval(() => {
    button.textContent = `${spinnerFrames[frame % spinnerFrames.length]} Working…`;
    frame += 1;
  }, 75);
  try {
    await fn();
  } catch (error) {
    console.error(error);
  } finally {
    clearInterval(timer);
    button.disabled = false;
    button.textContent = original;
  }
}

function startTranscriptionProgressMonitor(initialMessage) {
  showTranscriptionProgressState({
    active: true,
    failed: false,
    percent: 2,
    stage: "starting",
    message: initialMessage || "Starting transcription..."
  });
  stopTranscriptionProgressPolling();
  refreshTranscriptionProgressSnapshot().catch(console.error);
  transcriptionProgressPollTimer = setInterval(() => {
    refreshTranscriptionProgressSnapshot().catch(console.error);
  }, 1200);
}

function stopTranscriptionProgressPolling() {
  if (transcriptionProgressPollTimer) {
    clearInterval(transcriptionProgressPollTimer);
    transcriptionProgressPollTimer = null;
  }
}

async function initializeTranscriptionProgress() {
  const panel = document.getElementById("transcriptionProgressPanel");
  if (!panel) {
    return;
  }
  try {
    const snapshot = await apiJsonQuiet(`/api/projects/${projectId}/transcripts/progress`, {method: "GET"});
    if (snapshot.active) {
      transcriptionRequestInFlight = false;
      transcriptionAwaitingActiveState = false;
      showTranscriptionProgressState(snapshot);
      stopTranscriptionProgressPolling();
      transcriptionProgressPollTimer = setInterval(() => {
        refreshTranscriptionProgressSnapshot().catch(console.error);
      }, 1200);
      return;
    }
    panel.hidden = true;
  } catch (error) {
    console.error(error);
    panel.hidden = true;
  }
}

async function refreshTranscriptionProgressSnapshot() {
  const snapshot = await apiJsonQuiet(`/api/projects/${projectId}/transcripts/progress`, {method: "GET"});
  if (transcriptionRequestInFlight && transcriptionAwaitingActiveState && !snapshot.active) {
    return;
  }
  showTranscriptionProgressState(snapshot);
}

function showTranscriptionProgressState(snapshot) {
  const progressSection = document.getElementById("transcription-progress-section");
  const percentNode = document.getElementById("transcriptionProgressPercent");
  const fill = document.getElementById("transcriptionProgressFill");
  const messageNode = document.getElementById("transcriptionProgressMessage");
  const progressBar = document.getElementById("transcription-progress");
  
  if (!progressSection || !percentNode || !fill || !messageNode || !progressBar) {
    return;
  }
  
  const percent = clampPercent(snapshot?.percent ?? 0);
  const message = snapshot?.message || "Transcription in progress...";
  
  if (snapshot?.active) {
    progressSection.hidden = false;
  } else if (!snapshot?.active && !snapshot?.failed && percent >= 100) {
    // Completed successfully - show result section
    progressSection.hidden = true;
    document.getElementById("transcript-result-section").hidden = false;
    document.getElementById("task-card-unlocks-transcription").hidden = false;
  } else if (snapshot?.failed) {
    // Failed
    progressSection.hidden = true;
  } else {
    progressSection.hidden = true;
  }
  
  percentNode.textContent = `${percent}%`;
  fill.style.width = `${percent}%`;
  messageNode.textContent = message;
  progressBar.setAttribute("aria-valuenow", String(percent));
}

async function renderTranscriptInTaskCard() {
  try {
    const artifacts = await apiJsonQuiet(`/api/projects/${projectId}/artifacts/TRANSCRIPT`, {method: "GET"});
    if (!Array.isArray(artifacts) || artifacts.length === 0) {
      return;
    }
    
    const latest = artifacts[0];
    const currentContainer = document.getElementById("asset-current-TRANSCRIPT");
    const versionCount = document.getElementById("version-count-TRANSCRIPT");
    const versionTimeline = document.getElementById("version-timeline-TRANSCRIPT");
    
    if (!currentContainer) {
      return;
    }
    
    // Render current transcript
    const previewText = previewContent(latest.content, "TRANSCRIPT", 700);
    currentContainer.innerHTML = `<pre>${escapeHtml(previewText)}</pre>`;
    
    // Render versions
    if (versionCount && versionTimeline) {
      versionCount.textContent = String(artifacts.length);
      versionTimeline.innerHTML = "";
      
      artifacts.forEach((artifact, index) => {
        const entry = document.createElement("div");
        entry.className = "version-entry";
        const timestamp = formatTimestamp(artifact.createdAt);
        entry.innerHTML = `
          <div class="version-entry-time">${escapeHtml(timestamp)}</div>
          <button type="button" class="version-entry-button" onclick="viewTranscriptVersion('${artifact.id}')">View</button>
        `;
        versionTimeline.appendChild(entry);
      });
    }
    
    // Show result section if there's content
    if (latest.content) {
      document.getElementById("transcript-result-section").hidden = false;
      document.getElementById("task-card-unlocks-transcription").hidden = false;
    }
  } catch (error) {
    console.error("Failed to load transcript asset:", error);
  }
}

function viewTranscriptVersion(artifactId) {
  // For now, just copy it - in future could show modal
  apiJson(`/api/projects/${projectId}/artifacts/TRANSCRIPT/${artifactId}`, {method: "GET"})
    .then(artifact => {
      const currentContainer = document.getElementById("asset-current-TRANSCRIPT");
      if (currentContainer) {
        const previewText = previewContent(artifact.content, "TRANSCRIPT", 700);
        currentContainer.innerHTML = `<pre>${escapeHtml(previewText)}</pre>`;
        showStatus("Viewing version from " + formatTimestamp(artifact.createdAt), "info");
      }
    })
    .catch(error => showStatus("Failed to load version", "error"));
}

function copyAssetContent(category) {
  const container = document.getElementById(`asset-current-${category}`);
  if (!container) {
    showStatus(`No content to copy for ${category}`, "warning");
    return;
  }
  const text = container.textContent || container.innerText;
  if (!text) {
    showStatus(`No content to copy for ${category}`, "warning");
    return;
  }
  navigator.clipboard.writeText(text)
    .then(() => showStatus(`Copied ${formatCategoryLabel(category)} to clipboard`, "success"))
    .catch(() => showStatus("Failed to copy to clipboard", "error"));
}

function readCurrentTranscriptionPercent() {
  const node = document.getElementById("transcriptionProgressPercent");
  if (!node) {
    return 0;
  }
  const match = node.textContent.match(/\d+/);
  return match ? clampPercent(Number.parseInt(match[0], 10)) : 0;
}

function clampPercent(value) {
  return Math.min(100, Math.max(0, Number.isFinite(value) ? Math.round(value) : 0));
}

function getDraftValue(areaKey) {
  const input = document.getElementById(workflowAreas[areaKey]?.draftInputId || "");
  return input ? input.value.trim() : "";
}

function formatTimestamp(value) {
  if (!value) {
    return "unknown time";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  if (timestampFormatter) {
    return timestampFormatter.format(date);
  }
  return date.toLocaleString();
}

function formatCategoryLabel(category) {
  return categoryLabels[category] || category;
}

function isEditableArtifactCategory(category) {
  return editableArtifactCategories.has(category);
}

function previewContent(content, category, defaultMaxChars) {
  const maxChars = category === "TRANSCRIPT" ? 700 : defaultMaxChars;
  if (!content || content.length <= maxChars) {
    return content || "";
  }
  return `${content.slice(0, maxChars)}\n\n… preview truncated …`;
}

function autoResizeTextarea(textarea) {
  textarea.style.height = "auto";
  textarea.style.height = `${Math.min(Math.max(textarea.scrollHeight, 180), 560)}px`;
}

function escapeHtml(value) {
  return (value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
}

function createTimestampFormatter() {
  const locales = [];
  if (Array.isArray(navigator.languages) && navigator.languages.length > 0) {
    locales.push(...navigator.languages.filter(Boolean));
  }
  if (navigator.language) {
    locales.push(navigator.language);
  }
  const htmlLang = document?.documentElement?.lang;
  if (htmlLang) {
    locales.push(htmlLang);
  }
  try {
    return new Intl.DateTimeFormat(locales.length > 0 ? locales : undefined, {
      dateStyle: "medium",
      timeStyle: "short"
    });
  } catch (_) {
    try {
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
      });
    } catch (_) {
      return null;
    }
  }
}

function scheduleProjectConfigSave() {
  clearTimeout(configSaveTimer);
  configSaveTimer = setTimeout(() => {
    saveProjectConfig().catch((error) => {
      console.error(error);
      showStatus("Failed to autosave project workflow settings.", "error");
    });
  }, 450);
}

async function saveProjectConfig() {
  projectConfig = await apiJson(`/api/projects/${projectId}/config`, {
    method: "PUT",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(projectConfig)
  });
}

function scheduleGlobalConfigSave() {
  clearTimeout(globalConfigSaveTimer);
  globalConfigSaveTimer = setTimeout(() => {
    saveGlobalConfig().catch((error) => {
      console.error(error);
      showStatus("Failed to autosave global LLM definitions.", "error");
    });
  }, 450);
}

async function saveGlobalConfig() {
  globalConfig = await apiJson(`/api/config/global`, {
    method: "PUT",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(globalConfig)
  });
}

function scheduleProjectNoteSave(markdown) {
  clearTimeout(noteSaveTimers.get("project-notes"));
  updateProjectNotesSaveState("Autosaving project notes…");
  const timer = setTimeout(async () => {
    try {
      await saveProjectNotes(markdown);
    } catch (error) {
      console.error(error);
      updateProjectNotesSaveState("Failed to autosave project notes.", "error");
      showStatus("Failed to autosave project notes.", "error");
    }
  }, 450);
  noteSaveTimers.set("project-notes", timer);
}

async function saveProjectNotes(markdown) {
  const data = await apiJson(`/api/projects/${projectId}/notes`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({noteId: "project-notes", markdown})
  });
  projectNotes.markdown = data.markdown || markdown || "";
  projectNotes.html = data.html || "";
  renderProjectNotesPreview();
  updateProjectNotesSaveState("Project notes saved.");
  return data;
}

function updateProjectNotesSaveState(message, tone = "muted") {
  const node = document.getElementById("projectNotesSaveState");
  if (!node) {
    return;
  }
  node.textContent = message;
  node.classList.toggle("error", tone === "error");
}

function renderProjectNotesPreview() {
  const previewPane = document.getElementById("projectNotesPreviewPane");
  if (!previewPane) {
    return;
  }
  previewPane.innerHTML = projectNotes.html && projectNotes.html.trim()
      ? projectNotes.html
      : "<p class=\"muted\">No project notes yet.</p>";
}

function toggleProjectNotesDrawer(forceOpen) {
  const drawer = document.getElementById("projectNotesDrawer");
  if (!drawer) {
    return;
  }
  const open = typeof forceOpen === "boolean" ? forceOpen : !drawer.classList.contains("open");
  if (open) {
    closeLlmDefinitionsDrawer();
  }
  setDrawerState("projectNotesDrawer", "projectNotesToggle", open, "Show notes", "Hide notes");
}

function toggleLlmDefinitionsDrawer(forceOpen) {
  const drawer = document.getElementById("projectLlmDefinitionsDrawer");
  if (!drawer) {
    return;
  }
  const open = typeof forceOpen === "boolean" ? forceOpen : !drawer.classList.contains("open");
  if (open) {
    closeProjectNotesDrawer();
  }
  setDrawerState("projectLlmDefinitionsDrawer", "projectLlmDefinitionsToggle", open, "Show LLM definitions", "Hide LLM definitions");
}

function closeProjectNotesDrawer() {
  setDrawerState("projectNotesDrawer", "projectNotesToggle", false, "Show notes", "Hide notes");
}

function closeLlmDefinitionsDrawer() {
  setDrawerState("projectLlmDefinitionsDrawer", "projectLlmDefinitionsToggle", false, "Show LLM definitions", "Hide LLM definitions");
}

function setDrawerState(drawerId, toggleId, open, showText, hideText) {
  const drawer = document.getElementById(drawerId);
  const toggle = document.getElementById(toggleId);
  if (!drawer || !toggle) {
    return;
  }
  drawer.classList.toggle("open", open);
  drawer.setAttribute("aria-hidden", String(!open));
  toggle.setAttribute("aria-expanded", String(open));
  toggle.textContent = open ? hideText : showText;
}

async function setProjectNotesMode(mode) {
  const editorPane = document.getElementById("projectNotesEditorPane");
  const previewPane = document.getElementById("projectNotesPreviewPane");
  const editButton = document.getElementById("projectNotesEditButton");
  const previewButton = document.getElementById("projectNotesPreviewButton");
  if (!editorPane || !previewPane || !editButton || !previewButton) {
    return;
  }
  if (mode === "preview") {
    const textarea = document.getElementById("projectNotesInput");
    try {
      await saveProjectNotes(textarea.value);
    } catch (error) {
      console.error(error);
      return;
    }
  }
  projectNotesMode = mode;
  const preview = mode === "preview";
  editorPane.hidden = preview;
  previewPane.hidden = !preview;
  editButton.classList.toggle("active", !preview);
  previewButton.classList.toggle("active", preview);
}

function loadGuidanceForCategory(category) {
  if (!projectConfig.workspaceDrafts) {
    return "";
  }
  return projectConfig.workspaceDrafts[`guidance:${category}`] || "";
}

function saveGuidanceForCategory(category, text) {
  if (!projectConfig.workspaceDrafts) {
    projectConfig.workspaceDrafts = {};
  }
  projectConfig.workspaceDrafts[`guidance:${category}`] = text;
  scheduleProjectConfigSave();
}

function appendGuidanceChip(buttonElement, chipText) {
  const textarea = buttonElement.closest('.guidance-content')?.querySelector('.guidance-textarea');
  if (!textarea) return;
  
  const currentText = textarea.value.trim();
  const newText = currentText 
    ? currentText + ", " + chipText 
    : chipText;
  
  textarea.value = newText;
  textarea.dispatchEvent(new Event('input', { bubbles: true }));
  textarea.focus();
}

function bindAutosaveInputs() {
  document.querySelectorAll("[data-draft-key]").forEach((textarea) => {
    const draftKey = textarea.dataset.draftKey;
    textarea.value = projectConfig.workspaceDrafts?.[draftKey] || "";
    textarea.addEventListener("input", () => {
      if (!projectConfig.workspaceDrafts) {
        projectConfig.workspaceDrafts = {};
      }
      projectConfig.workspaceDrafts[draftKey] = textarea.value;
      scheduleProjectConfigSave();
      updateJourneyRail();
    });
  });

  const projectNotesInput = document.getElementById("projectNotesInput");
  if (projectNotesInput) {
    projectNotesInput.value = projectNotes.markdown || "";
    projectNotesInput.addEventListener("input", () => {
      projectNotes.markdown = projectNotesInput.value;
      scheduleProjectNoteSave(projectNotesInput.value);
    });
  }
  renderProjectNotesPreview();

  const renameInput = document.getElementById("projectRenameInput");
  if (renameInput) {
    renameInput.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        cancelProjectRename();
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        renameProject(document.getElementById("projectRenameSaveButton")).catch(console.error);
      }
    });
  }

  const hostInput = document.getElementById("hostDisplayName");
  const guestInput = document.getElementById("guestDisplayName");
  hostInput.value = projectConfig.hostDisplayName || "";
  guestInput.value = projectConfig.guestDisplayName || "";
  hostInput.addEventListener("input", () => {
    projectConfig.hostDisplayName = hostInput.value;
    scheduleProjectConfigSave();
  });
  guestInput.addEventListener("input", () => {
    projectConfig.guestDisplayName = guestInput.value;
    scheduleProjectConfigSave();
  });

  const globalInstructionInput = document.getElementById("globalInstructionInput");
  const projectInstructionInput = document.getElementById("projectInstructionInput");

  if (!globalConfig.categoryInstructions) {
    globalConfig.categoryInstructions = {};
  }
  if (!projectConfig.directives) {
    projectConfig.directives = {projectInstruction: "", categoryInstructions: {}};
  }
  if (!projectConfig.directives.categoryInstructions) {
    projectConfig.directives.categoryInstructions = {};
  }

  globalInstructionInput.value = globalConfig.globalInstruction || "";
  projectInstructionInput.value = projectConfig.directives.projectInstruction || "";

  globalInstructionInput.addEventListener("input", () => {
    globalConfig.globalInstruction = globalInstructionInput.value;
    scheduleGlobalConfigSave();
  });
  projectInstructionInput.addEventListener("input", () => {
    projectConfig.directives.projectInstruction = projectInstructionInput.value;
    scheduleProjectConfigSave();
  });

  document.querySelectorAll("[data-stage-definition]").forEach((textarea) => {
    const areaKey = textarea.dataset.stageDefinition;
    textarea.value = readAreaInstruction(projectConfig.directives.categoryInstructions || {}, areaKey);
    textarea.addEventListener("input", () => {
      applyAreaInstruction(projectConfig.directives.categoryInstructions, areaKey, textarea.value);
      scheduleProjectConfigSave();
    });
  });

  // Bind guidance textareas for asset blocks
  document.querySelectorAll('.asset-guidance .guidance-textarea').forEach((textarea) => {
    const category = textarea.closest('.asset-guidance')?.dataset.category;
    if (category) {
      textarea.value = loadGuidanceForCategory(category);
      textarea.addEventListener("input", () => {
        saveGuidanceForCategory(category, textarea.value);
      });
    }
  });
}

function applyAreaInstruction(categoryInstructions, areaKey, value) {
  const categories = workflowAreas[areaKey]?.categories || [];
  categories.forEach((category) => {
    categoryInstructions[category] = value;
  });
}

function readAreaInstruction(categoryInstructions, areaKey) {
  const categories = workflowAreas[areaKey]?.categories || [];
  for (const category of categories) {
    const value = categoryInstructions?.[category];
    if (value && value.trim()) {
      return value;
    }
  }
  return "";
}

function migrateLegacyPromotionState() {
  if (!projectConfig.workspaceDrafts) {
    projectConfig.workspaceDrafts = {};
  }
  if (projectConfig.workspaceDrafts.promotion && !projectConfig.workspaceDrafts.description && !projectConfig.workspaceDrafts.thumbnail && !projectConfig.workspaceDrafts["social-announcements"]) {
    const value = projectConfig.workspaceDrafts.promotion;
    projectConfig.workspaceDrafts.description = value;
    projectConfig.workspaceDrafts.thumbnail = value;
    projectConfig.workspaceDrafts["social-announcements"] = value;
    scheduleProjectConfigSave();
  }
  if (projectConfig.currentWorkflowStage === "promotion") {
    projectConfig.currentWorkflowStage = "description";
    scheduleProjectConfigSave();
  }
}

function renderCurrentPickForBlock(container, artifact, category) {
  const editable = isEditableArtifactCategory(category);
  const content = artifact.content || "";
  
  if (editable) {
    // For editable categories, render as editable textarea
    const div = document.createElement("div");
    div.className = "asset-current-content";
    
    const textarea = document.createElement("textarea");
    textarea.className = "asset-current-editor";
    textarea.value = content;
    textarea.rows = 4;
    
    div.appendChild(textarea);
    container.appendChild(div);
    
    // Wire up inline editor
    setupArtifactEditor({
      textarea,
      artifact,
      category,
      areaKey: categoryToArea[category],
      source: "current-pick"
    });
  } else {
    // For non-editable, render as read-only pre
    const div = document.createElement("div");
    div.className = "asset-current-content";
    div.innerHTML = `<pre>${escapeHtml(previewContent(content, category, 400))}</pre>`;
    container.appendChild(div);
  }
}

function showTier1Actions(block, category, artifact) {
  const copyBtn = block.querySelector(".asset-copy-button");
  const regenerateBtn = block.querySelector(".asset-regenerate-button");
  const refineBtn = block.querySelector(".asset-refine-button");
  const finalBtn = block.querySelector(".asset-final-button");
  
  if (copyBtn) {
    copyBtn.hidden = false;
    copyBtn.onclick = () => copyAssetContent(artifact);
  }
  
  if (regenerateBtn) {
    regenerateBtn.hidden = false;
    const areaKey = categoryToArea[category];
    const endpoint = getEndpointForCategory(category);
    regenerateBtn.onclick = () => runStageBriefAction(areaKey, endpoint, regenerateBtn);
  }
  
  if (refineBtn) {
    refineBtn.hidden = false;
    refineBtn.onclick = () => showRefinePopover(category, artifact.id);
  }
  
  if (finalBtn) {
    finalBtn.hidden = !artifact || artifact.final === true;
    if (!artifact?.final) {
      finalBtn.onclick = async () => {
        await finalizeArtifact(category, artifact.id);
        finalBtn.hidden = true;
      };
    }
  }
}

function copyAssetContent(artifact) {
  copyToClipboard(artifact.content || "");
}

function getEndpointForCategory(category) {
  const endpoints = {
    TOPIC_IDEA: "topic-ideas",
    GUEST_IDEA: "guest-ideas",
    YOUTUBE_TITLES: "youtube-titles",
    YOUTUBE_DESCRIPTION: "youtube-description",
    YOUTUBE_TAGS: "youtube-tags",
    THUMBNAIL_IDEA: "thumbnail-ideas",
    THUMBNAIL_PROMPT: "thumbnail-prompts",
    LINKEDIN_POST: "linkedin-posts",
    SOCIAL_POST: "social-posts",
    HASHTAGS: "hashtags",
    CHAPTERS: "chapters",
    SUMMARY: "summary"
  };
  return endpoints[category] || category.toLowerCase();
}

window.addEventListener("DOMContentLoaded", async () => {
  document.title = `${projectName} · Stream Helper`;
  toggleProjectRenameEditor(false);
  if (!projectConfig.workspaceDrafts) {
    projectConfig.workspaceDrafts = {};
  }
  migrateLegacyPromotionState();
  bindAutosaveInputs();
  updateJourneyRail();
  await setProjectNotesMode(projectNotesMode);
  const activeTab = projectConfig.currentWorkflowStage || "pre-stream";
  const activeButton = document.querySelector(`.tab-button[data-tab="${activeTab}"]`) || document.querySelector('.tab-button[data-tab="pre-stream"]');
  selectWorkflowTab(activeButton?.dataset.tab || "pre-stream", activeButton);
  await Promise.all(Object.keys(workflowAreas).map((areaKey) => reloadAreaHistory(areaKey)));
  updateJourneyRail();
  await initializeTranscriptionProgress();
  await renderTranscriptInTaskCard();
  initializeImageProviderCheck();
});
