import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent } from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import './KnowledgePage.css'

const KB_META = {
  faq:        { label: '常见 FAQ',     placeholder: '例如: 重疾险的等待期是多久？理赔时需要哪些材料？' },
  claims:     { label: '理赔案例库',   placeholder: '例如: 客户发生车祸住院手术, 重疾险和医疗险能同时赔吗？' },
  products:   { label: '险种大全',     placeholder: '例如: 百万医疗险和重疾险有什么区别？' },
  tips:       { label: '投保注意事项', placeholder: '例如: 有高血压的人能买重疾险吗？带病投保有哪些注意事项？' },
  coverage:   { label: '保障责任',     placeholder: '例如: 保障期内非疾病身故是否赔付？' },
  compliance: { label: '合规词库',     placeholder: '检查文案是否含违规词 (如"零风险"、"包赔")，输入要检查的文字' },
}

export default function KnowledgePage({ kbType = 'faq' }) {
  const meta = KB_META[kbType] || KB_META.faq
  const [question, setQuestion] = useState('')
  const [history, setHistory] = useState([]) // [{ q, a, model, time }]
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const isCompliance = kbType === 'compliance'

  const handleAsk = async () => {
    const q = question.trim()
    if (!q || loading) return
    setLoading(true)
    setError('')
    try {
      const endpoint = isCompliance ? 'text-compliance-check' : `kb-${kbType}`
      // 后端 text-compliance-check 读 `content`, kb-* 读 `question`
      const payload = isCompliance ? { content: q } : { question: q }
      const result = await callAgent(endpoint, payload)
      setHistory((h) => [
        { q, a: result?.content || '(空响应)', model: result?.model, time: new Date().toLocaleTimeString() },
        ...h,
      ])
      setQuestion('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="kb-page">
      <header className="kb-header">
        <h2>{meta.label}</h2>
        <p className="page-sub">
          {isCompliance
            ? '检测文案是否含违规/敏感词, AI 给出修改建议'
            : `输入问题, 从${meta.label}知识库中召回相关内容并整合回答`}
        </p>
      </header>

      <section className="kb-input-card">
        <textarea
          className="kb-textarea"
          rows={4}
          placeholder={meta.placeholder}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          disabled={loading}
        />
        <div className="kb-input-foot">
          <span className="kb-hint">⏎ 提交  ·  消耗 5 积分</span>
          <button
            className="btn-ask"
            onClick={handleAsk}
            disabled={!question.trim() || loading}
          >
            {loading ? '思考中…' : isCompliance ? '检测合规' : '提问'}
          </button>
        </div>
        {error && <div className="kb-error">{error}</div>}
      </section>

      {history.length > 0 && (
        <section className="kb-history">
          <h3>回答历史</h3>
          {history.map((item, idx) => (
            <div key={idx} className="kb-qa">
              <div className="kb-q">
                <span className="kb-q-icon">Q</span>
                <div className="kb-q-text">{item.q}</div>
                <span className="kb-q-time">{item.time}</span>
              </div>
              <div className="kb-a">
                <span className="kb-a-icon">A</span>
                <div className="kb-a-content">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{item.a}</ReactMarkdown>
                  <div className="kb-a-foot">
                    {item.model && <span className="kb-a-model">via {item.model}</span>}
                    <button
                      className="kb-copy"
                      onClick={() => copyToClipboard(stripMarkdown(item.a))}>
                      复制
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </section>
      )}

      {history.length === 0 && !loading && (
        <div className="kb-empty">
          <div className="empty-icon">📚</div>
          <div>提一个问题, AI 会从知识库里找答案</div>
          <p>支持险种概念、条款解读、理赔流程、投保注意事项等</p>
        </div>
      )}
    </div>
  )
}
