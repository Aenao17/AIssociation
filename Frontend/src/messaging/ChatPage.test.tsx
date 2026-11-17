import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext } from '../auth/AuthContext';
import type { AuthState } from '../auth/AuthContext';
import { ApiError } from '../auth/client';

import ChatPage from './ChatPage';
import MessageThread from './MessageThread';
import { mergeMessages } from './types';
import type { Conversation, Message, MessagePage, Participant } from './types';

const base = '/api/associations/a1/conversations';

function conversation(changes: Partial<Conversation> = {}): Conversation {
  return {
    id: 'c1',
    associationId: 'a1',
    type: 'ASSOCIATION',
    title: 'Alpha',
    projectId: null,
    projectName: null,
    departmentId: null,
    departmentName: null,
    createdByUserId: null,
    canManageMembers: false,
    lastMessage: null,
    ...changes,
  };
}

function message(seq: number, changes: Partial<Message> = {}): Message {
  return {
    id: `m${seq}`,
    seq,
    conversationId: 'c1',
    senderUserId: 'u2',
    senderName: 'Bianca',
    body: `Message ${seq}`,
    createdAt: '2026-11-16T10:00:00Z',
    mine: false,
    ...changes,
  };
}

function page(messages: Message[], hasMore = false): MessagePage {
  return { messages, hasMore };
}

function auth(request: AuthState['request'], signedOut = false): AuthState {
  return {
    user: signedOut
      ? null
      : { id: 'u1', displayName: 'Alex', email: 'alex@example.test', memberships: [], platformAdmin: false },
    request,
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    refreshUser: vi.fn(),
  };
}

interface Server {
  conversations: Conversation[];
  messages: Record<string, MessagePage>;
  contacts: Participant[];
  members: Record<string, Participant[]>;
  mutate?: (path: string, init: RequestInit) => unknown;
}

function fakeApi(server: Server) {
  const request = vi.fn();
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === '/api/workspaces/alpha') return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'MEMBER' };
    if (!path.startsWith(`${base}`)) throw new Error(`Unexpected request outside association a1: ${path}`);
    const rest = path.slice(base.length);
    if (init?.method && init.method !== 'GET') {
      if (server.mutate) return server.mutate(rest, init);
      return undefined;
    }
    if (rest === '') return server.conversations;
    if (rest === '/contacts') return server.contacts;
    const members = /^\/([^/]+)\/members$/.exec(rest);
    if (members) return server.members[members[1]] ?? [];
    const thread = /^\/([^/?]+)\/messages/.exec(rest);
    if (thread) return server.messages[thread[1]] ?? page([]);
    throw new Error(`Unhandled ${path}`);
  });
  return request;
}

