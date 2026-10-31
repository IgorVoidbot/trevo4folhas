const byId = (id) => document.getElementById(id);
const forms = ['match', 'market', 'selection'].map((name) =>
  byId(`${name}-form`),
);
const [matchForm, marketForm, selectionForm] = forms;
const state = {
  match: null,
  market: null,
  selection: null,
  page: 0,
  marketPage: 0,
  selectionPage: 0,
  zone: 'America/Sao_Paulo',
};
const labels = {
  OPEN: 'Aberta',
  SUSPENDED: 'Suspensa',
  CLOSED: 'Fechada',
  CANCELLED: 'Cancelada',
};
let busy = false;
function feedback(message) {
  byId('feedback').textContent = message;
}
async function api(path, method = 'GET', body) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') {
    headers['Content-Type'] = 'application/json';
    headers[document.querySelector('meta[name="csrf-header"]').content] =
      document.querySelector('meta[name="csrf-token"]').content;
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403)
      throw new Error(
        'Sua sessão expirou ou a ação não foi autorizada. Entre novamente antes de continuar.',
      );
    if (response.status === 409)
      throw new Error(
        'O registro mudou ou a operação não é permitida. Selecione novamente o registro, confira os dados e tente outra vez.',
      );
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.message ||
        'Não foi possível concluir a operação. Confira os dados e tente novamente.',
    );
  }
  return response.json();
}
async function attempt(action) {
  try {
    await action();
  } catch (error) {
    feedback(
      error instanceof TypeError
        ? 'Falha de conexão. Seus campos foram preservados. Consulte o catálogo antes de reenviar.'
        : error.message,
    );
  }
}
function field(form, name) {
  return form.elements.namedItem(name);
}
function fill(form, data) {
  for (const element of form.elements) {
    if (!element.name || !(element.name in data)) continue;
    if (element.type === 'checkbox') element.checked = data[element.name];
    else element.value = data[element.name] ?? '';
  }
}
function values(form) {
  const result = Object.fromEntries(new FormData(form));
  for (const element of form.elements)
    if (element.type === 'checkbox') result[element.name] = element.checked;
  return result;
}
function bankLocal(instant) {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: state.zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(instant));
  const p = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}
