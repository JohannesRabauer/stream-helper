const workflowAreas = {
  "pre-stream": {
    draftKey: "pre-stream",
    draftInputId: "draft-pre-stream",
    historyContainerId: "history-pre-stream",
    historyCountId: "history-count-pre-stream",
    categories: ["TOPIC_IDEA", "GUEST_IDEA"]
  },
  description: {
    draftKey: "description",
    draftInputId: "draft-description",
    historyContainerId: "history-description",
    historyCountId: "history-count-description",
    categories: ["YOUTUBE_TITLES", "YOUTUBE_DESCRIPTION", "YOUTUBE_TAGS"]
  },
  thumbnail: {
    draftKey: "thumbnail",
    draftInputId: "draft-thumbnail",
    historyContainerId: "history-thumbnail",
    historyCountId: "history-count-thumbnail",
    categories: ["THUMBNAIL_IDEA", "THUMBNAIL_PROMPT", "THUMBNAIL_ASSET"]
  },
  "social-announcements": {
    draftKey: "social-announcements",
    draftInputId: "draft-social-announcements",
    historyContainerId: "history-social-announcements",
    historyCountId: "history-count-social-announcements",
    categories: ["LINKEDIN_POST", "SOCIAL_POST", "HASHTAGS"]
  },
  transcription: {
    historyContainerId: "history-transcription",
    historyCountId: "history-count-transcription",
    categories: ["TRANSCRIPT"]
  },
  "post-stream": {
    draftKey: "post-stream",
    draftInputId: "draft-post-stream",
    historyContainerId: "history-post-stream",
    historyCountId: "history-count-post-stream",
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
  THUMBNAIL_IDEA: "Thumbnail ideas",
  THUMBNAIL_PROMPT: "Thumbnail prompts",
  THUMBNAIL_ASSET: "Thumbnail assets"
};

const categoryToArea = {
  TOPIC_IDEA: "pre-stream",
  GUEST_IDEA: "pre-stream",
  YOUTUBE_TITLES: "description",
  YOUTUBE_DESCRIPTION: "description",
  YOUTUBE_TAGS: "description",
  THUMBNAIL_IDEA: "thumbnail",
  THUMBNAIL_PROMPT: "thumbnail",
  THUMBNAIL_ASSET: "thumbnail",
  LINKEDIN_POST: "social-announcements",
  SOCIAL_POST: "social-announcements",
  HASHTAGS: "social-announcements",
  TRANSCRIPT: "transcription",
  CHAPTERS: "post-stream",
  SUMMARY: "post-stream"
};

const latestResultsByArea = {};
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
  "THUMBNAIL_IDEA",
  "THUMBNAIL_PROMPT"
]);

const spinnerFrames = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];
let transcriptionProgressPollTimer = null;
let transcriptionRequestInFlight = false;
let transcriptionAwaitingActiveState = false;
const timestampFormatter = createTimestampFormatter();

document.addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button || button.disabled) {
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
}

