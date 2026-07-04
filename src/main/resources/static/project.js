const templatePrompts = {
  pre: `Goal: Plan the next stream.\nAudience: Intermediate Java developers.\nConstraints: 90 minutes max, practical output, guest-friendly format.\n`,
  promo: `Generate content for YouTube, LinkedIn, and X.\nTone: Practical, energetic, and clear.\nInclude CTA for live attendance and replay watchers.\n`,
  post: `Use this transcript to create chapters and a detailed recap.\nFocus on key technical decisions, mistakes, fixes, and lessons learned.\n`
};

let lastResult = null;
let lastRawJson = "";

function toggleNoteView(mode) {
  document.getElementById("noteEditArea").hidden = mode !== "edit";
  document.getElementById("notePreviewArea").hidden = mode !== "preview";
}

function injectTemplate(kind) {
  const input = document.getElementById("assistantInput");
  input.value = templatePrompts[kind] ?? "";
  input.focus();
}

function exportProject() {
  window.location.href = `/api/projects/${projectId}/export`;
}

async function runBriefAction(endpoint, button) {
  await withButtonLoading(button, async () => {
    const input = document.getElementById("assistantInput").value.trim();
    if (!input) {
      showStatus("Please provide a brief first.", "warning");
      return;
    }
    const data = await apiJson(`/api/projects/${projectId}/${endpoint}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({brief: input})
    });
    renderResult(data);
  });
}

async function runTextAction(endpoint, button) {
  await withButtonLoading(button, async () => {
    const input = document.getElementById("assistantInput").value.trim();
    if (!input) {
      showStatus("Please provide transcript text or source context first.", "warning");
      return;
    }
    const data = await apiJson(`/api/projects/${projectId}/${endpoint}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({text: input})
    });
    renderResult(data);
  });
}

async function createThumbnail(builtIn, button) {
  await withButtonLoading(button, async () => {
    const input = document.getElementById("assistantInput").value.trim();
    if (!input) {
      showStatus("Please provide a thumbnail prompt context first.", "warning");
      return;
    }
    const data = await apiJson(`/api/projects/${projectId}/thumbnails/create`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({prompt: input, builtIn})
    });
    renderResult(data);
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
  });
}

async function loadEffectivePrompt(button) {
  await withButtonLoading(button, async () => {
    const category = document.getElementById("categorySelect").value;
    const data = await apiJson(`/api/projects/${projectId}/effective-prompt/${category}`, {
      method: "POST"
    });
    document.getElementById("effectivePromptBox").textContent = data.effectivePrompt || JSON.stringify(data, null, 2);
    showStatus(`Loaded effective prompt for ${category}.`, "success");
  });
}

async function loadArtifactHistory(button) {
  await withButtonLoading(button, async () => {
    const category = document.getElementById("artifactCategorySelect").value;
    const artifacts = await apiJson(`/api/projects/${projectId}/artifacts/${category}`, {method: "GET"});
    renderArtifactHistory(category, artifacts);
  });
}

async function finalizeArtifact(category, artifactId) {
  const data = await apiJson(
      `/api/projects/${projectId}/artifacts/${category}/${artifactId}/finalize`,
      {method: "POST"});
  showStatus(`Marked ${artifactId} as final for ${category}.`, "success");
  await loadArtifactHistory();
  return data;
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

  summary.textContent = `${data.variants.length} variant(s) for ${data.category || "result"} generated.`;

  if (Array.isArray(data.validationIssues) && data.validationIssues.length > 0) {
    const issueWrap = document.createElement("div");
    issueWrap.className = "validation-issues";
    const title = document.createElement("strong");
    title.textContent = "Validation checks:";
    issueWrap.appendChild(title);
    const ul = document.createElement("ul");
    data.validationIssues.forEach(issue => {
      const li = document.createElement("li");
      li.textContent = `${issue.code}: ${issue.message}`;
      ul.appendChild(li);
    });
    issueWrap.appendChild(ul);
    cards.appendChild(issueWrap);
  }

  data.variants.forEach(variant => {
    const card = document.createElement("article");
    card.className = "variant-card";
    card.innerHTML = `
      <div class="variant-card-header">
        <div>
          <h4>${escapeHtml(variant.strategy || "variant")}</h4>
          <span class="muted">ID: ${escapeHtml(variant.id)}</span>
        </div>
        <div class="inline-actions">
          ${variant.recommended ? '<span class="badge">Recommended</span>' : ""}
          ${variant.finalVersion ? '<span class="badge final">Final</span>' : ""}
        </div>
      </div>
      <pre>${escapeHtml(variant.content || "")}</pre>
      <div class="inline-actions">
        <button type="button" class="secondary-button">Copy content</button>
        <button type="button" class="secondary-button">Mark final</button>
      </div>
    `;
    const [copyBtn, finalBtn] = card.querySelectorAll("button");
    copyBtn.addEventListener("click", () => copyToClipboard(variant.content || ""));
    finalBtn.addEventListener("click", async () => {
      await finalizeArtifact(data.category, variant.id);
      showStatus(`Finalized ${variant.strategy}.`, "success");
    });
    cards.appendChild(card);
  });

  showStatus("Generation complete.", "success");
}

function renderArtifactHistory(category, artifacts) {
  const list = document.getElementById("artifactHistoryList");
  list.innerHTML = "";
  if (!Array.isArray(artifacts) || artifacts.length === 0) {
    list.innerHTML = `<p class="muted">No artifacts found for ${category} yet.</p>`;
    return;
  }

  artifacts.forEach(artifact => {
    const item = document.createElement("article");
    item.className = "artifact-item";
    item.innerHTML = `
      <div>
        <strong>${escapeHtml(artifact.strategy || "version")}</strong>
        <div class="muted">ID: ${escapeHtml(artifact.id)} · ${escapeHtml(artifact.createdAt || "")}</div>
      </div>
      <div class="inline-actions">
        ${artifact.finalVersion ? '<span class="badge final">Final</span>' : ""}
        <button type="button" class="secondary-button">Copy</button>
        <button type="button" class="secondary-button">Mark final</button>
      </div>
    `;
    const buttons = item.querySelectorAll("button");
    buttons[0].addEventListener("click", () => copyToClipboard(artifact.content || ""));
    buttons[1].addEventListener("click", async () => {
      await finalizeArtifact(category, artifact.id);
    });
    list.appendChild(item);
  });
  showStatus(`Loaded ${artifacts.length} artifact version(s).`, "success");
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
  const recommended = lastResult.variants.find(v => v.recommended) || lastResult.variants[0];
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

function showStatus(message, kind = "success") {
  const bar = document.getElementById("statusBar");
  bar.hidden = false;
  bar.textContent = message;
  bar.className = `status-bar ${kind}`;
}

async function withButtonLoading(button, fn) {
  if (!button) {
    return fn();
  }
  const original = button.textContent;
  button.disabled = true;
  button.textContent = "Working...";
  try {
    await fn();
  } catch (error) {
    console.error(error);
  } finally {
    button.disabled = false;
    button.textContent = original;
  }
}

function escapeHtml(value) {
  return (value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
}

window.addEventListener("DOMContentLoaded", () => {
  document.title = `${projectName} · Stream Helper`;
});
