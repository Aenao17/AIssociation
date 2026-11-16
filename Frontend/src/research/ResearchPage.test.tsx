import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { apiRequest } from '../auth/client';
import type * as Client from '../auth/client';

import ResearchPage from './ResearchPage';

vi.mock('../auth/client', async (original) => ({
  ...(await original<typeof Client>()),
  apiRequest: vi.fn(),
}));

function view() {
  return render(
    <MemoryRouter>
      <ResearchPage />
    </MemoryRouter>,
  );
}

function fill() {
  fireEvent.click(screen.getByRole('radio', { name: 'Member' }));
  fireEvent.click(screen.getByRole('radio', { name: '21–50' }));
  for (const option of ['Open call', 'Meetings', 'Google Workspace', 'Finding information', 'Tasks']) {
    fireEvent.click(screen.getByRole('checkbox', { name: option }));
  }
}

beforeEach(() => {
  vi.mocked(apiRequest).mockReset();
});

describe('research questionnaire', () => {
  it('saves a public response without an account and shows confirmation', async () => {
    vi.mocked(apiRequest).mockResolvedValue(undefined);
    view();
    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Send response' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Thank you for sharing.');
    const [path, options] = vi.mocked(apiRequest).mock.calls[0];
    expect(path).toBe('/api/public/research/responses');
    const body = JSON.parse(options?.body as string);
    expect(body.answers.tasks).toEqual(['Meetings']);
    expect(body.answers.contactEmail).toBe('');
    expect(body.submissionId).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Send response' })).not.toBeInTheDocument();
  });

  it('preserves answers and the submission ID when a failed request is retried', async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(new TypeError('Network')).mockResolvedValueOnce(undefined);
    view();
    fill();
    fireEvent.change(screen.getByLabelText(/What important need/), { target: { value: 'Flexible teams' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send response' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Your answers are still here.');
    expect(screen.getByLabelText(/What important need/)).toHaveValue('Flexible teams');
    fireEvent.click(screen.getByRole('button', { name: 'Send response' }));
    await screen.findByRole('status');
    expect(vi.mocked(apiRequest).mock.calls[0][1]?.body).toBe(vi.mocked(apiRequest).mock.calls[1][1]?.body);
  });

  it('limits priorities to three and allows deselection', () => {
    view();
    const choices = within(
      screen.getByRole('group', { name: 'Which capabilities would be most useful to your association?' }),
    );
    for (const name of ['Tasks', 'Recruitment', 'Event publishing'])
      fireEvent.click(choices.getByRole('checkbox', { name }));
    expect(choices.getByRole('checkbox', { name: 'Attendance' })).toBeDisabled();
    fireEvent.click(choices.getByRole('checkbox', { name: 'Tasks' }));
    expect(choices.getByRole('checkbox', { name: 'Attendance' })).toBeEnabled();
  });

  it('requires a checkbox answer and blocks repeated clicks during submission', async () => {
    view();
    fireEvent.click(screen.getByRole('radio', { name: 'Member' }));
    fireEvent.click(screen.getByRole('radio', { name: '21–50' }));
    fireEvent.click(screen.getByRole('button', { name: 'Send response' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose at least one option');
    expect(apiRequest).not.toHaveBeenCalled();
    let finish: (value: unknown) => void = () => undefined;
    vi.mocked(apiRequest).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    fill();
    fireEvent.click(screen.getByRole('button', { name: 'Send response' }));
    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled();
    expect(apiRequest).toHaveBeenCalledTimes(1);
    finish(undefined);
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Thank you'));
  });
});
