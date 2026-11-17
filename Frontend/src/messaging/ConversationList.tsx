import type { ReactElement } from 'react';

import { groupConversations, previewOf } from './types';
import type { Conversation } from './types';

interface Props {
  conversations: Conversation[] | null;
  selectedId: string | null;
  onSelect: (conversation: Conversation) => void;
  onNewGroup: () => void;
  onNewDirect: () => void;
}

export default function ConversationList({
  conversations,
  selectedId,
  onSelect,
  onNewGroup,
  onNewDirect,
}: Props): ReactElement {
  return (
    <nav className="chat-list" aria-label="Conversations">
      <div className="chat-list-actions">
        <button className="identity-primary" type="button" onClick={onNewGroup}>
          New group
        </button>
        <button className="identity-secondary" type="button" onClick={onNewDirect}>
          New direct message
        </button>
      </div>
      {conversations === null ? (
        <p role="status">Loading conversations…</p>
      ) : conversations.length === 0 ? (
        <p className="identity-empty">No conversations yet. Start a group or a direct message.</p>
      ) : (
        groupConversations(conversations).map((section) => (
          <section key={section.type} aria-label={section.label}>
            <h2>{section.label}</h2>
            <ul>
              {section.items.map((conversation) => (
                <li key={conversation.id}>
                  <button
                    type="button"
                    className={`chat-list-item${conversation.id === selectedId ? ' chat-list-item-active' : ''}`}
                    aria-current={conversation.id === selectedId ? 'true' : undefined}
                    onClick={() => onSelect(conversation)}
                  >
                    <span className="chat-list-title">{conversation.title}</span>
                    <span className="chat-list-preview">{previewOf(conversation)}</span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </nav>
  );
}
