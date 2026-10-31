import { Slip, decimalUnits, normalizeStake, preview } from './slip-state.js';

const byId = (id) => document.getElementById(id);
const panel = byId('bet-slip');
const desktop = window.matchMedia('(min-width: 64rem)');
let settings;
let slip;
let pageIndex = 0;
let loading = false;
let catalogEpoch = 0;
let revalidation;
const selectionButtons = new Map();
const money = (cents) =>
  cents === null
    ? '—'
    : `R$ ${(cents / 100n).toLocaleString('pt-BR')},${String(cents % 100n).padStart(2, '0')}`;
const oddText = (value) => value.replace('.', ',');
const message = (text) => {
  byId('catalog-feedback').textContent = text;
};
function node(tag, text, className = '') {
  const element = document.createElement(tag);
  element.textContent = text;
  element.className = className;
  return element;
}
function button(text, action, className = 'public-secondary') {
  const result = node('button', text, className);
  result.type = 'button';
  result.addEventListener('click', action);
  return result;
}
function invalidateReview() {
  byId('review-summary').hidden = true;
  byId('review-feedback').textContent = '';
}
function isSelected(item) {
  const selected = slip.items.get(item.matchId);
  return (
    selected?.selectionId === item.selectionId &&
    !selected.needsReview &&
    ['matchVersion', 'marketVersion', 'selectionVersion', 'odds'].every(
      (key) => selected[key] === item[key],
    )
  );
}
async function api(path) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(path, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      signal: controller.signal,
      cache: 'no-store',
    });
    if (!response.ok) {
      const messages = {
        400: 'Confira os filtros e tente novamente.',
        401: 'Sua sessão precisa ser atualizada. Recarregue a página; seu cupom não foi enviado.',
        403: 'Não foi possível autorizar a consulta. Recarregue a página antes de continuar.',
        404: 'Esta oferta está indisponível. Atualize o catálogo e revise o cupom.',
        409: 'As condições mudaram. Atualize o catálogo e revise suas seleções.',
        429: 'Muitas consultas em pouco tempo. Aguarde um momento antes de tentar novamente.',
      };
      throw new Error(
        messages[response.status] ||
          'O catálogo está indisponível. Tente novamente em instantes.',
      );
    }
    return await response.json();
  } catch (error) {
    if (error.name === 'AbortError' || error instanceof TypeError)
      throw new Error(
        'Falha de conexão. Seu cupom foi preservado nesta aba. Tente atualizar novamente.',
      );
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
function dateText(value) {
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: settings.bankTimeZone,
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}
function setFieldError(id, inputId, text) {
  byId(id).textContent = text;
  byId(id).hidden = !text;
  if (inputId)
    byId(inputId).setAttribute('aria-invalid', String(Boolean(text)));
}
function calculate() {
  const items = [...slip.items.values()];
  const normalized = normalizeStake(byId('stake').value);
  const amount = normalized === null ? null : decimalUnits(normalized);
  const result = preview(items, normalized);
  byId('combined-odds').textContent = result
    ? `${result.combinedHundredths / 100n},${String(result.combinedHundredths % 100n).padStart(2, '0')}`
    : '—';
  byId('potential-return').textContent = money(result?.returnCents ?? null);
  let error = '';
  if (
    byId('stake').value &&
    (amount === null ||
      amount < decimalUnits(settings.minStakeAmount) ||
      amount > decimalUnits(settings.maxStakeAmount))
  )
    error = `Informe um valor entre ${money(decimalUnits(settings.minStakeAmount))} e ${money(decimalUnits(settings.maxStakeAmount))}, com até duas casas decimais.`;
  if (result?.returnCents > decimalUnits(settings.maxPotentialReturn))
    error = `O retorno potencial excede o limite de ${money(decimalUnits(settings.maxPotentialReturn))}. Reduza o valor ou as seleções.`;
  setFieldError('stake-error', 'stake', error);
  return { normalized, amount, result, error };
}
function renderSlip() {
  const items = [...slip.items.values()];
  const list = byId('slip-items');
  list.replaceChildren();
  if (!items.length)
    list.append(
      node(
        'li',
        'Seu cupom está vazio. Escolha um palpite no catálogo.',
        'py-3 text-sm text-slate-400',
      ),
    );
  for (const item of items) {
    const row = node(
      'li',
      '',
      'rounded-xl border border-slate-700 bg-slate-950 p-3',
    );
    row.append(
      node('p', item.matchLabel, 'font-semibold'),
      node('p', item.marketLabel, 'mt-1 text-sm text-slate-300'),
      node(
        'p',
        `${item.selectionLabel} · Cotação ${oddText(item.odds)}`,
        'mt-2 text-sm text-emerald-300',
      ),
    );
    if (item.needsReview)
      row.append(
        node(
          'p',
          'Oferta indisponível ou alterada. Escolha novamente no catálogo ou remova este item.',
          'mt-2 text-sm text-amber-200',
        ),
      );
    row.append(
      button('Remover', () => {
        slip.remove(item.matchId);
        invalidateReview();
        renderSlip();
        byId('slip-title').focus();
      }),
    );
    list.append(row);
  }
  const count = `${items.length} ${items.length === 1 ? 'seleção' : 'seleções'}`;
  byId('slip-count').textContent = `${count} · Máximo ${slip.limit}`;
  byId('mobile-slip-label').textContent = `Abrir cupom · ${count}`;
  byId('clear-slip').disabled = !items.length;
  for (const [element, item] of selectionButtons) {
    if (!element.isConnected) {
      selectionButtons.delete(element);
      continue;
    }
    element.setAttribute('aria-pressed', String(isSelected(item)));
  }
  calculate();
}
function offerItem(match, market, selection) {
  return {
    matchId: match.id,
    marketId: market.id,
    selectionId: selection.id,
    matchVersion: selection.matchVersion,
    marketVersion: selection.marketVersion,
    selectionVersion: selection.selectionVersion,
    odds: selection.odds,
    startsAt: match.startsAt,
    matchLabel: `${match.homeTeam} × ${match.awayTeam}`,
    marketLabel: `${market.displayName} · ${market.period === 'FT' ? 'Tempo regulamentar' : market.period}${market.line !== 'NONE' ? ` · Linha ${oddText(market.line)}` : ''}`,
    selectionLabel: selection.displayName,
  };
}
function choose(item) {
  if (Date.parse(item.startsAt) <= Date.now()) {
    message('A partida já começou. Atualize o catálogo.');
    return;
  }
  const outcome = slip.choose(item);
  if (outcome === 'limit') {
    message(
      `Você pode escolher até ${slip.limit} partidas. Remova uma seleção para continuar.`,
    );
    return;
  }
  invalidateReview();
  renderSlip();
  message(
    outcome === 'replaced'
      ? 'Palpite substituído. O cupom mantém uma seleção por partida.'
      : outcome === 'removed'
        ? 'Seleção removida do cupom.'
        : 'Seleção adicionada ao cupom.',
  );
}
async function selections(
  target,
  match,
  market,
  index = 0,
  epoch = catalogEpoch,
) {
  const more = button('Carregar mais seleções', () => load(index + 1));
  async function load(current) {
    more.disabled = true;
    try {
      const page = await api(
        `/api/markets/${market.id}/selections?page=${current}&size=20`,
      );
      if (epoch !== catalogEpoch || !target.isConnected) return;
      if (current === 0) target.replaceChildren();
      more.remove();
      if (!page.totalElements)
        target.append(
          node(
            'p',
            'Nenhuma seleção disponível neste mercado.',
            'text-sm text-slate-400',
          ),
        );
      for (const selection of page.content) {
        const item = offerItem(match, market, selection);
        const choice = button('', () => choose(item), 'selection-choice');
        choice.append(
          node('span', selection.displayName),
          node(
            'strong',
            oddText(selection.odds),
            'tabular-nums text-emerald-300',
          ),
        );
        choice.setAttribute(
          'aria-label',
          `${selection.displayName}, cotação ${oddText(selection.odds)}`,
        );
        choice.setAttribute('aria-pressed', String(isSelected(item)));
        selectionButtons.set(choice, item);
        target.append(choice);
      }
      index = current;
      if (page.page + 1 < page.totalPages) target.append(more);
    } catch (error) {
      more.remove();
      target.append(
        node('p', error.message, 'text-sm text-amber-200'),
        button('Tentar seleções novamente', () => {
          target.replaceChildren();
          load(0);
        }),
      );
    } finally {
      more.disabled = false;
    }
  }
  await load(index);
}
async function markets(target, match, epoch) {
  let index = 0;
  const more = button('Carregar mais mercados', () => load(index + 1));
  async function load(current) {
    more.disabled = true;
    try {
      const page = await api(
        `/api/matches/${match.id}/markets?page=${current}&size=10`,
      );
      if (epoch !== catalogEpoch || !target.isConnected) return;
      if (current === 0) target.replaceChildren();
      more.remove();
      if (!page.totalElements)
        target.append(
          node(
            'p',
            'Nenhum mercado disponível. Atualize o catálogo.',
            'text-slate-400',
          ),
        );
      for (const market of page.content) {
        const section = node(
          'section',
          '',
          'space-y-3 border-t border-slate-700 pt-4',
        );
        section.append(node('h3', market.displayName, 'font-semibold'));
        section.append(
          node(
            'p',
            `Período: ${market.period === 'FT' ? 'Tempo regulamentar' : market.period} · Linha: ${market.line === 'NONE' ? 'Não se aplica' : oddText(market.line)} · Participante: ${market.participant === 'NONE' ? 'Não se aplica' : market.participant}`,
            'text-sm text-slate-300',
          ),
        );
        const rule = node('details', '', 'text-sm text-slate-300');
        rule.append(
          node(
            'summary',
            'Regra do mercado',
            'min-h-11 cursor-pointer text-emerald-300',
          ),
          node('p', market.ruleText),
        );
        section.append(rule);
        const choices = node(
          'div',
          'Carregando seleções…',
          market.marketType === '1X2'
            ? 'grid gap-2 sm:grid-cols-3'
            : 'grid gap-2 sm:grid-cols-2',
        );
        section.append(choices);
        target.append(section);
        await selections(choices, match, market, 0, epoch);
      }
      index = current;
      if (page.page + 1 < page.totalPages) target.append(more);
    } catch (error) {
      more.remove();
      target.append(
        node('p', error.message, 'text-sm text-amber-200'),
        button('Tentar mercados novamente', () => {
          target.replaceChildren();
          load(0);
        }),
      );
    } finally {
      more.disabled = false;
    }
  }
  await load(0);
}
async function catalog() {
  if (!settings || loading) return;
  loading = true;
  const epoch = ++catalogEpoch;
  selectionButtons.clear();
  const list = byId('match-list');
  list.setAttribute('aria-busy', 'true');
  list.replaceChildren(node('p', 'Carregando partidas…', 'p-4 text-slate-300'));
  for (const id of ['catalog-previous', 'catalog-next', 'refresh-catalog'])
    byId(id).disabled = true;
  try {
    const query = new URLSearchParams(new FormData(byId('catalog-filters')));
    for (const [key, value] of [...query]) if (!value) query.delete(key);
    query.set('page', pageIndex);
    query.set('size', '10');
    const page = await api(`/api/matches?${query}`);
    list.replaceChildren();
    if (!page.totalElements)
      list.append(
        node(
          'p',
          'Nenhuma partida disponível para estes filtros. Tente outra data ou competição.',
          'public-surface text-slate-300',
        ),
      );
    for (const match of page.content) {
      const card = node('article', '', 'public-surface space-y-3');
      card.append(
        node(
          'p',
          `${match.competition} · ${dateText(match.startsAt)}`,
          'text-sm text-slate-300',
        ),
        node(
          'h2',
          `${match.homeTeam} × ${match.awayTeam}`,
          'text-xl font-semibold',
        ),
      );
      const details = node('details', '', 'space-y-4');
      const summary = node(
        'summary',
        'Ver mercados e palpites',
        'min-h-11 cursor-pointer font-semibold text-emerald-300',
      );
      const target = node('div', '', 'space-y-4');
      details.append(summary, target);
      let loaded = false;
      details.addEventListener('toggle', () => {
        if (details.open && !loaded) {
          loaded = true;
          markets(target, match, epoch);
        }
      });
      card.append(details);
      list.append(card);
    }
    byId('catalog-page').textContent =
      `Página ${page.totalPages ? page.page + 1 : 0} de ${page.totalPages}`;
    byId('catalog-previous').disabled = page.page === 0;
    byId('catalog-next').disabled = page.page + 1 >= page.totalPages;
  } catch (error) {
    list.replaceChildren(
      node('p', error.message, 'public-surface text-amber-200'),
      button('Tentar catálogo novamente', catalog),
    );
    byId('catalog-page').textContent = '';
  } finally {
    loading = false;
    list.setAttribute('aria-busy', 'false');
    byId('refresh-catalog').disabled = false;
  }
}
async function allPages(path) {
  const rows = [];
  for (let index = 0; index < 100; index++) {
    const page = await api(`${path}?page=${index}&size=100`);
    rows.push(...page.content);
    if (index + 1 >= page.totalPages) return rows;
  }
  throw new Error(
    'Não foi possível conferir todas as ofertas. Revise seu cupom.',
  );
}
async function validateEntries() {
  if (!slip?.items.size) return;
  const entries = [...slip.items.values()];
  let changed = false;
  for (const previous of entries) {
    try {
      const match = await api(`/api/matches/${previous.matchId}`);
      const market = (await allPages(`/api/matches/${match.id}/markets`)).find(
        (row) => row.id === previous.marketId,
      );
      if (!market) throw new Error('Unavailable');
      const selection = (
        await allPages(`/api/markets/${market.id}/selections`)
      ).find((row) => row.id === previous.selectionId);
      if (
        !selection ||
        Date.parse(match.startsAt) <= Date.now() ||
        ['matchVersion', 'marketVersion', 'selectionVersion', 'odds'].some(
          (key) => previous[key] !== selection[key],
        )
      )
        throw new Error('Changed');
    } catch {
      if (slip.items.get(previous.matchId) === previous) {
        slip.markUnavailable(previous.matchId);
        changed = true;
      }
    }
  }
  if (changed) {
    invalidateReview();
    renderSlip();
    message(
      'Uma ou mais ofertas mudaram ou não puderam ser verificadas. Revise as seleções destacadas no cupom.',
    );
  }
}
function revalidate() {
  if (!revalidation)
    revalidation = validateEntries().finally(() => {
      revalidation = undefined;
    });
  return revalidation;
}
function modal(open, focus = true) {
  const active = open && !desktop.matches;
  panel.dataset.open = String(active);
  byId('slip-backdrop').hidden = !active;
  byId('open-slip').setAttribute('aria-expanded', String(active));
  document.body.classList.toggle('slip-open', active);
  for (const element of [
    document.querySelector('.catalog-column'),
    document.querySelector('header'),
    document.querySelector('footer'),
    document.querySelector('.mobile-slip-bar'),
  ])
    element.inert = active;
  if (active) {
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    if (focus) byId('slip-title').focus();
  } else {
    panel.removeAttribute('role');
    panel.removeAttribute('aria-modal');
    if (focus && !desktop.matches) byId('open-slip').focus();
  }
}
byId('open-slip').addEventListener('click', () => modal(true));
byId('close-slip').addEventListener('click', () => modal(false));
byId('slip-backdrop').addEventListener('click', () => modal(false));
desktop.addEventListener('change', () => modal(false, false));
panel.addEventListener('keydown', (event) => {
  if (desktop.matches || panel.dataset.open !== 'true') return;
  if (event.key === 'Escape') {
    event.preventDefault();
    modal(false);
  }
  if (event.key !== 'Tab') return;
  const focusable = [
    ...panel.querySelectorAll(
      'button:not(:disabled), input:not(:disabled), summary, a[href]',
    ),
  ].filter((element) => element.getClientRects().length);
  const first = focusable[0],
    last = focusable.at(-1);
  if (
    event.shiftKey &&
    (document.activeElement === first ||
      document.activeElement === byId('slip-title'))
  ) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
});
byId('catalog-filters').addEventListener('submit', (event) => {
  event.preventDefault();
  if (loading) return;
  pageIndex = 0;
  catalog();
});
byId('catalog-previous').addEventListener('click', () => {
  pageIndex--;
  catalog();
});
byId('catalog-next').addEventListener('click', () => {
  pageIndex++;
  catalog();
});
byId('refresh-catalog').addEventListener('click', async () => {
  message('');
  await revalidate();
  await catalog();
});
byId('clear-slip').addEventListener('click', () => {
  if (window.confirm('Limpar todas as seleções do cupom?')) {
    slip.clear();
    invalidateReview();
    renderSlip();
  }
});
byId('review-form').addEventListener('input', () => {
  invalidateReview();
  if (settings) calculate();
});
byId('bettor-phone').addEventListener('blur', () => {
  const input = byId('bettor-phone');
  const digits = input.value.trim().replace(/[\s()+-]/g, '');
  if (!/^(?:55)?[1-9]\d{9,10}$/.test(digits)) return;
  const international = digits.length > 11;
  const local = international ? digits.slice(2) : digits;
  const split = local.length === 11 ? 7 : 6;
  input.value = `${international ? '+55 ' : ''}(${local.slice(0, 2)}) ${local.slice(2, split)}-${local.slice(split)}`;
});
byId('review-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!settings || !slip) return;
  const reviewButton = byId('review-slip');
  reviewButton.disabled = true;
  try {
    await revalidate();
    const { amount, error, normalized, result } = calculate();
    const name = byId('bettor-name').value.trim();
    const rawPhone = byId('bettor-phone').value.trim();
    const phone = rawPhone.replace(/[\s()+-]/g, '');
    const nameError =
      name.length < 2 ? 'Informe seu nome, com ao menos dois caracteres.' : '';
    const phoneError = !/^(?:55)?[1-9]\d{9,10}$/.test(phone)
      ? 'Informe um telefone válido com DDD, com 10 ou 11 dígitos; o código 55 é opcional.'
      : '';
    const consentError =
      !byId('adult-consent').checked || !byId('rules-consent').checked
        ? 'Confirme a maioridade e a leitura das regras para revisar.'
        : '';
    setFieldError('name-error', 'bettor-name', nameError);
    setFieldError('phone-error', 'bettor-phone', phoneError);
    setFieldError('consent-error', null, consentError);
    const stale = [...slip.items.values()].some(
      (item) => item.needsReview || Date.parse(item.startsAt) <= Date.now(),
    );
    if (
      !slip.items.size ||
      stale ||
      amount === null ||
      error ||
      nameError ||
      phoneError ||
      consentError
    ) {
      if (amount === null)
        setFieldError(
          'stake-error',
          'stake',
          'Informe o valor do pré-bilhete.',
        );
      byId('review-feedback').textContent = !slip.items.size
        ? 'Escolha ao menos uma seleção.'
        : stale
          ? 'Remova ou escolha novamente as ofertas alteradas no catálogo.'
          : 'Confira os campos destacados antes de revisar.';
      const first = panel.querySelector('[aria-invalid="true"]');
      if (first) first.focus();
      return;
    }
    // No mutation endpoint exists in Sprint 2. Keep the reviewed request only in memory.
    const draft = {
      stakeAmount: normalized,
      bettorName: name,
      bettorPhone: phone,
      items: [...slip.items.values()].map(
        ({ selectionId, matchVersion, marketVersion, selectionVersion }) => ({
          selectionId,
          matchVersion,
          marketVersion,
          selectionVersion,
        }),
      ),
    };
    const summary = byId('review-summary');
    summary.replaceChildren(
      node(
        'h4',
        'Prévia revisada — nenhum pedido emitido',
        'font-semibold text-emerald-300',
      ),
      node('p', `${draft.bettorName} · ${draft.bettorPhone}`, 'mt-2'),
      node(
        'p',
        `${draft.items.length} seleções · Valor ${money(amount)} · Retorno potencial ${money(result.returnCents)}`,
        'mt-2',
      ),
    );
    summary.hidden = false;
    byId('review-feedback').textContent =
      'Revisão concluída. A emissão estará disponível em uma próxima etapa.';
  } finally {
    reviewButton.disabled = false;
  }
});
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && settings && !loading) revalidate();
});
setInterval(() => {
  if (!slip) return;
  let changed = false;
  for (const item of slip.items.values())
    if (!item.needsReview && Date.parse(item.startsAt) <= Date.now()) {
      slip.markUnavailable(item.matchId);
      changed = true;
    }
  if (changed) {
    invalidateReview();
    renderSlip();
  }
}, 30000);
async function initialize() {
  try {
    settings = await api('/api/config');
    if (
      !Number.isInteger(settings.maxSelections) ||
      settings.maxSelections < 1 ||
      settings.maxSelections > 10 ||
      [
        settings.minStakeAmount,
        settings.maxStakeAmount,
        settings.maxPotentialReturn,
      ].some((value) => decimalUnits(value) === null)
    )
      throw new Error(
        'Configurações indisponíveis. Tente recarregar a página.',
      );
    slip = new Slip(settings.maxSelections);
    byId('catalog-zone').textContent =
      `Datas e horários da banca: ${settings.bankTimeZone}.`;
    byId('stake-limits').textContent =
      `De ${money(decimalUnits(settings.minStakeAmount))} a ${money(decimalUnits(settings.maxStakeAmount))}. Máximo de ${slip.limit} partidas.`;
    byId('review-slip').disabled = false;
    renderSlip();
    await catalog();
  } catch (error) {
    settings = null;
    byId('match-list').setAttribute('aria-busy', 'false');
    byId('match-list').replaceChildren(
      node('p', error.message, 'public-surface text-amber-200'),
      button('Tentar novamente', initialize),
    );
  }
}
initialize();