function utcFromBank(value) {
  let instant = new Date(`${value}Z`).getTime();
  for (let i = 0; i < 3; i += 1)
    instant +=
      new Date(`${value}Z`).getTime() -
      new Date(`${bankLocal(instant)}Z`).getTime();
  if (bankLocal(instant) !== value)
    throw new Error(
      'O horário informado não existe no fuso da banca. Confira o início.',
    );
  return new Date(instant).toISOString();
}
function card(target, text, action) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'catalog-button break-words text-left';
  button.textContent = text;
  button.addEventListener('click', () => attempt(action));
  target.append(button);
}
function formatInstant(value) {
  return value
    ? new Intl.DateTimeFormat('pt-BR', {
        timeZone: state.zone,
        dateStyle: 'short',
        timeStyle: 'short',
      }).format(new Date(value))
    : 'sem evidência';
}
function evidence(item) {
  const source =
    item.sourceNamespace === 'MANUAL' ? 'Origem manual' : 'API-Football';
  const update =
    item.sourceNamespace === 'MANUAL'
      ? item.manualReviewedAt
      : item.providerUpdatedAt;
  return `${source} · Atualização: ${formatInstant(update)}${item.providerStatus !== 'OPEN' ? ' · Indisponível na origem' : ''}${item.structuralConflict ? ' · Alteração estrutural: revisão necessária' : ''}`;
}
async function loadMatches() {
  const query = new URLSearchParams(new FormData(byId('filters')));
  for (const [key, value] of [...query]) if (!value) query.delete(key);
  query.set('page', state.page);
  query.set('size', '10');
  const page = await api(`/api/admin/matches?${query}`);
  const list = byId('matches');
  list.replaceChildren();
  for (const match of page.content)
    card(
      list,
      `${match.homeTeam} × ${match.awayTeam} · ${match.competition} · ${new Intl.DateTimeFormat('pt-BR', { timeZone: state.zone, dateStyle: 'short', timeStyle: 'short' }).format(new Date(match.startsAt))} · ${labels[match.status]} · ${evidence(match)}${match.adminSuspended ? ' · Suspensão administrativa' : ''}`,
      () => selectMatch(match.id),
    );
  if (!page.totalElements) list.textContent = 'Nenhuma partida encontrada.';
  byId('page-label').textContent =
    `Página ${page.totalPages ? page.page + 1 : 0} de ${page.totalPages}`;
  byId('previous').disabled = state.page === 0;
  byId('next').disabled = state.page + 1 >= page.totalPages;
}
function resetMarket() {
  state.market = null;
  state.selection = null;
  marketForm.reset();
  selectionForm.reset();
  for (const name of ['marketType', 'period', 'line', 'participant', 'ruleKey'])
    field(marketForm, name).disabled = false;
  field(marketForm, 'adminSuspended').disabled = true;
  byId('market-title').textContent = 'Novo mercado';
  byId('selection-section').hidden = true;
}
async function selectMatch(id) {
  const match = await api(`/api/admin/matches/${id}`);
  state.match = match;
  fill(matchForm, { ...match, startsAt: bankLocal(match.startsAt) });
  field(matchForm, 'status').disabled = false;
  field(matchForm, 'adminSuspended').disabled = false;
  byId('match-title').textContent = 'Editar partida';
  resetMarket();
  byId('offers-section').hidden = false;
  state.marketPage = 0;
  byId('offers-title').textContent =
    `Mercados: ${match.homeTeam} × ${match.awayTeam}`;
  byId('offers-title').focus();
  await loadMarkets();
}
async function loadMarkets(append = false) {
  const page = await api(
    `/api/admin/matches/${state.match.id}/markets?page=${state.marketPage}&size=20`,
  );
  if (!append) byId('markets').replaceChildren();
  for (const market of page.content)
    card(
      byId('markets'),
      `${market.displayName} · ${market.period} · Linha: ${market.line === 'NONE' ? 'não se aplica' : market.line} · ${market.curated ? 'Aprovado' : 'Aguardando curadoria'} · ${evidence(market)}${market.adminSuspended ? ' · Suspenso' : ''}`,
      async () => {
        state.market = market;
        state.selection = null;
        selectionForm.reset();
        field(selectionForm, 'selectionCode').disabled = false;
        field(selectionForm, 'adminSuspended').disabled = true;
        fill(marketForm, {
          ...market,
          line: market.line === 'NONE' ? '' : market.line,
          participant: market.participant === 'NONE' ? '' : market.participant,
        });
        for (const name of [
          'marketType',
          'period',
          'line',
          'participant',
          'ruleKey',
        ])
          field(marketForm, name).disabled = true;
        field(marketForm, 'adminSuspended').disabled = false;
        byId('market-title').textContent = 'Revisar mercado';
        byId('selection-section').hidden = false;
        state.selectionPage = 0;
        field(marketForm, 'displayName').focus();
        await loadSelections();
      },
    );
  byId('more-markets').hidden = state.marketPage + 1 >= page.totalPages;
}
async function loadSelections(append = false) {
  const page = await api(
    `/api/admin/markets/${state.market.id}/selections?page=${state.selectionPage}&size=20`,
  );
  if (!append) byId('selections').replaceChildren();
  for (const selection of page.content)
    card(
      byId('selections'),
      `${selection.displayName} · Cotação ${selection.odds.replace('.', ',')} · ${selection.curated ? 'Aprovada' : 'Aguardando curadoria'} · ${evidence(selection)}${selection.adminSuspended ? ' · Suspensa' : ''}`,
      () => {
        state.selection = selection;
        fill(selectionForm, selection);
        field(selectionForm, 'selectionCode').disabled = true;
        field(selectionForm, 'adminSuspended').disabled = false;
        field(selectionForm, 'displayName').focus();
      },
    );
  byId('more-selections').hidden = state.selectionPage + 1 >= page.totalPages;
}
for (const form of forms)
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (busy) return;
    attempt(async () => {
      const data = values(form);
      let path;
      let editing;
      if (form === matchForm) {
        editing = state.match;
        data.startsAt =
          editing && data.startsAt === bankLocal(editing.startsAt)
            ? editing.startsAt
            : utcFromBank(data.startsAt);
        path = `/api/admin/matches${editing ? `/${editing.id}` : ''}`;
        if (editing) data.matchVersion = editing.matchVersion;
        else {
          delete data.status;
          delete data.adminSuspended;
        }
      } else if (form === marketForm) {
        editing = state.market;
        data.matchVersion = state.match.matchVersion;
        path = editing
          ? `/api/admin/markets/${editing.id}`
          : `/api/admin/matches/${state.match.id}/markets`;
        if (editing) data.marketVersion = editing.marketVersion;
        else delete data.adminSuspended;
      } else {
        editing = state.selection;
        data.matchVersion = state.match.matchVersion;
        data.marketVersion = state.market.marketVersion;
        data.odds = data.odds.replace(',', '.');
        path = editing
          ? `/api/admin/selections/${editing.id}`
          : `/api/admin/markets/${state.market.id}/selections`;
        if (editing) data.selectionVersion = editing.selectionVersion;
        else delete data.adminSuspended;
      }
      if (
        editing &&
        (data.adminSuspended ||
          ['SUSPENDED', 'CLOSED', 'CANCELLED'].includes(data.status)) &&
        !window.confirm(
          'Confirmar o bloqueio desta oferta? Pré-bilhetes pendentes podem ficar impedidos de confirmação.',
        )
      )
        return;
      busy = true;
      const submits = forms.map((item) =>
        item.querySelector('[type="submit"]'),
      );
      for (const submit of submits) submit.disabled = true;
      try {
        const saved = await api(path, editing ? 'PATCH' : 'POST', data);
        feedback(
          'Registro salvo. A publicação depende das condições de elegibilidade.',
        );
        if (form === matchForm) {
          await loadMatches();
          await selectMatch(saved.id);
        } else if (form === marketForm) {
          resetMarket();
          state.marketPage = 0;
          await loadMarkets();
        } else {
          state.selection = null;
          selectionForm.reset();
          field(selectionForm, 'selectionCode').disabled = false;
          field(selectionForm, 'adminSuspended').disabled = true;
          state.selectionPage = 0;
          await loadSelections();
        }
      } finally {
        busy = false;
        for (const submit of submits) submit.disabled = false;
      }
    });
  });
