import { useEffect, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent, ReactElement } from 'react';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';

import MembersPanel from './MembersPanel';
import { MAX_MESSAGE_LENGTH, mergeMessages, messagingErrorText } from './types';
import type { Conversation, Message, MessagePage } from './types';

interface Props {
  base: string; 
  conversation: Conversation;
  onBack: () => void; 
  onUnavailable: () => void; 
  onLeft: () => void;
  onSent: (message: Message) => void; 
  pollMs?: number; 
}

function subtitle(conversation: Conversation): string {
  switch (conversation.type) {
    case 'ASSOCIATION':
      return 'Everyone in the association';
    case 'PROJECT':
      return 'Project team';
    case 'DEPARTMENT':
      return 'Department and project managers';
    case 'GROUP':
      return 'Custom group';
    case 'DIRECT':
      return 'Direct message';
  }
}

function when(value: string): string {
  return new Date(value).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' });
}

export default function MessageThread({
  base,
  conversation,
  onBack,
  onUnavailable,
  onLeft,
  onSent,
  pollMs = 4000,
}: Props): ReactElement {
  const { request } = useAuth();
  const url = `${base}/${conversation.id}/messages`;

  const [messages, setMessages] = useState<Message[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState('');
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState('');
  const [attempt, setAttempt] = useState(0);

  const lastSeq = useRef(0);
  const ready = useRef(false);
  const log = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    ready.current = false;
    lastSeq.current = 0;

    async function first(): Promise<void> {
      try {
        const page = await request<MessagePage>(url); 
        if (!active) return;
        setMessages(page.messages);
        setHasOlder(page.hasMore);
        lastSeq.current = page.messages.length ? page.messages[page.messages.length - 1].seq : 0;
        ready.current = true;
        setError('');
        setStatus('ready');
      } catch (failure) {
        if (!active) return;
        if (failure instanceof ApiError && failure.status === 404) {
          onUnavailable(); 
          return;
        }
        setError(messagingErrorText(failure));
        setStatus('error');
      }
    }

    async function poll(): Promise<void> {
      if (!ready.current) return;
      try {
        for (let round = 0; round < 10; round += 1) {
          const page = await request<MessagePage>(`${url}?after=${lastSeq.current}`);
          if (!active) return;
          if (page.messages.length) {
            setMessages((current) => mergeMessages(current, page.messages));
            lastSeq.current = page.messages[page.messages.length - 1].seq;
          }
          if (!page.hasMore) break;
        }
      } catch (failure) {
        if (active && failure instanceof ApiError && failure.status === 404) onUnavailable();
      }
    }

    void first();
    const timer = window.setInterval(() => void poll(), pollMs);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [request, url, attempt, pollMs, onUnavailable]);

  const newestId = messages.length ? messages[messages.length - 1].id : '';
  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight;
  }, [newestId]);

  async function loadOlder(): Promise<void> {
    if (!messages.length) return;
    setLoadingOlder(true);
    try {
      const page = await request<MessagePage>(`${url}?before=${messages[0].seq}`);
      setMessages((current) => mergeMessages(current, page.messages));
      setHasOlder(page.hasMore);
    } catch (failure) {
      setError(messagingErrorText(failure));
    } finally {
      setLoadingOlder(false);
    }
  }

  async function send(event?: FormEvent): Promise<void> {
    event?.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    setSending(true);
    setSendError('');
    try {
      const sent = await request<Message>(url, { method: 'POST', body: JSON.stringify({ body }) });
      setMessages((current) => mergeMessages(current, [sent]));
      setDraft('');
      onSent(sent);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 404) onUnavailable();
      else setSendError(messagingErrorText(failure)); 
    } finally {
      setSending(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  return (
    <div className="chat-thread">
      <header className="chat-thread-header">
        <button className="identity-secondary chat-back" type="button" onClick={onBack}>
          All conversations
        </button>
        <div>
          <h2>{conversation.title}</h2>
          <p className="identity-help">{subtitle(conversation)}</p>
        </div>
        <MembersPanel base={base} conversation={conversation} onLeft={onLeft} />
      </header>

      {status === 'error' && (
        <div className="identity-error" role="alert">
          <p>{error}</p>
          <button
            className="identity-secondary"
            type="button"
            onClick={() => {
              setStatus('loading');
              setAttempt((n) => n + 1);
            }}
          >
            Retry messages
          </button>
        </div>
      )}
      {status === 'loading' && <p role="status">Loading messages…</p>}

      {status === 'ready' && (
        <div className="chat-log" ref={log} role="log" aria-live="polite" aria-label="Messages">
          {hasOlder && (
            <button
              className="identity-secondary chat-older"
              type="button"
              disabled={loadingOlder}
              onClick={() => void loadOlder()}
            >
              Load older messages
            </button>
          )}
          {messages.length === 0 && <p className="identity-empty">No messages yet. Say hello.</p>}
          {messages.map((message) => (
            <article key={message.id} className={`chat-message${message.mine ? ' chat-message-mine' : ''}`}>
              <p className="chat-message-meta">
                <strong>{message.mine ? 'You' : message.senderName}</strong>{' '}
                <time dateTime={message.createdAt}>{when(message.createdAt)}</time>
              </p>
              <p className="chat-message-body">{message.body}</p>
            </article>
          ))}
        </div>
      )}

      <form className="chat-composer" onSubmit={(event) => void send(event)}>
        {sendError && (
          <p className="identity-error" role="alert">
            {sendError}
          </p>
        )}
        <label>
          Message
          <textarea
            value={draft}
            rows={2}
            maxLength={MAX_MESSAGE_LENGTH}
            placeholder="Write a message…"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={onKeyDown}
          />
        </label>
        <button className="identity-primary" type="submit" disabled={status !== 'ready' || sending || !draft.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}