import { useState } from 'react'
import { type CardDto, cardImageUrl } from '../lib/api'

function CardTile({ card, onAdd }: { card: CardDto; onAdd?: (c: CardDto) => Promise<void> }) {
  const [state, setState] = useState<'idle' | 'busy' | 'added'>('idle')

  const add = async () => {
    if (!onAdd) return
    setState('busy')
    try {
      await onAdd(card)
      setState('added')
    } catch {
      setState('idle')
    }
  }

  return (
    <div className="card">
      <img src={cardImageUrl(card.pokewalletId)} alt={card.cardName} loading="lazy"
           onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }} />
      <div className="card-name">{card.cardName}</div>
      <div className="card-meta">{card.setName}</div>
      <span className="badge">{card.rarity}</span>
      {card.marketPriceUsd != null && <div className="price">${card.marketPriceUsd.toFixed(2)}</div>}
      {onAdd && (
        <button className="btn btn-block" style={{ marginTop: 12 }} onClick={add}
                disabled={state !== 'idle'}>
          {state === 'added' ? '✓ Added' : state === 'busy' ? '…' : '+ Add to Collection'}
        </button>
      )}
    </div>
  )
}

export default function CardGrid({ cards, onAdd }: { cards: CardDto[]; onAdd?: (c: CardDto) => Promise<void> }) {
  return (
    <div className="card-grid">
      {cards.map((c) => <CardTile key={c.pokewalletId} card={c} onAdd={onAdd} />)}
    </div>
  )
}
