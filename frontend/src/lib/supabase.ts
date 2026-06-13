import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string

if (!url || !anonKey || anonKey.startsWith('<')) {
  // Surfaced in the console rather than crashing the whole app at import time.
  console.warn('Supabase env not configured — set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY in frontend/.env')
}

export const supabase = createClient(url, anonKey)
