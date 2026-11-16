import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import ProjectPages from './ProjectPages';
import type { ProjectOverview, TeamMember } from './types';

const base = '/api/associations/a1/projects/p1';
const overview = (manager = false): ProjectOverview => ({
  project: {
    id: 'p1',
    associationId: 'a1',
    name: 'Community fair',
    description: 'Our project',
    recruitmentDeadline: '2099-01-01T12:00:00Z',
    canManage: manager,
    canOpen: manager,
  },
  recruitmentDescription: 'Join our team',
  recruitmentDeadline: '2099-01-01T12:00:00Z',
  positions: [{ id: 'pos1', departmentId: 'd1', departmentName: 'Outreach', roleLabel: 'Volunteer' }],
  associationAdmin: false,
});
const manager: TeamMember = {
  id: 'tm1',
  userId: 'u1',
  displayName: 'Alex',
  projectRole: 'PROJECT_MANAGER',
  departmentId: null,
  departmentName: null,
  positionId: null,
  positionLabel: null,
  reportingParentMembershipId: null,
  active: true,
};

function state(): AuthState {
  return {
    user: { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin: false },
    request: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    refreshUser: vi.fn(),
  };
}
function view(auth: AuthState, path = '/workspaces/assoc-a/projects/p1') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/workspaces/:slug/projects" element={<ProjectPages />} />
          <Route path="/workspaces/:slug/projects/:projectId" element={<ProjectPages />} />
          <Route path="/login" element={<p>Sign in first</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}
function requests(auth: AuthState, manage = false) {
  vi.mocked(auth.request).mockImplementation(async (path) => {
    if (path === '/api/workspaces/assoc-a')
      return { associationId: 'a1', name: 'Association A', role: 'MEMBER', slug: 'assoc-a' };
    if (path === `${base}/recruitment`) return overview(manage);
    if (path === `${base}/team`) return [manager];
    return [];
  });
}

