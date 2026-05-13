import { getToken } from './auth'

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function callAgent(endpoint, payload, model) {
  const res = await fetch(`/api/agents/${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify({ ...payload, model }),
  })
  if (!res.ok) {
    let msg = `接口调用失败 (${res.status})`
    try { const d = await res.json(); if (d?.error) msg = d.error } catch {}
    throw new Error(msg)
  }
  return res.json()
}

async function postJson(path, payload) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
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
    // HTTP 402 = 积分不足，抛出带特殊标记的错误
    if (res.status === 402) {
      const err = new Error(msg)
      err.code = 'INSUFFICIENT_CREDITS'
      throw err
    }
    throw new Error(msg)
  }
  return res.json()
}

async function getJson(path) {
  const res = await fetch(path, { headers: authHeaders() })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
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
  return getJson('/api/notes/rewrite-modes')
}

export async function rewriteWechat(payload) {
  return postJson('/api/notes/wechat/rewrite', payload)
}

export async function fetchImageTemplates() {
  return getJson('/api/agents/image-templates')
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
  return getJson('/api/topics/search-options')
}

export async function refreshTopics() {
  return postJson('/api/topics/refresh', {})
}

export async function fetchUserProfile() {
  return getJson('/api/profile')
}

// ─── 积分 ────────────────────────────────────────────────────────────────────
export async function fetchCreditsBalance() {
  return getJson('/api/credits/balance')
}

export async function fetchCreditsSummary() {
  return getJson('/api/credits/summary')
}

export async function fetchCreditsRecords(filter = 'all', page = 0, size = 20) {
  return getJson(`/api/credits/records?filter=${filter}&page=${page}&size=${size}`)
}

export async function fetchCreditsRecordContent(recordId) {
  return getJson(`/api/credits/records/${recordId}/content`)
}

// ─── 客户答疑 ────────────────────────────────────
export async function fetchAdvisorySessions() {
  return getJson('/api/advisory/sessions')
}

export async function createAdvisorySession(name, summary = '') {
  return postJson('/api/advisory/sessions', { name, summary })
}

export async function fetchAdvisorySession(id) {
  return getJson(`/api/advisory/sessions/${id}`)
}

export async function analyzeCustomer(sessionId, customerInfo, question, channel) {
  return postJson(`/api/advisory/sessions/${sessionId}/analyze`, { customerInfo, question, channel })
}

export async function deleteAdvisorySession(id) {
  const res = await fetch(`/api/advisory/sessions/${id}`, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) throw new Error('删除失败')
  return res.json()
}

// ─── 个人风格 ──────────────────────────────────────────────────────────────

export async function fetchStyleProfile() {
  return getJson('/api/style/profile')
}

export async function addStyleSource(payload) {
  return postJson('/api/style/sources', payload)
}

export async function uploadStyleSourceFile(file, title) {
  const fd = new FormData()
  fd.append('file', file)
  if (title) fd.append('title', title)
  const res = await fetch('/api/style/sources/upload', {
    method: 'POST',
    headers: authHeaders(),
    body: fd,
  })
  if (!res.ok) {
    let msg = `上传失败 (${res.status})`
    try { const d = await res.json(); if (d?.error) msg = d.error } catch {}
    throw new Error(msg)
  }
  return res.json()
}

export async function deleteStyleSource(id) {
  const res = await fetch(`/api/style/sources/${id}`, { method: 'DELETE', headers: authHeaders() })
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

export async function regenOneImage(content, imageDescription, imageRatio, imageProvider) {
  return postJson('/api/agents/xhs-regen-one-image', { content, style: imageDescription, imageRatio, imageProvider })
}

export async function parseRefMaterial(file) {
  const fd = new FormData()
  fd.append('file', file)
  const res = await fetch('/api/agents/parse-ref', {
    method: 'POST',
    headers: authHeaders(),
    body: fd,
  })
  if (!res.ok) {
    let msg = `解析失败 (${res.status})`
    try { const d = await res.json(); if (d?.error) msg = d.error } catch {}
    throw new Error(msg)
  }
  return res.json()
}

export async function generateSeedanceVideo(payload) {
  return postJson('/api/agents/video-generate-seedance', payload)
}

export async function mergeVideos(urls) {
  return postJson('/api/video/merge', { urls })
}

// ─── Admin (手动开通) ──────────────────────────────────────
// 用法：在浏览器控制台 import 或直接 fetch
// grantAccess('138xxxxxxxx') — 需要在后端 application.yml 里配 admin.secret-key
export async function grantAccess(phone, adminKey = 'chengzhi-admin-2024') {
  const res = await fetch('/api/admin/grant', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': adminKey },
    body: JSON.stringify({ phone }),
  })
  return res.json()
}

export async function revokeAccess(phone, adminKey = 'chengzhi-admin-2024') {
  const res = await fetch('/api/admin/revoke', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': adminKey },
    body: JSON.stringify({ phone }),
  })
  return res.json()
}
