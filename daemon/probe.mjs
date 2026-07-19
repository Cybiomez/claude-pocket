// Разведка Agent SDK: сессии, resume, стрим, слеш-команды, usage
import { query } from '@anthropic-ai/claude-agent-sdk';

const log = (...a) => console.log('[probe]', ...a);

async function runTurn(promptText, resumeId) {
  const opts = {
    cwd: '/home/alex',
    permissionMode: 'bypassPermissions',
    allowDangerouslySkipPermissions: true,
    includePartialMessages: true,
    ...(resumeId ? { resume: resumeId } : {}),
  };
  const q = query({ prompt: promptText, options: opts });
  let sessionId = null, initInfo = null, resultText = null, deltas = 0;
  for await (const msg of q) {
    if (msg.type === 'system' && msg.subtype === 'init') {
      sessionId = msg.session_id;
      initInfo = { model: msg.model, permissionMode: msg.permissionMode, slash: msg.slash_commands.length, version: msg.claude_code_version };
      log('init:', JSON.stringify(initInfo), 'session:', sessionId);
      // Дёргаем экспериментальные методы прямо в живой сессии
      try {
        const cmds = await q.supportedCommands();
        log('supportedCommands:', cmds.length, 'например:', cmds.slice(0, 8).map(c => c.name).join(', '));
      } catch (e) { log('supportedCommands FAIL:', e.message); }
    }
    if (msg.type === 'stream_event') deltas++;
    if (msg.type === 'result') {
      resultText = msg.subtype === 'success' ? msg.result : `[${msg.subtype}]`;
      log('result:', JSON.stringify(resultText?.slice(0, 100)), 'cost:', msg.total_cost_usd, 'turns:', msg.num_turns, 'session:', msg.session_id);
      try {
        const usage = await q.usage_EXPERIMENTAL_MAY_CHANGE_DO_NOT_RELY_ON_THIS_API_YET();
        log('usage: available=', usage.rate_limits_available, 'sub=', usage.subscription_type,
          '5h=', usage.rate_limits?.five_hour?.utilization, '7d=', usage.rate_limits?.seven_day?.utilization);
      } catch (e) { log('usage FAIL:', e.message); }
      try {
        const ctx = await q.getContextUsage();
        log('context:', ctx.totalTokens, '/', ctx.maxTokens, '=', ctx.percentage + '%');
      } catch (e) { log('getContextUsage FAIL:', e.message); }
    }
  }
  log('stream deltas:', deltas);
  return sessionId;
}

const t0 = Date.now();
log('=== Ход 1: новая сессия ===');
const sid = await runTurn('Ответь ровно одним словом: пинг');
log('=== Ход 2: resume той же сессии ===');
const sid2 = await runTurn('Каким словом ты только что ответил? Ответь одним словом.', sid);
log('same session id:', sid === sid2, sid, '→', sid2);
log('done in', ((Date.now() - t0) / 1000).toFixed(1) + 's');
