const workflowAreas = {
  "pre-stream": {
    draftKey: "pre-stream",
    draftInputId: "draft-pre-stream",
    historyContainerId: "history-pre-stream",
    historyCountId: "history-count-pre-stream",
    categories: ["TOPIC_IDEA", "GUEST_IDEA"]
  },
  promotion: {
    draftKey: "promotion",
    draftInputId: "draft-promotion",
    historyContainerId: "history-promotion",
    historyCountId: "history-count-promotion",
    categories: ["YOUTUBE_DESCRIPTION", "LINKEDIN_POST", "SOCIAL_POST", "HASHTAGS", "YOUTUBE_TAGS", "THUMBNAIL_PROMPT", "THUMBNAIL_ASSET"]
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
  YOUTUBE_DESCRIPTION: "YouTube descriptions",
  LINKEDIN_POST: "LinkedIn posts",
  SOCIAL_POST: "Social posts",
  HASHTAGS: "Hashtags",
  YOUTUBE_TAGS: "YouTube tags",
  TRANSCRIPT: "Transcripts",
  CHAPTERS: "Chapters",
  SUMMARY: "Summaries",
  THUMBNAIL_PROMPT: "Thumbnail prompts",
  THUMBNAIL_ASSET: "Thumbnail assets"
};

const categoryToArea = {
  TOPIC_IDEA: "pre-stream",
  GUEST_IDEA: "pre-stream",
  YOUTUBE_DESCRIPTION: "promotion",
  LINKEDIN_POST: "promotion",
  SOCIAL_POST: "promotion",
  HASHTAGS: "promotion",
  YOUTUBE_TAGS: "promotion",
  THUMBNAIL_PROMPT: "promotion",
  THUMBNAIL_ASSET: "promotion",
  TRANSCRIPT: "transcription",
  CHAPTERS: "post-stream",
  SUMMARY: "post-stream"
};

let lastResult = null;
let lastRawJson = "";
let projectConfig = JSON.parse(JSON.stringify(initialProjectConfig || {}));
let workflowNotes = {...(initialWorkflowNotes || {})};
let configSaveTimer = null;
const noteSaveTimers = new Map();

const spinnerFrames = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];

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

function selectWorkflowTab(areaKey, button) {
  document.querySelectorAll("[data-tab-panel]").forEach((panel) => {
    panel.hidden = panel.dataset.tabPanel !== areaKey;
    panel.classList.toggle("active", panel.dataset.tabPanel === areaKey);
  });
  document.querySelectorAll(".tab-button").forEach((tabButton) => {
    tabButton.classList.toggle("active", tabButton.dataset.tab === areaKey);
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
    const data = await apiJson(`/api/projects/${projectId}/transcripts/file`, {
      method: "POST",
      body: formData
    });
    renderResult(data);
    await reloadAreaHistory("transcription");
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
    const data = await apiJson(`/api/projects/${projectId}/transcripts/youtube`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        youtubeUrl,
        language: language || null,
        diarize: document.getElementById("transcriptionDiarize").checked
      })
    });
    renderResult(data);
    await reloadAreaHistory("transcription");
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