function show(state: AuthState) {
  return render(
    <AuthContext.Provider value={state}>
      <MemoryRouter initialEntries={['/workspaces/alpha/chat']}>
        <Routes>
          <Route path="/workspaces/:slug/chat" element={<ChatPage />} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

const sample: Server = {
  conversations: [
    conversation({ lastMessage: message(2, { body: 'See you at ten' }) }),
    conversation({ id: 'c2', type: 'PROJECT', title: 'Spring Fair', projectName: 'Spring Fair' }),
    conversation({ id: 'c3', type: 'DEPARTMENT', title: 'Spring Fair · Logistics', departmentName: 'Logistics' }),
    conversation({ id: 'c4', type: 'GROUP', title: 'Banner crew', createdByUserId: 'u1', canManageMembers: true }),
    conversation({ id: 'c5', type: 'DIRECT', title: 'Bianca' }),
  ],
  messages: { c1: page([message(1), message(2, { body: 'See you at ten' })]) },
  contacts: [
    { userId: 'u2', displayName: 'Bianca' },
    { userId: 'u3', displayName: 'Dan' },
  ],
  members: { c4: [{ userId: 'u1', displayName: 'Alex' }] },
};

function server(changes: Partial<Server> = {}): Server {
  return { ...sample, ...changes };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ChatPage', () => {
  it('lists every conversation type in its own section and only ever calls association a1', async () => {
    const request = fakeApi(server());
    show(auth(request));
    expect(await screen.findByRole('button', { name: /Alpha/ })).toBeInTheDocument();
    for (const label of ['Association', 'Projects', 'Departments', 'Groups', 'Direct messages']) {
      expect(screen.getByRole('region', { name: label })).toBeInTheDocument();
    }
    expect(screen.getByText('Bianca: See you at ten')).toBeInTheDocument();
    for (const [path] of request.mock.calls) {
      expect(path === '/api/workspaces/alpha' || String(path).startsWith('/api/associations/a1/')).toBe(true);
    }
  });

  it('shows an empty state and a retry on failure', async () => {
    const request = vi.fn();
    request.mockImplementation(async (path: string) => {
      if (path === '/api/workspaces/alpha')
        return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'MEMBER' };
      throw new TypeError('offline');
    });
    show(auth(request));
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to reach the server');
    request.mockImplementation(async (path: string) =>
      path === '/api/workspaces/alpha' ? { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'MEMBER' } : [],
    );
    fireEvent.click(screen.getByRole('button', { name: 'Retry messages list' }));
    expect(await screen.findByText(/No conversations yet/)).toBeInTheDocument();
  });

  it('opens a conversation and shows its messages, marking my own', async () => {
    show(auth(fakeApi(server({ messages: { c1: page([message(1), message(2, { mine: true, body: 'Mine' })]) } }))));
    fireEvent.click(await screen.findByRole('button', { name: /Alpha/ }));
    const log = await screen.findByRole('log', { name: 'Messages' });
    expect(within(log).getByText('Message 1')).toBeInTheDocument();
    expect(within(log).getByText('You')).toBeInTheDocument();
    expect(within(log).getByText('Mine')).toBeInTheDocument();
  });

  it('shows an empty thread prompt', async () => {
    show(auth(fakeApi(server({ messages: { c1: page([]) } }))));
    fireEvent.click(await screen.findByRole('button', { name: /Alpha/ }));
    expect(await screen.findByText('No messages yet. Say hello.')).toBeInTheDocument();
  });

  it('sends a message, keeps it once, and updates the list preview', async () => {
    const sent = message(3, { mine: true, body: 'Hello team', senderName: 'Alex' });
    const request = fakeApi(server({ mutate: () => sent }));
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: /Alpha/ }));
    await screen.findByRole('log');
    const send = screen.getByRole('button', { name: 'Send' });
    expect(send).toBeDisabled(); 
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: '  Hello team  ' } });
    fireEvent.click(send);
    expect(await within(screen.getByRole('log')).findByText('Hello team')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(`${base}/c1/messages`, {
      method: 'POST',
      body: JSON.stringify({ body: 'Hello team' }),
    });
    expect(screen.getByLabelText('Message')).toHaveValue('');
    expect(screen.getByText('You: Hello team')).toBeInTheDocument();
  });

  it('keeps the draft and shows an error when sending fails', async () => {
    show(
      auth(
        fakeApi(
          server({
            mutate: () => {
              throw new ApiError(400, 'VALIDATION_ERROR', 'bad');
            },
          }),
        ),
      ),
    );
    fireEvent.click(await screen.findByRole('button', { name: /Alpha/ }));
    await screen.findByRole('log');
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Draft text' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('must not be empty or too long');
    expect(screen.getByLabelText('Message')).toHaveValue('Draft text');
  });

  it('loses a conversation gracefully when the server says it is no longer available', async () => {
    const request = fakeApi(server());
    request.mockImplementation(async (path: string, init?: RequestInit) => {
      if (path === '/api/workspaces/alpha')
        return { associationId: 'a1', name: 'Alpha', slug: 'alpha', role: 'MEMBER' };
      if (path.endsWith('/messages')) throw new ApiError(404, 'CONVERSATION_NOT_FOUND', 'gone');
      return init?.method ? undefined : sample.conversations;
    });
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: /Alpha/ }));
    expect(await screen.findByText('That conversation is no longer available to you.')).toBeInTheDocument();
    expect(screen.getByText('Select a conversation to read and write messages.')).toBeInTheDocument();
  });

  it('creates a custom group from this association’s contacts and opens it', async () => {
    const created = conversation({
      id: 'c9',
      type: 'GROUP',
      title: 'Poster crew',
      createdByUserId: 'u1',
      canManageMembers: true,
    });
    const request = fakeApi(server({ mutate: () => created }));
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: 'New group' }));
    const dialog = await screen.findByRole('dialog', { name: 'New group' });
    fireEvent.click(await within(dialog).findByLabelText('Dan'));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create group' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Give the group a name.');
    fireEvent.change(within(dialog).getByLabelText('Group name'), { target: { value: ' Poster crew ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create group' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(request).toHaveBeenCalledWith(`${base}/groups`, {
      method: 'POST',
      body: JSON.stringify({ name: 'Poster crew', memberUserIds: ['u3'] }),
    });
    expect(await screen.findByRole('heading', { name: 'Poster crew' })).toBeInTheDocument();
  });

  it('opens a direct conversation with a chosen member', async () => {
    const direct = conversation({ id: 'c8', type: 'DIRECT', title: 'Dan' });
    const request = fakeApi(server({ mutate: () => direct }));
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: 'New direct message' }));
    const dialog = await screen.findByRole('dialog', { name: 'New direct message' });
    expect(within(dialog).getByRole('button', { name: 'Start conversation' })).toBeDisabled();
    await within(dialog).findByRole('option', { name: 'Dan' });
    fireEvent.change(within(dialog).getByLabelText('Person'), { target: { value: 'u3' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Start conversation' }));
    expect(await screen.findByRole('heading', { name: 'Dan' })).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(`${base}/direct`, { method: 'POST', body: JSON.stringify({ userId: 'u3' }) });
  });

  it('lets a group creator add and remove members', async () => {
    const request = fakeApi(
      server({
        mutate: (path, init) =>
          init.method === 'PUT'
            ? [
                { userId: 'u1', displayName: 'Alex' },
                { userId: 'u3', displayName: 'Dan' },
              ]
            : undefined,
      }),
    );
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: /Banner crew/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Members' }));
    const select = await screen.findByLabelText('Add a member');
    await within(select).findByRole('option', { name: 'Dan' });
    fireEvent.change(select, { target: { value: 'u3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add member' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Remove Dan' }));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Remove Dan' })).not.toBeInTheDocument());
    expect(request).toHaveBeenCalledWith(`${base}/c4/members/u3`, { method: 'PUT' });
    expect(request).toHaveBeenCalledWith(`${base}/c4/members/u3`, { method: 'DELETE' });
  });

  it('shows a non-creator no member controls, only Leave, and removes the group after leaving', async () => {
    const groups = [
      conversation({ id: 'c4', type: 'GROUP', title: 'Banner crew', createdByUserId: 'u2', canManageMembers: false }),
    ];
    const request = fakeApi(
      server({
        conversations: groups,
        members: {
          c4: [
            { userId: 'u1', displayName: 'Alex' },
            { userId: 'u2', displayName: 'Bianca' },
          ],
        },
      }),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    show(auth(request));
    fireEvent.click(await screen.findByRole('button', { name: /Banner crew/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Members' }));
    await screen.findByRole('list', { name: 'Members' });
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Add a member')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Leave group' }));
    expect(await screen.findByText('You left the group.')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(`${base}/c4/members/u1`, { method: 'DELETE' });
    expect(screen.queryByRole('button', { name: /Banner crew/ })).not.toBeInTheDocument();
  });

  it('sends signed-out visitors to the login page', () => {
    show(auth(vi.fn(), true));
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });
});

