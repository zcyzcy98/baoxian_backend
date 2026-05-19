import { useState, useEffect, useRef } from 'react'
import { sendCode as apiSendCode, verifyCode as apiVerifyCode, setToken } from '../auth'
import PayModal from '../components/PayModal'
import './LandingPage.css'

const BrandMark = () => (
  <svg className="brand-mark" viewBox="0 0 28 28" fill="none">
    <circle cx="14" cy="14" r="13" stroke="#1F1F1E" strokeWidth="1.4"/>
    <circle cx="14" cy="9" r="2.2" fill="#CC785C"/>
    <circle cx="9" cy="17" r="2.2" fill="#3F5C52"/>
    <circle cx="19" cy="17" r="2.2" fill="#1F1F1E"/>
    <path d="M14 11.2 L9.8 15.6 M14 11.2 L18.2 15.6 M11 17.5 L17 17.5" stroke="#1F1F1E" strokeWidth="1.1" strokeLinecap="round"/>
  </svg>
)

const QrSvg = () => (
  <svg viewBox="0 0 120 120" style={{width:'78%',height:'78%'}}>
    <rect width="120" height="120" fill="#FAF9F5"/>
    <g fill="#1F1F1E">
      <rect x="6" y="6" width="22" height="22"/>
      <rect x="92" y="6" width="22" height="22"/>
      <rect x="6" y="92" width="22" height="22"/>
    </g>
    <g fill="#FAF9F5">
      <rect x="10" y="10" width="14" height="14"/>
      <rect x="96" y="10" width="14" height="14"/>
      <rect x="10" y="96" width="14" height="14"/>
    </g>
    <g fill="#1F1F1E">
      <rect x="14" y="14" width="6" height="6"/>
      <rect x="100" y="14" width="6" height="6"/>
      <rect x="14" y="100" width="6" height="6"/>
      <rect x="34" y="6" width="3" height="3"/><rect x="40" y="6" width="3" height="3"/>
      <rect x="46" y="9" width="3" height="3"/><rect x="52" y="6" width="3" height="3"/>
      <rect x="58" y="9" width="3" height="3"/><rect x="64" y="6" width="3" height="3"/>
      <rect x="70" y="9" width="3" height="3"/><rect x="76" y="6" width="3" height="3"/>
      <rect x="82" y="9" width="3" height="3"/>
      <rect x="6" y="34" width="3" height="3"/><rect x="9" y="40" width="3" height="3"/>
      <rect x="6" y="46" width="3" height="3"/><rect x="12" y="52" width="3" height="3"/>
      <rect x="6" y="58" width="3" height="3"/><rect x="9" y="64" width="3" height="3"/>
      <rect x="6" y="70" width="3" height="3"/><rect x="12" y="76" width="3" height="3"/>
      <rect x="6" y="82" width="3" height="3"/>
      <rect x="34" y="34" width="3" height="3"/><rect x="40" y="34" width="3" height="3"/>
      <rect x="46" y="34" width="3" height="3"/><rect x="34" y="40" width="3" height="3"/>
      <rect x="46" y="40" width="3" height="3"/><rect x="40" y="46" width="3" height="3"/>
      <rect x="52" y="46" width="3" height="3"/><rect x="58" y="46" width="3" height="3"/>
      <rect x="64" y="46" width="3" height="3"/><rect x="70" y="46" width="3" height="3"/>
      <rect x="34" y="52" width="3" height="3"/><rect x="40" y="52" width="3" height="3"/>
      <rect x="58" y="52" width="3" height="3"/><rect x="76" y="52" width="3" height="3"/>
      <rect x="82" y="52" width="3" height="3"/><rect x="46" y="58" width="3" height="3"/>
      <rect x="52" y="58" width="3" height="3"/><rect x="64" y="58" width="3" height="3"/>
      <rect x="76" y="58" width="3" height="3"/><rect x="34" y="64" width="3" height="3"/>
      <rect x="40" y="64" width="3" height="3"/><rect x="58" y="64" width="3" height="3"/>
      <rect x="70" y="64" width="3" height="3"/><rect x="82" y="64" width="3" height="3"/>
      <rect x="46" y="70" width="3" height="3"/><rect x="52" y="70" width="3" height="3"/>
      <rect x="64" y="70" width="3" height="3"/><rect x="76" y="70" width="3" height="3"/>
      <rect x="40" y="76" width="3" height="3"/><rect x="46" y="76" width="3" height="3"/>
      <rect x="58" y="76" width="3" height="3"/><rect x="82" y="76" width="3" height="3"/>
      <rect x="34" y="82" width="3" height="3"/><rect x="52" y="82" width="3" height="3"/>
      <rect x="64" y="82" width="3" height="3"/><rect x="70" y="82" width="3" height="3"/>
      <rect x="92" y="34" width="3" height="3"/><rect x="98" y="40" width="3" height="3"/>
      <rect x="104" y="34" width="3" height="3"/><rect x="110" y="40" width="3" height="3"/>
      <rect x="92" y="46" width="3" height="3"/><rect x="104" y="52" width="3" height="3"/>
      <rect x="92" y="58" width="3" height="3"/><rect x="98" y="64" width="3" height="3"/>
      <rect x="110" y="58" width="3" height="3"/><rect x="104" y="70" width="3" height="3"/>
      <rect x="92" y="76" width="3" height="3"/><rect x="98" y="82" width="3" height="3"/>
      <rect x="110" y="76" width="3" height="3"/>
      <rect x="34" y="92" width="3" height="3"/><rect x="40" y="98" width="3" height="3"/>
      <rect x="46" y="92" width="3" height="3"/><rect x="52" y="104" width="3" height="3"/>
      <rect x="58" y="92" width="3" height="3"/><rect x="64" y="98" width="3" height="3"/>
      <rect x="70" y="92" width="3" height="3"/><rect x="76" y="104" width="3" height="3"/>
      <rect x="82" y="92" width="3" height="3"/><rect x="34" y="110" width="3" height="3"/>
      <rect x="46" y="110" width="3" height="3"/><rect x="58" y="110" width="3" height="3"/>
      <rect x="70" y="110" width="3" height="3"/><rect x="82" y="110" width="3" height="3"/>
    </g>
    <rect x="48" y="48" width="24" height="24" fill="#FAF9F5"/>
    <circle cx="60" cy="60" r="9" fill="#CC785C"/>
    <text x="60" y="64" textAnchor="middle" fontFamily="Noto Serif SC" fontSize="11" fontWeight="700" fill="#FAF9F5">承</text>
  </svg>
)

