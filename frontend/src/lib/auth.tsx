import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from './supabase'
import { api } from './api'

interface AuthValue {
  session: Session | null
  user: User | null
  loading: boolean
  username: string | null
  signIn: (email: string, password: string) => Promise<void>
  signUp: (email: string, password: string, username: string) => Promise<{ needsConfirmation: boolean }>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session)
      setLoading(false)
    })
    const { data: sub } = supabase.auth.onAuthStateChange((_event, s) => setSession(s))
    return () => sub.subscription.unsubscribe()
  }, [])

  const signIn = async (email: string, password: string) => {
    const { error } = await supabase.auth.signInWithPassword({ email, password })
    if (error) throw error
  }

  const signUp = async (email: string, password: string, username: string) => {
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: { data: { username } },
    })
    if (error) throw error
    // If email confirmation is disabled, a session exists immediately — mirror
    // the user into MySQL right away. Otherwise the sync happens on first sign-in.
    if (data.session) {
      await api.syncUser().catch(() => {})
      return { needsConfirmation: false }
    }
    return { needsConfirmation: true }
  }

  const signOut = async () => {
    await supabase.auth.signOut()
  }

  // Ensure the MySQL shadow row exists once we have a session (covers the
  // confirm-by-email path where signUp didn't return a session).
  useEffect(() => {
    if (session) api.syncUser().catch(() => {})
  }, [session?.user?.id])

  const user = session?.user ?? null
  const username =
    (user?.user_metadata?.username as string | undefined) ??
    (user?.email ? user.email.split('@')[0] : null)

  return (
    <AuthContext.Provider value={{ session, user, loading, username, signIn, signUp, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
