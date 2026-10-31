// A logical attempt keeps its exact body/key until the server resolves it.
// Personal data stays in memory; reload is never used as a retry mechanism.
export class TicketIssuance {
  constructor({ element, feedback, freeze, conflict, dateText }) {
    this.element = element;
    this.feedback = feedback;
    this.freeze = freeze;
    this.conflict = conflict;
    this.dateText = dateText;
    this.draft = null;
    this.attempt = null;
    this.receipt = null;
    this.busy = false;
    this.csrf = null;
    element.addEventListener('click', () => this.submit());
  }
  get locked() {
    return Boolean(this.attempt || this.receipt);
  }
  invalidate() {
    if (this.locked) return;
    this.draft = null;
    this.element.disabled = true;
  }
  review(draft) {
    if (this.locked) return;
    this.draft = structuredClone(draft);
    this.element.disabled = false;
  }
  async token() {
    const response = await fetch('/api/session', {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) throw new Error('Session unavailable');
    this.csrf = await response.json();
    if (!this.csrf.headerName || !this.csrf.token)
      throw new Error('Invalid session');
  }
  async submit() {
    if (this.busy || this.receipt || (!this.draft && !this.attempt)) return;
    this.busy = true;
    this.element.disabled = true;
    this.freeze(true);
    try {
      // Establish the session before the first POST. Never automatically retry a POST.
      if (!this.csrf) await this.token();
      this.attempt ??= {
        key: crypto.randomUUID(),
        body: JSON.stringify(this.draft),
      };
      this.feedback.textContent = 'Gerando pré-bilhete… Aguarde nesta aba.';
      const response = await fetch('/api/tickets', {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'Idempotency-Key': this.attempt.key,
          [this.csrf.headerName]: this.csrf.token,
        },
        body: this.attempt.body,
        signal: AbortSignal.timeout(10000),
      });
      if (response.status === 200 || response.status === 201) {
        const receipt = await response.json();
        if (!receipt.code || !receipt.summary || !Array.isArray(receipt.items))
          throw new Error('Invalid receipt');
        this.receipt = receipt;
        this.render();
        return;
      }
      if (response.status === 403) {
        this.csrf = null;
        this.feedback.textContent =
          'A sessão ou a autorização expirou. Clique em Tentar novamente para atualizar a autorização e recuperar esta mesma tentativa. Se a sessão original foi perdida, a banca precisará verificar o pedido.';
      } else if (response.status === 429) {
        this.feedback.textContent = `Muitas tentativas. Aguarde ${response.headers.get('Retry-After') || '60'} segundos e use Tentar novamente. Seus dados foram preservados.`;
      } else {
        const error = await response.json().catch(() => ({}));
        if (
          response.status === 400 ||
          (response.status === 409 &&
            ['CATALOG_CHANGED', 'RULES_CHANGED'].includes(error.errorCode))
        ) {
          this.attempt = null;
          this.draft = null;
          this.freeze(false);
          this.conflict(error.errorCode);
          this.feedback.textContent =
            error.errorCode === 'RULES_CHANGED'
              ? 'As regras mudaram. Recarregue a página, leia as condições e monte uma nova revisão.'
              : response.status === 409
                ? 'As ofertas mudaram. Atualize o catálogo, escolha novamente seus palpites e revise antes de gerar.'
                : 'Confira os campos e faça uma nova revisão do cupom.';
        } else if (
          response.status === 409 &&
          error.errorCode === 'IDEMPOTENCY_CONFLICT'
        ) {
          this.feedback.textContent =
            'Não foi possível recuperar esta tentativa na sessão atual. Preserve os dados e consulte a banca antes de iniciar outro pedido.';
        } else {
          this.feedback.textContent =
            'Não foi possível confirmar o resultado. Use Tentar novamente nesta aba para consultar a mesma tentativa, sem gerar um pedido duplicado.';
        }
      }
    } catch {
      this.feedback.textContent =
        'A conexão falhou e o resultado pode ser desconhecido. Seus dados foram preservados. Use Tentar novamente nesta aba com a mesma tentativa.';
      if (!this.attempt) this.freeze(false);
    } finally {
      this.busy = false;
      this.element.textContent =
        this.attempt && !this.receipt
          ? 'Tentar novamente'
          : 'Gerar pré-bilhete';
      this.element.disabled = Boolean(
        this.receipt || (!this.draft && !this.attempt),
      );
    }
  }
  render() {
    const receipt = this.receipt;
    const target = document.getElementById('ticket-result');
    target.hidden = false;
    target.replaceChildren();
    const add = (tag, text, className = '') => {
      const item = document.createElement(tag);
      item.textContent = text;
      item.className = className;
      target.append(item);
      return item;
    };
    add('h3', 'Pré-bilhete gerado', 'text-xl font-semibold text-emerald-300');
    add('p', receipt.code, 'break-all text-2xl font-bold');
    const status = add('p', '', 'text-amber-200');
    const updateStatus = () => {
      const expired = Date.parse(receipt.expiresAt) <= Date.now();
      status.textContent =
        receipt.status === 'PENDING' && !expired
          ? 'Pré-bilhete pendente — válido somente após confirmação da banca.'
          : 'Prazo encerrado ou pedido já decidido. Consulte a banca.';
      if (expired) link?.removeAttribute('href');
    };
    add(
      'p',
      `Emitido em ${this.dateText(receipt.createdAt)}. Validade: ${this.dateText(receipt.expiresAt)}.`,
    );
    add('p', `${receipt.bettorName} · ${receipt.bettorPhone}`, 'break-words');
    add(
      'p',
      `Versão das regras: ${receipt.rulesVersion}`,
      'text-sm text-slate-400',
    );
    const full = add('textarea', '', 'catalog-input min-h-48 text-sm');
    full.id = 'persisted-summary';
    full.value = receipt.summary;
    full.readOnly = true;
    full.setAttribute(
      'aria-label',
      'Resumo completo persistido do pré-bilhete',
    );
    const copyFeedback = add('p', '', 'text-sm text-amber-200');
    copyFeedback.setAttribute('role', 'status');
    const copy = (label, value) => {
      const button = add('button', label, 'public-secondary');
      button.type = 'button';
      button.addEventListener('click', async () => {
        try {
          await navigator.clipboard.writeText(value);
          copyFeedback.textContent = 'Copiado.';
        } catch {
          full.focus();
          full.select();
          copyFeedback.textContent =
            'A cópia automática não está disponível. O resumo está selecionado: use o comando Copiar do seu dispositivo. O código aparece na segunda linha.';
        }
      });
    };
    copy('Copiar código', receipt.code);
    copy('Copiar resumo', receipt.summary);
    let link;
    if (/^[1-9]\d{7,14}$/.test(receipt.bankWhatsappNumber || '')) {
      add(
        'p',
        `WhatsApp da banca: +${receipt.bankWhatsappNumber}`,
        'break-all',
      );
      link = add(
        'a',
        'Enviar para o WhatsApp',
        'public-primary block text-center',
      );
      link.href = `https://wa.me/${receipt.bankWhatsappNumber}?text=${encodeURIComponent(receipt.whatsappMessage)}`;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.id = 'send-whatsapp';
      link.addEventListener('click', (event) => {
        if (
          Date.parse(receipt.expiresAt) <= Date.now() ||
          receipt.status !== 'PENDING'
        ) {
          event.preventDefault();
          updateStatus();
          return;
        }
        // Deliberately not awaited: telemetry failure must not prevent navigation.
        fetch('/api/tickets/whatsapp-click', {
          method: 'POST',
          credentials: 'same-origin',
          keepalive: true,
          headers: {
            'Content-Type': 'application/json',
            [this.csrf.headerName]: this.csrf.token,
          },
          body: JSON.stringify({ requestKey: this.attempt.key }),
        }).catch(() => {});
      });
    } else
      add(
        'p',
        'O contato da banca não está configurado. Copie o código e o resumo para atendimento.',
      );
    if (receipt.whatsappMessage !== receipt.summary)
      add(
        'p',
        'A mensagem do link é curta. Copie e envie também o resumo completo acima para conferir todos os palpites e regras.',
        'text-sm text-amber-200',
      );
    add(
      'p',
      'Se o WhatsApp não abrir, use o número exibido e copie o resumo. Abrir a conversa não envia a mensagem automaticamente e não confirma pagamento.',
      'text-sm text-slate-300',
    );
    updateStatus();
    setInterval(updateStatus, 1000);
    this.feedback.textContent =
      'Pedido persistido. Confira o resumo e escolha se deseja abrir o WhatsApp.';
    target.tabIndex = -1;
    target.focus();
  }
}