export default function LandingPage({ onAuthSuccess, noAccess, phone: naBannerPhone, onLogout }) {
  const rootRef = useRef(null)
  const [scrolled, setScrolled] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [modalView, setModalView] = useState('form') // 'form' | 'no-access'
  const [payOpen, setPayOpen] = useState(false)

  // phone input
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [countdown, setCountdown] = useState(0)
  const [loading, setLoading] = useState(false)
  const [errorHtml, setErrorHtml] = useState('')
  const [successPhone, setSuccessPhone] = useState('')
  const [devCode, setDevCode] = useState('')
  const timerRef = useRef(null)
  const phoneInputRef = useRef(null)

  // scroll listener on the root scrollable div
  useEffect(() => {
    const el = rootRef.current
    if (!el) return
    const handler = () => setScrolled(el.scrollTop > 12)
    el.addEventListener('scroll', handler, { passive: true })
    return () => el.removeEventListener('scroll', handler)
  }, [])

  // ESC closes modal
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape' && modalOpen) closeModal() }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [modalOpen])

  useEffect(() => { return () => clearInterval(timerRef.current) }, [])

  function openModal(mode) {
    setModalOpen(true)
    setModalView('form')
    setErrorHtml('')
    setDevCode('')
    setTimeout(() => phoneInputRef.current?.focus(), 50)
  }

  function closeModal() {
    setModalOpen(false)
  }

  function scrollToPricing() {
    closeModal()
    rootRef.current?.querySelector('#pricing')?.scrollIntoView({ behavior: 'smooth' })
  }

  function startCountdown() {
    setCountdown(60)
    clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      setCountdown(n => {
        if (n <= 1) { clearInterval(timerRef.current); return 0 }
        return n - 1
      })
    }, 1000)
  }

  async function handleSendCode() {
    const p = phone.trim()
    if (!/^1[3-9]\d{9}$/.test(p)) {
      setErrorHtml('<strong>手机号格式不正确</strong>请输入 11 位有效手机号')
      phoneInputRef.current?.focus()
      return
    }
    setErrorHtml('')
    setLoading(true)
    try {
      const data = await apiSendCode(p)
      startCountdown()
      if (data._devCode) {
        setDevCode(data._devCode)
        setErrorHtml(`<strong>验证码已发送</strong>开发模式验证码：${data._devCode}`)
      } else {
        setErrorHtml('<strong>验证码已发送</strong>请查收短信')
      }
    } catch (e) {
      setErrorHtml(`<strong>发送失败</strong>${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  async function handleSubmit() {
    const p = phone.trim()
    const c = code.trim()
    if (!/^1[3-9]\d{9}$/.test(p)) {
      setErrorHtml('<strong>手机号格式不正确</strong>请输入 11 位有效手机号')
      return
    }
    if (!/^\d{6}$/.test(c)) {
      setErrorHtml('<strong>请输入 6 位验证码</strong>没收到？点"获取验证码"重试')
      return
    }
    setLoading(true)
    setErrorHtml('')
    try {
      const data = await apiVerifyCode(p, c)
      setToken(data.token)
      clearInterval(timerRef.current)
      onAuthSuccess({ phone: data.phone, hasAccess: data.hasAccess })
      if (!data.hasAccess) {
        closeModal()
      }
    } catch (e) {
      setErrorHtml(`<strong>验证失败</strong>${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="lp-root" ref={rootRef}>
      {noAccess && (
        <div className="lp-no-access-banner">
          已登录（{naBannerPhone?.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')}）· 账号待开通，请联系客服完成开通后刷新页面
          <button className="lp-no-access-logout" onClick={onLogout}>退出</button>
        </div>
      )}

      {/* ── Nav ── */}
      <nav className={`nav${scrolled ? ' scrolled' : ''}`}>
        <div className="nav-inner">
          <a href="#" className="brand" aria-label="承知">
            <BrandMark />
            <span>承知<span className="brand-suffix">CHENGZHI</span></span>
          </a>
          <div className="nav-links">
            <a href="#features">产品功能</a>
            <a href="#advantages">为什么是我们</a>
            <a href="#pricing">定价</a>
            <a href="#contact">联系</a>
          </div>
          <div className="nav-cta">
            {noAccess ? (
              <>
                <span className="btn btn-text" style={{cursor:'default'}}>
                  {naBannerPhone?.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')}
                </span>
                <button className="btn btn-ghost" onClick={onLogout}>退出</button>
              </>
            ) : (
              <button className="btn btn-text" onClick={() => openModal()}>登录 / 注册</button>
            )}
            {noAccess
              ? <button className="btn btn-primary" onClick={() => setPayOpen(true)}>立即开通</button>
              : <a href="#pricing" className="btn btn-primary">立即开通</a>
            }
          </div>
        </div>
      </nav>

      {/* ── Hero ── */}
      <section className="hero">
        <div className="hero-decor" aria-hidden="true">
          <svg viewBox="0 0 1400 800" preserveAspectRatio="xMidYMid slice" style={{position:'absolute',inset:0,width:'100%',height:'100%',opacity:.5}}>
            <g stroke="#E4DDCE" strokeWidth="1" fill="none">
              <path d="M120 180 L240 240 L180 360" strokeDasharray="3 4"/>
              <path d="M1200 120 L1280 220 L1180 320" strokeDasharray="3 4"/>
              <path d="M1100 600 L1240 540 L1300 660" strokeDasharray="3 4"/>
            </g>
            <g fill="#E9B79E">
              <circle cx="120" cy="180" r="3"/><circle cx="240" cy="240" r="2"/>
              <circle cx="180" cy="360" r="2.5"/><circle cx="1200" cy="120" r="2.5"/>
              <circle cx="1280" cy="220" r="2"/><circle cx="1180" cy="320" r="3"/>
              <circle cx="1100" cy="600" r="2.5"/><circle cx="1240" cy="540" r="3"/>
              <circle cx="1300" cy="660" r="2"/>
            </g>
          </svg>
        </div>
        <div className="container">
          <div className="hero-grid">
            <div className="hero-text">
              <div className="eyebrow">
                <span className="dot"></span>
                <span>保险经纪人专属 · 全域 AI 获客系统</span>
              </div>
              <h1 className="hero-title">点击发布，<br/>等<em>客户上门</em>。</h1>
              <p className="hero-sub">抖音、小红书、视频号、公众号——每天花 10 分钟，AI 把全网最火的爆款拆给你、改成你自己的内容、发到四个平台，剩下的时间留给签单。</p>
              <div className="hero-actions">
                <button className="btn btn-primary btn-lg" onClick={() => openModal()}>立即开始使用 <span className="btn-arrow">→</span></button>
                <a href="#features" className="btn btn-ghost btn-lg">看产品怎么跑</a>
              </div>
              <div className="hero-meta">
                <span><span className="ck">✓</span>四大平台一键覆盖</span>
                <span><span className="ck">✓</span>全功能解锁，无按次收费</span>
              </div>
            </div>
            <div className="hero-visual" aria-hidden="true">
              <svg className="kg-bg" viewBox="0 0 400 420">
                <g stroke="#D9D0BE" strokeWidth="1" fill="none" strokeDasharray="3 5">
                  <path d="M200 60 L100 200"/><path d="M200 60 L300 200"/>
                  <path d="M200 60 L200 180"/><path d="M100 200 L200 320"/>
                  <path d="M300 200 L200 320"/><path d="M200 180 L200 320"/>
                  <path d="M100 200 L200 180"/><path d="M300 200 L200 180"/>
                </g>
                <g>
                  <circle cx="200" cy="60" r="8" fill="#CC785C"/>
                  <circle cx="100" cy="200" r="6" fill="#3F5C52"/>
                  <circle cx="300" cy="200" r="6" fill="#C49B5C"/>
                  <circle cx="200" cy="180" r="5" fill="#1F1F1E"/>
                  <circle cx="200" cy="320" r="7" fill="#A85A40"/>
                </g>
              </svg>
              <div className="float-card fc-1">
                <div className="fc-bar"><span className="d"></span><span>抓爆款 · 今日 Top</span></div>
                <div className="row"><span className="num">01</span><span style={{flex:1}}>医保报销 60% 之后剩下的谁兜</span><span className="heat">🔥 9.8</span></div>
                <div className="row"><span className="num">02</span><span style={{flex:1}}>90 后宝妈的 3 张保单清单</span><span className="heat">🔥 9.5</span></div>
                <div className="row"><span className="num">03</span><span style={{flex:1}}>重疾险新规，老客户要换吗</span><span className="heat">🔥 9.2</span></div>
              </div>
              <div className="float-card fc-2">
                <div className="fc-bar"><span className="d"></span><span>改稿 · 小红书版</span></div>
                <p className="gen">最近有个客户问我：买了百万医疗险是不是就够了？我跟她说先别急，咱们看一组真实数据</p>
              </div>
              <div className="float-card fc-3">
                <div className="fc-bar"><span className="d"></span><span>一键发四端</span></div>
                <div className="publish-grid">
                  <div className="pub-cell dy">抖</div>
                  <div className="pub-cell xhs">小</div>
                  <div className="pub-cell sph">视</div>
                  <div className="pub-cell gzh">公</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 痛点 ── */}
      <section className="section pain">
        <div className="container">
          <div className="section-head center">
            <div className="section-tag">— Why we exist —</div>
            <h2 className="section-title">你不是不努力，<br/>是被无效的内容劳动耗光了时间。</h2>
            <p className="section-sub">做经纪人这事儿，难的不是签单，是签单之前那一万件杂事。等你把内容写完发出去，签单的窗口已经过了。</p>
          </div>
          <div className="pain-grid">
            {[
              {h:'每天想选题想得头大，发出去又没人看', p:'选题靠拍脑袋，一周才憋出一个。蹭热点怕跑偏，蹭专业又没共鸣，写完心累内容还冷场。'},
              {h:'写一条小红书要 2 小时，时间都耗在打字上', p:'同一个选题，抖音要短钩子、小红书要清单体、公众号要长文——四个平台四种语感，一个人顶不住。'},
              {h:'客户问的细节太刁，我得现翻条款回', p:'等待期、免责、续保、理赔流程——每个都不能答错，每次查 5 分钟，回得慢客户就走了。'},
              {h:'写得猛怕踩合规，写得软客户没感觉', p:'"100% 赔付"、"包治百病"——一句错就被通报。但写得太温和，客户根本记不住你这个人。'},
            ].map(({h,p}) => (
              <div className="pain-cell" key={h}>
                <div className="pain-q">"</div>
                <div className="pain-text"><h4>{h}</h4><p>{p}</p></div>
              </div>
            ))}
          </div>
          <div className="pain-answer">
            <p className="pain-answer-text">所以我们做了一件事：把<em>整条获客流水线</em>，<br/>装进一套懂保险的 AI 系统。</p>
          </div>
        </div>
      </section>

      {/* ── 核心功能 5 步 ── */}
      <section className="section features" id="features">
        <div className="container">
          <div className="section-head">
            <div className="section-tag">— Core Pipeline · 获客流水线 —</div>
            <h2 className="section-title">5 步获客流水线，<br/>把你从内容劳动里解放出来</h2>
            <p className="section-sub">你只需要做两件事：早上花 5 分钟挑选题，傍晚花 5 分钟回高意向客户的私信。中间所有的"写、发、答、防"，AI 替你做完。</p>
          </div>

          {/* Step 01 */}
          <div className="feature-block">
            <div className="fb-text">
              <div className="fb-step"><span className="num">Step 01</span>抓爆款 · Discover</div>
              <h3>每天清晨给你 10 个<br/>已经被全网验证过的爆款</h3>
              <p>AI 实时扫描抖音、小红书、视频号、公众号上所有保险相关的高热内容，按"已被市场验证"的爆款指数和你目标客户人群的匹配度，挑出当日最值得做的 10 个选题。</p>
              <ul className="fb-list">
                <li><span className="li-marker">A</span><div className="li-content"><strong>全网实时热度榜</strong><span>不是闭门造车的"AI 推荐"，是真有人在追的话题</span></div></li>
                <li><span className="li-marker">B</span><div className="li-content"><strong>已避开你做过的</strong><span>排除你最近 30 天发过的选题，确保人设连贯不重复</span></div></li>
                <li><span className="li-marker">C</span><div className="li-content"><strong>客户人群匹配</strong><span>你目标是宝妈 / 中产 / 高净值——只挑对得上的选题</span></div></li>
              </ul>
            </div>
            <div className="fb-visual">
              <div className="mk-head"><div className="t">今日爆款选题 · 2026-05-04</div><div className="tag">为「林经纪」筛选</div></div>
              <div className="mk-trends">
                <div className="trend-row"><div className="heat-bar" style={{background:'var(--clay)'}}></div><span className="rank">01</span><div className="text">医保报销 60% 之后，剩下的谁来兜</div><div className="meta"><span className="platform-chip dy">抖</span><span className="platform-chip gzh">公</span></div></div>
                <div className="trend-row"><div className="heat-bar" style={{background:'var(--sand)'}}></div><span className="rank">02</span><div className="text">90 后宝妈必备的 3 张保单清单</div><div className="meta"><span className="platform-chip xhs">小</span></div></div>
                <div className="trend-row"><div className="heat-bar" style={{background:'var(--pine)'}}></div><span className="rank">03</span><div className="text">重疾险新规落地，老客户要不要换</div><div className="meta"><span className="platform-chip dy">抖</span><span className="platform-chip sph">视</span></div></div>
                <div className="trend-row" style={{opacity:.55}}><div className="heat-bar" style={{background:'var(--ink-3)'}}></div><span className="rank">04</span><div className="text">高净值家庭那 3 笔"保命钱"放哪</div><div className="meta"><span className="platform-chip gzh">公</span></div></div>
              </div>
            </div>
          </div>

          {/* Step 02 */}
          <div className="feature-block reverse">
            <div className="fb-text">
              <div className="fb-step"><span className="num">Step 02</span>改稿 · Rewrite</div>
              <h3>一个选题，<br/>4 个平台原生语感的稿件</h3>
              <p>选好选题，AI 调用 200+ 已拆解过的爆款模板，结合保险知识图谱里的真实条款和理赔案例，把同一个选题改写成 4 个平台各自的语感——每一篇都标注引用来源，绝不胡编。</p>
              <ul className="fb-list">
                <li><span className="li-marker">A</span><div className="li-content"><strong>四种语感各写一篇</strong><span>抖音钩子+反转、小红书清单+干货、视频号口播稿、公众号长文，一次出齐</span></div></li>
                <li><span className="li-marker">B</span><div className="li-content"><strong>引用都能追溯</strong><span>每个数据都标条款编号、案例 ID，让你说每句话都有依据</span></div></li>
                <li><span className="li-marker">C</span><div className="li-content"><strong>不出 AI 味</strong><span>专业训练剔除"赋能、智享、一键 XX"等套话，读起来像你自己写的</span></div></li>
              </ul>
            </div>
            <div className="fb-visual">
              <div className="mk-head"><div className="t">改稿 · 同一个选题</div><div className="tag">4 个平台版本</div></div>
              <div className="mk-tabs">
                <button className="mk-tab">抖音</button>
                <button className="mk-tab">小红书</button>
                <button className="mk-tab active">公众号</button>
                <button className="mk-tab">视频号</button>
              </div>
              <div className="mk-doc">
                <h5>30 岁，到底要不要买重疾险？</h5>
                <p>最近老张来找我问这个，他 32 岁，工作稳定，身体还行。</p>
                <p>我没急着回答，先翻了 2024 年一组数据：<span className="hi">重疾发病率在 30-40 岁这个区间，比 20-30 岁高 2.7 倍</span>。30 岁不是"开始考虑"，是"已在风险窗口"。</p>
                <p>更现实的是社保——<span className="hi">大量自费药和自费项目根本不在保障范围</span>...</p>
                <div className="cite">📎 引用：精算师协会《2024 重疾发生率表》· 案例 #C-0317 · FAQ #082</div>
              </div>
            </div>
          </div>

          {/* Step 03 */}
          <div className="feature-block">
            <div className="fb-text">
              <div className="fb-step"><span className="num">Step 03</span>发布 · Distribute</div>
              <h3>一次点击，<br/>稿件同时铺到四个平台</h3>
              <p>不用切账号、不用挨个复制粘贴。所有平台的发布权限授权一次后，一键就能同时把改好的稿件、封面图、话题标签都铺到抖音、小红书、视频号、公众号——发布完了你才需要去签单。</p>
              <ul className="fb-list">
                <li><span className="li-marker">A</span><div className="li-content"><strong>账号统一托管</strong><span>四大平台账号一次授权，之后再也不用反复登入</span></div></li>
                <li><span className="li-marker">B</span><div className="li-content"><strong>定时发布</strong><span>不是非得现在发，可以排到每天最佳流量时段</span></div></li>
                <li><span className="li-marker">C</span><div className="li-content"><strong>数据自动回流</strong><span>发布后的播放、点赞、评论，全部回流到你的工作台</span></div></li>
              </ul>
            </div>
            <div className="fb-visual">
              <div className="mk-head"><div className="t">一键发布</div><div className="tag">即将发布</div></div>
              <div className="mk-publish">
                {[{cls:'dy',label:'抖音 · 短视频版',sub:'30 秒口播稿 + 自动配字幕'},{cls:'xhs',label:'小红书 · 图文版',sub:'9 张图 + 清单体正文'},{cls:'sph',label:'视频号 · 短片版',sub:'口播 + 微信生态适配'},{cls:'gzh',label:'公众号 · 长文版',sub:'2,400 字 + 排版完成'}].map(({cls,label,sub})=>(
                  <div className="pub-row" key={label}>
                    <div className={`pub-icon ${cls}`}>{cls==='dy'?'抖':cls==='xhs'?'小':cls==='sph'?'视':'公'}</div>
                    <div className="pub-info"><strong>{label}</strong><span>{sub}</span></div>
                    <div className="pub-status"><span className="ok">✓</span>就绪</div>
                  </div>
                ))}
                <div className="pub-button">同时发布到 4 个平台</div>
              </div>
            </div>
          </div>

          {/* Step 04 */}
          <div className="feature-block reverse">
            <div className="fb-text">
              <div className="fb-step"><span className="num">Step 04</span>接住流量 · Convert</div>
              <h3>客户来了，<br/>每个问题都答得专业、安全、引人</h3>
              <p>内容发出去会有源源不断的评论和私信。AI 自动识别客户真实意图、给出引用过条款的专业回复、提前过滤合规风险——你不在线的时候，没有一个客户在等你。</p>
              <ul className="fb-list">
                <li><span className="li-marker">A</span><div className="li-content"><strong>意图识别</strong><span>"随便问问"还是"准备买了"，AI 比你判断得准</span></div></li>
                <li><span className="li-marker">B</span><div className="li-content"><strong>200+ FAQ 库自动答</strong><span>等待期、免责、续保、理赔——常见问题秒回</span></div></li>
                <li><span className="li-marker">C</span><div className="li-content"><strong>合规前置审查</strong><span>每条对外内容/回复都会经过合规校验，把违规拦在发出去之前</span></div></li>
              </ul>
            </div>
            <div className="fb-visual">
              <div className="mk-head"><div className="t">私信 · 抖音</div><div className="tag">高意向 · 待跟进</div></div>
              <div className="mk-traffic">
                <div className="chat-msg user">百万医疗险等待期是 30 天还是 90 天啊？</div>
                <div className="chat-msg ai">
                  <strong>大多数百万医疗险等待期是 30 天</strong>（一般疾病），但<strong>重大疾病等待期会延长到 90-180 天</strong>。每家产品略有差异，方便发我保单照片，我帮你具体看下。
                  <div className="chat-cite"><span className="ref">FAQ #047</span><span className="ref">条款 §4.2</span><span className="ref">案例 #C-0091</span></div>
                </div>
                <div className="intent-box">
                  <strong>💡 意图分析</strong>
                  客户已具体到某款产品的细节问题，购买意向 <b>87%</b>。建议在回复后主动邀约 15 分钟视频沟通。
                </div>
              </div>
            </div>
          </div>

          {/* Step 05 */}
          <div className="feature-block">
            <div className="fb-text">
              <div className="fb-step"><span className="num">Step 05</span>提醒 · Close</div>
              <h3>你只需要在<br/>真正的高意向客户出现时出场</h3>
              <p>所有低优先级的咨询都被 AI 接住了。一旦出现真有购买意向的客户——可能是问到产品细节、可能是在评论区主动提需求——AI 会立刻提醒你出场，并附上这个客户的完整画像和建议话术。</p>
              <ul className="fb-list">
                <li><span className="li-marker">A</span><div className="li-content"><strong>高意向自动标星</strong><span>每个客户有动态意向打分，超过 80 分立即提醒</span></div></li>
                <li><span className="li-marker">B</span><div className="li-content"><strong>客户画像聚合</strong><span>从内容互动到私信记录，给你一份"准客户档案"</span></div></li>
                <li><span className="li-marker">C</span><div className="li-content"><strong>建议下一步动作</strong><span>邀约视频、寄方案书、约线下面谈——AI 给具体建议</span></div></li>
              </ul>
            </div>
            <div className="fb-visual">
              <div className="mk-head"><div className="t">今日待跟进客户</div><div className="tag">3 位高意向</div></div>
              <div className="mk-leads">
                <div className="lead-card hot">
                  <div className="lead-avatar">王</div>
                  <div className="lead-info"><strong>王女士 · 32 岁宝妈</strong><div className="question">"百万医疗险等待期是 30 还是 90 天啊？"</div><div className="platform">抖音私信 · 5 分钟前</div></div>
                  <div className="lead-score"><b>87</b><span>HOT</span></div>
                </div>
                <div className="lead-card">
                  <div className="lead-avatar b2">陈</div>
                  <div className="lead-info"><strong>陈先生 · 38 岁中产</strong><div className="question">"重疾险换不换值得吗？我老婆..."</div><div className="platform">小红书评论 · 1 小时前</div></div>
                  <div className="lead-score" style={{color:'var(--pine)'}}><b>72</b><span>WARM</span></div>
                </div>
                <div className="lead-card">
                  <div className="lead-avatar b3">李</div>
                  <div className="lead-info"><strong>李先生 · 高净值客户</strong><div className="question">"想了解下信托类的保险产品"</div><div className="platform">公众号留言 · 3 小时前</div></div>
                  <div className="lead-score" style={{color:'var(--sand)'}}><b>68</b><span>WARM</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 差异化壁垒 ── */}
      <section className="section advantages" id="advantages">
        <div className="container">
          <div className="section-head">
            <div className="section-tag">— Why us · 凭什么是我们 —</div>
            <h2 className="section-title">通用 AI 写不了保险，<br/>因为它没看过这些东西。</h2>
            <p className="section-sub">随便一个 GPT、豆包都能写文案，但写保险就出戏——条款引错、案例编造、合规踩雷。我们花了一年把这一行最重的"真东西"攒在了系统里。</p>
          </div>
          <div className="adv-grid">
            {[{n:'300',sup:'+',h:'真实理赔案例库',p:'来自一线经纪人的脱敏真实案例，不是网上抓的，每条都标注产品、地区、争议点'},{n:'200',sup:'+',h:'高频客户 FAQ 库',p:'等待期、免责、续保、理赔流程——每个高频问题都有专业 / 温度 / 引导三套话术'},{n:'200',sup:'+',h:'爆款模板拆解库',p:'四大平台 200+ 真实爆款逐条拆解：钩子结构、节奏点、留评话术，全部沉淀成可复用的模板'},{n:'1',sup:'',h:'套保险行业知识图谱',p:'险种 × 条款 × 理赔 × 监管 全行业图谱，是这套系统不会编造、不会出错的底层依靠'}].map(({n,sup,h,p})=>(
              <div className="adv-cell" key={h}>
                <div className="adv-num">{n}{sup && <sup>{sup}</sup>}</div>
                <h4>{h}</h4><p>{p}</p>
              </div>
            ))}
          </div>
          <div className="compare">
            <div className="compare-col them">
              <div className="compare-label">⚠ 通用 AI 写出来的</div>
              <h4>"重疾险智能赋能您的家庭保障"</h4>
              <div className="compare-quote">
                <p>"重疾险作为<span className="strike">保障人民群众美好生活</span>的关键金融产品，能够为您和家人提供<span className="strike">全方位、高品质</span>的保障。<span className="strike">买了重疾险就等于买了一份安心</span>，让您从容应对人生风险。"</p>
                <div className="compare-cites"><span className="ref">无引用</span><span className="ref">无案例</span></div>
              </div>
              <div className="compare-foot">套话堆砌、合规踩雷、无数据无引用——客户看一眼就划走，发出去还可能被平台限流或合规通报。</div>
            </div>
            <div className="compare-col us">
              <div className="compare-label">✓ 承知系统写出来的</div>
              <h4>"30 岁，到底要不要买重疾险？"</h4>
              <div className="compare-quote">
                <p>"老张今年 32 岁。我翻了 2024 年的发病率数据：<span className="hi-good">30-40 岁这个区间，重疾发病率比 20-30 岁高 2.7 倍</span>。社保医保有起付线、有封顶线、还有<span className="hi-good">大量自费药不在保障范围</span>——比如靶向药里的奥希替尼，一盒 5 万多。"</p>
                <div className="compare-cites"><span className="ref">条款 §3.1</span><span className="ref">案例 #C-0317</span><span className="ref">FAQ #082</span><span className="ref">精算表 2024</span></div>
              </div>
              <div className="compare-foot"><strong>每个数据都能溯源，每个案例都来自真实理赔</strong>——客户读到的是有体感的故事，平台合规自动通过。</div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 多平台覆盖 ── */}
      <section className="section platforms">
        <div className="container">
          <div className="section-head">
            <div className="section-tag">— Coverage · 全域覆盖 —</div>
            <h2 className="section-title">中国保险经纪人最该在的<br/>四个流量阵地，我们都在</h2>
            <p className="section-sub">每个平台的语感、节奏、格式都不一样。承知不是把一篇文章硬塞过去，而是为每个平台原生改写。</p>
          </div>
          <div className="platform-grid">
            {[{cls:'dy',n:'抖音',d:'短视频 + 私域转化主战场，节奏快、钩子重',f:['30 秒口播','反转钩子','字幕模板']},{cls:'xhs',n:'小红书',d:'中产女性聚集地，封面 + 清单体内容',f:['9 图笔记','清单体','封面文案']},{cls:'sph',n:'视频号',d:'微信生态熟人转化，稳重、信任感',f:['口播短片','朋友圈引流','直播脚本']},{cls:'gzh',n:'公众号',d:'深度长文 + 长尾搜索，建立专业人设',f:['2000 字长文','排版模板','SEO 标题']}].map(({cls,n,d,f})=>(
              <div className="platform-card" key={n}>
                <div className={`pf-logo ${cls}`}>{cls==='dy'?'抖':cls==='xhs'?'小':cls==='sph'?'视':'公'}</div>
                <h4>{n}</h4>
                <p className="pf-desc">{d}</p>
                <div className="pf-formats">{f.map(t=><span key={t}>{t}</span>)}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── 定价 ── */}
      <section className="section pricing" id="pricing">
        <div className="container">
          <div className="section-head center">
            <div className="section-tag">— Pricing · 一个价格全部解锁 —</div>
            <h2 className="section-title">一笔保单的佣金，<br/>换一年的获客流水线。</h2>
            <p className="section-sub">没有版本拆分、没有功能阉割、没有按调用次数收费。开通就是全部。</p>
          </div>
          <div className="pricing-card">
            <div className="pricing-head">
              <div className="price-badge">FOUNDER PRICE · 首发定价</div>
              <h3>承知 · 全域获客年卡</h3>
              <p className="price-desc">面向独立保险经纪人 · 1 个账号</p>
              <div className="price-row">
                <span className="price-currency">¥</span>
                <span className="price-amount">1,499</span>
                <span className="price-unit">/年</span>
              </div>
              <p className="price-note">折合每天约 4.1 元 · 比一杯咖啡便宜</p>
              <button className="btn btn-primary btn-lg" onClick={() => noAccess ? setPayOpen(true) : openModal('signup')}>立即开通 <span className="btn-arrow">→</span></button>
            </div>
            <div className="pricing-body">
              <h5>开通即解锁全部能力</h5>
              <ul className="pricing-features">
                {['全网爆款选题日报（每日 10 个）','4 平台一键改稿与发布','个人 IP 人设档案与一致性校验','200+ FAQ 自动答疑','客户意图识别 + 高意向提醒','合规前置审查（不限次）','300+ 真实理赔案例库访问权限','200+ 爆款模板库访问权限','保险知识图谱全开放','专属经纪人社群 · 内容点评答疑','不限智能体调用次数','首批用户次年续费价格锁定'].map(f=>(
                  <li key={f}><span className="ck">✓</span><span>{f}</span></li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* ── 联系我们 ── */}
      <section className="section contact" id="contact">
        <div className="container">
          <div className="section-head">
            <div className="section-tag">— Contact · 联系我们 —</div>
            <h2 className="section-title">想试试，<br/>或者还有问题想问？</h2>
            <p className="section-sub">加微信、打电话、来公司聊都行。我们也在做经纪人朋友的事，把信任放在前头。</p>
          </div>
          <div className="contact-grid">
            <div className="contact-info">
              <div className="ci-row"><div className="ci-label">— Phone · 联系电话</div><div className="ci-value">+86 138-XXXX-1234</div></div>
              <div className="ci-row"><div className="ci-label">— Address · 公司地址</div><div className="ci-value small">江苏省南京市江宁区南站智汇园</div></div>
              <div className="ci-row"><div className="ci-label">— Hours · 服务时间</div><div className="ci-value small">工作日 09:30 — 18:30 / 周末微信回复</div></div>
            </div>
            <div className="qr-card">
              <div className="qr-img"><QrSvg /></div>
              <h5>企业微信 · 添加咨询</h5>
              <p>扫码联系产品顾问，免费试用 7 天</p>
            </div>
          </div>
        </div>
      </section>

      {/* ── Footer ── */}
      <footer className="footer">
        <div className="container">
          <div className="footer-inner">
            <div className="brand">
              <BrandMark />
              <span>承知<span className="brand-suffix">CHENGZHI</span></span>
            </div>
            <div className="footer-meta">
              <span>© 2026 承知科技</span>
              <span>苏 ICP 备 XXXXXXXX 号</span>
              <span>江苏省南京市江宁区南站智汇园</span>
            </div>
          </div>
        </div>
      </footer>

      {/* ── Modal ── */}
      <div className={`lp-modal-backdrop${modalOpen ? ' active' : ''}`} onClick={e => e.target === e.currentTarget && closeModal()}>
        <div className="lp-modal" role="dialog" aria-modal="true">
          <button className="lp-modal-close" onClick={closeModal} aria-label="关闭">×</button>

          {/* 表单视图 */}
          <div style={{display: modalView === 'form' ? 'block' : 'none'}}>
            <h3>登录 / 注册</h3>
            <p className="lp-modal-sub">输入手机号即可一步完成登录或注册。如果你还没有账号，我们会自动为你创建。</p>

            <div className={`lp-modal-error${errorHtml ? ' active' : ''}`} dangerouslySetInnerHTML={{__html: errorHtml}} />

            <div className="lp-field">
              <label htmlFor="lp-phone">手机号</label>
              <input
                type="tel" id="lp-phone" ref={phoneInputRef}
                placeholder="请输入 11 位手机号" maxLength={11} autoComplete="tel"
                value={phone}
                onChange={e => setPhone(e.target.value.replace(/\D/g, '').slice(0,11))}
              />
            </div>
            <div className="lp-field">
              <label htmlFor="lp-code">验证码</label>
              <div className="lp-field-row">
                <input
                  type="text" id="lp-code"
                  placeholder="6 位验证码" maxLength={6} inputMode="numeric"
                  value={code}
                  onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0,6))}
                  onKeyDown={e => e.key === 'Enter' && handleSubmit()}
                />
                <button
                  className="btn btn-ghost"
                  onClick={handleSendCode}
                  disabled={countdown > 0 || loading}
                >
                  {countdown > 0 ? `重新发送 ${countdown}s` : '获取验证码'}
                </button>
              </div>
            </div>

            <button className="btn btn-primary btn-submit" onClick={handleSubmit} disabled={loading}>
              {loading ? '请稍候…' : '登录 / 注册'}
            </button>

            <div className="lp-modal-foot">
              提交即表示同意 <a href="#">《用户协议》</a> 和 <a href="#">《隐私政策》</a><br/>
              进入后台需要先开通会员（¥1499/年）
            </div>
          </div>

          {/* 已注册但未付费提示 */}
          <div className={`lp-modal-success${modalView === 'no-access' ? ' active' : ''}`}>
            <div className="icon">!</div>
            <h4>账号已开通</h4>
            <p>
              我们已为手机号 <strong style={{color:'var(--ink)'}}>{successPhone}</strong> 创建账号。<br/>
              进入工作台前，需要先<strong style={{color:'var(--clay-deep)'}}>开通会员（¥1499/年）</strong>解锁全部功能。
            </p>
            <button className="btn btn-primary btn-submit" onClick={scrollToPricing}>
              前往开通 <span className="btn-arrow">→</span>
            </button>
            <div className="lp-modal-foot" style={{marginTop:18}}>
              已开通的用户，开通成功后会自动登录到工作台。
            </div>
          </div>
        </div>
      </div>

      {payOpen && (
        <PayModal
          onClose={() => setPayOpen(false)}
          onSuccess={(me) => {
            setPayOpen(false)
            onAuthSuccess(me)
          }}
        />
      )}
    </div>
  )
}
