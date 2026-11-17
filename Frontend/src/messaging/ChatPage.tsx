import { IonContent, IonPage } from '@ionic/react';
import { useCallback, useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Navigate, useParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import BackLink from '../components/BackLink';
import BrandHeader from '../components/BrandHeader';
import type { Workspace } from '../workspaces/types';

import ConversationList from './ConversationList';
import MessageThread from './MessageThread';
import { NewDirectDialog, NewGroupDialog } from './NewGroupDialog';
import { messagingErrorText } from './types';
import type { Conversation, Message } from './types';
import '../pages/Identity.css';
import '../events/Events.css';
import './Messaging.css';

const LIST_REFRESH_MS = 20000;

function Content({ slug }: { slug: string }): ReactElement {
  const { user, request } = useAuth();
  const userId = user?.id ?? null;
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [conversations, setConversations] = useState<Conversation[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [creating, setCreating] = useState<'group' | 'direct' | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!userId) return undefined;
    let active = true;
    request<Workspace>(`/api/workspaces/${encodeURIComponent(slug)}`)
      .then((scope) => {
        if (active) setWorkspace(scope);
      })
      .catch((failure: unknown) => {
        if (active) setError(messagingErrorText(failure));
      });
    return () => {
      active = false;
    };
  }, [request, userId, slug, attempt]);

  const associationId = workspace?.associationId ?? null;
  const base = associationId ? `/api/associations/${associationId}/conversations` : '';

  useEffect(() => {
    if (!associationId) return undefined;
    let active = true;
    async function load(): Promise<void> {
      try {
        const list = await request<Conversation[]>(`/api/associations/${associationId}/conversations`);
        if (active) {
          setConversations(list);
          setError('');
        }
      } catch (failure) {
        if (active) setError(messagingErrorText(failure));
      }
    }
    void load();
    const timer = window.setInterval(() => void load(), LIST_REFRESH_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [request, associationId, attempt]);

  const onUnavailable = useCallback(() => {
    setSelectedId(null);
    setNotice('That conversation is no longer available to you.');
    setAttempt((n) => n + 1);
  }, []);
  const onLeft = useCallback((id: string) => {
    setConversations((current) => (current ?? []).filter((item) => item.id !== id));
    setSelectedId(null);
    setNotice('You left the group.');
  }, []);

  if (!user) return <Navigate to="/login" replace />;

  function upsert(conversation: Conversation): void {
    setConversations((current) => [conversation, ...(current ?? []).filter((item) => item.id !== conversation.id)]);
  }

  function opened(conversation: Conversation): void {
    upsert(conversation);
    setSelectedId(conversation.id);
    setCreating(null);
    setNotice('');
  }

  function sent(message: Message): void {
    setConversations((current) => {
      const target = (current ?? []).find((item) => item.id === message.conversationId);
      if (!target) return current;
      return [{ ...target, lastMessage: message }, ...(current ?? []).filter((item) => item.id !== target.id)];
    });
  }

  const selected = conversations?.find((item) => item.id === selectedId) ?? null;

  return (
    <IonPage>
      <IonContent>
        <main className="identity-shell events-shell chat-shell">
          <BrandHeader wide />
          <BackLink to={`/workspaces/${encodeURIComponent(slug)}`}>Workspace</BackLink>
          <p className="identity-eyebrow">{workspace?.name ?? 'Association'}</p>
          <h1>Messages</h1>

          {error && (
            <div className="identity-error" role="alert">
              <p>{error}</p>
              <button className="identity-secondary" type="button" onClick={() => setAttempt((n) => n + 1)}>
                Retry messages list
              </button>
            </div>
          )}
          {notice && (
            <p className="identity-notice" role="status">
              {notice}
            </p>
          )}

          {creating === 'group' && base && (
            <NewGroupDialog base={base} onCreated={opened} onClose={() => setCreating(null)} />
          )}
          {creating === 'direct' && base && (
            <NewDirectDialog base={base} onOpened={opened} onClose={() => setCreating(null)} />
          )}

          <div className={`chat-layout${selected ? ' chat-has-selection' : ''}`}>
            <ConversationList
              conversations={conversations}
              selectedId={selectedId}
              onSelect={(conversation) => {
                setSelectedId(conversation.id);
                setNotice('');
              }}
              onNewGroup={() => setCreating('group')}
              onNewDirect={() => setCreating('direct')}
            />
            <section className="chat-thread-pane" aria-label="Conversation">
              {selected ? (
                <MessageThread
                  key={selected.id}
                  base={base}
                  conversation={selected}
                  onBack={() => setSelectedId(null)}
                  onUnavailable={onUnavailable}
                  onLeft={() => onLeft(selected.id)}
                  onSent={sent}
                />
              ) : (
                <p className="identity-empty">Select a conversation to read and write messages.</p>
              )}
            </section>
          </div>
        </main>
      </IonContent>
    </IonPage>
  );
}

export default function ChatPage(): ReactElement {
  const { slug } = useParams<{ slug: string }>();
  const { user } = useAuth();

  return <Content key={`${slug}:${user?.id}`} slug={slug ?? ''} />;
}
