import { useEffect, useRef } from 'react'
import { api, cardImageUrl, CARD_BACK_URL } from '../lib/api'

/**
 * Occasional rainfall of card images across the screen. Prefers the signed-in
 * user's own collection; falls back to high-rarity cards when the collection is
 * empty or the user isn't signed in. Caps the pool to a small set so the browser
 * caches each image after first load, avoiding repeated /card-image calls.
 */
async function resolvePool(): Promise<string[]> {
  try {
    const col = await api.collection()
    const ids = col.cards.map((c) => c.cardId)
    if (ids.length) return ids.slice(0, 16)
  } catch { /* not signed in or request failed → fall back below */ }
  const rare = await api.rareCards()
  return rare.slice(0, 10)
}

export default function CardRain() {
  const layerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const layer = layerRef.current
    if (!layer) return
    let active = 0
    let stopped = false
    const timers: number[] = []
    const MAX = 12

    resolvePool().then((ids) => {
      if (!ids.length || stopped) return

      const spawn = () => {
        if (active >= MAX) return
        active++
        const id = ids[Math.floor(Math.random() * ids.length)]
        const w = 72 + Math.floor(Math.random() * 34)
        const dur = 8 + Math.random() * 7
        const rot = (Math.random() * 28 - 14).toFixed(1)
        const op = (0.45 + Math.random() * 0.25).toFixed(2)
        const x = Math.random() * Math.max(0, window.innerWidth - w)
        const img = document.createElement('img')
        img.src = cardImageUrl(id)
        img.addEventListener('error', () => { if (!img.dataset.fallback) { img.dataset.fallback = '1'; img.src = CARD_BACK_URL } })
        img.className = 'rain-card'
        img.width = w
        img.style.left = `${x}px`
        img.style.setProperty('--r', `${rot}deg`)
        img.style.setProperty('--op', op)
        img.style.animationDuration = `${dur}s`
        img.addEventListener('animationend', () => { img.remove(); active-- })
        layer.appendChild(img)
      }

      const loop = () => {
        if (stopped) return
        spawn()
        timers.push(window.setTimeout(loop, 1600 + Math.random() * 2600))
      }
      ;[0, 900, 1800, 2700].forEach((d) => timers.push(window.setTimeout(spawn, d)))
      timers.push(window.setTimeout(loop, 3800))
    })

    return () => {
      stopped = true
      timers.forEach(clearTimeout)
      layer.replaceChildren()
    }
  }, [])

  return <div id="cardRainLayer" ref={layerRef} />
}