function renderResult(data) {
  lastResult = data;
  lastRawJson = JSON.stringify(data, null, 2);
  document.getElementById("resultBox").textContent = lastRawJson;

  const summary = document.getElementById("resultSummary");
  const cards = document.getElementById("variantCards");
  cards.innerHTML = "";

  if (!data.variants || !Array.isArray(data.variants)) {
    summary.textContent = "No variant payload returned.";
    return;
  }

  summary.textContent = `${data.variants.length} saved version(s) for ${formatCategoryLabel(data.category || "result")}.`;

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
    card.innerHTML = `
      <div class="variant-card-header">
        <div>
          <h4>${escapeHtml(variant.strategy || "variant")}</h4>
          <div class="muted">Saved ${escapeHtml(formatTimestamp(variant.createdAt))}</div>
        </div>
        <div class="inline-actions">
          ${variant.recommended ? '<span class="badge">Recommended</span>' : ""}
          ${variant.finalVersion ? '<span class="badge final">Final</span>' : ""}
        </div>
      </div>
      <pre>${escapeHtml(previewContent(variant.content || "", data.category, 2400))}</pre>
      <div class="inline-actions">
        <button type="button" class="secondary-button">Copy content</button>
        <button type="button" class="secondary-button">Mark final</button>
      </div>
    `;
    const [copyButton, finalButton] = card.querySelectorAll("button");
    copyButton.addEventListener("click", () => copyToClipboard(variant.content || ""));
    finalButton.addEventListener("click", async () => {
      await finalizeArtifact(data.category, variant.id);
    });
    cards.appendChild(card);
  });

  const areaKey = categoryToArea[data.category];
  if (areaKey) {
    reloadAreaHistory(areaKey).catch(console.error);
  }
  showStatus("Saved a new version.", "success");
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

  categoryResults.forEach(({category, artifacts}) => {
    const group = document.createElement("section");
    group.className = "history-group";

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
        item.innerHTML = `
          <div class="artifact-copy">
            <strong>${escapeHtml(artifact.strategy || "version")}</strong>
            <div class="muted">Saved ${escapeHtml(formatTimestamp(artifact.createdAt))}</div>
            <pre>${escapeHtml(previewContent(artifact.content || "", category, 1100))}</pre>
          </div>
          <div class="inline-actions artifact-actions">
            ${artifact.finalVersion ? '<span class="badge final">Final</span>' : ""}
            <button type="button" class="secondary-button">Copy</button>
            <button type="button" class="secondary-button">Mark final</button>
          </div>
        `;
        const [copyButton, finalButton] = item.querySelectorAll("button");
        copyButton.addEventListener("click", () => copyToClipboard(artifact.content || ""));
        finalButton.addEventListener("click", async () => {
          await finalizeArtifact(category, artifact.id);
        });
        list.appendChild(item);
      });
    }

    group.appendChild(list);
    container.appendChild(group);
  });
}

function copyRawResult() {
  if (!lastRawJson) {
    showStatus("No result available yet.", "warning");
    return;
  }
  copyToClipboard(lastRawJson);
}

function copyRecommendedContent() {
  if (!lastResult || !Array.isArray(lastResult.variants)) {
    showStatus("No result available yet.", "warning");
    return;
  }
  const recommended = lastResult.variants.find((variant) => variant.recommended) || lastResult.variants[0];
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

function getDraftValue(areaKey) {
  const input = document.getElementById(workflowAreas[areaKey]?.draftInputId || "");
  return input ? input.value.trim() : "";
}

function formatTimestamp(value) {
  if (!value) {
    return "unknown time";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function formatCategoryLabel(category) {
  return categoryLabels[category] || category;
}

function previewContent(content, category, defaultMaxChars) {
  const maxChars = category === "TRANSCRIPT" ? 700 : defaultMaxChars;
  if (!content || content.length <= maxChars) {
    return content || "";
  }
  return `${content.slice(0, maxChars)}\n\n… preview truncated …`;
}

function escapeHtml(value) {
  return (value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
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

function scheduleNoteSave(noteId, markdown) {
  clearTimeout(noteSaveTimers.get(noteId));
  const timer = setTimeout(async () => {
    try {
      await apiJson(`/api/projects/${projectId}/notes`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({noteId, markdown})
      });
    } catch (error) {
      console.error(error);
      showStatus(`Failed to autosave ${noteId}.`, "error");
    }
  }, 450);
  noteSaveTimers.set(noteId, timer);
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

  document.querySelectorAll("[data-note-id]").forEach((textarea) => {
    const noteKey = textarea.dataset.noteKey;
    const noteId = textarea.dataset.noteId;
    textarea.value = workflowNotes[noteKey] || "";
    textarea.addEventListener("input", () => {
      workflowNotes[noteKey] = textarea.value;
      scheduleNoteSave(noteId, textarea.value);
    });
  });

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
}

window.addEventListener("DOMContentLoaded", async () => {
  document.title = `${projectName} · Stream Helper`;
  if (!projectConfig.workspaceDrafts) {
    projectConfig.workspaceDrafts = {};
  }
  bindAutosaveInputs();
  const activeTab = projectConfig.currentWorkflowStage || "pre-stream";
  const activeButton = document.querySelector(`.tab-button[data-tab="${activeTab}"]`) || document.querySelector('.tab-button[data-tab="pre-stream"]');
  selectWorkflowTab(activeButton?.dataset.tab || "pre-stream", activeButton);
  await Promise.all(Object.keys(workflowAreas).map((areaKey) => reloadAreaHistory(areaKey)));
});
