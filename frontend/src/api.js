export async function callAgent(endpoint, payload, model) {
  const res = await fetch(`/api/agents/${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...payload, model }),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`接口调用失败 (${res.status}): ${text || res.statusText}`)
  }
  return res.json()
}

async function postJson(path, payload) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const data = await res.json()
      if (data?.error) msg = data.error
    } catch {
      // Keep the HTTP status message when the response body is not JSON.
    }
    throw new Error(msg)
  }
  return res.json()
}

export async function extractXhsNote(url, cookie) {
  return postJson('/api/notes/extract', { url, cookie })
}

export async function extractWechatArticle(url, cookie) {
  return postJson('/api/notes/wechat/extract', { url, cookie })
}

/**
 * 自动识别小红书 / 公众号链接, 返回 { source: 'xhs' | 'wechat', note }.
 * 前端用这一个接口就可以同时支持两条路径.
 */
export async function extractAutoNote(url, cookie) {
  return postJson('/api/notes/extract-auto', { url, cookie })
}

export async function rewriteXhsNote(payload) {
  return postJson('/api/notes/rewrite', payload)
}

export async function createWechatDraft(payload) {
  return postJson('/api/wechat/draft', payload)
}

export async function fetchRewriteModes() {
  const res = await fetch('/api/notes/rewrite-modes')
  if (!res.ok) throw new Error('加载改写模式列表失败')
  return res.json()
}

export async function rewriteWechat(payload) {
  return postJson('/api/notes/wechat/rewrite', payload)
}

export async function fetchImageTemplates() {
  const res = await fetch('/api/agents/image-templates')
  if (!res.ok) throw new Error('加载图片模板失败')
  return res.json()
}

export async function generateImage(payload) {
  return postJson('/api/agents/image', payload)
}

export async function generateSeedreamImage(payload) {
  return postJson('/api/agents/image/seedream', payload)
}

export async function createLibTvProject() {
  return postJson('/api/agents/libtv/new-project', {})
}

export async function fetchLibTvSessionVideos(sessionId, afterSeq = 0, finalOnly = false) {
  return postJson('/api/agents/libtv/session-videos', { sessionId, afterSeq, finalOnly })
}

// ─── 选题广场 ────────────────────────────────────
export async function fetchDailyTopics(payload = {}) {
  return postJson('/api/topics/daily', payload)
}

export async function searchHotTopics(keyword, limit = 50, hashid = '') {
  return postJson('/api/topics/search', { keyword, limit, hashid })
}

export async function fetchSearchOptions() {
  const res = await fetch('/api/topics/search-options')
  if (!res.ok) throw new Error('加载搜索选项失败')
  return res.json()
}

// ─── 客户答疑 ────────────────────────────────────
export async function fetchAdvisorySessions() {
  const res = await fetch('/api/advisory/sessions')
  if (!res.ok) throw new Error('加载客户列表失败')
  return res.json()
}

export async function createAdvisorySession(name, summary = '') {
  return postJson('/api/advisory/sessions', { name, summary })
}

export async function fetchAdvisorySession(id) {
  const res = await fetch(`/api/advisory/sessions/${id}`)
  if (!res.ok) throw new Error('加载客户会话失败')
  return res.json()
}

export async function analyzeCustomer(sessionId, customerInfo, question, channel) {
  return postJson(`/api/advisory/sessions/${sessionId}/analyze`, { customerInfo, question, channel })
}

export async function deleteAdvisorySession(id) {
  const res = await fetch(`/api/advisory/sessions/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('删除失败')
  return res.json()
}

// ─── 个人风格 ──────────────────────────────────────────────────────────────

export async function fetchStyleProfile() {
  const res = await fetch('/api/style/profile')
  if (!res.ok) throw new Error('加载风格档案失败')
  return res.json()
}

export async function addStyleSource(payload) {
  return postJson('/api/style/sources', payload)
}

export async function deleteStyleSource(id) {
  const res = await fetch(`/api/style/sources/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('删除素材失败')
  return res.json()
}

export async function trainStyle(model) {
  return postJson('/api/style/train', model ? { model } : {})
}

export async function previewStyle(topic, model) {
  return postJson('/api/style/preview', { topic, model })
}

export async function generateXhsBatchImages(content, imageCount, imageRatio, imageProvider) {
  return postJson('/api/agents/xhs-batch-images', { content, imageCount, imageRatio, imageProvider })
}

export async function generateSeedanceVideo(payload) {
  return postJson('/api/agents/video-generate-seedance', payload)
}

export async function mergeVideos(urls) {
  return postJson('/api/video/merge', { urls })
}
