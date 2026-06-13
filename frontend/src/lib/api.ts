// Typed client for the Spring Boot API. Injects the current Supabase session
// JWT as a Bearer token; public endpoints ignore it, protected ones require it.
import { supabase } from './supabase'

const BASE = '/api'

async function authHeaders(): Promise<Record<string, string>> {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, { headers: await authHeaders() })
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

async function post<T>(path: string, body: unknown): Promise<T | null> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`POST ${path} → ${res.status}`)
  return res.status === 204 ? null : (res.json() as Promise<T>)
}

export interface CardDto {
  pokewalletId: string
  cardName: string
  setName: string
  rarity: string
  cardType: string
  marketPriceUsd: number | null
}

export interface SearchResponse {
  results: CardDto[]
  total: number
  page: number
  totalPages: number
  pageSize: number
}

export interface CollectionCardDto {
  cardId: string
  cardName: string
  setName: string
  rarity: string
  condition: string
  marketPriceUsd: number | null
  count: number
  collectionIds: string[]
}

export interface CollectionResponse {
  cards: CollectionCardDto[]
  totalCopies: number
  pricedCopies: number
  totalValue: number
}

export interface UserDto {
  userId: string
  username: string
  email: string
}

export const cardImageUrl = (id: string, size: 'low' | 'high' = 'low') =>
  `${BASE.replace('/api', '')}/card-image/${id}?size=${size}`

export const api = {
  // Read (public)
  search: (q: string, set: string, page: number) =>
    get<SearchResponse>(`/search?q=${encodeURIComponent(q)}&set=${encodeURIComponent(set)}&page=${page}`),
  sets: (q: string) => get<string[]>(`/sets?q=${encodeURIComponent(q)}`),
  rareCards: () => get<string[]>('/rare-cards'),
  market: (set: string) => get<CardDto[]>(`/market?set=${encodeURIComponent(set)}`),
  marketSets: () => get<string[]>('/market/sets'),

  // Authenticated
  me: () => get<UserDto>('/users/me'),
  syncUser: () => post<UserDto>('/users/sync', {}),
  collection: () => get<CollectionResponse>('/collection'),
  addFromSearch: (card: CardDto, condition = 'Near Mint') =>
    post<{ collectionId: string }>('/commands/add-from-search', {
      pokewalletId: card.pokewalletId,
      cardName: card.cardName,
      setName: card.setName,
      rarity: card.rarity,
      cardType: card.cardType,
      condition,
      marketPriceUsd: card.marketPriceUsd,
    }),
  addCopy: (pokewalletId: string, condition = 'Near Mint') =>
    post<{ collectionId: string }>('/commands/add-copy', { pokewalletId, condition }),
  removeCard: (collectionId: string) =>
    post<void>('/commands/remove-card', { collectionId }),
}
