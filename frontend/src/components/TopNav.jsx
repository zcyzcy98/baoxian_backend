import { SECTIONS } from '../data/agents'
import './TopNav.css'

export default function TopNav({ activeSection, onSelect }) {
  return (
    <header className="topnav">
      <div className="topnav-brand">AI 工作台</div>
      <nav className="topnav-tabs">
        {SECTIONS.map((s) => (
          <button
            type="button"
            key={s.id}
            className={'topnav-tab' + (activeSection === s.id ? ' is-active' : '')}
            onClick={() => onSelect(s.id)}
          >
            {s.label}
          </button>
        ))}
      </nav>
    </header>
  )
}
