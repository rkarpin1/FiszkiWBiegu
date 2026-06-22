#!/bin/sh
# Jednorazowa konfiguracja po sklonowaniu repozytorium.

set -e

echo ">>> Konfiguracja git hooks..."
git config core.hooksPath .githooks
echo "    core.hooksPath = .githooks"

echo ""
echo "Gotowe. Pre-push hook aktywny — cargo test uruchomi się przed każdym push do master."
