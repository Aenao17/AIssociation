import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PublicAssociationsPage from './PublicAssociationsPage';

vi.mock('../events/EventDiscovery', () => ({ default: () => null }));

afterEach(() => vi.unstubAllGlobals());

function respond(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function view(path = '/associations') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/associations" element={<PublicAssociationsPage />} />
        <Route path="/associations/:slug" element={<PublicAssociationsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('public association discovery (FR-11)', () => {
  it('allows anonymous discovery and opens a profile through its public API', async () => {
    const association = { id: 'a1', name: 'Student Society', slug: 'student-society' };
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(respond([association]))
      .mockResolvedValueOnce(respond(association));
    vi.stubGlobal('fetch', fetcher);
    view();
    fireEvent.click(await screen.findByRole('link', { name: association.name }));
    expect(await screen.findByRole('heading', { name: association.name })).toBeInTheDocument();
    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/public/associations',
      '/api/public/associations/student-society',
    ]);
    for (const [, options] of fetcher.mock.calls) {
      expect(options.headers.has('Authorization')).toBe(false);
      expect(options.credentials).toBe('omit');
    }
    expect(screen.queryByRole('button', { name: /member|edit|publish/i })).not.toBeInTheDocument();
  });

  it('loads a profile when opened directly without a session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ id: 'a1', name: 'Direct Society', slug: 'direct' })));
    view('/associations/direct');
    expect(await screen.findByRole('heading', { name: 'Direct Society' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'All associations' })).toHaveAttribute('href', '/associations');
  });

  it('distinguishes an empty directory from a failed request and retries a failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new TypeError('network')).mockResolvedValueOnce(respond([])));
    view();
    expect(await screen.findByRole('alert')).toHaveTextContent('could not load');
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(/No associations are listed yet/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows a missing-profile message and directory navigation for an unknown slug', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ code: 'ASSOCIATION_NOT_FOUND' }, 404)));
    view('/associations/missing');
    expect(await screen.findByRole('alert')).toHaveTextContent('Association not found');
    expect(screen.getByRole('link', { name: 'All associations' })).toBeInTheDocument();
  });

  it('clears the old profile while the directory is loading and allows opening another profile', async () => {
    let resolveDirectory!: (value: Response) => void;
    const directory = new Promise<Response>((resolve) => {
      resolveDirectory = resolve;
    });
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(respond({ id: 'a1', name: 'Original', slug: 'original' }))
      .mockImplementationOnce(() => directory)
      .mockResolvedValueOnce(respond({ id: 'a2', name: 'Current', slug: 'current' }));
    vi.stubGlobal('fetch', fetcher);
    view('/associations/original');
    await screen.findByRole('heading', { name: 'Original' });
    fireEvent.click(screen.getByRole('link', { name: 'All associations' }));
    expect(screen.queryByRole('heading', { name: 'Original' })).not.toBeInTheDocument();
    resolveDirectory(respond([{ id: 'a2', name: 'Current', slug: 'current' }]));
    fireEvent.click(await screen.findByRole('link', { name: 'Current' }));
    expect(await screen.findByRole('heading', { name: 'Current' })).toBeInTheDocument();
  });
});
