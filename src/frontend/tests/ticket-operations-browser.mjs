// Disposable local app only. All identities, transfers and fixtures are synthetic.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
const output = 'target/verification-sprint4';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
let selected;
async function emit(context) {
  if (!selected) {
    const matches = await (
      await context.request.get(`${base}/api/matches`)
    ).json();
    const match = matches.content[0];
    const markets = await (
      await context.request.get(`${base}/api/matches/${match.id}/markets`)
    ).json();
    const market = markets.content.find((m) => m.marketType === '1X2');
    const choices = await (
      await context.request.get(`${base}/api/markets/${market.id}/selections`)
    ).json();
    selected = choices.content.find((s) => s.selectionCode === 'HOME');
  }
  const csrf = await (await context.request.get(`${base}/api/session`)).json();
  const response = await context.request.post(`${base}/api/tickets`, {
    headers: {
      [csrf.headerName]: csrf.token,
      'Idempotency-Key': crypto.randomUUID(),
    },
    data: {
      bettorName: "Pessoa D'Água <teste>",
      bettorPhone: '11999999999',
      stakeAmount: '10.00',
      adultDeclared: true,
      rulesAccepted: true,
      rulesVersion: 'PREVIEW-2026-10-31-V1',
      items: [
        {
          selectionId: selected.id,
          matchVersion: selected.matchVersion,
          marketVersion: selected.marketVersion,
          selectionVersion: selected.selectionVersion,
        },
      ],
    },
  });
  assert.equal(response.status(), 201);
  return response.json();
}
async function login(page) {
  await page.goto(`${base}/admin/login`);
  await page.locator('[name=username]').fill(process.env.TEST_ADMIN_USERNAME);
  await page.locator('[name=password]').fill(process.env.TEST_ADMIN_PASSWORD);
  await page.locator('button[type=submit]').click();
  await page.waitForURL(`${base}/admin`);
}
async function show(page, ticket) {
  await page
    .locator('#ticket-filters [name=code]')
    .fill(`  ${ticket.code.toLowerCase()}  `);
  const response = page.waitForResponse((r) => {
    const url = new URL(r.url());
    return (
      url.pathname === '/api/admin/tickets' &&
      url.searchParams.get('code')?.trim().toUpperCase() === ticket.code
    );
  });
  await page
    .getByRole('button', { name: 'Filtrar pré-bilhetes', exact: true })
    .click();
  assert.equal((await response).status(), 200);
  await page.waitForFunction((code) => {
    const list = globalThis.document.getElementById('ticket-list');
    return (
      list.getAttribute('aria-busy') === 'false' &&
      list.querySelectorAll('article').length === 1 &&
      list.textContent.includes(code)
    );
  }, ticket.code);
  await page
    .locator('#ticket-list article')
    .filter({ hasText: ticket.code })
    .getByRole('button', { name: 'Ver pré-bilhete', exact: true })
    .click();
  await page
    .locator('#ticket-detail h2')
    .filter({ hasText: ticket.code })
    .waitFor();
}
try {
  for (const width of [360, 1440]) {
    const anonymous = await browser.newContext();
    const t = await emit(anonymous),
      cancelled = await emit(anonymous);
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      timezoneId: 'UTC',
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await login(page);
    await page
      .getByRole('link', { name: 'Gerenciar pré-bilhetes', exact: true })
      .click();
    await page
      .locator('#ticket-zone')
      .filter({ hasText: 'America/Sao_Paulo' })
      .waitFor();
    await show(page, t);
    assert.equal(
      await page.locator('#ticket-detail teste, #ticket-detail img').count(),
      0,
    );
    assert.match(
      await page.locator('#ticket-detail').textContent(),
      /Tempo regulamentar/,
    );
    assert.doesNotMatch(
      await page.locator('#ticket-detail').textContent(),
      /Invalid Date|NaN/,
    );
    const second = await context.newPage();
    await second.goto(`${base}/admin/tickets`);
    await show(second, t);
    await second.locator('#confirm-ticket').click();
    await page.locator('#confirm-ticket').click();
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#decision-dialog').isVisible(), false);
    assert.equal(
      await page.locator(':focus').getAttribute('id'),
      'confirm-ticket',
    );
    await page.locator('#confirm-ticket').click();
    await page.locator('#save-decision').click();
    await page
      .locator('#decision-feedback')
      .filter({ hasText: 'Declare a conferência' })
      .waitFor();
    await page.locator('#pix-checked').check();
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/${t.id}/confirm`) && r.request().method() === 'POST',
    );
    await page.locator('#save-decision').click();
    assert.equal((await response).status(), 200);
    await page
      .locator('#ticket-feedback')
      .filter({ hasText: 'confirmado após' })
      .waitFor();
    assert.match(
      await page.locator('#ticket-detail').textContent(),
      /Confirmado/,
    );
    assert.equal(await page.locator('#confirm-ticket').count(), 0);
    await second.locator('#pix-checked').check();
    const conflict = second.waitForResponse(
      (r) =>
        r.url().endsWith(`/${t.id}/confirm`) && r.request().method() === 'POST',
    );
    await second.locator('#save-decision').click();
    assert.equal((await conflict).status(), 409);
    await second
      .locator('#ticket-feedback')
      .filter({ hasText: 'mudou ou já foi decidido' })
      .waitFor();
    assert.equal(await second.locator('#confirm-ticket').count(), 0);
    const persisted = await (
      await context.request.get(`${base}/api/admin/tickets/${t.id}`)
    ).json();
    assert.equal(persisted.ticketVersion, 2);
    assert.equal(persisted.ticket.potentialReturn, '18.00');
    assert.ok(persisted.handledAt);
    await second.close();
    await page.screenshot({
      path: `${output}/confirmed-${width}.png`,
      fullPage: true,
    });
    await show(page, cancelled);
    await page.locator('#cancel-ticket').click();
    await page.locator('#save-decision').click();
    await page
      .locator('#decision-feedback')
      .filter({ hasText: 'Informe um motivo' })
      .waitFor();
    await page
      .locator('#cancel-reason')
      .fill("Atendimento fictício D'Água <script>texto</script>");
    await page
      .locator('#operator-note')
      .fill('Observação fictícia\nsegunda linha');
    await page.locator('#save-decision').click();
    await page
      .locator('#ticket-feedback')
      .filter({ hasText: 'cancelado.' })
      .waitFor();
    assert.match(
      await page.locator('#ticket-detail').textContent(),
      /Cancelado/,
    );
    assert.match(
      await page.locator('#ticket-detail').textContent(),
      /segunda linha/,
    );
    assert.equal(await page.locator('#ticket-detail script').count(), 0);
    assert.equal(
      await page.evaluate(
        () =>
          globalThis.document.documentElement.scrollWidth <=
          globalThis.innerWidth,
      ),
      true,
    );
    assert.equal(
      await page.evaluate(
        () => globalThis.localStorage.length + globalThis.sessionStorage.length,
      ),
      0,
    );
    await page.screenshot({
      path: `${output}/cancelled-${width}.png`,
      fullPage: true,
    });
    if (width === 1440) {
      await page.evaluate(
        () => (globalThis.document.documentElement.style.zoom = '2'),
      );
      assert.equal(
        await page.evaluate(
          () =>
            globalThis.document.documentElement.scrollWidth <=
            globalThis.innerWidth,
        ),
        true,
      );
      await page.screenshot({ path: `${output}/zoom-200.png`, fullPage: true });
    }
    assert.deepEqual(errors, []);
    await context.close();
    await anonymous.close();
  }
  for (const failure of [400, 403, 503, 'network']) {
    const anonymous = await browser.newContext();
    const ticket = await emit(anonymous);
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    await login(page);
    await page.goto(`${base}/admin/tickets`);
    await show(page, ticket);
    await page.locator('#cancel-ticket').click();
    await page.locator('#cancel-reason').fill('Motivo fictício preservado');
    await page.locator('#operator-note').fill('Observação preservada');
    let calls = 0;
    await page.route(
      `**/api/admin/tickets/${ticket.id}/cancel`,
      async (route) => {
        calls++;
        if (calls === 1) {
          if (failure === 'network') {
            const response = await route.fetch();
            assert.equal(response.status(), 200);
            await route.abort('connectionreset');
          } else
            await route.fulfill({
              status: failure,
              contentType: 'application/json',
              body: JSON.stringify({
                errorCode: 'TEST_FAILURE',
                message: 'Falha simulada. Confira o pedido.',
              }),
            });
        } else await route.continue();
      },
    );
    await page.locator('#save-decision').click();
    await page
      .locator('#decision-feedback')
      .filter({
        hasText:
          failure === 'network'
            ? 'Falha de conexão'
            : failure === 403
              ? 'Sua sessão expirou'
              : 'Falha simulada',
      })
      .waitFor();
    assert.equal(calls, 1);
    assert.equal(
      await page.locator('#cancel-reason').inputValue(),
      'Motivo fictício preservado',
    );
    assert.equal(
      await page.locator('#operator-note').inputValue(),
      'Observação preservada',
    );
    if (failure === 503 || failure === 'network')
      assert.equal(await page.locator('#save-decision').isDisabled(), true);
    await page.locator('#close-decision').click();
    await page
      .getByRole('button', { name: 'Atualizar detalhe', exact: true })
      .click();
    if (failure === 'network') {
      await page
        .locator('#ticket-detail')
        .getByText('Cancelado', { exact: true })
        .waitFor();
      assert.equal(await page.locator('#cancel-ticket').count(), 0);
      assert.equal(calls, 1);
    } else {
      await page.locator('#cancel-ticket').click();
      await page.locator('#cancel-reason').fill('Nova conferência explícita');
      await page.locator('#save-decision').click();
      await page
        .locator('#ticket-feedback')
        .filter({ hasText: 'cancelado.' })
        .waitFor();
      assert.equal(calls, 2);
    }
    await context.close();
    await anonymous.close();
  }
  const anonymous = await browser.newContext();
  const pending = await emit(anonymous);
  await writeFile(
    `${output}/restart-ticket.json`,
    JSON.stringify({ id: pending.id }),
  );
  await anonymous.close();
  console.log(
    'PASS: authenticated queue/detail/search, snapshots, Pix declaration, confirmation, cancellation reason/note, escaped text, second-tab conflict, preserved fields in 400/403/503/network failures, recovery of committed lost response, 360/1440, 200% zoom, keyboard/focus and no PII storage.',
  );
} finally {
  await browser.close();
}
