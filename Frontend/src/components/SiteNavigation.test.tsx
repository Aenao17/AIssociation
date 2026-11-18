import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { CurrentUser } from '../auth/types';

import SiteNavigation from './SiteNavigation';

const member: CurrentUser = {
  id: 'test-user',
  displayName: 'Test member',
  email: 'member@example.test',
  platformAdmin: false,
  memberships: [
    { associationId: 'a', associationName: 'Alpha', associationSlug: 'alpha', role: 'MEMBER' },
    { associationId: 'b', associationName: 'Beta', associationSlug: 'beta', role: 'ADMIN' },
  ],
};

function view(user: CurrentUser | null = null, path = '/home') {
  return render(
    <AuthContext.Provider
      value={{ user, login: vi.fn(), register: vi.fn(), logout: vi.fn(), refreshUser: vi.fn(), request: vi.fn() }}
    >
      <MemoryRouter initialEntries={[path]}>
        <SiteNavigation />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

const desktop = () => within(screen.getByRole('navigation', { name: 'Main navigation' }));

describe('shared navigation', () => {
  it('offers public destinations and sign-in without exposing workspace or admin links', () => {
    view();
    expect(desktop().getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    expect(desktop().getByRole('link', { name: 'Share feedback' })).toHaveAttribute('href', '/research');
    expect(desktop().getByRole('link', { name: 'Sign in' })).toBeInTheDocument();
    expect(desktop().queryByText('Your workspaces')).not.toBeInTheDocument();
    expect(desktop().queryByRole('link', { name: 'Manage associations' })).not.toBeInTheDocument();
  });

  it('keeps workspace tools in the selected association and updates them when switching', () => {
    view(member, '/workspaces/alpha/projects/project-id/tasks');
    expect(desktop().getByRole('link', { name: 'Projects & tasks' })).toHaveAttribute('aria-current', 'page');
    expect(desktop().getByRole('link', { name: 'Messages' })).toHaveAttribute('href', '/workspaces/alpha/messages');
    expect(desktop().queryByRole('link', { name: 'Manage associations' })).not.toBeInTheDocument();
    fireEvent.click(desktop().getByRole('link', { name: 'Beta' }));
    expect(desktop().getByRole('link', { name: 'Messages' })).toHaveAttribute('href', '/workspaces/beta/messages');
    expect(desktop().getByRole('link', { name: 'Workspace overview' })).toHaveAttribute('aria-current', 'page');
  });

  it('shows platform administration only for a platform administrator', () => {
    view({ ...member, platformAdmin: true });
    expect(desktop().getByRole('link', { name: 'Manage associations' })).toHaveAttribute('href', '/admin/associations');
    expect(desktop().getByRole('link', { name: 'Research responses' })).toHaveAttribute('href', '/admin/research');
  });

  it('does not invent workspace tools for an association outside the current memberships', () => {
    view(member, '/workspaces/outsider/messages');
    expect(desktop().queryByRole('link', { name: 'Messages' })).not.toBeInTheDocument();
  });

  it('collapses to named links, keeps the state while navigating, and expands again', () => {
    const { container } = view();
    const collapse = screen.getByRole('button', { name: 'Collapse sidebar' });
    expect(collapse).toHaveAttribute('aria-expanded', 'true');
    fireEvent.click(collapse);
    expect(container.querySelector('.site-sidebar')).toHaveClass('site-sidebar-collapsed');
    const expand = screen.getByRole('button', { name: 'Expand sidebar' });
    expect(expand).toHaveAttribute('aria-expanded', 'false');
    const events = desktop().getByRole('link', { name: 'Events & calendar' });
    expect(events).toHaveAttribute('title', 'Events & calendar');
    fireEvent.click(events);
    expect(events).toHaveAttribute('aria-current', 'page');
    expect(container.querySelector('.site-sidebar')).toHaveClass('site-sidebar-collapsed');
    fireEvent.click(expand);
    expect(container.querySelector('.site-sidebar')).not.toHaveClass('site-sidebar-collapsed');
    expect(screen.getByRole('button', { name: 'Collapse sidebar' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('closes the mobile menu on Escape and after selecting a destination', () => {
    const { container } = view();
    const menu = container.querySelector('details')!;
    const summary = menu.querySelector('summary')!;
    menu.open = true;
    fireEvent.keyDown(summary, { key: 'Escape' });
    expect(menu.open).toBe(false);
    expect(summary).toHaveFocus();
    menu.open = true;
    fireEvent.click(
      within(screen.getByRole('navigation', { name: 'Mobile navigation' })).getByRole('link', {
        name: 'Events & calendar',
      }),
    );
    expect(container.querySelector('details')).not.toHaveAttribute('open');
    expect(desktop().getByRole('link', { name: 'Events & calendar' })).toHaveAttribute('aria-current', 'page');
  });
});