describe('projects and recruitment', () => {
  it('lets an administrator edit details and reports a blocked deletion without navigating away', async () => {
    const auth = state();
    requests(auth);
    const original = vi.mocked(auth.request).getMockImplementation()!;
    vi.mocked(auth.request).mockImplementation(async (path, options) => {
      if (path === `${base}/recruitment`) return { ...overview(), associationAdmin: true };
      if (options?.method === 'DELETE') throw new ApiError(409, 'PROJECT_IN_USE', 'Project has applications.');
      return original(path, options);
    });
    view(auth);
    await screen.findByRole('heading', { name: 'Edit project details' });
    fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'Edited project' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save project' }));
    await waitFor(() =>
      expect(auth.request).toHaveBeenCalledWith(base, {
        method: 'PATCH',
        body: JSON.stringify({ name: 'Edited project', description: 'Our project' }),
      }),
    );
    await waitFor(() => expect(screen.getByRole('button', { name: 'Delete project' })).not.toBeDisabled());
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    try {
      fireEvent.click(screen.getByRole('button', { name: 'Delete project' }));
      expect(auth.request).not.toHaveBeenCalledWith(base, { method: 'DELETE' });
      confirm.mockReturnValue(true);
      fireEvent.click(screen.getByRole('button', { name: 'Delete project' }));
      expect(await screen.findByRole('alert')).toHaveTextContent('Project has applications.');
      expect(screen.getByRole('heading', { name: 'Community fair' })).toBeInTheDocument();
    } finally {
      confirm.mockRestore();
    }
  });

  it('shows recruitment to association members without requesting private team or review data', async () => {
    const auth = state();
    requests(auth);
    view(auth);
    await screen.findByRole('heading', { name: 'Community fair' });
    expect(screen.getByRole('button', { name: 'Submit application' })).toBeInTheDocument();
    expect(auth.request).not.toHaveBeenCalledWith(`${base}/team`);
    expect(auth.request).not.toHaveBeenCalledWith(`${base}/applications`);
    expect(screen.queryByRole('heading', { name: 'Project configuration' })).not.toBeInTheDocument();
  });

  it('submits only the selected position and motivation, retaining a rejected application form', async () => {
    const auth = state();
    requests(auth);
    const original = vi.mocked(auth.request).getMockImplementation()!;
    vi.mocked(auth.request).mockImplementation(async (path, options) => {
      if (path === `${base}/applications` && options?.method === 'POST')
        throw new ApiError(409, 'APPLICATION_EXISTS', 'Already applied.');
      return original(path, options);
    });
    view(auth);
    await screen.findByRole('button', { name: 'Submit application' });
    fireEvent.change(screen.getByLabelText('Position'), { target: { value: 'pos1' } });
    fireEvent.change(screen.getByLabelText('Motivation'), { target: { value: 'I can help with outreach.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Already applied.');
    expect(auth.request).toHaveBeenCalledWith(`${base}/applications`, {
      method: 'POST',
      body: JSON.stringify({ positionId: 'pos1', motivation: 'I can help with outreach.' }),
    });
    expect(screen.getByLabelText('Motivation')).toHaveValue('I can help with outreach.');
  });

  it('accepts an applicant with an explicit reporting parent and refreshes the team', async () => {
    const auth = state();
    requests(auth, true);
    const original = vi.mocked(auth.request).getMockImplementation()!;
    let accepted = false;
    vi.mocked(auth.request).mockImplementation(async (path, options) => {
      if (path === `${base}/applications/app1/accept`) {
        accepted = true;
        return {};
      }
      if (path === `${base}/applications`)
        return [
          {
            id: 'app1',
            positionId: 'pos1',
            positionLabel: 'Volunteer',
            departmentName: 'Outreach',
            userId: 'u2',
            displayName: 'Sam',
            motivation: 'I volunteer',
            status: accepted ? 'ACCEPTED' : 'PENDING',
            createdAt: '2026-11-16T00:00:00Z',
            reviewedAt: null,
          },
        ];
      return original(path, options);
    });
    view(auth);
    const accept = await screen.findByRole('button', { name: 'Accept application' });
    expect(accept).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Reporting manager for Sam'), { target: { value: 'tm1' } });
    fireEvent.click(accept);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Application accepted.'));
    expect(auth.request).toHaveBeenCalledWith(`${base}/applications/app1/accept`, {
      method: 'POST',
      body: JSON.stringify({ reportingParentMembershipId: 'tm1' }),
    });
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Accept application' })).not.toBeInTheDocument());
  });

  it('clears private project information when refreshed access is revoked', async () => {
    const auth = state();
    requests(auth, true);
    view(auth);
    await screen.findByRole('heading', { name: 'Project configuration' });
    vi.mocked(auth.request).mockRejectedValue(new ApiError(403, 'MEMBERSHIP_REQUIRED', 'Access revoked.'));
    fireEvent.click(screen.getByRole('button', { name: 'Refresh project' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Access revoked.');
    expect(screen.queryByRole('heading', { name: 'Project configuration' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Project team & reporting' })).not.toBeInTheDocument();
  });

  it('creates a project with account UUID appointments from the admin workspace', async () => {
    const auth = state();
    vi.mocked(auth.request).mockImplementation(async (path, options) => {
      if (path === '/api/workspaces/assoc-a')
        return { associationId: 'a1', name: 'Association A', role: 'ADMIN', slug: 'assoc-a' };
      if (path === '/api/associations/a1/members')
        return [{ id: 'association-member-id', userId: 'u2', displayName: 'Sam' }];
      if (path === '/api/associations/a1/projects' && options?.method === 'POST') return overview();
      if (path === `${base}/recruitment`) return { ...overview(), associationAdmin: true };
      return [];
    });
    view(auth, '/workspaces/assoc-a/projects');
    await screen.findByRole('heading', { name: 'Create a project' });
    fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'Community fair' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Our project' } });
    const select = screen.getByLabelText('Project managers') as HTMLSelectElement;
    select.options[0].selected = true;
    fireEvent.change(select);
    fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
    await screen.findByRole('heading', { name: 'Community fair' });
    expect(auth.request).toHaveBeenCalledWith('/api/associations/a1/projects', {
      method: 'POST',
      body: JSON.stringify({ name: 'Community fair', description: 'Our project', managerUserIds: ['u2'] }),
    });
  });

  it('sends unauthenticated visitors to sign in without making project requests', async () => {
    const auth = state();
    auth.user = null;
    view(auth);
    expect(await screen.findByText('Sign in first')).toBeInTheDocument();
    expect(auth.request).not.toHaveBeenCalled();
  });
});
