import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'

export default function TopBar({ onSignIn }: { onSignIn: () => void }) {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { user, username, signOut } = useAuth()

  const urlQuery = params.get('q') ?? ''
  const urlSet = params.get('set') ?? ''

  const [query, setQuery] = useState(urlQuery)
  const [setNames, setSetNames] = useState<string[]>([])
  const [menuOpen, setMenuOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => setQuery(urlQuery), [urlQuery])

  // Query-aware set list for the dropdown.
  useEffect(() => {
    api.sets(urlQuery).then(setSetNames).catch(() => setSetNames([]))
  }, [urlQuery])

  // Close the profile menu on outside click.
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [])

  const go = (q: string, set: string) => {
    const sp = new URLSearchParams()
    if (q) sp.set('q', q)
    if (set) sp.set('set', set)
    navigate(`/?${sp.toString()}`)
  }

  return (
    <header className="topbar">
      <div className="brand-wrap">
        <Link className="brand" to="/">Poké<span className="brand-dot">·</span>Collect</Link>
      </div>

      <form className="search-form" onSubmit={(e) => { e.preventDefault(); go(query.trim(), urlSet) }} role="search">
        <select className="set-select" value={urlSet} onChange={(e) => go(query.trim(), e.target.value)}>
          <option value="">All Sets</option>
          {setNames.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <input type="text" placeholder="Search Pokémon cards..." value={query} onChange={(e) => setQuery(e.target.value)} autoComplete="off" />
        <button type="submit" aria-label="Search">↵</button>
      </form>

      <div className="profile-wrap" ref={wrapRef}>
        {user ? (
          <>
            <button className="profile-btn" onClick={() => setMenuOpen((o) => !o)}>
              <span className="profile-avatar">{(username ?? '?').slice(0, 1).toUpperCase()}</span>
              <span>{username}</span>
            </button>
            <div className={`profile-menu ${menuOpen ? 'open' : ''}`}>
              <Link to="/collection" onClick={() => setMenuOpen(false)}>My Collection</Link>
              <div className="divider" />
              <button onClick={() => { setMenuOpen(false); signOut() }}>Sign out</button>
            </div>
          </>
        ) : (
          <button className="profile-btn" onClick={onSignIn}>
            <span className="profile-avatar">?</span>
            <span>Sign in</span>
          </button>
        )}
      </div>
    </header>
  )
}
