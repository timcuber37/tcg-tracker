import { useState } from 'react'
import { useAuth } from '../lib/auth'

export default function AuthModal({ onClose }: { onClose: () => void }) {
  const { signIn, signUp } = useAuth()
  const [tab, setTab] = useState<'signin' | 'register'>('signin')
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // form fields
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [username, setUsername] = useState('')

  const submitSignIn = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setBusy(true)
    try {
      await signIn(email, password)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign in failed')
    } finally {
      setBusy(false)
    }
  }

  const submitRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setNote(null)
    if (!/^[A-Za-z0-9_]{3,30}$/.test(username)) return setError('Username must be 3–30 alphanumeric characters')
    if (password.length < 8) return setError('Password must be at least 8 characters')
    if (password !== confirm) return setError('Passwords do not match')
    setBusy(true)
    try {
      const { needsConfirmation } = await signUp(email, password, username)
      if (needsConfirmation) {
        setNote('Account created — check your email to confirm, then sign in.')
        setTab('signin')
      } else {
        onClose()
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="auth-dialog">
        <div className="auth-tabs">
          <button className={`auth-tab ${tab === 'signin' ? 'active' : ''}`} onClick={() => { setTab('signin'); setError(null) }}>Sign in</button>
          <button className={`auth-tab ${tab === 'register' ? 'active' : ''}`} onClick={() => { setTab('register'); setError(null) }}>Create account</button>
        </div>

        {error && <div className="auth-error" style={{ marginBottom: 12 }}>{error}</div>}
        {note && <div className="auth-note" style={{ marginBottom: 12 }}>{note}</div>}

        {tab === 'signin' ? (
          <form className="auth-panel" onSubmit={submitSignIn}>
            <input type="email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <input type="password" placeholder="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            <button className="btn btn-block" type="submit" disabled={busy} style={{ padding: 10 }}>{busy ? '…' : 'Sign in'}</button>
          </form>
        ) : (
          <form className="auth-panel" onSubmit={submitRegister}>
            <input type="text" placeholder="Username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
            <input type="email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <input type="password" placeholder="Password (min 8 chars)" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            <input type="password" placeholder="Confirm password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
            <button className="btn btn-block" type="submit" disabled={busy} style={{ padding: 10 }}>{busy ? '…' : 'Create account'}</button>
          </form>
        )}
      </div>
    </div>
  )
}
