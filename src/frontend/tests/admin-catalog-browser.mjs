// Run only against a disposable, locally bootstrapped application. No production data.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || 'playwright'
);
const base = process.env.TEST_BASE_URL;
assert.equal(new URL(base).hostname, '127.0.0.1');
const screenshots = 'target/verification-sprint1';
await mkdir(screenshots, { recursive: true });
const browser = await chromium.launch({
  executablePath: '/usr/bin/google-chrome',
  headless: true,
});
try {
  for (const width of [360, 1440]) {
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      timezoneId: 'UTC',
    });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto(`${base}/admin/login`);
    await page
      .locator('[name="username"]')
      .fill(process.env.TEST_ADMIN_USERNAME);
    await page
      .locator('[name="password"]')
      .fill(process.env.TEST_ADMIN_PASSWORD);
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(`${base}/admin`);
    await page
      .getByRole('link', { name: 'Gerenciar catálogo de partidas' })
      .click();
    await page
      .locator('#bank-zone')
      .filter({ hasText: 'America/Sao_Paulo' })
      .waitFor();
    const match = page.locator('#match-form');
    await match.locator('[name="competition"]').fill(`Copa simulada ${width}`);
    await match.locator('[name="homeTeam"]').fill('Clube fictício A');
    await match.locator('[name="awayTeam"]').fill('Clube fictício B');
    await match.locator('[name="startsAt"]').fill('2030-06-01T15:00');
    await match.getByRole('button', { name: 'Salvar partida' }).click();
    await page.locator('#offers-section:not([hidden])').waitFor();
    const market = page.locator('#market-form');
    await market.locator('[name="marketType"]').fill('TOTALS');
    await market.locator('[name="displayName"]').fill('Total de gols');
    await market.locator('[name="period"]').fill('FT');
    await market.locator('[name="line"]').fill('2.5');
    await market.locator('[name="ruleKey"]').fill('REGULATION_TIME');
    await market
      .locator('[name="ruleText"]')
      .fill('Tempo regulamentar, incluindo acréscimos.');
    await market.locator('[name="curated"]').check();
    await market.getByRole('button', { name: 'Salvar mercado' }).click();
    await page
      .locator('#markets button')
      .filter({ hasText: 'Total de gols' })
      .click();
    const selection = page.locator('#selection-form');
    await selection.locator('[name="selectionCode"]').fill('OVER');
    await selection.locator('[name="displayName"]').fill('Mais de 2,5');
    await selection.locator('[name="odds"]').fill('1,85');
    await selection.locator('[name="curated"]').check();
    await selection.getByRole('button', { name: 'Salvar seleção' }).click();
    await page
      .locator('#selections button')
      .filter({ hasText: 'Mais de 2,5' })
      .waitFor();
    const publicPage = await (
      await context.request.get(
        `${base}/api/matches?competition=Copa%20simulada%20${width}`,
      )
    ).json();
    assert.equal(publicPage.totalElements, 1);
    assert.equal(publicPage.content[0].startsAt, '2030-06-01T18:00:00Z');
    assert.equal(
      await page.evaluate(
        () =>
          globalThis.document.documentElement.scrollWidth <=
          globalThis.innerWidth,
      ),
      true,
    );
    await match.locator('[name="competition"]').focus();
    await page.keyboard.press('Tab');
    assert.equal(await page.locator(':focus').getAttribute('name'), 'homeTeam');
    assert.equal(
      await page
        .locator(':focus')
        .evaluate((node) => globalThis.getComputedStyle(node).outlineStyle),
      'solid',
    );
    await page.screenshot({
      path: `${screenshots}/catalog-${width}.png`,
      fullPage: true,
    });
    page.on('dialog', (dialog) => dialog.accept());
    await match.locator('[name="status"]').selectOption('SUSPENDED');
    await match.getByRole('button', { name: 'Salvar partida' }).click();
    await page
      .locator('#matches button')
      .filter({ hasText: `Copa simulada ${width}` })
      .filter({ hasText: 'Suspensa' })
      .waitFor();
    assert.equal(
      (
        await (
          await context.request.get(
            `${base}/api/matches?competition=Copa%20simulada%20${width}`,
          )
        ).json()
      ).totalElements,
      0,
    );
    assert.deepEqual(errors, []);
    await context.close();
  }
  console.log(
    'PASS: Chrome 360/1440, criação e curadoria, catálogo público, fuso UTC → banca, suspensão, teclado e ausência de overflow/erros JS.',
  );
} finally {
  await browser.close();
}
