import { useEffect, useMemo, useState } from 'react'
import { fetchLibTvSessionVideos } from '../api'
import MessageBubble from './MessageBubble'
import './VideoMergePanel.css'

const MERGE_POLL_INTERVAL_MS = 25000
const MERGE_MAX_POLLS = 144

export default function VideoMergePanel({
  agent,
  sourceMessages,
  messages,
  loading,
  onSubmit,
  onClear,
}) {
  const [selectedIds, setSelectedIds] = useState([])
  const [instruction, setInstruction] = useState('')
  const [sessionId, setSessionId] = useState('')
  const [sessionVideos, setSessionVideos] = useState([])
  const [sessionLoading, setSessionLoading] = useState(false)
  const [sessionError, setSessionError] = useState('')
  const [activeSessionId, setActiveSessionId] = useState('')
  const [mergePolls, setMergePolls] = useState([])
  const [mergePollsTick, setMergePollsTick] = useState(0)

  const videos = activeSessionId ? sessionVideos : []
  const selectedVideos = selectedIds
    .map((id) => videos.find((video) => video.id === id))
    .filter(Boolean)

  const isMerging = mergePolls.length > 0

  const availableSessionIds = useMemo(() => {
    const ids = new Set()
    sourceMessages.forEach((msg) => {
      if (msg.sessionId) {
        ids.add(msg.sessionId)
      }
    })
    return Array.from(ids).sort().reverse()
  }, [sourceMessages])

  const latestSessionId = availableSessionIds[0] || null

  useEffect(() => {
    if (mergePolls.length === 0) return undefined
    const timer = setInterval(() => {
      setMergePollsTick((prev) => prev + 1)
    }, MERGE_POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [mergePolls.length])

  useEffect(() => {
    const due = mergePolls.filter(
      (poll) => poll.tick > poll.handledTick && !poll.requesting,
    )
    due.forEach((poll) => pollMergeTask(poll))
  }, [mergePolls, mergePollsTick])

  const pollMergeTask = async (poll) => {
    setMergePolls((polls) =>
      polls.map((p) => (p.id === poll.id ? { ...p, requesting: true, handledTick: poll.tick } : p)),
    )

    try {
      const result = await fetchLibTvSessionVideos(poll.sessionId, poll.cursor || 0)
      const urls = (result.resultUrls || []).filter(
        (url) => isVideoUrl(url) && !poll.seenUrls.includes(url),
      )
      const nextPollCount = (poll.pollCount || 0) + 1

      if (urls.length > 0) {
        const newVideos = urls.map((url) => ({
          id: `${poll.sessionId}-${Date.now()}-${url}`,
          url,
          sessionId: poll.sessionId,
        }))

        setMergePolls((polls) =>
          polls.map((p) =>
            p.id === poll.id
              ? {
                  ...p,
                  requesting: false,
                  cursor: result.nextSeq ?? p.cursor ?? 0,
                  seenUrls: [...p.seenUrls, ...urls],
                  pollCount: nextPollCount,
                  hasResult: true,
                  resultVideos: [...(p.resultVideos || []), ...newVideos],
                  currentStep: 5,
                  progressPercent: 100,
                  stepName: '合成完成',
                }
              : p,
          ),
        )
      } else {
        if (nextPollCount >= MERGE_MAX_POLLS) {
          setMergePolls((polls) =>
            polls.map((p) => (p.id === poll.id ? { ...p, completed: true } : p)),
          )
          return
        }

        setMergePolls((polls) =>
          polls.map((p) =>
            p.id === poll.id
              ? {
                  ...p,
                  requesting: false,
                  cursor: result.nextSeq ?? p.cursor ?? 0,
                  pollCount: nextPollCount,
                  currentStep: result.currentStep ?? p.currentStep ?? 1,
                  totalSteps: result.totalSteps ?? p.totalSteps ?? 5,
                  progressPercent: result.progressPercent ?? Math.min(90, 20 + nextPollCount * 2),
                  stepName: result.stepName || '视频合成中',
                }
              : p,
          ),
        )
      }
    } catch (err) {
      console.error('Merge polling failed:', err)
      setMergePolls((polls) =>
        polls.map((p) => (p.id === poll.id ? { ...p, error: err.message } : p)),
      )
    }
  }

  const toggleVideo = (video) => {
    setSelectedIds((prev) =>
      prev.includes(video.id)
        ? prev.filter((id) => id !== video.id)
        : [...prev, video.id],
    )
  }

  const moveSelected = (idx, dir) => {
    setSelectedIds((prev) => {
      const next = [...prev]
      const target = idx + dir
      if (target < 0 || target >= next.length) return prev
      ;[next[idx], next[target]] = [next[target], next[idx]]
      return next
    })
  }

  const handleSubmit = () => {
    if (loading || selectedVideos.length < 2) return
    const mergeSessionId = `merge-${Date.now()}`

    setMergePolls((prev) => [
      ...prev,
      {
        id: mergeSessionId,
        sessionId: mergeSessionId,
        tick: 0,
        handledTick: 0,
        requesting: false,
        pollCount: 0,
        cursor: 0,
        seenUrls: [],
        currentStep: 0,
        totalSteps: 5,
        progressPercent: 10,
        stepName: '视频合成中',
        resultVideos: [],
      },
    ])

    onSubmit(
      agent,
      {
        videoUrls: selectedVideos.map((video) => video.url),
        script: instruction.trim(),
      },
      'libtv',
    )
  }

  const handleLoadSession = async () => {
    const value = sessionId.trim()
    if (!value || sessionLoading || loading) return
    setSessionError('')
    setSessionLoading(true)
    try {
      const result = await fetchLibTvSessionVideos(value)
      const loaded = urlsToVideos(result.resultUrls || [], result.sessionId || value)
      setSessionVideos(loaded)
      setActiveSessionId(value)
      setSelectedIds([])
      if (loaded.length === 0) {
        setSessionError('这个 session 暂时没有可拼接的视频结果。')
      }
    } catch (err) {
      setSessionError(err.message)
    } finally {
      setSessionLoading(false)
    }
  }



  const activeMergePoll = mergePolls.find((p) => !p.completed && !p.error)

  const displayMessages = messages && messages.length > 0 ? messages : [{
    role: 'assistant',
    type: 'text',
    content: videos.length > 0
      ? '请选择要拼接的视频，并调整顺序。'
      : '请先加载一个 Session 以获得可拼接的视频。',
  }]

  return (
    <main className="merge-panel">
      <header className="merge-header">
        <h1 className="merge-title">{agent.name}</h1>
        <button
          type="button"
          className="merge-header-action"
          onClick={onClear}
          disabled={loading || !(messages && messages.length > 0)}
          title="清空当前拼接记录"
        >
          清空记录
        </button>
      </header>

      <div className="merge-body">
        <section className="merge-col merge-source">
          <div className="merge-col-head">
            <h2>Session 视频</h2>
            <span>{videos.length} 个</span>
          </div>
          <div className="merge-session-box">
            <label className="merge-label">
              LibTV sessionId
              <input
                value={sessionId}
                onChange={(e) => setSessionId(e.target.value)}
                placeholder="输入 sessionId 或使用下方快捷按钮"
                disabled={loading || sessionLoading}
              />
            </label>
            {latestSessionId && !activeSessionId && (
              <div className="merge-latest-session">
                <span className="merge-latest-label">当前最新:</span>
                <span className="merge-latest-id">{latestSessionId.slice(0, 12)}...</span>
                <button
                  type="button"
                  className="merge-latest-btn"
                  onClick={() => setSessionId(latestSessionId)}
                  disabled={loading || sessionLoading}
                >
                  加载
                </button>
              </div>
            )}
            <div className="merge-session-actions">
              <button
                type="button"
                onClick={handleLoadSession}
                disabled={loading || sessionLoading || !sessionId.trim()}
              >
                {sessionLoading ? '加载中...' : '加载 Session'}
              </button>
            </div>
            {activeSessionId && <div className="merge-session-current">当前来源: {activeSessionId}</div>}
            {sessionError && <div className="merge-error">{sessionError}</div>}
          </div>
          {!activeSessionId ? (
            <div className="merge-empty">请先加载一个 Session。</div>
          ) : videos.length === 0 ? (
            <div className="merge-empty">这个 session 没有可展示的视频。</div>
          ) : (
            <div className="merge-video-list">
              {videos.map((video, idx) => {
                const checked = selectedIds.includes(video.id)
                return (
                  <button
                    type="button"
                    key={video.id}
                    className={'merge-video-item' + (checked ? ' is-selected' : '')}
                    onClick={() => toggleVideo(video)}
                    disabled={loading}
                  >
                    <video src={video.url} muted playsInline preload="metadata" />
                    <div className="merge-video-info">
                      <strong>视频 {idx + 1}</strong>
                      <span>{checked ? `已选择 #${selectedIds.indexOf(video.id) + 1}` : '点击选择'}</span>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </section>

        <section className="merge-col merge-order">
          <div className="merge-col-head">
            <h2>拼接顺序</h2>
            <span>{selectedVideos.length} 个</span>
          </div>

          <div className="merge-selected-list">
            {selectedVideos.length === 0 ? (
              <div className="merge-empty">按你想要的顺序选择左侧视频。</div>
            ) : (
              selectedVideos.map((video, idx) => (
                <div className="merge-selected-item" key={video.id}>
                  <span className="merge-index">{idx + 1}</span>
                  <video src={video.url} muted playsInline preload="metadata" />
                  <div className="merge-move-actions">
                    <button type="button" onClick={() => moveSelected(idx, -1)} disabled={idx === 0 || loading}>上移</button>
                    <button type="button" onClick={() => moveSelected(idx, 1)} disabled={idx === selectedVideos.length - 1 || loading}>下移</button>
                    <button type="button" onClick={() => toggleVideo(video)} disabled={loading}>移除</button>
                  </div>
                </div>
              ))
            )}
          </div>

          <label className="merge-label">
            补充要求
            <textarea
              value={instruction}
              onChange={(e) => setInstruction(e.target.value)}
              placeholder="可选。例如: 中间加自然转场, 不要改变原素材内容"
              rows={3}
              disabled={loading}
            />
          </label>

          {activeMergePoll && (
            <div className="merge-loading-indicator">
              <div className="merge-loading-spinner" />
              <div>
                <div>{activeMergePoll.stepName || '视频合成中'}</div>
                <div className="merge-polling-info">
                  <span>第 <span className="poll-count">{activeMergePoll.pollCount || 0}</span> 次同步</span>
                  <span>{activeMergePoll.progressPercent || 0}%</span>
                </div>
              </div>
            </div>
          )}

          <button
            type="button"
            className={'merge-submit' + (isMerging ? ' is-merging' : '')}
            onClick={handleSubmit}
            disabled={loading || selectedVideos.length < 2}
          >
            {loading ? '合成中...' : isMerging ? '合成中...' : '按当前顺序合成视频'}
          </button>
        </section>

        <section className="merge-col merge-result">
          <div className="merge-col-head">
            <h2>合成结果</h2>
          </div>
          <div className="merge-messages">
            {displayMessages.map((msg, idx) => (
              <MessageBubble key={idx} message={msg} />
            ))}
            {loading && (
              <MessageBubble message={{ role: 'assistant', type: 'loading', content: '合成中...' }} />
            )}
            {activeMergePoll && activeMergePoll.resultVideos && activeMergePoll.resultVideos.length > 0 && (
              <MessageBubble
                message={{
                  role: 'assistant',
                  type: 'video',
                  content: '视频合成完成！',
                  resultUrls: activeMergePoll.resultVideos.map((v) => v.url),
                  videoUrl: activeMergePoll.resultVideos[0]?.url,
                }}
              />
            )}
          </div>
        </section>
      </div>
    </main>
  )
}

function extractVideos(messages = []) {
  const seen = new Set()
  const out = []
  for (const msg of messages || []) {
    if (msg?.role !== 'assistant') continue
    const urls = Array.isArray(msg.resultUrls) && msg.resultUrls.length > 0
      ? msg.resultUrls
      : [msg.videoUrl].filter(Boolean)
    for (const url of urls) {
      if (!isVideoUrl(url) || seen.has(url)) continue
      seen.add(url)
      out.push({
        id: `${msg.sessionId || 'video'}-${out.length}-${url}`,
        url,
        sessionId: msg.sessionId,
        projectUrl: msg.projectUrl,
      })
    }
  }
  return out
}

function urlsToVideos(urls = [], sessionId = 'session') {
  const seen = new Set()
  const out = []
  for (const url of urls || []) {
    if (!isVideoUrl(url) || seen.has(url)) continue
    seen.add(url)
    out.push({
      id: `${sessionId}-${out.length}-${url}`,
      url,
      sessionId,
    })
  }
  return out
}

function isVideoUrl(url) {
  return /\.(mp4|mov|webm)(\?|$)/i.test(url || '')
}