byId('filters').addEventListener('submit', (event) => {
  event.preventDefault();
  state.page = 0;
  attempt(loadMatches);
});
byId('previous').addEventListener('click', () => {
  state.page -= 1;
  attempt(loadMatches);
});
byId('next').addEventListener('click', () => {
  state.page += 1;
  attempt(loadMatches);
});
byId('new-match').addEventListener('click', () => {
  state.match = null;
  matchForm.reset();
  byId('match-title').textContent = 'Nova partida';
  byId('offers-section').hidden = true;
  field(matchForm, 'status').disabled = true;
  field(matchForm, 'adminSuspended').disabled = true;
  field(matchForm, 'competition').focus();
});
byId('new-market').addEventListener('click', resetMarket);
byId('new-selection').addEventListener('click', () => {
  state.selection = null;
  selectionForm.reset();
  field(selectionForm, 'selectionCode').disabled = false;
  field(selectionForm, 'adminSuspended').disabled = true;
});
byId('more-markets').addEventListener('click', () => {
  state.marketPage += 1;
  attempt(() => loadMarkets(true));
});
byId('more-selections').addEventListener('click', () => {
  state.selectionPage += 1;
  attempt(() => loadSelections(true));
});
async function syncStatus() {
  const status = await api('/api/admin/catalog-sync/status');
  byId('sync-status').textContent =
    `${status.message} Orçamento diário: ${status.dailyBudget}. Reserva: ${status.reserve}. Chamadas usadas: ${status.used ?? 'indisponível'}. Restantes para importação: ${status.remaining ?? 'indisponível'}. Última tentativa: ${formatInstant(status.lastAttemptAt)}. Último sucesso: ${formatInstant(status.lastSuccessfulSyncAt)}.`;
  byId('sync-run').disabled = !status.enabled || status.running;
}
byId('sync-refresh').addEventListener('click', () => attempt(syncStatus));
byId('sync-run').addEventListener('click', () =>
  attempt(async () => {
    if (
      !window.confirm(
        'Executar importação? Cada chamada consome o orçamento configurado do fornecedor.',
      )
    )
      return;
    byId('sync-run').disabled = true;
    try {
      const result = await api('/api/admin/catalog-sync', 'POST', {});
      feedback(result.message);
    } finally {
      await syncStatus();
    }
  }),
);
attempt(async () => {
  const config = await api('/api/config');
  state.zone = config.bankTimeZone;
  byId('bank-zone').textContent = `Datas e horários da banca: ${state.zone}.`;
  await loadMatches();
  await syncStatus();
});
