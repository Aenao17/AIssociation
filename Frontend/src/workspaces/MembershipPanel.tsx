import { useCallback, useEffect, useState } from 'react';
import type { FormEvent, ReactElement } from 'react';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';

import type { AssociationMember, AssociationRole } from './types';

interface Props {
  associationId: string;
  onChange: () => Promise<boolean>;
}

export default function MembershipPanel({ associationId, onChange }: Props): ReactElement {
  const { user, request } = useAuth();
  const [members, setMembers] = useState<AssociationMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<AssociationRole>('MEMBER');
  const [draftRoles, setDraftRoles] = useState<Record<string, AssociationRole>>({});
  const [removal, setRemoval] = useState<string | null>(null);
  const endpoint = `/api/associations/${associationId}/members`;

  const acceptMembers = useCallback((result: AssociationMember[]) => {
    setMembers(result);
    setDraftRoles(Object.fromEntries(result.map((member) => [member.id, member.role])));
  }, []);

  useEffect(() => {
    let active = true;
    request<AssociationMember[]>(endpoint)
      .then((result) => {
        if (active) acceptMembers(result);
      })
      .catch(() => {
        if (active) setLoadError('Unable to load members. Your administrator access may have changed.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [request, endpoint, acceptMembers]);

  async function loadMembers() {
    setLoading(true);
    setLoadError('');
    try {
      acceptMembers(await request<AssociationMember[]>(endpoint));
    } catch {
      setMembers([]);
      setLoadError('Unable to load members. Refresh the workspace to check your access.');
    } finally {
      setLoading(false);
    }
  }

  async function mutate(operation: () => Promise<unknown>, message: string, afterSave?: () => void) {
    setBusy(true);
    setError('');
    setNotice('');
    let saved = false;
    try {
      await operation();
      saved = true;
      setNotice(message);
      afterSave?.();
      setRemoval(null);
      if (await onChange()) await loadMembers();
    } catch (failure) {
      setError(
        saved
          ? 'The change was saved, but the updated view could not be loaded. Refresh the workspace.'
          : failure instanceof ApiError
            ? `${failure.message} ${Object.values(failure.fieldErrors).join(' ')}`.trim()
            : 'Unable to complete the change. Refresh the list before retrying.',
      );
    } finally {
      setBusy(false);
    }
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void mutate(
      () => request(endpoint, { method: 'POST', body: JSON.stringify({ email, role }) }),
      'Member added.',
      () => setEmail(''),
    );
  }

  const adminCount = members.filter((member) => member.role === 'ADMIN').length;
  return (
    <section className="identity-card membership-panel" aria-labelledby="manage-members-heading">
      <p className="identity-eyebrow">Association administration</p>
      <h2 id="manage-members-heading">Manage members</h2>
      <p className="identity-intro">Add an existing account and choose its role in this association.</p>
      <form onSubmit={add} className="membership-add-form">
        <label>
          Account email
          <input
            type="email"
            name="email"
            autoComplete="off"
            required
            maxLength={254}
            value={email}
            disabled={busy}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          Association role
          <select value={role} disabled={busy} onChange={(event) => setRole(event.target.value as AssociationRole)}>
            <option value="MEMBER">Member</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </label>
        <button type="submit" className="identity-primary" disabled={busy}>
          {busy ? 'Saving…' : 'Add member'}
        </button>
      </form>
      <p className="identity-help">
        The person must register first. Association administrators can manage members here; this does not grant platform
        administration.
      </p>
      {notice && (
        <p className="identity-notice" role="status">
          {notice}
        </p>
      )}
      {error && (
        <p className="identity-error" role="alert">
          {error}
        </p>
      )}
      {loadError && (
        <p className="identity-error" role="alert">
          {loadError}
        </p>
      )}
      {loading ? (
        <p role="status">Loading members…</p>
      ) : (
        <>
          <p className="identity-help">
            Keep at least one administrator. Add or promote another administrator before removing the last one.
          </p>
          <ul className="workspace-member-list">
            {members.map((member) => {
              const lastAdmin = member.role === 'ADMIN' && adminCount === 1;
              return (
                <li key={member.id}>
                  <div className="member-details">
                    <strong>
                      {member.displayName}
                      {member.userId === user?.id ? ' (you)' : ''}
                    </strong>
                    <span>{member.email}</span>
                  </div>
                  <div className="member-actions">
                    <label>
                      Role
                      <select
                        aria-label={`Role for ${member.email}`}
                        value={draftRoles[member.id] ?? member.role}
                        disabled={busy || lastAdmin}
                        onChange={(event) =>
                          setDraftRoles((previous) => ({
                            ...previous,
                            [member.id]: event.target.value as AssociationRole,
                          }))
                        }
                      >
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Administrator</option>
                      </select>
                    </label>
                    <button
                      type="button"
                      className="identity-secondary"
                      disabled={busy || !draftRoles[member.id] || draftRoles[member.id] === member.role}
                      onClick={() => {
                        void mutate(
                          () =>
                            request(`${endpoint}/${member.id}`, {
                              method: 'PATCH',
                              body: JSON.stringify({ role: draftRoles[member.id] }),
                            }),
                          'Member role updated.',
                        );
                      }}
                      aria-label={`Save role for ${member.email}`}
                    >
                      Save role
                    </button>
                    <button
                      type="button"
                      className="identity-secondary"
                      disabled={busy || lastAdmin}
                      onClick={() => setRemoval(member.id)}
                      aria-label={`Remove ${member.email}`}
                    >
                      Remove
                    </button>
                  </div>
                  {removal === member.id && (
                    <div className="member-removal" role="alert">
                      <p>Remove {member.displayName} from this association? They will lose workspace access.</p>
                      <div className="identity-actions">
                        <button
                          type="button"
                          className="identity-secondary"
                          disabled={busy}
                          onClick={() => setRemoval(null)}
                        >
                          Cancel
                        </button>
                        <button
                          type="button"
                          className="identity-primary"
                          disabled={busy}
                          onClick={() => {
                            void mutate(
                              () => request(`${endpoint}/${member.id}`, { method: 'DELETE' }),
                              'Member removed.',
                            );
                          }}
                        >
                          Confirm removal
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </>
      )}
      <button
        type="button"
        className="identity-secondary"
        disabled={loading || busy}
        onClick={() => {
          void loadMembers();
        }}
      >
        Refresh members
      </button>
    </section>
  );
}
