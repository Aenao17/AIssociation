import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import ProjectTasksPage from './ProjectTasksPage';
import type { AssigneeOption, Task } from './types';

const base = '/api/associations/a1/projects/p1';

function makeTask(changes: Partial<Task> = {}): Task {
  return {
    id: 't1',
    associationId: 'a1',
    projectId: 'p1',
    projectName: 'Community fair',
    title: 'Draw poster',
    description: 'A3 poster',
    dueAt: '2099-01-01T12:00:00Z',
    status: 'TODO',
    assigneeUserId: 'u2',
    assigneeName: 'Bianca',
    createdByUserId: 'u1',
    createdByName: 'Alex',
    createdAt: '2026-11-16T10:00:00Z',
    updatedAt: '2026-11-16T10:00:00Z',
    canManage: false,
    canChangeStatus: true,
    ...changes,
  };
}

const bianca: AssigneeOption = {
  userId: 'u2',
  displayName: 'Bianca',
  projectRole: 'MEMBER',
  departmentName: 'Design',
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

function mockApi(auth: AuthState, tasks: Task[], options: AssigneeOption[]) {
  vi.mocked(auth.request).mockImplementation(async (path, init) => {
    if (init?.method) return undefined;
    if (path === '/api/workspaces/assoc-a')
      return { associationId: 'a1', name: 'Association A', role: 'MEMBER', slug: 'assoc-a' };
    if (path === base) return { project: { id: 'p1', name: 'Community fair' } };
    if (path === `${base}/tasks`) return tasks;
    if (path === `${base}/tasks/assignee-options`) return options;
    return [];
  });
}

function view(auth: AuthState) {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/workspaces/assoc-a/projects/p1/tasks']}>
        <Routes>
          <Route path="/workspaces/:slug/projects/:projectId/tasks" element={<ProjectTasksPage />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

function calls(auth: AuthState, method: string) {
  return vi.mocked(auth.request).mock.calls.filter((call) => call[1]?.method === method);
}

describe('project tasks', () => {
  it('lets a member change the status of their own task without manager controls', async () => {
    const auth = state();
    mockApi(auth, [makeTask()], []);
    view(auth);
    await screen.findByRole('heading', { name: 'Draw poster' });
    expect(screen.queryByRole('heading', { name: 'New task' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'IN_PROGRESS' } });
    await waitFor(() => expect(calls(auth, 'PATCH')).toHaveLength(1));
    expect(calls(auth, 'PATCH')[0][0]).toBe(`${base}/tasks/t1/status`);
    expect(JSON.parse(calls(auth, 'PATCH')[0][1]?.body as string)).toEqual({ status: 'IN_PROGRESS' });
    expect(await screen.findByText('Status changed to In progress.')).toBeInTheDocument();
  });

  it('groups tasks by status', async () => {
    const auth = state();
    mockApi(auth, [makeTask(), makeTask({ id: 't2', title: 'Book room', status: 'DONE' })], []);
    view(auth);
    expect(await screen.findByRole('heading', { name: 'To do (1)' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'In progress (0)' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Done (1)' })).toBeInTheDocument();
  });

  it('lets a manager create a task with an ISO deadline', async () => {
    const auth = state();
    mockApi(auth, [], [bianca]);
    view(auth);
    await screen.findByRole('heading', { name: 'New task' });
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Print flyers' } });
    fireEvent.change(screen.getByLabelText('Assignee'), { target: { value: 'u2' } });
    fireEvent.change(screen.getByLabelText('Deadline'), { target: { value: '2099-02-01T10:30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create task' }));

    await waitFor(() => expect(calls(auth, 'POST')).toHaveLength(1));
    const sent = JSON.parse(calls(auth, 'POST')[0][1]?.body as string);
    expect(calls(auth, 'POST')[0][0]).toBe(`${base}/tasks`);
    expect(sent.title).toBe('Print flyers');
    expect(sent.assigneeUserId).toBe('u2');
    expect(sent.dueAt).toBe(new Date('2099-02-01T10:30').toISOString());
  });

  it('lets a manager edit a task and asks for confirmation before deleting', async () => {
    const auth = state();
    mockApi(auth, [makeTask({ canManage: true })], [bianca]);
    view(auth);
    await screen.findByRole('heading', { name: 'Draw poster' });

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const column = within(screen.getByRole('region', { name: 'To do (1)' }));
    fireEvent.change(column.getByLabelText('Title'), { target: { value: 'Draw two posters' } });
    fireEvent.click(column.getByRole('button', { name: 'Save task' }));
    await waitFor(() => expect(calls(auth, 'PUT')).toHaveLength(1));
    expect(JSON.parse(calls(auth, 'PUT')[0][1]?.body as string).title).toBe('Draw two posters');

    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    expect(calls(auth, 'DELETE')).toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await waitFor(() => expect(calls(auth, 'DELETE')).toHaveLength(1));
    expect(calls(auth, 'DELETE')[0][0]).toBe(`${base}/tasks/t1`);
  });

  it('shows the server message when access is denied', async () => {
    const auth = state();
    vi.mocked(auth.request).mockImplementation(async (path) => {
      if (path === '/api/workspaces/assoc-a')
        return { associationId: 'a1', name: 'Association A', role: 'MEMBER', slug: 'assoc-a' };
      throw new ApiError(403, 'PROJECT_TEAM_REQUIRED', 'Project team membership is required.');
    });
    view(auth);
    expect(await screen.findByRole('alert')).toHaveTextContent('Project team membership is required.');
  });

  it('shows a server validation error and keeps the form', async () => {
    const auth = state();
    mockApi(auth, [], [bianca]);
    vi.mocked(auth.request).mockImplementation(async (path, init) => {
      if (init?.method === 'POST') throw new ApiError(400, 'INVALID_DEADLINE', 'Choose a future deadline.');
      if (path === '/api/workspaces/assoc-a')
        return { associationId: 'a1', name: 'Association A', role: 'MEMBER', slug: 'assoc-a' };
      if (path === base) return { project: { id: 'p1', name: 'Community fair' } };
      if (path === `${base}/tasks/assignee-options`) return [bianca];
      return [];
    });
    view(auth);
    await screen.findByRole('heading', { name: 'New task' });
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Late' } });
    fireEvent.change(screen.getByLabelText('Assignee'), { target: { value: 'u2' } });
    fireEvent.change(screen.getByLabelText('Deadline'), { target: { value: '2000-01-01T10:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create task' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a future deadline.');
    expect(screen.getByLabelText('Title')).toHaveValue('Late');
  });
});
