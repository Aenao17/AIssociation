import { expect, test } from '@playwright/test';

// Explicit opt-in, disposable EventWorkflowTests PostgreSQL fixtures only.
test.skip(!process.env.EVENTS_E2E_API_URL, 'Requires the isolated event verification fixture.');
test('standalone draft → edit → publish → public profile/list/agenda/detail', async ({ page, browser }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('event-1@example.test');
  await page.getByLabel('Password', { exact: true }).fill('synthetic-event-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/account$/);
  // Client navigation retains the in-memory session.
  await page.getByRole('link', { name: 'Open workspace' }).first().click();
  await page.getByRole('link', { name: 'Events & public profile' }).click();
  await page.getByRole('button', { name: 'Create event', exact: true }).click();
  const title = `Public event ${Date.now()}`;
  const start = new Date(Date.now() + 86400000 * 2);
  start.setUTCSeconds(0, 0);
  const end = new Date(start.getTime() + 7200000);
  const deadline = new Date(start.getTime() - 3600000);
  await page.getByLabel('Title', { exact: true }).fill(title);
  await page.getByLabel('Description', { exact: true }).fill('Synthetic browser verification event.');
  await page.getByLabel('Location', { exact: true }).fill('Campus hall');
  await page.getByLabel('Start (UTC)', { exact: true }).fill(start.toISOString().slice(0, 16));
  await page.getByLabel('End (UTC)', { exact: true }).fill(end.toISOString().slice(0, 16));
  await page.getByLabel('Registration deadline (UTC)', { exact: true }).fill(deadline.toISOString().slice(0, 16));
  await page.getByRole('button', { name: 'Save event', exact: true }).click();
  await expect(page.getByRole('button', { name: `Publish ${title}`, exact: true })).toBeVisible();
  const anonymous = await browser.newContext({ viewport: page.viewportSize() });
  const visitor = await anonymous.newPage();
  const origin = new URL(page.url()).origin;
  await visitor.goto(`${origin}/events`);
  await expect(visitor.getByRole('link', { name: title, exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: `Edit ${title}`, exact: true }).click();
  await page.getByLabel('Capacity (leave empty for unlimited)', { exact: true }).fill('30');
  await page.getByRole('button', { name: 'Save event', exact: true }).click();
  await page.getByRole('button', { name: `Publish ${title}`, exact: true }).click();
  const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title, exact: true }) });
  const detailPath = await card.getByRole('link', { name: 'View public event' }).getAttribute('href');
  await visitor.reload();
  await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await visitor.getByRole('button', { name: 'Agenda calendar' }).click();
  await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await visitor.goto(`${origin}/associations/alpha`);
  await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await visitor.goto(`${origin}${detailPath}`);
  await expect(visitor.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(visitor.getByText('Capacity: 30', { exact: true })).toBeVisible();
  await anonymous.close();
});

test('project manager publishes two project events; another manager is denied', async ({ page, request, browser }) => {
  const api = process.env.EVENTS_E2E_API_URL!;
  async function token(number: number): Promise<string> {
    const response = await request.post(`${api}/api/auth/login`, {
      data: { email: `event-${number}@example.test`, password: 'synthetic-event-password' },
    });
    expect(response.ok()).toBeTruthy();
    return ((await response.json()) as { accessToken: string }).accessToken;
  }
  const associationId = '40000000-0000-0000-0000-000000000001';
  const admin = await token(1);
  const projectName = `Event project ${Date.now()}`;
  const projectResponse = await request.post(`${api}/api/associations/${associationId}/projects`, {
    headers: { Authorization: `Bearer ${admin}` },
    data: {
      name: projectName,
      description: 'Synthetic project event verification.',
      managerUserIds: ['41000000-0000-0000-0000-000000000003'],
    },
  });
  expect(projectResponse.status()).toBe(201);
  const { project } = (await projectResponse.json()) as { project: { id: string } };
  await page.goto('/login');
  await page.getByLabel('Email').fill('event-3@example.test');
  await page.getByLabel('Password', { exact: true }).fill('synthetic-event-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/account$/);
  await page.getByRole('link', { name: 'Open workspace' }).first().click();
  await page.getByRole('link', { name: 'Events & public profile' }).click();
  const start = new Date(Date.now() + 86400000 * 3);
  start.setUTCSeconds(0, 0);
  const titles = [`${projectName} morning`, `${projectName} evening`];
  for (const title of titles) {
    await page.getByRole('button', { name: 'Create event', exact: true }).click();
    await page.getByLabel('Event belongs to').selectOption(project.id);
    await expect(page.getByRole('option', { name: 'Standalone association event' })).toHaveCount(0);
    await page.getByLabel('Title', { exact: true }).fill(title);
    await page.getByLabel('Description', { exact: true }).fill('A real project-linked event workflow.');
    await page.getByLabel('Location', { exact: true }).fill('Campus hall');
    await page.getByLabel('Start (UTC)', { exact: true }).fill(start.toISOString().slice(0, 16));
    await page
      .getByLabel('End (UTC)', { exact: true })
      .fill(new Date(start.getTime() + 7200000).toISOString().slice(0, 16));
    await page
      .getByLabel('Registration deadline (UTC)', { exact: true })
      .fill(new Date(start.getTime() - 3600000).toISOString().slice(0, 16));
    await page.getByRole('button', { name: 'Save event', exact: true }).click();
    await page.getByRole('button', { name: `Publish ${title}`, exact: true }).click();
    const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title, exact: true }) });
    await expect(card.getByRole('link', { name: 'View public event' })).toBeVisible();
  }
  const manager = await token(3);
  const listResponse = await request.get(`${api}/api/associations/${associationId}/events`, {
    headers: { Authorization: `Bearer ${manager}` },
  });
  expect(listResponse.ok()).toBeTruthy();
  const list = (await listResponse.json()) as { projectId: string | null; event: { id: string } }[];
  const projectEvents = list.filter((entry) => entry.projectId === project.id);
  expect(projectEvents).toHaveLength(2);
  const unrelated = await token(4);
  const denied = await request.post(
    `${api}/api/associations/${associationId}/events/${projectEvents[0].event.id}/cancel`,
    { headers: { Authorization: `Bearer ${unrelated}` } },
  );
  expect(denied.status()).toBe(403);
  const anonymous = await browser.newContext({ viewport: page.viewportSize() });
  const visitor = await anonymous.newPage();
  const origin = new URL(page.url()).origin;
  await visitor.goto(`${origin}/events?association=${associationId}`);
  await visitor.getByLabel('From (UTC)').fill(start.toISOString().slice(0, 10));
  await visitor.getByLabel('Through (UTC)').fill(start.toISOString().slice(0, 10));
  for (const title of titles) await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await visitor.getByRole('button', { name: 'Agenda calendar' }).click();
  for (const title of titles) await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await visitor.goto(`${origin}/associations/alpha`);
  for (const title of titles) await expect(visitor.getByRole('link', { name: title, exact: true })).toBeVisible();
  await anonymous.close();
});