describe('MessageThread polling and paging', () => {
  function thread(request: AuthState['request'], pollMs = 20) {
    return render(
      <AuthContext.Provider value={auth(request)}>
        <MessageThread
          base={base}
          conversation={conversation()}
          onBack={vi.fn()}
          onUnavailable={vi.fn()}
          onLeft={vi.fn()}
          onSent={vi.fn()}
          pollMs={pollMs}
        />
      </AuthContext.Provider>,
    );
  }

  it('polls with the newest seq as cursor and shows only new messages once', async () => {
    const request = vi.fn();
    let served = false;
    request.mockImplementation(async (path: string) => {
      if (path.includes('after=')) {
        if (served) return page([]);
        served = true;
        return page([message(3, { body: 'Brand new' })]);
      }
      return page([message(1), message(2)]);
    });
    thread(request);
    expect(await screen.findByText('Brand new')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(`${base}/c1/messages?after=2`);
    await waitFor(() => expect(request.mock.calls.some(([path]) => String(path).endsWith('after=3'))).toBe(true));
    expect(screen.getAllByText('Brand new')).toHaveLength(1);
  });

  it('loads older messages before the oldest shown', async () => {
    const request = vi.fn();
    request.mockImplementation(async (path: string) => {
      if (path.includes('before=')) return page([message(1, { body: 'Ancient' })], false);
      if (path.includes('after=')) return page([]);
      return page([message(2), message(3)], true);
    });
    thread(request, 60000);
    fireEvent.click(await screen.findByRole('button', { name: 'Load older messages' }));
    expect(await screen.findByText('Ancient')).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(`${base}/c1/messages?before=2`);
    expect(screen.queryByRole('button', { name: 'Load older messages' })).not.toBeInTheDocument();
  });

  it('shows a load error with retry', async () => {
    const request = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValue(page([message(1)]));
    thread(request, 60000);
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to reach the server');
    fireEvent.click(screen.getByRole('button', { name: 'Retry messages' }));
    expect(await screen.findByText('Message 1')).toBeInTheDocument();
  });
});

describe('mergeMessages', () => {
  it('removes duplicates and keeps oldest-first order', () => {
    const merged = mergeMessages([message(1), message(3)], [message(2), message(3)]);
    expect(merged.map((item) => item.seq)).toEqual([1, 2, 3]);
  });
});