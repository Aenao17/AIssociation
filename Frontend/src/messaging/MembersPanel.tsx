import { useEffect, useState } from 'react';
import type { ReactElement } from 'react';

import { useAuth } from '../auth/useAuth';

import { messagingErrorText } from './types';
import type { Conversation, Participant } from './types';

interface Props {
  base: string; 
  conversation: Conversation;
  onLeft: () => void;
}

export default function MembersPanel({ base, conversation, onLeft }: Props): ReactElement {
  const { user, request } = useAuth();
  const [open, setOpen] = useState(false);
  const [members, setMembers] = useState<Participant[] | null>(null);
  const [contacts, setContacts] = useState<Participant[]>([]);
  const [pick, setPick] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const url = `${base}/${conversation.id}`;
  const isGroup = conversation.type === 'GROUP';
  const manage = isGroup && conversation.canManageMembers;

  useEffect(() => {
    if (!open) return undefined; 
    let active = true;
    async function load(): Promise<void> {
      try {
        const list = await request<Participant[]>(`${url}/members`);
        const people = manage ? await request<Participant[]>(`${base}/contacts`) : [];
        if (active) {
          setMembers(list);
          setContacts(people);
          setError('');
        }
      } catch (failure) {
        if (active) setError(messagingErrorText(failure));
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, [open, request, url, base, manage]);

  async function add(): Promise<void> {
    if (!pick) return;
    setBusy(true);
    setError('');
    try {
      setMembers(await request<Participant[]>(`${url}/members/${encodeURIComponent(pick)}`, { method: 'PUT' }));
      setPick('');
    } catch (failure) {
      setError(messagingErrorText(failure));
    } finally {
      setBusy(false);
    }
  }

  async function remove(person: Participant): Promise<void> {
    setBusy(true);
    setError('');
    try {
      await request<void>(`${url}/members/${encodeURIComponent(person.userId)}`, { method: 'DELETE' });
      setMembers((current) => (current ?? []).filter((item) => item.userId !== person.userId));
    } catch (failure) {
      setError(messagingErrorText(failure));
    } finally {
      setBusy(false);
    }
  }

  async function leave(): Promise<void> {
    if (!user || !window.confirm(`Leave “${conversation.title}”? You will no longer see its messages.`)) return;
    setBusy(true);
    setError('');
    try {
      await request<void>(`${url}/members/${encodeURIComponent(user.id)}`, { method: 'DELETE' });
      onLeft();
    } catch (failure) {
      setError(messagingErrorText(failure));
      setBusy(false);
    }
  }

  const memberIds = new Set((members ?? []).map((person) => person.userId));
  const candidates = contacts.filter((person) => !memberIds.has(person.userId));

  return (
    <div className="chat-members">
      <button
        className="identity-secondary"
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {open ? 'Hide members' : 'Members'}
      </button>
      {open && (
        <div className="chat-members-body">
          {error && (
            <p className="identity-error" role="alert">
              {error}
            </p>
          )}
          {!members && !error && <p role="status">Loading members…</p>}
          {members && (
            <ul aria-label="Members">
              {members.map((person) => (
                <li key={person.userId}>
                  <span>{person.displayName}</span>
                  {manage && person.userId !== user?.id && (
                    <button
                      className="identity-secondary"
                      type="button"
                      disabled={busy}
                      onClick={() => void remove(person)}
                    >
                      Remove {person.displayName}
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {manage && members && (
            <div className="chat-members-add">
              <label>
                Add a member
                <select value={pick} onChange={(event) => setPick(event.target.value)}>
                  <option value="">Choose a person…</option>
                  {candidates.map((person) => (
                    <option key={person.userId} value={person.userId}>
                      {person.displayName}
                    </option>
                  ))}
                </select>
              </label>
              <button className="identity-primary" type="button" disabled={busy || !pick} onClick={() => void add()}>
                Add member
              </button>
            </div>
          )}
          {isGroup && !conversation.canManageMembers && (
            <button className="identity-secondary" type="button" disabled={busy} onClick={() => void leave()}>
              Leave group
            </button>
          )}
        </div>
      )}
    </div>
  );
}