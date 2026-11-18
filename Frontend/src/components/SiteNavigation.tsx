import { useRef } from 'react';
import type { ReactElement } from 'react';
import { Link, matchPath, NavLink, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';

import './SiteNavigation.css';

function NavigationLinks({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const match = matchPath('/workspaces/:slug/*', pathname);
  const workspace = user?.memberships.find((membership) => membership.associationSlug === match?.params.slug);
  const base = workspace ? `/workspaces/${encodeURIComponent(workspace.associationSlug)}` : '';

  return (
    <>
      <div className="site-nav-section">
        <span className="site-nav-label">Explore</span>
        <NavLink to="/home" end onClick={onNavigate}>
          Home
        </NavLink>
        <NavLink to="/events" onClick={onNavigate}>
          Events &amp; calendar
        </NavLink>
        <NavLink to="/associations" onClick={onNavigate}>
          Associations
        </NavLink>
        <NavLink to="/research" onClick={onNavigate}>
          Share feedback
        </NavLink>
      </div>
      {workspace && (
        <div className="site-nav-section">
          <span className="site-nav-label">{workspace.associationName}</span>
          <NavLink to={base} end onClick={onNavigate}>
            Workspace overview
          </NavLink>
          <NavLink to={`${base}/projects`} onClick={onNavigate}>
            Projects &amp; tasks
          </NavLink>
          <NavLink to={`${base}/events`} onClick={onNavigate}>
            Manage events
          </NavLink>
          <NavLink to={`${base}/messages`} onClick={onNavigate}>
            Messages
          </NavLink>
        </div>
      )}
      {user && user.memberships.length > 0 && (
        <div className="site-nav-section">
          <span className="site-nav-label">Your workspaces</span>
          {user.memberships.map((membership) => (
            <NavLink
              key={membership.associationId}
              to={`/workspaces/${encodeURIComponent(membership.associationSlug)}`}
              end
              onClick={onNavigate}
            >
              {membership.associationName}
            </NavLink>
          ))}
        </div>
      )}
      {user?.platformAdmin && (
        <div className="site-nav-section">
          <span className="site-nav-label">Platform administration</span>
          <NavLink to="/admin/associations" onClick={onNavigate}>
            Manage associations
          </NavLink>
          <NavLink to="/admin/research" onClick={onNavigate}>
            Research responses
          </NavLink>
        </div>
      )}
      <div className="site-nav-section site-nav-account">
        <span className="site-nav-label">Account</span>
        {user ? (
          <NavLink to="/account" onClick={onNavigate}>
            My account
          </NavLink>
        ) : (
          <>
            <NavLink to="/login" onClick={onNavigate}>
              Sign in
            </NavLink>
            <NavLink to="/register" onClick={onNavigate}>
              Create account
            </NavLink>
          </>
        )}
      </div>
    </>
  );
}

export default function SiteNavigation(): ReactElement {
  const details = useRef<HTMLDetailsElement>(null);
  const location = useLocation();

  function closeMenu() {
    if (!details.current) return;
    details.current.open = false;
    details.current.querySelector('summary')?.focus();
  }

  return (
    <>
      <a className="site-skip-link" href={`${location.pathname}${location.search}#site-content`}>
        Skip to content
      </a>
      <aside className="site-sidebar">
        <Link className="site-nav-brand" to="/home" aria-label="AIssociation home">
          <img src="/brand/logo-ink.svg" alt="AIssociation" width="200" height="23" />
        </Link>
        <nav aria-label="Main navigation">
          <NavigationLinks />
        </nav>
      </aside>
      <header className="site-mobile-header">
        <Link className="site-nav-brand" to="/home" aria-label="AIssociation home">
          <img src="/brand/logo-ink.svg" alt="AIssociation" width="180" height="20" />
        </Link>
        <details
          key={location.key}
          ref={details}
          className="site-mobile-menu"
          onKeyDown={(event) => {
            if (event.key === 'Escape') closeMenu();
          }}
        >
          <summary>
            Menu <span aria-hidden="true">☰</span>
          </summary>
          <nav aria-label="Mobile navigation">
            <NavigationLinks onNavigate={closeMenu} />
          </nav>
        </details>
      </header>
    </>
  );
}
