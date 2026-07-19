// Разведка 2: streaming input mode — usage/context на живом процессе, слеш-команда, interrupt
import { query } from '@anthropic-ai/claude-agent-sdk';

const log = (...a) => console.log('[p2]', ...a);

// Управляемый асинх-итератор входных сообщений
function makeInput() {
  const queue = []; let notify = null; let closed = false;
  return {
    push(m) { queue.push(m); notify?.(); },
    close() { closed = true; notify?.(); },
    async *[Symbol.asyncIterator]() {
      while (true) {
        while (queue.length) yield queue.shift();
        if (closed) return;
        await new Promise(r => { notify = r; });
        notify = null;
      }
    },
  };
}

const input = makeInput();
const q = query({
  prompt: input,
  options: {
    cwd: '/home/alex',
    permissionMode: 'bypassPermissions',
    allowDangerouslySkipPermissions: true,
    includePartialMessages: true,
    resume: '72a2418f-b6a9-478c-9772-1fa59adc6288',
  },
});

input.push({ type: 'user', message: { role: 'user', content: '/context' }, parent_tool_use_id: null, session_id: '' });

let phase = 1;
const t0 = Date.now();
let interruptTimer = null;
for await (const msg of q) {
  if (msg.type === 'system' && msg.subtype === 'init') log('init session:', msg.session_id);
  if (msg.type === 'assistant') {
    const texts = msg.message.content.filter(b => b.type === 'text').map(b => b.text.slice(0, 80));
    log('assistant:', JSON.stringify(texts));
  }
  if (msg.type === 'system' && msg.subtype !== 'init') log('system msg:', msg.subtype, JSON.stringify(msg).slice(0, 200));
  if (msg.type === 'result') {
    log(`result (phase ${phase}):`, msg.subtype, JSON.stringify((msg.result || '').slice(0, 120)));
    if (phase === 1) {
      // Живой процесс: дёргаем usage и context
      try {
        const usage = await q.usage_EXPERIMENTAL_MAY_CHANGE_DO_NOT_RELY_ON_THIS_API_YET();
        log('USAGE OK: available=', usage.rate_limits_available, 'sub=', usage.subscription_type,
          '5h=', usage.rate_limits?.five_hour?.utilization + '%', 'resets', usage.rate_limits?.five_hour?.resets_at,
          '7d=', usage.rate_limits?.seven_day?.utilization + '%');
      } catch (e) { log('usage FAIL:', e.message); }
      try {
        const ctx = await q.getContextUsage();
        log('CONTEXT OK:', ctx.totalTokens, '/', ctx.maxTokens, '=', ctx.percentage + '%', 'model:', ctx.model);
      } catch (e) { log('getContextUsage FAIL:', e.message); }
      phase = 2;
      // Ход 2: длинная задача, прервём через 6с
      input.push({ type: 'user', message: { role: 'user', content: 'Посчитай медленно от 1 до 50, выводя каждое число отдельной строкой с пояснением' }, parent_tool_use_id: null, session_id: '' });
      interruptTimer = setTimeout(async () => {
        log('>>> interrupt()');
        try { await q.interrupt(); log('interrupt OK'); } catch (e) { log('interrupt FAIL:', e.message); }
      }, 6000);
    } else {
      clearTimeout(interruptTimer);
      input.close();
    }
  }
}
log('done in', ((Date.now() - t0) / 1000).toFixed(1) + 's');
