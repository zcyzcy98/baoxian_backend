const PREFIX = 'agent-workspace:'
const KEY_ACTIVE = PREFIX + 'active-id'
const KEY_ACTIVE_SECTION = PREFIX + 'active-section'
const KEY_MESSAGES = (id) => `${PREFIX}messages:${id}`

export function loadActiveId() {
  try {
    return localStorage.getItem(KEY_ACTIVE) || null
  } catch {
    return null
  }
}

export function saveActiveId(id) {
  try {
    if (id) localStorage.setItem(KEY_ACTIVE, id)
  } catch {}
}

export function loadActiveSection() {
  try {
    return localStorage.getItem(KEY_ACTIVE_SECTION) || null
  } catch {
    return null
  }
}

export function saveActiveSection(id) {
  try {
    if (id) localStorage.setItem(KEY_ACTIVE_SECTION, id)
  } catch {}
}

export function loadMessages(agentId) {
  try {
    const raw = localStorage.getItem(KEY_MESSAGES(agentId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function saveMessages(agentId, messages) {
  try {
    localStorage.setItem(KEY_MESSAGES(agentId), JSON.stringify(messages))
  } catch {}
}

export function clearMessages(agentId) {
  try {
    localStorage.removeItem(KEY_MESSAGES(agentId))
  } catch {}
}

const KEY_WORKFLOW = (id) => `${PREFIX}workflow:${id}`

export function loadWorkflowState(agentId) {
  try {
    const raw = localStorage.getItem(KEY_WORKFLOW(agentId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

export function saveWorkflowState(agentId, state) {
  try {
    localStorage.setItem(KEY_WORKFLOW(agentId), JSON.stringify(state))
  } catch {}
}

export function clearWorkflowState(agentId) {
  try {
    localStorage.removeItem(KEY_WORKFLOW(agentId))
  } catch {}
}
