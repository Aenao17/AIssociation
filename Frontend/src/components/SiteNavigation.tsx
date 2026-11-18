import { IonIcon } from '@ionic/react';
import {
  businessOutline,
  calendarOutline,
  chatbubblesOutline,
  chevronBackOutline,
  chevronForwardOutline,
  clipboardOutline,
  createOutline,
  gridOutline,
  homeOutline,
  logInOutline,
  peopleOutline,
  personAddOutline,
  personCircleOutline,
  settingsOutline,
} from 'ionicons/icons';
import { useRef, useState } from 'react';
import type { ReactElement } from 'react';
import { Link, matchPath, NavLink, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/useAuth';

import './SiteNavigation.css';

function NavigationLink({
  to,
  label,
  icon,
  end = false,
  onNavigate,
}: {
  to: string;
  label: string;
  icon: string;
  end?: boolean;
  onNavigate?: () => void;
}) {
  return (
    <NavLink to={to} end={end} onClick={onNavigate} aria-label={label} title={label}>
      <IonIcon icon={icon} aria-hidden="true" />
      <span className="site-nav-link-label">{label}</span>
    </NavLink>
  );
}

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
        <NavigationLink to="/home" label="Home" icon={homeOutline} end onNavigate={onNavigate} />
        <NavigationLink to="/events" label="Events &amp; calendar" icon={calendarOutline} onNavigate={onNavigate} />
        <NavigationLink to="/associations" label="Associations" icon={peopleOutline} onNavigate={onNavigate} />
        <NavigationLink to="/research" label="Share feedback" icon={createOutline} onNavigate={onNavigate} />
      </div>
      {workspace && (
        <div className="site-nav-section">
          <span className="site-nav-label">{workspace.associationName}</span>
          <NavigationLink to={base} label="Workspace overview" icon={gridOutline} end onNavigate={onNavigate} />
          <NavigationLink
            to={`${base}/projects`}
            label="Projects &amp; tasks"
            icon={clipboardOutline}
            onNavigate={onNavigate}
          />
          <NavigationLink to={`${base}/events`} label="Manage events" icon={calendarOutline} onNavigate={onNavigate} />
          <NavigationLink to={`${base}/messages`} label="Messages" icon={chatbubblesOutline} onNavigate={onNavigate} />
        </div>
      )}
      {user && user.memberships.length > 0 && (
        <div className="site-nav-section">
          <span className="site-nav-label">Your workspaces</span>
          {user.memberships.map((membership) => (
            <NavigationLink
              key={membership.associationId}
              to={`/workspaces/${encodeURIComponent(membership.associationSlug)}`}
              label={membership.associationName}
              icon={businessOutline}
              end
              onNavigate={onNavigate}
            />
          ))}
        </div>
      )}
      {user?.platformAdmin && (
        <div className="site-nav-section">
          <span className="site-nav-label">Platform administration</span>
          <NavigationLink
            to="/admin/associations"
            label="Manage associations"
            icon={settingsOutline}
            onNavigate={onNavigate}
          />
          <NavigationLink
            to="/admin/research"
            label="Research responses"
            icon={clipboardOutline}
            onNavigate={onNavigate}
          />
        </div>
      )}
      <div className="site-nav-section site-nav-account">
        <span className="site-nav-label">Account</span>
        {user ? (
          <NavigationLink to="/account" label="My account" icon={personCircleOutline} onNavigate={onNavigate} />
        ) : (
          <>
            <NavigationLink to="/login" label="Sign in" icon={logInOutline} onNavigate={onNavigate} />
            <NavigationLink to="/register" label="Create account" icon={personAddOutline} onNavigate={onNavigate} />
          </>
        )}
      </div>
    </>
  );
}

export default function SiteNavigation(): ReactElement | null {
  const [collapsed, setCollapsed] = useState(false);
  const details = useRef<HTMLDetailsElement>(null);
  const location = useLocation();

  function closeMenu() {
    if (!details.current) return;
    details.current.open = false;
    details.current.querySelector('summary')?.focus();
  }

  if (matchPath('/login', location.pathname) || matchPath('/register', location.pathname)) return null;

  return (
    <>
      <a className="site-skip-link" href={`${location.pathname}${location.search}#site-content`}>
        Skip to content
      </a>
      <aside className={`site-sidebar${collapsed ? ' site-sidebar-collapsed' : ''}`}>
        <Link className="site-nav-brand" to="/home" aria-label="AIssociation home">
          <img
            src={collapsed ? '/brand/symbol-ink.svg' : '/brand/logo-ink.svg'}
            alt="AIssociation"
            width="200"
            height="23"
          />
        </Link>
        <button
          type="button"
          className="site-sidebar-toggle"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-expanded={!collapsed}
          aria-controls="site-desktop-navigation"
          onClick={() => setCollapsed(!collapsed)}
        >
          <IonIcon icon={collapsed ? chevronForwardOutline : chevronBackOutline} aria-hidden="true" />
          <span className="site-nav-link-label">Collapse sidebar</span>
        </button>
        <nav id="site-desktop-navigation" aria-label="Main navigation">
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
