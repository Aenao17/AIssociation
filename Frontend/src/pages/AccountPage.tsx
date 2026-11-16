import { useState } from 'react';
import type { ReactElement } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';
import DashboardShell from '../components/DashboardShell';
import ResearchResponsesPage from '../research/ResearchResponsesPage';

import PlatformAssociationsPage from './PlatformAssociationsPage';
import './Workspace.css';

export default function AccountPage(): ReactElement {
  const { user, refreshUser } = useAuth();
  const [search, setSearch] = useSearchParams();
  const panel = search.get('administration') === 'research' ? 'research' : 'associations';
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  if (!user) return <Navigate to="/login" replace />;
  async function refresh(): Promise<void> {
    setPending(true);
    setError('');
    try {
      await refreshUser();
    } catch {
      setError('Unable to refresh memberships. Please try again.');
    } finally {
      setPending(false);
    }
  }
  function select(next: string): void {
    const params = new URLSearchParams(search);
    params.set('administration', next);
    setSearch(params);
  }
  return (
    <DashboardShell account>
      <header className="dashboard-heading">
        <p className="identity-eyebrow">Your account</p>
        <h1>Hello, {user.displayName}</h1>
        <p className="identity-intro">{user.email}</p>
      </header>
      {user.platformAdmin && (
        <section className="account-administration" aria-labelledby="administration-heading">
          <div className="dashboard-section-heading">
            <div>
              <h2 id="administration-heading">Administration</h2>
              <p>Manage the platform and explore feedback from student communities.</p>
            </div>
          </div>
          <div className="account-admin-actions" role="group" aria-label="Administration views">
            <button
              type="button"
              aria-pressed={panel === 'associations'}
              aria-controls="account-administration"
              onClick={() => select('associations')}
            >
              <span>Platform administration</span>
              <span aria-hidden="true">→</span>
            </button>
            <button
              type="button"
              aria-pressed={panel === 'research'}
              aria-controls="account-administration"
              onClick={() => select('research')}
            >
              <span>Research responses</span>
              <span aria-hidden="true">→</span>
            </button>
          </div>
          <div
            id="account-administration"
            className="dashboard-panel"
            role="region"
            aria-label={panel === 'research' ? 'Research responses' : 'Platform administration'}
          >
            {panel === 'research' ? (
              <ResearchResponsesPage key={user.id} embedded />
            ) : (
              <PlatformAssociationsPage key={user.id} embedded />
            )}
          </div>
        </section>
      )}
      <section className="account-workspaces" aria-labelledby="memberships-heading">
        <div className="dashboard-section-heading">
          <div>
            <h2 id="memberships-heading">Your workspaces</h2>
            <p>Choose an association to work with your team.</p>
          </div>
          <button
            type="button"
            className="identity-secondary"
            disabled={pending}
            onClick={() => {
              void refresh();
            }}
          >
            {pending ? 'Refreshing…' : 'Refresh memberships'}
          </button>
        </div>
        {error && (
          <p className="identity-error" role="alert">
            {error}
          </p>
        )}
        {!user.memberships.length ? (
          <p className="identity-empty">
            You aren’t a member of an association yet. An association administrator can add your account.
          </p>
        ) : (
          <ul className="account-workspace-list">
            {user.memberships.map((membership) => (
              <li key={membership.associationId} className="account-workspace-card">
                <span className="dashboard-role">{membership.role === 'ADMIN' ? 'Administrator' : 'Member'}</span>
                <h3>{membership.associationName}</h3>
                <Link
                  className="identity-secondary"
                  to={`/workspaces/${encodeURIComponent(membership.associationSlug)}`}
                >
                  Open workspace <span aria-hidden="true">&nbsp;→</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <p className="identity-help">Event participation is separate from association membership.</p>
      </section>
    </DashboardShell>
  );
}
