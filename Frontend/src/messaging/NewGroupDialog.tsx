import { useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';

import { useAuth } from '../auth/useAuth';

import { MAX_GROUP_NAME_LENGTH, messagingErrorText } from './types';
import type { Conversation, Participant } from './types';

interface ContactsState {
  contacts: Participant[] | null; 
  error: string;
}


function useContacts(base: string): ContactsState {
  const { request } = useAuth();
  const [state, setState] = useState<ContactsState>({ contacts: null, error: '' });
  useEffect(() => {
    let active = true;
    request<Participant[]>(`${base}/contacts`)
      .then((contacts) => {
        if (active) setState({ contacts, error: '' });
      })
      .catch((failure: unknown) => {
        if (active) setState({ contacts: null, error: messagingErrorText(failure) });
      });
    return () => {
      active = false;
    };
  }, [request, base]);
  return state;
}

interface GroupProps {
  base: string;
  onCreated: (conversation: Conversation) => void;
  onClose: () => void;
}

export function NewGroupDialog({ base, onCreated, onClose }: GroupProps): ReactElement {
  const { request } = useAuth();
  const { contacts, error: contactsError } = useContacts(base);
  const [name, setName] = useState('');
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  function toggle(userId: string, checked: boolean): void {
    setChosen((current) => {
      const next = new Set(current);
      if (checked) next.add(userId);
      else next.delete(userId);
      return next;
    });
  }

  async function submit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError('Give the group a name.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const created = await request<Conversation>(`${base}/groups`, {
        method: 'POST',
        body: JSON.stringify({ name: trimmed, memberUserIds: [...chosen] }),
      });
      onCreated(created);
    } catch (failure) {
      setError(messagingErrorText(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="chat-dialog" role="dialog" aria-label="New group">
      <h2>New group</h2>
      <form onSubmit={(event) => void submit(event)}>
        <label>
          Group name
          <input
            value={name}
            maxLength={MAX_GROUP_NAME_LENGTH}
            onChange={(event) => setName(event.target.value)}
            autoComplete="off"
          />
        </label>
        <fieldset>
          <legend>Members (you are added automatically)</legend>
          {contactsError && (
            <p className="identity-error" role="alert">
              {contactsError}
            </p>
          )}
          {contacts === null && !contactsError && <p role="status">Loading people…</p>}
          {contacts?.length === 0 && <p className="identity-empty">No other members in this association yet.</p>}
          {contacts?.map((person) => (
            <label key={person.userId} className="chat-check">
              <input
                type="checkbox"
                checked={chosen.has(person.userId)}
                onChange={(event) => toggle(person.userId, event.target.checked)}
              />
              {person.displayName}
            </label>
          ))}
        </fieldset>
        {error && (
          <p className="identity-error" role="alert">
            {error}
          </p>
        )}
        <div className="identity-actions">
          <button className="identity-primary" type="submit" disabled={busy}>
            Create group
          </button>
          <button className="identity-secondary" type="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}

interface DirectProps {
  base: string;
  onOpened: (conversation: Conversation) => void;
  onClose: () => void;
}

export function NewDirectDialog({ base, onOpened, onClose }: DirectProps): ReactElement {
  const { request } = useAuth();
  const { contacts, error: contactsError } = useContacts(base);
  const [userId, setUserId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (!userId) return;
    setBusy(true);
    setError('');
    try {
      onOpened(await request<Conversation>(`${base}/direct`, { method: 'POST', body: JSON.stringify({ userId }) }));
    } catch (failure) {
      setError(messagingErrorText(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="chat-dialog" role="dialog" aria-label="New direct message">
      <h2>New direct message</h2>
      <form onSubmit={(event) => void submit(event)}>
        <label>
          Person
          <select value={userId} onChange={(event) => setUserId(event.target.value)}>
            <option value="">Choose a person…</option>
            {contacts?.map((person) => (
              <option key={person.userId} value={person.userId}>
                {person.displayName}
              </option>
            ))}
          </select>
        </label>
        {contactsError && (
          <p className="identity-error" role="alert">
            {contactsError}
          </p>
        )}
        {contacts?.length === 0 && <p className="identity-empty">No other members in this association yet.</p>}
        {error && (
          <p className="identity-error" role="alert">
            {error}
          </p>
        )}
        <div className="identity-actions">
          <button className="identity-primary" type="submit" disabled={busy || !userId}>
            Start conversation
          </button>
          <button className="identity-secondary" type="button" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}