import { useEffect, useRef } from 'react'
import { api, cardImageUrl } from '../lib/api'

/**
 * Occasional rainfall of high-rarity card images across the screen.
 * Uses a small pool (10 ids) so the browser caches each image after first load,
 * avoiding repeated /card-image calls.
 */
export default function CardRain() {
  const layerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const layer = layerRef.current
    if (!layer) return
    let active = 0
    let stopped = false
    const timers: number[] = []
    const MAX = 12

    api.rareCards().then((all) => {
      const ids = all.slice(0, 10)
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
