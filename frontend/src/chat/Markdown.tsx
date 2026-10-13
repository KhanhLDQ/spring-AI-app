import { Fragment, type ReactNode } from 'react'

// Minimal renderer for what the model actually emits in chat: paragraphs, bullet and
// numbered lists, **bold**, *italic* and `code`. Builds React elements (no innerHTML),
// so model output can never inject markup. Unclosed markers stay literal while streaming.

const INLINE = /(\*\*[^*\n]+\*\*|`[^`\n]+`|\*[^*\s][^*\n]*\*)/g

function inline(text: string): ReactNode[] {
  return text.split(INLINE).map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={i}>{part.slice(2, -2)}</strong>
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return <code key={i}>{part.slice(1, -1)}</code>
    }
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      return <em key={i}>{part.slice(1, -1)}</em>
    }
    return <Fragment key={i}>{part}</Fragment>
  })
}

type Block =
  | { kind: 'p'; lines: string[] }
  | { kind: 'ul' | 'ol'; items: string[] }

const BULLET = /^\s*[-*•]\s+(.*)$/
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/

function parse(source: string): Block[] {
  const blocks: Block[] = []
  for (const line of source.split('\n')) {
    const bullet = BULLET.exec(line)
    const numbered = bullet ? null : NUMBERED.exec(line)
    const last = blocks[blocks.length - 1]
    if (bullet || numbered) {
      const kind = bullet ? 'ul' : 'ol'
      const text = (bullet ?? numbered)![1]
      if (last && last.kind === kind) last.items.push(text)
      else blocks.push({ kind, items: [text] })
    } else if (line.trim() === '') {
      blocks.push({ kind: 'p', lines: [] })
    } else if (last && last.kind === 'p') {
      last.lines.push(line)
    } else {
      blocks.push({ kind: 'p', lines: [line] })
    }
  }
  return blocks.filter((b) => b.kind !== 'p' || b.lines.length > 0)
}

export function Markdown({ text }: { text: string }) {
  return (
    <div className="md">
      {parse(text).map((block, i) => {
        if (block.kind === 'p') {
          return (
            <p key={i}>
              {block.lines.map((l, j) => (
                <Fragment key={j}>
                  {j > 0 && <br />}
                  {inline(l)}
                </Fragment>
              ))}
            </p>
          )
        }
        const Tag = block.kind
        return (
          <Tag key={i}>
            {block.items.map((item, j) => (
              <li key={j}>{inline(item)}</li>
            ))}
          </Tag>
        )
      })}
    </div>
  )
}
