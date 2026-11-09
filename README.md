# Mekronize

    backend/   Laravel 13 — web panel (içerik, playlist, sync key) + senkron API (cPanel'e uygun)
    android/   Kotlin — çevrimdışı oynatıcı, senkron, Android Auto

Akış: web panelde giriş → size ait **sync key** → Android uygulamasında key ile senkron → liste cihaza iner,
silinenler cihazdan kalkar. İçerik YouTube linki (cihaz indirir, sunucuda depolama yok) ya da dosya yükleme (sunucuda saklanır) olabilir.
Detaylar için `backend/README.md` ve `android/README.md`.

## Yasal uyarı
Android uygulaması, YouTube bağlantılarını cihazda indirebilen bir modül içerir. YouTube kullanım şartları kendi sunduğu yollar dışında indirmeyi yasaklar ve içerik telif hakkıyla korunabilir.
Bu yazılım yalnızca hakkına sahip olduğun ya da indirme izni bulunan içerikler için, kişisel kullanım amacıyla sunulmuştur. Kullanımın sorumluluğu kullanıcıya aittir.
