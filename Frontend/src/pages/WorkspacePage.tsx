import { useCallback, useEffect, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';

import { ApiError } from '../auth/client';
import { useAuth } from '../auth/useAuth';
import DashboardShell from '../components/DashboardShell';
import MembershipPanel from '../workspaces/MembershipPanel';
import type { Workspace } from '../workspaces/types';
import './Identity.css';
import './Workspace.css';

function WorkspaceContent({ slug }: { slug: string }): ReactElement {
  const { user, request, refreshUser } = useAuth();
  const key = `${user?.id}:${slug}`;
  const endpoint = `/api/workspaces/${encodeURIComponent(slug ?? '')}`;
  const [snapshot, setSnapshot] = useState<{ key: string; data: Workspace } | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const workspace = snapshot?.key === key ? snapshot.data : null;
  const message = error?.key === key ? error.message : '';

  const showFailure = useCallback(
    (failure: unknown) => {
      setSnapshot(null);
      setError({
        key,
        message:
          failure instanceof ApiError && failure.status === 403
            ? 'You do not have access to this association workspace.'
            : failure instanceof ApiError && failure.status === 404
              ? 'This association could not be found.'
              : 'Unable to load the workspace. Please try again.',
      });
    },
    [key],
  );

  useEffect(() => {
    if (!user?.id) return;
    let active = true;
    request<Workspace>(endpoint)
      .then((data) => {
        if (active) {
          setSnapshot({ key, data });
          setError(null);
        }
      })
      .catch((failure: unknown) => {
        if (active) showFailure(failure);
      });
    return () => {
      active = false;
    };
  }, [user?.id, request, endpoint, key, showFailure]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refreshUser().catch(() => undefined);
      const data = await request<Workspace>(endpoint);
      setSnapshot({ key, data });
      setError(null);
      return data.role === 'ADMIN';
    } catch (failure) {
      showFailure(failure);
      return false;
    } finally {
      setRefreshing(false);
    }
  }, [refreshUser, request, endpoint, key, showFailure]);

  if (!user) return <Navigate to="/login" replace />;
  return (
    <DashboardShell>
      <div className="workspace-layout">
        <section className="identity-card" aria-labelledby="workspace-heading">
          <p className="identity-eyebrow">Association workspace</p>
          <h1 id="workspace-heading">{workspace?.name ?? 'Your workspace'}</h1>
          {message ? (
            <p className="identity-error" role="alert">
              {message}
            </p>
          ) : !workspace ? (
            <p role="status">Loading workspace…</p>
          ) : (
            <>
              <p className="identity-intro">
                Your role: <strong>{workspace.role === 'ADMIN' ? 'Administrator' : 'Member'}</strong>
              </p>
              <p>
                {workspace.role === 'ADMIN'
                  ? 'Manage your association’s membership below.'
                  : 'You are signed in to your association’s workspace.'}
              </p>
              <nav className="workspace-module-nav" aria-label="Workspace tools">
                <Link className="identity-primary" to={`/workspaces/${encodeURIComponent(slug)}/projects`}>
                  Projects &amp; recruitment
                </Link>
                <Link className="identity-secondary" to={`/workspaces/${encodeURIComponent(slug)}/events`}>
                  Events &amp; public profile
                </Link>
              </nav>
            </>
          )}
          <div className="identity-actions">
            <button
              type="button"
              className="identity-secondary"
              disabled={refreshing}
              onClick={() => {
                void refresh();
              }}
            >
              {refreshing ? 'Refreshing…' : 'Refresh workspace'}
            </button>
          </div>
        </section>
        {workspace?.role === 'ADMIN' && (
          <MembershipPanel key={workspace.associationId} associationId={workspace.associationId} onChange={refresh} />
        )}
      </div>
    </DashboardShell>
  );
}

// Isolate in-flight loads and cached view state when switching user/workspace.
export default function WorkspacePage(): ReactElement {
  const { slug } = useParams<{ slug: string }>();
  const { user } = useAuth();
  return <WorkspaceContent key={`${user?.id}:${slug}`} slug={slug ?? ''} />;
}
