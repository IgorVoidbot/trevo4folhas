// One synthetic ticket through public UI, explicit intercepted WhatsApp and admin UI.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
const output = 'target/verification-sprint5';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
try {
  const publicContext = await browser.newContext({
    viewport: { width: 360, height: 900 },
  });
  const page = await publicContext.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(base);
  const card = page.locator('#match-list article').first();
  await card.locator('summary').first().click();
  await card.locator('.selection-choice').first().click();
  await page.locator('#open-slip').click();
  await page.locator('#stake').fill('10,00');
  await page.locator('#bettor-name').fill('Pessoa fictícia do fluxo');
  await page.locator('#bettor-phone').fill('11999999999');
  await page.locator('#adult-consent').check();
  await page.locator('#rules-consent').check();
  await page.locator('#review-slip').click();
  await page.locator('#review-summary:not([hidden])').waitFor();
  const created = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/tickets' &&
      r.request().method() === 'POST',
  );
  await page.locator('#generate-ticket').click();
  const createdResponse = await created;
  assert.equal(createdResponse.status(), 201);
  const ticket = await createdResponse.json();
  await page.locator('#ticket-result:not([hidden])').waitFor();
  assert.equal(ticket.status, 'PENDING');
  await page.locator('#ticket-result').scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${output}/flow-public.png` });
  await publicContext.route('https://wa.me/**', (route) =>
    route.fulfill({
      status: 200,
      body: 'Conversa simulada: nenhuma mensagem real enviada.',
    }),
  );
  const popupPromise = page.waitForEvent('popup');
  const clicked = page.waitForResponse(
    (r) => new URL(r.url()).pathname === '/api/tickets/whatsapp-click',
  );
  await page.locator('#send-whatsapp').click();
  const popup = await popupPromise;
  await popup.waitForLoadState();
  assert.equal(
    new URL(popup.url()).searchParams.get('text'),
    ticket.whatsappMessage,
  );
  assert.equal((await clicked).status(), 204);
  await popup.close();
  const adminContext = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });
  const admin = await adminContext.newPage();
  admin.on('pageerror', (e) => errors.push(e.message));
  await admin.goto(`${base}/admin/login`);
  await admin.locator('[name=username]').fill(process.env.TEST_ADMIN_USERNAME);
  await admin.locator('[name=password]').fill(process.env.TEST_ADMIN_PASSWORD);
  await admin.locator('button[type=submit]').click();
  await admin.waitForURL(`${base}/admin`);
  const before = await (
    await adminContext.request.get(`${base}/api/admin/tickets/${ticket.id}`)
  ).json();
  assert.equal(before.ticket.status, 'PENDING');
  assert.ok(before.ticket.whatsappClickedAt);
  await admin
    .getByRole('link', { name: 'Gerenciar pré-bilhetes', exact: true })
    .click();
  await admin
    .locator('#ticket-zone')
    .filter({ hasText: 'America/Sao_Paulo' })
    .waitFor();
  await admin.locator('[name=code]').fill(ticket.code);
  const filtered = admin.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/admin/tickets' &&
      new URL(r.url()).searchParams.get('code') === ticket.code,
  );
  await admin.locator('#apply-ticket-filters').click();
  await filtered;
  await admin.waitForFunction(
    () =>
      globalThis.document
        .getElementById('ticket-list')
        .getAttribute('aria-busy') === 'false' &&
      globalThis.document.querySelectorAll('#ticket-list article').length === 1,
  );
  await admin
    .locator('#ticket-list article')
    .filter({ hasText: ticket.code })
    .getByRole('button', { name: 'Ver pré-bilhete', exact: true })
    .click();
  await admin
    .locator('#ticket-detail h2')
    .filter({ hasText: ticket.code })
    .waitFor();
  await admin.locator('#confirm-ticket').click();
  await admin.locator('#pix-checked').check();
  const saved = admin.waitForResponse(
    (r) =>
      r.url().endsWith(`/${ticket.id}/confirm`) &&
      r.request().method() === 'POST',
  );
  await admin.locator('#save-decision').click();
  const result = await (await saved).json();
  assert.equal(result.current.ticket.id, ticket.id);
  assert.equal(result.current.ticket.status, 'CONFIRMED');
  assert.equal(result.current.ticket.potentialReturn, ticket.potentialReturn);
  await admin
    .locator('#ticket-feedback')
    .filter({ hasText: 'confirmado após' })
    .waitFor();
  await admin.screenshot({ path: `${output}/flow-admin.png`, fullPage: true });
  assert.deepEqual(errors, []);
  await adminContext.close();
  await publicContext.close();
  console.log(
    'PASS: same persisted ticket through 360px public review/issuance, explicit simulated WhatsApp click with PENDING preserved, then 1440px authenticated Pix declaration/confirmation; immutable financial return.',
  );
} finally {
  await browser.close();
}
