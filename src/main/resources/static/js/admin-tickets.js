const byId = (id) => document.getElementById(id);
const labels = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmado',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};
let zone = 'America/Sao_Paulo',
  pageIndex = 0,
  loading = false,
  current = null,
  command = null,
  saving = false,
  opener;
const dialog = byId('decision-dialog');
const feedback = (text) => {
  byId('ticket-feedback').textContent = text;
};
function date(value) {
  return value
    ? new Intl.DateTimeFormat('pt-BR', {
        timeZone: zone,
        dateStyle: 'short',
        timeStyle: 'short',
      }).format(new Date(value))
    : '—';
}
function money(value) {
  const [whole, fraction] = value.split('.');
  return `R$ ${BigInt(whole).toLocaleString('pt-BR')},${(fraction || '').padEnd(2, '0')}`;
}
function node(tag, text, className = '') {
  const el = document.createElement(tag);
  el.textContent = text;
  el.className = className;
  return el;
}
function button(text, action) {
  const el = node('button', text, 'public-secondary');
  el.type = 'button';
  el.addEventListener('click', action);
  return el;
}
async function api(path, body) {
  const headers = { Accept: 'application/json' };
  if (body) {
    headers['Content-Type'] = 'application/json';
    headers[document.querySelector('meta[name="csrf-header"]').content] =
      document.querySelector('meta[name="csrf-token"]').content;
  }
  const response = await fetch(path, {
    method: body ? 'POST' : 'GET',
    headers,
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
    cache: 'no-store',
    signal: AbortSignal.timeout(10000),
  });
  const data = await response.json().catch(() => ({}));
  if (response.ok) return data;
  const error = new Error(
    response.status === 401 || response.status === 403
      ? 'Sua sessão expirou ou o operador está inativo. Entre novamente antes de continuar.'
      : data.message ||
          'Não foi possível concluir a operação. Confira os campos e tente novamente.',
  );
  error.status = response.status;
  error.current = data.current;
  throw error;
}
function problem(error) {
  return error.name === 'TimeoutError' || error instanceof TypeError
    ? 'Falha de conexão. O resultado pode ser desconhecido. Atualize o detalhe antes de decidir novamente; os campos foram preservados.'
    : error.message;
}
function effective(ticket) {
  return ticket.status === 'PENDING' &&
    Date.parse(ticket.expiresAt) <= Date.now()
    ? 'EXPIRED'
    : ticket.status;
}
function renderDetail(detail) {
  current = detail;
  const t = detail.ticket,
    status = effective(t),
    target = byId('ticket-detail');
  target.replaceChildren();
  target.append(
    node('h2', t.code, 'break-all text-xl font-semibold text-emerald-300'),
    node('p', labels[status], 'font-semibold text-amber-200'),
    node('p', `${t.bettorName} · ${t.bettorPhone}`, 'break-words'),
    node(
      'p',
      `Valor: ${money(t.stakeAmount)} · Retorno potencial: ${money(t.potentialReturn)}`,
    ),
    node('p', `Emissão: ${date(t.createdAt)} · Validade: ${date(t.expiresAt)}`),
    node(
      'p',
      `Versão: ${detail.ticketVersion} · Regras: ${t.rulesVersion}`,
      'break-words text-sm text-slate-400',
    ),
  );
  if (detail.affectedByCancelledMatch)
    target.append(
      node(
        'p',
        status === 'CONFIRMED'
          ? 'Atenção: partida cancelada em bilhete confirmado. Preserve o registro e trate eventuais acordos manualmente fora do sistema.'
          : 'Atenção: uma partida deste pré-bilhete foi cancelada. A confirmação está bloqueada; avalie cancelar o pedido enquanto válido.',
        'rounded-xl border border-amber-500 p-3 text-amber-200',
      ),
    );
  for (const item of t.items) {
    const card = node(
      'article',
      '',
      'space-y-2 border-t border-slate-700 pt-3',
    );
    card.append(
      node(
        'h3',
        `${item.homeTeamSnapshot} × ${item.awayTeamSnapshot}`,
        'font-semibold break-words',
      ),
      node(
        'p',
        `${item.competitionSnapshot} · ${date(item.matchDateSnapshot)}`,
        'text-sm text-slate-300',
      ),
      node(
        'p',
        `${item.marketSnapshot} · ${item.selectionSnapshot} · Cotação ${String(item.selectedOdds).replace('.', ',')}`,
        'break-words',
      ),
      node(
        'p',
        `Período: ${item.periodSnapshot === 'FT' ? 'Tempo regulamentar' : item.periodSnapshot} · Linha: ${item.lineSnapshot === 'NONE' ? 'Não se aplica' : item.lineSnapshot} · Participante: ${item.participantSnapshot === 'NONE' ? 'Não se aplica' : item.participantSnapshot}`,
        'break-words text-sm',
      ),
      node(
        'p',
        item.ruleSnapshot,
        'break-words whitespace-pre-wrap text-sm text-slate-300',
      ),
    );
    target.append(card);
  }
  if (detail.handledAt)
    target.append(
      node(
        'p',
        `Responsável: ${detail.handledByName} · Decisão em ${date(detail.handledAt)}`,
        'break-words',
      ),
    );
  if (detail.cancellationReason)
    target.append(
      node(
        'p',
        `Motivo: ${detail.cancellationReason}`,
        'break-words whitespace-pre-wrap',
      ),
    );
  if (detail.operatorNote)
    target.append(
      node(
        'p',
        `Observação: ${detail.operatorNote}`,
        'break-words whitespace-pre-wrap',
      ),
    );
  const refresh = button('Atualizar detalhe', () => loadDetail(t.id));
  target.append(refresh);
  if (status === 'PENDING') {
    const confirm = button('Conferir Pix e confirmar', (event) =>
      openDecision('confirm', event.currentTarget),
    );
    confirm.id = 'confirm-ticket';
    confirm.disabled = detail.affectedByCancelledMatch;
    target.append(confirm);
    const cancel = button('Cancelar pré-bilhete', (event) =>
      openDecision('cancel', event.currentTarget),
    );
    cancel.id = 'cancel-ticket';
    target.append(cancel);
  } else if (status === 'EXPIRED')
    target.append(
      node(
        'p',
        'Prazo encerrado. Pagamento após o vencimento exige atendimento manual fora do sistema. O pré-bilhete não pode ser reativado.',
        'text-amber-200',
      ),
    );
}
async function loadDetail(id) {
  if (saving) return;
  try {
    renderDetail(await api(`/api/admin/tickets/${id}`));
    byId('ticket-detail').focus();
  } catch (error) {
    feedback(problem(error));
  }
}
async function loadQueue() {
  if (loading) return;
  loading = true;
  byId('apply-ticket-filters').disabled = true;
  byId('ticket-list').setAttribute('aria-busy', 'true');
  for (const id of ['ticket-previous', 'ticket-next', 'refresh-tickets'])
    byId(id).disabled = true;
  try {
    const query = new URLSearchParams(new FormData(byId('ticket-filters')));
    for (const [key, value] of [...query]) if (!value) query.delete(key);
    query.set('page', pageIndex);
    query.set('size', '20');
    const data = await api(`/api/admin/tickets?${query}`);
    zone = data.bankTimeZone;
    byId('ticket-zone').textContent =
      `Datas de emissão e horários no fuso ${zone}.`;
    byId('ticket-counts').textContent =
      `Contagens desta consulta: ${Object.entries(data.counts)
        .map(([key, value]) => `${labels[key]}: ${value}`)
        .join(' · ')}`;
    const list = byId('ticket-list');
    list.replaceChildren();
    if (!data.tickets.totalElements)
      list.append(
        node(
          'p',
          'Nenhum pré-bilhete encontrado para estes filtros.',
          'public-surface',
        ),
      );
    for (const t of data.tickets.content) {
      const card = node('article', '', 'public-surface space-y-2');
      card.append(
        node('h2', t.code, 'break-all font-semibold'),
        node('p', `${labels[t.status]} · ${t.bettorName}`, 'break-words'),
        node('p', t.bettorPhone, 'break-all text-sm'),
        node(
          'p',
          `${money(t.stakeAmount)} · Retorno potencial ${money(t.potentialReturn)}`,
        ),
        node(
          'p',
          `Emissão ${date(t.createdAt)} · Validade ${date(t.expiresAt)}`,
          'text-sm text-slate-400',
        ),
      );
      if (t.affectedByCancelledMatch)
        card.append(
          node('p', 'Atenção: inclui partida cancelada.', 'text-amber-200'),
        );
      card.append(button('Ver pré-bilhete', () => loadDetail(t.id)));
      list.append(card);
    }
    byId('ticket-page').textContent =
      `Página ${data.tickets.totalPages ? pageIndex + 1 : 0} de ${data.tickets.totalPages}`;
    byId('ticket-previous').disabled = pageIndex === 0;
    byId('ticket-next').disabled = pageIndex + 1 >= data.tickets.totalPages;
  } catch (error) {
    feedback(problem(error));
    byId('ticket-list').replaceChildren(
      node(
        'p',
        'Não foi possível carregar a fila. Use Atualizar fila para tentar novamente.',
      ),
    );
  } finally {
    loading = false;
    byId('apply-ticket-filters').disabled = false;
    byId('ticket-list').setAttribute('aria-busy', 'false');
    byId('refresh-tickets').disabled = false;
  }
}
function openDecision(action, source) {
  if (saving || !current || effective(current.ticket) !== 'PENDING') return;
  byId('save-decision').disabled = false;
  command = { action, id: current.ticket.id, version: current.ticketVersion };
  opener = source;
  byId('decision-title').textContent =
    action === 'confirm'
      ? 'Confirmar após conferir o Pix'
      : 'Cancelar pré-bilhete pendente';
  byId('decision-context').textContent =
    `${current.ticket.code} · ${current.ticket.bettorName} · ${money(current.ticket.stakeAmount)} · Válido até ${date(current.ticket.expiresAt)}.`;
  byId('pix-field').hidden = action !== 'confirm';
  byId('reason-field').hidden = action !== 'cancel';
  byId('pix-checked').checked = false;
  byId('cancel-reason').value = '';
  byId('operator-note').value = '';
  byId('decision-feedback').textContent = '';
  dialog.showModal();
  (action === 'confirm' ? byId('pix-checked') : byId('cancel-reason')).focus();
}
function closeDecision() {
  if (saving) return;
  dialog.close();
  if (opener?.isConnected) opener.focus();
  else byId('ticket-detail').focus();
}
byId('close-decision').addEventListener('click', closeDecision);
dialog.addEventListener('cancel', (event) => {
  if (saving) event.preventDefault();
});
byId('decision-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (saving || !command) return;
  const body = {
    ticketVersion: command.version,
    operatorNote: byId('operator-note').value.trim() || null,
  };
  if (command.action === 'confirm') {
    if (!byId('pix-checked').checked) {
      byId('decision-feedback').textContent =
        'Declare a conferência do Pix antes de confirmar.';
      byId('pix-checked').focus();
      return;
    }
    body.pixReceiptChecked = true;
  } else {
    body.reason = byId('cancel-reason').value.trim();
    if (body.reason.length < 2) {
      byId('decision-feedback').textContent =
        'Informe um motivo com ao menos dois caracteres.';
      byId('cancel-reason').focus();
      return;
    }
  }
  saving = true;
  for (const el of byId('decision-form').elements) el.disabled = true;
  try {
    const result = await api(
      `/api/admin/tickets/${command.id}/${command.action}`,
      body,
    );
    renderDetail(result.current);
    feedback(
      command.action === 'confirm'
        ? 'Pré-bilhete confirmado após a conferência declarada do Pix.'
        : 'Pré-bilhete cancelado.',
    );
    dialog.close();
    byId('ticket-detail').focus();
    await loadQueue();
  } catch (error) {
    if (error.status === 409) {
      if (error.current) renderDetail(error.current);
      else {
        try {
          renderDetail(await api(`/api/admin/tickets/${command.id}`));
        } catch {
          /* Preserve the known state and require a manual refresh. */
        }
      }
      feedback(problem(error));
      dialog.close();
      byId('ticket-detail').focus();
      await loadQueue();
    } else {
      byId('decision-feedback').textContent = problem(error);
      if (!error.status || error.status >= 500) {
        command = null;
        byId('decision-feedback').textContent +=
          ' Feche esta janela e atualize o detalhe para consultar o resultado antes de nova decisão.';
      }
    }
  } finally {
    saving = false;
    for (const el of byId('decision-form').elements) el.disabled = false;
    byId('save-decision').disabled = command === null;
  }
});
byId('ticket-filters').addEventListener('submit', (event) => {
  event.preventDefault();
  pageIndex = 0;
  feedback('');
  loadQueue();
});
byId('refresh-tickets').addEventListener('click', () => {
  feedback('');
  loadQueue();
});
byId('ticket-previous').addEventListener('click', () => {
  pageIndex--;
  loadQueue();
});
byId('ticket-next').addEventListener('click', () => {
  pageIndex++;
  loadQueue();
});
setInterval(() => {
  if (
    current &&
    !dialog.open &&
    effective(current.ticket) !== current.ticket.status
  )
    renderDetail(current);
}, 1000);
loadQueue();
