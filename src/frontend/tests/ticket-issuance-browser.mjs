// Run against the disposable application prepared by the Sprint 3 verification runner.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
await mkdir('target/verification-sprint3', { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
async function reviewed(page, width) {
  await page.goto(base);
  const cards = page.locator('#match-list article');
  await cards.nth(2).waitFor();
  for (const [index, label] of [
    [0, 'Vitória do mandante, cotação 1,80'],
    [1, 'Mais de 2,5 gols, cotação 2,50'],
  ]) {
    await cards.nth(index).locator('summary').first().click();
    await cards
      .nth(index)
      .getByRole('button', { name: label, exact: true })
      .click();
  }
  if (width < 1024) await page.locator('#open-slip').click();
  await page.locator('#stake').fill('10,00');
  await page.locator('#bettor-name').fill("Pessoa D'Água <teste>");
  await page.locator('#bettor-phone').fill('11999999999');
  await page.locator('#adult-consent').check();
  await page.locator('#rules-consent').check();
  await page.locator('#review-slip').click();
  await page.locator('#review-summary:not([hidden])').waitFor();
}
try {
  for (const width of [360, 1440]) {
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      timezoneId: 'UTC',
    });
    const page = await context.newPage();
    const errors = [],
      attempts = [];
    page.on('pageerror', (error) => errors.push(error.message));
    let saved;
    // Simulate a response lost AFTER the real server has committed.
    await page.route('**/api/tickets', async (route) => {
      attempts.push({
        body: route.request().postData(),
        key: route.request().headers()['idempotency-key'],
      });
      const response = await route.fetch();
      if (attempts.length === 1) {
        assert.equal(response.status(), 201);
        saved = await response.json();
        await route.abort('connectionreset');
      } else {
        assert.equal(response.status(), 200);
        assert.equal((await response.json()).id, saved.id);
        await route.fulfill({ response });
      }
    });
    await reviewed(page, width);
    await page.locator('#generate-ticket').dblclick({ delay: 5 });
    await page
      .locator('#review-feedback')
      .filter({ hasText: 'conexão falhou' })
      .waitFor();
    assert.equal(attempts.length, 1);
    assert.equal(await page.locator('#stake').isDisabled(), true);
    await page.locator('#generate-ticket').click();
    await page.locator('#ticket-result:not([hidden])').waitFor();
    assert.deepEqual(attempts[0], attempts[1]);
    const payload = JSON.parse(attempts[0].body);
    assert.deepEqual(
      Object.keys(payload).sort(),
      [
        'adultDeclared',
        'bettorName',
        'bettorPhone',
        'items',
        'rulesAccepted',
        'rulesVersion',
        'stakeAmount',
      ].sort(),
    );
    assert.deepEqual(
      Object.keys(payload.items[0]).sort(),
      [
        'selectionId',
        'matchVersion',
        'marketVersion',
        'selectionVersion',
      ].sort(),
    );
    assert.equal(saved.potentialReturn, '45.00');
    assert.equal(
      await page.locator('#persisted-summary').inputValue(),
      saved.summary,
    );
    assert.equal(await page.locator('#ticket-result teste').count(), 0);
    assert.match(
      await page.locator('#ticket-result').textContent(),
      /pendente.*confirmação da banca/s,
    );
    assert.equal(await page.locator('#generate-ticket').isDisabled(), true);
    const href = await page.locator('#send-whatsapp').getAttribute('href');
    const url = new URL(href);
    assert.equal(url.hostname, 'wa.me');
    assert.equal(url.pathname, '/5511999999999');
    assert.equal(url.searchParams.get('text'), saved.whatsappMessage);
    assert.match(url.searchParams.get('text'), /D'Água|Mensagem curta/);
    await page.evaluate(() =>
      Object.defineProperty(globalThis.navigator, 'clipboard', {
        configurable: true,
        value: {
          writeText: () =>
            Promise.reject(new Error('Synthetic denied permission')),
        },
      }),
    );
    await page
      .getByRole('button', { name: 'Copiar resumo', exact: true })
      .click();
    await page
      .getByText('A cópia automática não está disponível.', { exact: false })
      .waitFor();
    assert.equal(
      await page
        .locator('#persisted-summary')
        .evaluate((el) => el.selectionEnd - el.selectionStart),
      saved.summary.length,
    );
    await context.route('https://wa.me/**', (route) =>
      route.fulfill({
        status: 200,
        body: 'WhatsApp simulado — nenhuma mensagem enviada',
      }),
    );
    // Telemetry is permitted to fail without blocking the explicit link.
    await page.route('**/api/tickets/whatsapp-click', (route) => route.abort());
    const popupPromise = page.waitForEvent('popup');
    await page.locator('#send-whatsapp').click();
    const popup = await popupPromise;
    await popup.waitForLoadState();
    assert.equal(
      new URL(popup.url()).searchParams.get('text'),
      saved.whatsappMessage,
    );
    await popup.close();
    assert.equal(
      await page.evaluate(
        () => globalThis.localStorage.length + globalThis.sessionStorage.length,
      ),
      0,
    );
    assert.equal(
      await page.evaluate(
        () =>
          globalThis.document.documentElement.scrollWidth <=
          globalThis.innerWidth,
      ),
      true,
    );
    await page.locator('#ticket-result').scrollIntoViewIfNeeded();
    await page.screenshot({
      path: `target/verification-sprint3/ticket-${width}.png`,
    });
    assert.deepEqual(errors, []);
    await context.close();
  }
  for (const failure of [400, 403, 409, 429, 503]) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    const attempts = [];
    let first = true;
    await page.route('**/api/tickets', async (route) => {
      attempts.push({
        key: route.request().headers()['idempotency-key'],
        body: route.request().postData(),
      });
      if (first) {
        first = false;
        await route.fulfill({
          status: failure,
          contentType: 'application/json',
          headers: { 'Retry-After': '1' },
          body: JSON.stringify({
            errorCode: failure === 409 ? 'CATALOG_CHANGED' : 'TEST_FAILURE',
          }),
        });
      } else await route.continue();
    });
    await reviewed(page, 1440);
    await page.locator('#generate-ticket').click();
    await page.waitForFunction(
      () =>
        !globalThis.document
          .getElementById('review-feedback')
          .textContent.includes('Gerando'),
    );
    assert.equal(attempts.length, 1);
    assert.equal(await page.locator('#ticket-result').isHidden(), true);
    if ([400, 409].includes(failure)) {
      assert.equal(await page.locator('#generate-ticket').isDisabled(), true);
      assert.equal(await page.locator('#stake').isDisabled(), false);
      // Explicit new selection/review is required after a catalog conflict.
      if (failure === 409) {
        await page
          .locator('#match-list article')
          .first()
          .getByRole('button', {
            name: 'Vitória do mandante, cotação 1,80',
            exact: true,
          })
          .click();
        await page
          .locator('#match-list article')
          .nth(1)
          .getByRole('button', {
            name: 'Mais de 2,5 gols, cotação 2,50',
            exact: true,
          })
          .click();
      }
      await page.locator('#review-slip').click();
      await page.locator('#review-summary:not([hidden])').waitFor();
    }
    await page.locator('#generate-ticket').click();
    await page.locator('#ticket-result:not([hidden])').waitFor();
    assert.equal(attempts.length, 2);
    if ([403, 429, 503].includes(failure))
      assert.deepEqual(attempts[0], attempts[1]);
    else assert.notEqual(attempts[0].key, attempts[1].key);
    await context.close();
  }
  console.log(
    'PASS: real issuance/replay after lost committed response, duplicate click, immutable attempt, persisted summary, 360/1440, encoded WhatsApp with no external delivery, telemetry failure, copy fallback, 400/403/409/429/503 recovery, no browser PII storage.',
  );
} finally {
  await browser.close();
}
