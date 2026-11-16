import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import EventWorkspace from './EventWorkspace';

function view(role = 'ADMIN', canManage = true) {
  const event = {
    id: 'e1',
    title: 'Workshop',
    description: 'Description',
    location: 'Hall',
    startsAt: '2027-01-01T12:00:00Z',
    endsAt: '2027-01-01T13:00:00Z',
    registrationDeadline: '2027-01-01T11:00:00Z',
    timeZone: 'Europe/Bucharest',
    capacity: null,
    status: 'DRAFT',
    association: { id: 'a1', name: 'Alpha', slug: 'alpha' },
  };
  const request = vi.fn().mockImplementation((path: string, options?: RequestInit) => {
    if (options)
      return Promise.resolve({
        event: { ...event, status: path.endsWith('/publish') ? 'PUBLISHED' : 'DRAFT' },
        projectId: null,
        updatedAt: 'now',
      });
    if (path.startsWith('/api/workspaces/'))
      return Promise.resolve({ associationId: 'a1', name: 'Alpha', slug: 'alpha', role });
    if (path.endsWith('/projects')) return Promise.resolve([{ id: 'p1', name: 'Project', canManage }]);
    if (path.startsWith('/api/public/')) return Promise.resolve({ description: '', website: null });
    return Promise.resolve([]);
  });
  const auth: AuthState = {
    user: { id: 'u1', displayName: 'Student', email: 'student@example.test', platformAdmin: false, memberships: [] },
    request,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
  };
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/workspaces/alpha/events']}>
        <Routes>
          <Route path="/workspaces/:slug/events" element={<EventWorkspace />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  return { request, event };
}
describe('event workspace FR-10', () => {
  it('requires confirmation and removes a deleted event only after server success', async () => {
    const { request, event } = view();
    await screen.findByRole('button', { name: 'Create event' });
    const original = request.getMockImplementation()!;
    request.mockImplementation((path: string, options?: RequestInit) => {
      if (options?.method === 'DELETE') return Promise.resolve(undefined);
      if (path === '/api/associations/a1/events')
        return Promise.resolve([{ event, projectId: null, updatedAt: 'now' }]);
      return original(path, options);
    });
    fireEvent.click(screen.getByRole('button', { name: 'Refresh events' }));
    const remove = await screen.findByRole('button', { name: 'Delete Workshop' });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    try {
      fireEvent.click(remove);
      expect(request).not.toHaveBeenCalledWith('/api/associations/a1/events/e1', { method: 'DELETE' });
      confirm.mockReturnValue(true);
      fireEvent.click(remove);
      expect(await screen.findByRole('status')).toHaveTextContent('Event deleted.');
      expect(screen.queryByRole('heading', { name: 'Workshop' })).not.toBeInTheDocument();
      expect(request).toHaveBeenCalledWith('/api/associations/a1/events/e1', { method: 'DELETE' });
    } finally {
      confirm.mockRestore();
    }
  });
  it('creates a standalone draft with UTC timestamps then publishes directly', async () => {
    const { request } = view();
    fireEvent.click(await screen.findByRole('button', { name: 'Create event' }));
    for (const [label, value] of [
      ['Title', 'Workshop'],
      ['Description', 'Description'],
      ['Location', 'Hall'],
      ['Start (UTC)', '2027-01-01T12:00'],
      ['End (UTC)', '2027-01-01T13:00'],
      ['Registration deadline (UTC)', '2027-01-01T11:00'],
    ]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole('button', { name: 'Save event' }));
    await screen.findByRole('button', { name: 'Publish Workshop' });
    const createCall = request.mock.calls.find(([, options]) => options?.method === 'POST');
    expect(JSON.parse(createCall?.[1].body)).toMatchObject({
      projectId: null,
      capacity: null,
      startsAt: '2027-01-01T12:00:00.000Z',
      registrationDeadline: '2027-01-01T11:00:00.000Z',
    });
    fireEvent.click(screen.getByRole('button', { name: 'Publish Workshop' }));
    expect(await screen.findByRole('link', { name: 'View public event' })).toHaveAttribute('href', '/events/e1');
    expect(request).toHaveBeenCalledWith('/api/associations/a1/events/e1/publish', { method: 'POST' });
  });
  it('allows a project manager to select only their permitted projects, with no profile editing', async () => {
    view('MEMBER');
    fireEvent.click(await screen.findByRole('button', { name: 'Create event' }));
    expect(screen.getByLabelText('Event belongs to')).toHaveValue('p1');
    expect(screen.queryByRole('option', { name: 'Standalone association event' })).not.toBeInTheDocument();
    expect(screen.queryByText('Edit public association presentation')).not.toBeInTheDocument();
  });
  it('does not offer creation to an ordinary association member', async () => {
    view('MEMBER', false);
    await screen.findByText(/No events you can manage yet/);
    expect(screen.queryByRole('button', { name: 'Create event' })).not.toBeInTheDocument();
  });
  it('shows a rejected save without claiming success', async () => {
    const { request } = view();
    fireEvent.click(await screen.findByRole('button', { name: 'Create event' }));
    for (const [label, value] of [
      ['Title', 'Workshop'],
      ['Description', 'Description'],
      ['Location', 'Hall'],
      ['Start (UTC)', '2027-01-01T12:00'],
      ['End (UTC)', '2027-01-01T13:00'],
      ['Registration deadline (UTC)', '2027-01-01T11:00'],
    ])
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    request.mockRejectedValueOnce(new ApiError(403, 'ASSOCIATION_ADMIN_REQUIRED', 'Permission changed.'));
    fireEvent.submit(screen.getByRole('button', { name: 'Save event' }).closest('form')!);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Permission changed.'));
    expect(screen.queryByText('Event saved.')).not.toBeInTheDocument();
  });
});
