import { expect, test } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

const api = process.env.PROJECT_E2E_API_URL ?? 'http://localhost:18080';
const password = 'synthetic-password';
const userId = (number: number): string => `30000000-0000-0000-0000-${String(number).padStart(12, '0')}`;
const email = (number: number): string => `raul-test-${number}@example.test`;

async function login(page: Page, user: number, slug: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(email(user));
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.locator(`a[href="/workspaces/${slug}"]`).click();
  await page.getByRole('link', { name: 'Projects & recruitment' }).click();
}

async function token(request: APIRequestContext, user: number): Promise<string> {
  const result = await request.post(`${api}/api/auth/login`, { data: { email: email(user), password } });
  expect(result.ok()).toBeTruthy();
  return ((await result.json()) as { accessToken: string }).accessToken;
}

test('administrator, manager and member complete recruitment and reporting on PostgreSQL', async ({
  page,
  browser,
  request,
}, info) => {
  test.skip(!process.env.PROJECT_E2E_API_URL, 'Opt in with a disposable PostgreSQL fixture and PROJECT_E2E_API_URL.');
  const adminToken = await token(request, 1);
  const headers = { Authorization: `Bearer ${adminToken}` };
  const slug = `raul-ui-${info.project.name.toLowerCase()}-${Date.now()}`;
  const association = await request.post(`${api}/api/admin/associations`, {
    headers,
    data: { name: 'Raul UI verification', slug, initialAdminEmail: email(1) },
  });
  expect(association.status()).toBe(201);
  const associationId = ((await association.json()) as { id: string }).id;
  for (const user of [3, 6]) {
    const result = await request.post(`${api}/api/associations/${associationId}/members`, {
      headers,
      data: { email: email(user), role: 'MEMBER' },
    });
    expect(result.status()).toBe(201);
  }

  const projectName = 'Community fair';
  await login(page, 1, slug);
  await page.getByLabel('Project name', { exact: true }).fill(projectName);
  await page.getByLabel('Description', { exact: true }).fill('A project created through the real interface.');
  await page.getByRole('listbox', { name: 'Project managers', exact: true }).selectOption(userId(3));
  await page.getByRole('button', { name: 'Create project', exact: true }).click();
  await expect(page.getByRole('heading', { name: projectName, exact: true })).toBeVisible();
  const projectId = page.url().split('/').at(-1)!;
  const base = `${api}/api/associations/${associationId}/projects/${projectId}`;

  // Inherit the current project's desktop/mobile settings while using separate accounts.
  const { baseURL, viewport, isMobile, hasTouch, deviceScaleFactor, userAgent } = info.project.use;
  const contextOptions = { baseURL, viewport, isMobile, hasTouch, deviceScaleFactor, userAgent };
  const managerContext = await browser.newContext(contextOptions);
  const memberContext = await browser.newContext(contextOptions);
  try {
    const managerPage = await managerContext.newPage();
    const memberPage = await memberContext.newPage();
    await login(managerPage, 3, slug);
    await managerPage.getByRole('link', { name: projectName, exact: true }).click();
    await managerPage.getByLabel('Call description', { exact: true }).fill('Help organize the community fair.');
    await managerPage.getByLabel('Recruitment deadline', { exact: true }).fill('2099-01-01T12:00');
    await managerPage.getByRole('button', { name: 'Save recruitment call' }).click();
    await expect(managerPage.getByRole('status')).toContainText('Recruitment call updated.');
    for (const position of [
      { role: 'Developer', department: 'Engineering' },
      { role: 'Designer', department: 'Design' },
    ]) {
      await managerPage.getByLabel('Role label', { exact: true }).fill(position.role);
      await managerPage.getByLabel('Department name', { exact: true }).fill(position.department);
      await managerPage.getByRole('button', { name: 'Save position', exact: true }).click();
      await expect(managerPage.getByRole('status')).toContainText('Position saved.');
      await expect(managerPage.getByLabel('Role label', { exact: true })).toHaveValue('');
    }

    await managerPage.getByRole('heading', { name: projectName, exact: true }).scrollIntoViewIfNeeded();
    await managerPage.screenshot({ path: info.outputPath('project-overview.png'), fullPage: true });
    await login(memberPage, 6, slug);
    await memberPage.getByRole('link', { name: projectName, exact: true }).click();
    for (const label of ['Developer / Engineering', 'Designer / Design']) {
      await memberPage.getByRole('combobox', { name: 'Position', exact: true }).selectOption({ label });
      await memberPage.getByLabel('Motivation', { exact: true }).fill(`I would like to help as ${label}.`);
      await memberPage.getByRole('button', { name: 'Submit application' }).click();
      await expect(memberPage.getByRole('status')).toContainText('Application submitted for review.');
      await expect(memberPage.getByLabel('Motivation', { exact: true })).toHaveValue('');
    }
    await managerPage.getByRole('button', { name: 'Refresh project', exact: true }).click();
    const application = managerPage
      .locator('.project-record')
      .filter({ has: managerPage.getByRole('heading', { name: 'Synthetic 6 · Developer / Engineering' }) });
    await application
      .getByLabel('Reporting manager for Synthetic 6')
      .selectOption({ label: 'Synthetic 3 (Project manager)' });
    await application.getByRole('button', { name: 'Accept application' }).click();
    await expect(managerPage.getByRole('status')).toContainText('Application accepted.');
    await expect(managerPage.getByRole('button', { name: 'Appoint department manager' })).toBeVisible();
    await managerPage.getByRole('button', { name: 'Appoint department manager' }).click();
    await expect(managerPage.getByRole('status')).toContainText('Department manager appointed');

    await memberPage.getByRole('button', { name: 'Refresh project', exact: true }).click();
    await expect(memberPage.getByText('Accepted', { exact: true })).toBeVisible();
    await expect(memberPage.getByText('Closed — accepted for another position', { exact: true })).toBeVisible();
    await expect(memberPage.getByRole('heading', { name: 'Project team & reporting' })).toBeVisible();
    await expect(memberPage.getByRole('heading', { name: 'Project configuration' })).toHaveCount(0);

    const managerToken = await token(request, 3);
    const response = await request.get(`${base}/team`, { headers: { Authorization: `Bearer ${managerToken}` } });
    expect(response.ok()).toBeTruthy();
    const team = (await response.json()) as {
      userId: string;
      projectRole: string;
      reportingParentMembershipId: string | null;
    }[];
    const selected = team.filter((member) => member.userId === userId(6));
    expect(selected).toHaveLength(1);
    expect(selected[0].projectRole).toBe('DEPARTMENT_MANAGER');
    expect(selected[0].reportingParentMembershipId).toBeTruthy();
    const bodyWidth = await managerPage.locator('body').evaluate((element) => element.scrollWidth);
    expect(bodyWidth).toBeLessThanOrEqual((managerPage.viewportSize()?.width ?? bodyWidth) + 1);
    await managerPage.screenshot({ path: info.outputPath('project-manager.png'), fullPage: true });
    await memberPage.screenshot({ path: info.outputPath('project-member.png'), fullPage: true });
  } finally {
    await managerContext.close();
    await memberContext.close();
  }
});