async function runStageBriefAction(areaKey, endpoint, button) {
  await withButtonLoading(button, async () => {
    const input = getDraftValue(areaKey);
    if (!input) {
      showStatus("Please add a working brief first.", "warning");
      return;
    }
    const data = await apiJson(`/api/projects/${projectId}/${endpoint}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({brief: input})
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
    latestResultsByArea[areaKey] = data;
    renderInlineResult(areaKey, data);
    reloadAreaHistory(areaKey).catch(console.error);
  }
  showStatus("Saved a new version.", "success");
}

function renderInlineResult(areaKey, data) {
  const panel = document.getElementById(`inline-result-${areaKey}`);
  const summary = document.getElementById(`inline-result-summary-${areaKey}`);
  const cards = document.getElementById(`inline-result-cards-${areaKey}`);
  const rawBox = document.getElementById(`inline-result-json-${areaKey}`);
  if (!panel || !summary || !cards || !rawBox) {
    return;
  }
  panel.hidden = false;
  rawBox.textContent = JSON.stringify(data, null, 2);
  cards.innerHTML = "";

  if (!data.variants || !Array.isArray(data.variants)) {
    summary.textContent = "No variant payload returned.";
    return;
  }

  summary.textContent = `${data.variants.length} saved version(s) for ${formatCategoryLabel(data.category || "result")}.`;

  const threadTurnsById = buildRefinementTurnsByThread(data.variants);
  if (Array.isArray(data.validationIssues) && data.validationIssues.length > 0) {
    const issueWrap = document.createElement("div");
    issueWrap.className = "validation-issues";
    issueWrap.innerHTML = `<strong>Validation checks:</strong>`;
    const list = document.createElement("ul");
    data.validationIssues.forEach((issue) => {
      const item = document.createElement("li");
      item.textContent = `${issue.code}: ${issue.message}`;
      list.appendChild(item);
    });
    issueWrap.appendChild(list);
    cards.appendChild(issueWrap);
  }

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
      areaKey
    });
    cards.appendChild(card);
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
}

function renderAreaHistory(areaKey, categoryResults) {
  const area = workflowAreas[areaKey];
  const container = document.getElementById(area.historyContainerId);
  const countNode = document.getElementById(area.historyCountId);
  container.innerHTML = "";

  const totalEntries = categoryResults.reduce((sum, result) => sum + (Array.isArray(result.artifacts) ? result.artifacts.length : 0), 0);
  countNode.textContent = `${totalEntries} entr${totalEntries === 1 ? "y" : "ies"}`;

  if (totalEntries === 0) {
    container.innerHTML = `<p class="muted">No saved history for this stage yet.</p>`;
    return;
  }

  const latestEntryId = resolveLatestHistoryEntryId(categoryResults);

  categoryResults.forEach(({category, artifacts}) => {
    const group = document.createElement("section");
    group.className = "history-group";
    const threadTurnsById = buildRefinementTurnsByThread(artifacts || []);

    const header = document.createElement("div");
    header.className = "section-heading history-group-heading";
    header.innerHTML = `
      <h3>${escapeHtml(formatCategoryLabel(category))}</h3>
      <span>${Array.isArray(artifacts) ? artifacts.length : 0} version(s)</span>
    `;
    group.appendChild(header);

    const list = document.createElement("div");
    list.className = "artifact-list";

    if (!Array.isArray(artifacts) || artifacts.length === 0) {
      list.innerHTML = `<p class="muted">Nothing saved yet.</p>`;
    } else {
      artifacts.forEach((artifact, index) => {
        const item = document.createElement("article");
        item.className = "artifact-item";
        item.style.animationDelay = `${index * 0.05}s`;
        const editable = isEditableArtifactCategory(category);
        const expanded = artifact.id === latestEntryId;
        item.innerHTML = `
          <details class="artifact-item-details" ${expanded ? "open" : ""}>
            <summary class="artifact-item-summary">
              <div>
                <strong>${escapeHtml(artifact.strategy || "version")}</strong>
                <div class="muted artifact-timestamp">Saved ${escapeHtml(formatTimestamp(artifact.createdAt))}</div>
              </div>
              <div class="inline-actions artifact-badges">
                ${renderArtifactBadges(artifact)}
              </div>
            </summary>
            <div class="artifact-item-body">
              <div class="artifact-copy">
                ${editable
                  ? `<textarea class="artifact-editor artifact-editor-history" rows="7" aria-label="Editable ${escapeHtml(formatCategoryLabel(category))} history text"></textarea>
                     <div class="artifact-save-state muted">Autosaves as a new version when you pause typing.</div>`
                  : `<pre>${escapeHtml(previewContent(artifact.content || "", category, 1100))}</pre>`}
              </div>
              <div class="inline-actions artifact-actions">
                <button type="button" class="secondary-button artifact-copy-button">Copy</button>
                <button type="button" class="secondary-button artifact-final-button">Mark final</button>
              </div>
              ${renderRefinePanel(category, resolveRefinementTurnsForArtifact(threadTurnsById, artifact))}
            </div>
          </details>
        `;
        const copyButton = item.querySelector(".artifact-copy-button");
        const finalButton = item.querySelector(".artifact-final-button");
        const textarea = item.querySelector(".artifact-editor");
        if (textarea) {
         setupArtifactEditor({
            textarea,
            artifact,
            category,
            areaKey,
            timestampNode: item.querySelector(".artifact-timestamp"),
            badgesNode: item.querySelector(".artifact-badges"),
            saveStateNode: item.querySelector(".artifact-save-state"),
            source: "history"
          });
        }
        copyButton.addEventListener("click", () => copyToClipboard(textarea ? textarea.value : (artifact.content || "")));
        finalButton.addEventListener("click", async () => {
          await finalizeArtifact(category, artifact.id);
        });
        bindRefinementControls({
          container: item,
          artifact,
          category,
          areaKey
        });
        list.appendChild(item);
      });
    }

    group.appendChild(list);
    container.appendChild(group);
  });
}

function resolveLatestHistoryEntryId(categoryResults) {
  let latestId = null;
  let latestTimestamp = Number.NEGATIVE_INFINITY;

  categoryResults.forEach(({artifacts}) => {
    if (!Array.isArray(artifacts)) {
      return;
    }
    artifacts.forEach((artifact) => {
      if (!artifact?.id) {
        return;
      }
      const parsedTimestamp = Date.parse(artifact.createdAt || "");
      if (latestId === null) {
        latestId = artifact.id;
        latestTimestamp = Number.isFinite(parsedTimestamp) ? parsedTimestamp : Number.NEGATIVE_INFINITY;
        return;
      }
      if (Number.isFinite(parsedTimestamp) && parsedTimestamp > latestTimestamp) {
        latestId = artifact.id;
        latestTimestamp = parsedTimestamp;
      }
    });
  });

  return latestId;
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
    } else if (source === "history") {
      bumpHistoryCount(areaKey);
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

function bumpHistoryCount(areaKey) {
  if (!areaKey) {
    return;
  }
  const node = document.getElementById(workflowAreas[areaKey]?.historyCountId || "");
  if (!node) {
    return;
  }
  const match = node.textContent.match(/^(\d+)/);
  const current = match ? Number.parseInt(match[1], 10) : 0;
  node.textContent = `${current + 1} entr${current + 1 === 1 ? "y" : "ies"}`;
}

function copyRawResult(areaKey) {
  const result = latestResultsByArea[areaKey];
  if (!result) {
    showStatus("No result available yet.", "warning");
    return;
  }
  copyToClipboard(JSON.stringify(result, null, 2));
}

function copyRecommendedContent(areaKey) {
  const result = latestResultsByArea[areaKey];
  if (!result || !Array.isArray(result.variants)) {
    showStatus("No result available yet.", "warning");
    return;
  }
  const recommended = result.variants.find((variant) => variant.recommended) || result.variants[0];
  if (!recommended) {
    showStatus("No generated content available.", "warning");
    return;
  }
  copyToClipboard(recommended.content || "");
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
  const panel = document.getElementById("transcriptionProgressPanel");
  const percentNode = document.getElementById("transcriptionProgressPercent");
  const fill = document.getElementById("transcriptionProgressFill");
  const messageNode = document.getElementById("transcriptionProgressMessage");
  const track = panel?.querySelector(".task-progress-track");
  if (!panel || !percentNode || !fill || !messageNode || !track) {
    return;
  }
  const percent = clampPercent(snapshot?.percent ?? 0);
  const message = snapshot?.message || "Transcription in progress...";
  panel.hidden = false;
  panel.classList.toggle("running", Boolean(snapshot?.active));
  panel.classList.toggle("complete", !snapshot?.active && !snapshot?.failed && percent >= 100);
  panel.classList.toggle("error", Boolean(snapshot?.failed));
  percentNode.textContent = `${percent}%`;
  fill.style.width = `${percent}%`;
  messageNode.textContent = message;
  track.setAttribute("aria-valuenow", String(percent));
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

window.addEventListener("DOMContentLoaded", async () => {
  document.title = `${projectName} · Stream Helper`;
  toggleProjectRenameEditor(false);
  if (!projectConfig.workspaceDrafts) {
    projectConfig.workspaceDrafts = {};
  }
  migrateLegacyPromotionState();
  bindAutosaveInputs();
  await setProjectNotesMode(projectNotesMode);
  const activeTab = projectConfig.currentWorkflowStage || "pre-stream";
  const activeButton = document.querySelector(`.tab-button[data-tab="${activeTab}"]`) || document.querySelector('.tab-button[data-tab="pre-stream"]');
  selectWorkflowTab(activeButton?.dataset.tab || "pre-stream", activeButton);
  await Promise.all(Object.keys(workflowAreas).map((areaKey) => reloadAreaHistory(areaKey)));
  await initializeTranscriptionProgress();
});
