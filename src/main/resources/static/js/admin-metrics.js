const byId = (id) => document.getElementById(id);
const money = (value) => {
  if (value === null) return '—';
  const [whole, fraction] = value.split('.');
  return `R$ ${BigInt(whole).toLocaleString('pt-BR')},${(fraction || '').padEnd(2, '0')}`;
};
function rows(id, values) {
  const target = byId(id);
  target.replaceChildren();
  for (const [label, value] of values) {
    const cell = document.createElement('div');
    cell.className = 'min-w-0 rounded-xl border border-slate-700 p-3';
    const title = document.createElement('dt');
    title.textContent = label;
    title.className = 'text-sm text-slate-300';
    const data = document.createElement('dd');
    data.textContent = value;
    data.className = 'mt-2 break-words text-xl font-semibold text-emerald-300';
    cell.append(title, data);
    target.append(cell);
  }
}
const percent = (value) =>
  value === null ? '—' : `${value.replace('.', ',')}%`;
const duration = (value) =>
  value === null ? '—' : `${value.replace('.', ',')} s`;
let busy = false;
async function load() {
  if (busy) return;
  busy = true;
  byId('load-metrics').disabled = true;
  byId('metrics-feedback').textContent = 'Carregando indicadores…';
  try {
    const query = new URLSearchParams(new FormData(byId('metrics-filters')));
    for (const [key, value] of [...query]) if (!value) query.delete(key);
    const response = await fetch(`/api/admin/metrics?${query}`, {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(
        response.status === 401 || response.status === 403
          ? 'Sessão expirada ou operador inativo. Entre novamente.'
          : error.message || 'Não foi possível consultar os indicadores.',
      );
    }
    const data = await response.json(),
      c = data.cohort,
      f = data.confirmed;
    byId('metrics-filters').elements.from.value = data.from;
    byId('metrics-filters').elements.to.value = data.to;
    const date = new Intl.DateTimeFormat('pt-BR', {
      timeZone: data.bankTimeZone,
      dateStyle: 'short',
      timeStyle: 'short',
    });
    byId('metrics-zone').textContent =
      `Fuso da banca: ${data.bankTimeZone}. Consulta em ${date.format(new Date(data.asOf))}.`;
    rows('cohort-values', [
      ['Emitidos', c.emitted],
      ['Pendentes válidos', c.validPending],
      ['Expirados efetivos', c.effectiveExpired],
      ['Cancelados', c.cancelled],
      ['Confirmados desta coorte', c.confirmed],
      ['Cliques observados no WhatsApp', c.whatsappClicks],
    ]);
    byId('cohort-state').textContent = !c.emitted
      ? 'Sem emissões no período; conversões sem base.'
      : c.closed
        ? 'Coorte encerrada: validade de todos os bilhetes concluída.'
        : `Coorte aberta até ${date.format(new Date(c.closesAt))}. Aguarde o encerramento para comparar conversões.`;
    rows('conversion-values', [
      ['Emissão → clique observado', percent(c.clickConversionPercent)],
      ['Emissão → confirmação', percent(c.confirmationConversionPercent)],
      ['Expiração da coorte', percent(c.expirationPercent)],
    ]);
    rows('confirmed-values', [
      ['Confirmados por horário da decisão', f.count],
      ['Volume confirmado', money(f.volume)],
      ['Ticket médio confirmado', money(f.averageStake)],
      [
        'Retornos potenciais dos bilhetes selecionados',
        money(f.potentialReturns),
      ],
      ['Mediana até confirmação', duration(f.medianSeconds)],
      ['p90 até confirmação', duration(f.p90Seconds)],
    ]);
    byId('timing-sample').textContent =
      `Amostra dos tempos: ${f.timingSampleSize} confirmações. Datas de emissão e de decisão são eixos distintos; este volume não é o numerador da conversão da coorte.`;
    byId('metrics-warning').textContent = [
      f.affectedByCancelledMatch
        ? `${f.affectedByCancelledMatch} confirmados incluem partida cancelada. Histórico e volume preservados; atendimento manual necessário.`
        : '',
      f.invalidTimingRecords
        ? `${f.invalidTimingRecords} registros têm datas inconsistentes e foram excluídos somente da amostra de tempos. Revise a operação.`
        : '',
    ]
      .filter(Boolean)
      .join(' ');
    byId('metrics-feedback').textContent = 'Indicadores atualizados.';
  } catch (error) {
    byId('metrics-feedback').textContent =
      error instanceof TypeError || error.name === 'TimeoutError'
        ? 'Falha de conexão. Tente atualizar novamente; confira o horário da última consulta.'
        : error.message;
  } finally {
    busy = false;
    byId('load-metrics').disabled = false;
  }
}
byId('metrics-filters').addEventListener('submit', (event) => {
  event.preventDefault();
  load();
});
load();
