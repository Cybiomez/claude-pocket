#!/usr/bin/env bash
# Установка серверной части Claude Pocket.
# Движок сессий вынесен в отдельный модуль-ядро mycelium-mind — этот скрипт его ставит.
# Запуск:  ./install.sh   (или  curl -fsSL <адрес>/install.sh | bash)
set -euo pipefail

MIND_REPO="${MYCELIUM_MIND_REPO:-https://github.com/Cybiomez/mycelium-mind.git}"
MIND_DIR="$HOME/.mycelium-mind/repo"

say() { printf '\033[1;36m[claude-pocket]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[claude-pocket]\033[0m %s\n' "$*" >&2; exit 1; }

# Проверки
command -v node >/dev/null 2>&1 || die "Нужен Node.js >= 20 (https://nodejs.org)"
[ "$(node -e 'console.log(process.versions.node.split(".")[0])')" -ge 20 ] || die "Node.js слишком старый, нужен >= 20"
command -v npm >/dev/null 2>&1 || die "Не найден npm"
command -v git >/dev/null 2>&1 || die "Не найден git"
command -v claude >/dev/null 2>&1 || die "Не найден Claude Code CLI (npm i -g @anthropic-ai/claude-code && claude)"

# Ставим/обновляем ядро mycelium-mind и его сервис (демон на 127.0.0.1:8787)
say "Ставлю движок сессий (mycelium-mind)…"
mkdir -p "$(dirname "$MIND_DIR")"
if [ -d "$MIND_DIR/.git" ]; then
  git -C "$MIND_DIR" pull --ff-only
else
  rm -rf "$MIND_DIR"
  git clone --depth 1 "$MIND_REPO" "$MIND_DIR"
fi
bash "$MIND_DIR/deploy/install.sh"

say "Готово. Клиент Claude Pocket подключается к демону на 127.0.0.1:8787 (токен ~/.claude-pocket/token)."
