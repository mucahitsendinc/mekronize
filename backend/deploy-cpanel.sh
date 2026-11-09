#!/usr/bin/env bash
# Yerelde çalıştır: cPanel'e yüklenecek mekronize-backend.zip dosyasını üretir.
set -e
cd "$(dirname "$0")"
composer install --no-dev --optimize-autoloader --no-interaction
rm -f mekronize-backend.zip
zip -rq mekronize-backend.zip . \
  -x ".env" ".git/*" "node_modules/*" "tests/*" "storage/logs/*" "storage/framework/sessions/*" \
     "storage/app/private/*" "database/*.sqlite" "mekronize-backend.zip"
echo "Hazır: $(pwd)/mekronize-backend.zip"
composer install --no-interaction >/dev/null 2>&1 || true
