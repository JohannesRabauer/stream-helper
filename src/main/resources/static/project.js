async function runJsonEndpoint(endpoint) {
  const input = document.getElementById("assistantInput").value;
  const response = await fetch(`/api/projects/${projectId}/${endpoint}`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({brief: input})
  });
  await renderResponse(response);
}

async function runTextEndpoint(endpoint) {
  const input = document.getElementById("assistantInput").value;
  const response = await fetch(`/api/projects/${projectId}/${endpoint}`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({text: input})
  });
  await renderResponse(response);
}

async function createThumbnail(builtIn) {
  const input = document.getElementById("assistantInput").value;
  const response = await fetch(`/api/projects/${projectId}/thumbnails/create`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({prompt: input, builtIn})
  });
  await renderResponse(response);
}

async function transcribeFile() {
  const fileInput = document.getElementById("transcriptionFile");
  if (!fileInput.files.length) {
    alert("Please choose a file first.");
    return;
  }
  const formData = new FormData();
  formData.append("file", fileInput.files[0]);
  const language = document.getElementById("transcriptionLanguage").value;
  if (language) {
    formData.append("language", language);
  }
  formData.append("diarize", document.getElementById("transcriptionDiarize").checked.toString());
  const response = await fetch(`/api/projects/${projectId}/transcripts/file`, {
    method: "POST",
    body: formData
  });
  await renderResponse(response);
}

async function transcribeYoutube() {
  const youtubeUrl = document.getElementById("youtubeUrl").value;
  if (!youtubeUrl) {
    alert("Please enter a YouTube URL.");
    return;
  }
  const language = document.getElementById("transcriptionLanguage").value;
  const response = await fetch(`/api/projects/${projectId}/transcripts/youtube`, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({
      youtubeUrl,
      language: language || null,
      diarize: document.getElementById("transcriptionDiarize").checked
    })
  });
  await renderResponse(response);
}

async function loadEffectivePrompt() {
  const category = document.getElementById("categorySelect").value;
  const response = await fetch(`/api/projects/${projectId}/effective-prompt/${category}`, {
    method: "POST"
  });
  const data = await response.json();
  document.getElementById("effectivePromptBox").textContent = data.effectivePrompt || JSON.stringify(data, null, 2);
}

async function renderResponse(response) {
  const box = document.getElementById("resultBox");
  const text = await response.text();
  try {
    const json = JSON.parse(text);
    box.textContent = JSON.stringify(json, null, 2);
  } catch (e) {
    box.textContent = text;
  }
  if (!response.ok) {
    box.textContent = `HTTP ${response.status}\n${box.textContent}`;
  }
}
