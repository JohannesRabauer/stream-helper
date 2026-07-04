let globalConfig = JSON.parse(JSON.stringify(initialGlobalConfig || {}));
let globalConfigSaveTimer = null;

function toggleGlobalLlmDefinitionsDrawer(forceOpen) {
  const drawer = document.getElementById("globalLlmDefinitionsDrawer");
  const toggle = document.getElementById("globalLlmDefinitionsToggle");
  if (!drawer || !toggle) {
    return;
  }
  const open = typeof forceOpen === "boolean" ? forceOpen : !drawer.classList.contains("open");
  drawer.classList.toggle("open", open);
  drawer.setAttribute("aria-hidden", String(!open));
  toggle.setAttribute("aria-expanded", String(open));
  toggle.textContent = open ? "Hide global LLM definitions" : "Show global LLM definitions";
}

function scheduleGlobalConfigSave() {
  clearTimeout(globalConfigSaveTimer);
  updateGlobalDefinitionsSaveState("Autosaving global definitions...");
  globalConfigSaveTimer = setTimeout(() => {
    saveGlobalConfig().catch((error) => {
      console.error(error);
      updateGlobalDefinitionsSaveState("Failed to autosave global definitions.", true);
      showProjectsStatus("Failed to autosave global definitions.", "error");
    });
  }, 450);
}

async function saveGlobalConfig() {
  globalConfig = await apiJson("/api/config/global", {
    method: "PUT",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(globalConfig)
  });
  updateGlobalDefinitionsSaveState("Global definitions saved.");
}

function updateGlobalDefinitionsSaveState(message, isError = false) {
  const node = document.getElementById("globalDefinitionsSaveState");
  if (!node) {
    return;
  }
  node.textContent = message;
  node.classList.toggle("error", isError);
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
    throw new Error(data.message || `HTTP ${response.status}`);
  }
  return data;
}

function showProjectsStatus(message, kind = "success", timeoutMs = 3500) {
  const icons = {success: "✓", warning: "⚠", error: "✕"};
  const bar = document.getElementById("projectsStatusBar");
  if (!bar) {
    return;
  }
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

window.addEventListener("DOMContentLoaded", () => {
  const input = document.getElementById("globalDefinitionsInput");
  if (!input) {
    return;
  }
  input.value = globalConfig.globalInstruction || "";
  input.addEventListener("input", () => {
    globalConfig.globalInstruction = input.value;
    scheduleGlobalConfigSave();
  });
});
