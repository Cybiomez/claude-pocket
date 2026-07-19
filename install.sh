#!/usr/bin/env bash
# Установка claude-pocketd — демона для Android-клиента Claude Pocket.
# Запуск:  curl -fsSL <адрес>/install.sh | bash
# или из клона репозитория:  ./install.sh
set -euo pipefail

REPO_URL="${CLAUDE_POCKET_REPO:-https://github.com/Cybiomez/claude-pocket.git}"
INSTALL_DIR="$HOME/.claude-pocket/app"
DATA_DIR="$HOME/.claude-pocket"

say() { printf '\033[1;36m[claude-pocket]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[claude-pocket]\033[0m %s\n' "$*" >&2; exit 1; }

# 1. Проверки
command -v node >/dev/null 2>&1 || die "Нужен Node.js >= 20 (не найден node). Установи: https://nodejs.org"
NODE_MAJOR=$(node -e 'console.log(process.versions.node.split(".")[0])')
[ "$NODE_MAJOR" -ge 20 ] || die "Node.js слишком старый ($(node --version)), нужен >= 20"
command -v npm >/dev/null 2>&1 || die "Не найден npm"
command -v claude >/dev/null 2>&1 || die "Не найден Claude Code CLI. Установи его и залогинься: npm i -g @anthropic-ai/claude-code && claude"
command -v git >/dev/null 2>&1 || die "Не найден git"

# 2. Код демона
mkdir -p "$DATA_DIR"
if [ -f "$(dirname "$0")/daemon/src/server.mjs" ] 2>/dev/null; then
  # Запуск из клона репо
  SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
  say "Использую локальный код: $SRC_DIR"
  mkdir -p "$INSTALL_DIR"
  cp -r "$SRC_DIR/daemon/src" "$SRC_DIR/daemon/package.json" "$SRC_DIR/daemon/package-lock.json" "$INSTALL_DIR/" 2>/dev/null || \
  cp -r "$SRC_DIR/daemon/src" "$SRC_DIR/daemon/package.json" "$INSTALL_DIR/"
else
  say "Клонирую $REPO_URL"
  if [ -d "$INSTALL_DIR/.git" ]; then
    git -C "$INSTALL_DIR" pull --ff-only
  else
    rm -rf "$INSTALL_DIR"
    git clone --depth 1 "$REPO_URL" "$INSTALL_DIR.tmp"
    mkdir -p "$INSTALL_DIR"
    cp -r "$INSTALL_DIR.tmp/daemon/src" "$INSTALL_DIR.tmp/daemon/package.json" "$INSTALL_DIR.tmp/daemon/package-lock.json" "$INSTALL_DIR/" 2>/dev/null || true
    rm -rf "$INSTALL_DIR.tmp"
  fi
fi

# 3. Зависимости
say "Ставлю npm-зависимости…"
cd "$INSTALL_DIR"
npm install --omit=dev --no-audit --no-fund --loglevel=error

# 4. Первый запуск создаст config.json и token
say "Инициализация конфига и токена…"
node -e "import('./src/config.mjs').then(m => { m.loadConfig(); m.loadToken(); console.log('ok'); })"
chmod 600 "$DATA_DIR/token"

# 5. systemd-сервис (system-level, от текущего пользователя — переживает logout)
SERVICE_FILE=/etc/systemd/system/claude-pocketd.service
say "Регистрирую systemd-сервис (нужен sudo)…"
sudo tee "$SERVICE_FILE" > /dev/null <<UNIT
[Unit]
Description=Claude Pocket daemon (Android-клиент для Claude Code)
After=network.target

[Service]
Type=simple
User=$USER
Environment=HOME=$HOME
Environment=PATH=$PATH
WorkingDirectory=$INSTALL_DIR
ExecStart=$(command -v node) $INSTALL_DIR/src/server.mjs
Restart=always
RestartSec=3
# Демон слушает только 127.0.0.1 — снаружи недоступен
[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now claude-pocketd
sleep 1
if systemctl is-active --quiet claude-pocketd; then
  say "Демон запущен: $(curl -s http://127.0.0.1:8787/api/health || echo 'порт ещё поднимается')"
else
  die "Сервис не стартовал. Логи: journalctl -u claude-pocketd -n 50"
fi

say ""
say "Готово! Что дальше:"
say "  1. Поставь APK Claude Pocket на телефон"
say "  2. В приложении укажи: адрес этого сервера, SSH-порт, логин и пароль/ключ"
say "  3. Приложение само найдёт демон и заберёт токен"
say ""
say "Рабочий каталог агента: \$HOME ($HOME). Изменить: $DATA_DIR/config.json + sudo systemctl restart claude-pocketd"
