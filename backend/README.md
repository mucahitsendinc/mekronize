# Mekronize Backend (Laravel)

Web panel + senkron API. YouTube linkleri sadece bağlantı olarak saklanır; dosya yüklemeleri `storage/app/private/media` altında tutulur.

## Yerel çalıştırma
    composer install && cp .env.example .env && php artisan key:generate
    # .env içinde DB_CONNECTION=sqlite yapıp: touch database/database.sqlite
    php artisan migrate && php artisan serve

## cPanel kurulumu
1. **PHP 8.3**: cPanel > MultiPHP Manager. Gerekli eklentiler: mbstring, openssl, pdo_mysql, fileinfo, curl.
2. **MySQL**: cPanel > MySQL Veritabanları'ndan veritabanı + kullanıcı oluştur.
3. `./deploy-cpanel.sh` ile `mekronize-backend.zip` üret, File Manager ile `/home/KULLANICI/mekronize/` içine yükle ve aç (`public_html` DIŞINDA).
4. cPanel > Alan Adları > alt alan adı (örn. `api.alanadin.com`) oluştur, **Document Root = `/home/KULLANICI/mekronize/public`**.
5. `.env.example` dosyasını `.env` yap, bilgileri doldur. Terminal (veya SSH) ile:
       cd ~/mekronize && php artisan key:generate --force && php artisan migrate --force && php artisan optimize
   Terminal yoksa: yerelde `php artisan key:generate --show` ile APP_KEY al, yerelde migrate edip SQL'i phpMyAdmin'den içe aktar.
6. AutoSSL ile HTTPS'i etkinleştir (Android uygulaması https ister).
7. Büyük dosya yükleme web arayüzünde 4 MB'lık parçalarla yapıldığı için PHP `upload_max_filesize` yükseltmek gerekmez. Yine de `post_max_size` ≥ 8M olmalı. Kota: cPanel disk alanını izle.
8. `storage/` ve `bootstrap/cache/` yazılabilir olmalı (755/775).

## Giriş modeli
Ad/e-posta/parola yok. Hesap = **6 haneli sayısal key**. Girişte "Mevcut key'im var" (key'i yaz) ya da "Yeni giriş" (yeni key üretilir, otomatik giriş) seçilir.
Key'i saatte en fazla **4 kez** değiştirebilirsin. Yanlış key denemesi IP başına saatte 20 ile sınırlıdır; yeni giriş IP başına saatte 10.
Eski sürümden yükseltirken: `php artisan migrate --force` sonra `php artisan mekronize:keys` (mevcut kullanıcıların yeni key'lerini listeler).

## API
- `POST /api/v1/sync` `{ "key": "123456" }` → `{ items:[…], playlists:[…] }` (dakikada 20 istek sınırı)
- `GET /api/v1/media/{id}/download?signature=…` imzalı, 12 saat geçerli, `Range` destekli.
