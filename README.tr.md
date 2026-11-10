<div align="center">

# 🎵 Mekronize

**Kendi müzik ve video kütüphanen — web'den yönet, Android'de internetsiz çal.**

[English](README.md) · [Türkçe](README.tr.md)

![Laravel](https://img.shields.io/badge/Laravel-13-FF2D20?logo=laravel&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)
![APK](https://img.shields.io/badge/APK-~4.7%20MB-5b5bf0)

</div>

## Neden Mekronize?

Akış uygulamaları internet, reklam ve abonelik ister; telefonun ya da arabanın çoğu zaman bunların hiçbiri yoktur.
Mekronize ile kütüphaneni rahat bir web panelinde bir kez hazırlarsın, sonra **telefonuna senkronlayıp internet olmadan çalarsın**. Arayüz araba dostu, büyük kutucuklu ve Android Auto ile de çalışır.

Hesap, e-posta ya da şifre yok. Kimliğin tek bir **6 haneli key**.

## Nasıl çalışır?

```
 ┌──────────────┐  linkler / dosyalar ┌──────────────┐   sync key     ┌──────────────────┐
 │  Web panel   │ ──────────────────▶ │ Laravel API  │ ◀───────────── │  Android uygulama │
 │ (her şey)    │                     │ (cPanel'e ok)│ ──────────────▶│  çevrimdışı oynatıcı │
 └──────────────┘                     └──────────────┘   liste        └──────────────────┘
                                                                  dosyaları / YouTube'u indirir
```

1. Web panele 6 haneli key ile gir (ya da yeni key oluştur).
2. İçerik ekle: YouTube linki yapıştır (tek video, playlist, hatta otomatik Mix) ya da ses/video dosyalarını sürükle-bırak.
3. Playlist'leri düzenle, öğeleri yeniden adlandır ya da sil.
4. Android uygulamasında aynı key'i gir ve **Senkron**'a bas. Yeni içerik iner, silinenler cihazdan kalkar.

Telefon ve Android Auto **salt okunurdur**: her şey web'den yönetilir.

## Özellikler

### Web panel (`backend/`)
- Açık temalı, modern panel: kütüphane istatistikleri, filtreler, satır içi yeniden adlandırma.
- **YouTube linkleri** — sunucu yalnızca linki tutar; cihaz senkron sırasında indirir (sunucuda depolama ya da trafik yok).
- **Playlist algılama** — `watch?v=…&list=…` linkini yapıştırınca listenin tamamını ekleyip eklemeyeceğini sorar (en fazla 300 video; `RD…` Mix'ler ~25) ve istersen Mekronize playlist'i de oluşturur.
- **Dosya yükleme** — sürükle-bırak, çoklu dosya, 4 MB parçalı yükleme ve dosya başına ilerleme; küçük paylaşımlı hosting limitlerinde de çalışır.
- Sıralanabilir playlist'ler, 6 haneli key ile giriş, saatte en fazla 4 key değişimi, imzalı indirme adresleri ve hız sınırı.
- Düz **cPanel** hostinge kurulabilir (MySQL ya da SQLite), bkz. `backend/deploy-cpanel.sh`.

### Android uygulama (`android/`)
- **Önce çevrimdışı**; doğrudan yerel kütüphaneyle açılır.
- CarPlay tarzı ana ekran: Playlistler · Tümü · Videolar · Müzikler · Favoriler · Karışık çal · Senkron · Ayarlar kutuları. İçeriği olmayan kutular karartılır.
- Gerçek bir oynatıcı: alttaki mini bar tam ekran oynatıcıya büyür; **sağa/sola kaydırarak** parça değiştirilir; karışık, tekrar, favori.
- Video modunda kontroller otomatik gizlenir ve video ekranı doldurur; tek dokunuş videoyu durdurmadan kontrolleri geri getirir.
- **Android Auto** göz atma ağacı (ses).
- **Android 5.0+** ve düşük RAM'li cihazlarda çalışır; ~4,7 MB release APK, Compose yok.
- YouTube çözümleme cihazda, NewPipe Extractor ile.

## Teknolojiler

| Bölüm | Yığın |
|-------|-------|
| Backend | PHP 8.3, Laravel 13, MySQL/SQLite, Blade, saf JS |
| Android | Kotlin, XML view, Media3 ExoPlayer, SQLite, OkHttp, NewPipe Extractor, R8 |

## Başlarken

**Backend**
```bash
cd backend
composer install
cp .env.example .env && php artisan key:generate
php artisan migrate
php artisan serve          # http://127.0.0.1:8000
php artisan test
```

**Android**
```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease -Pmekronize.baseUrl=https://sunucu-adresin   # imzalı release
```
Release çıktısı ayrıca `dist/mekronize-<sürüm>.apk` olarak kopyalanır. Ayrıntılar: [`backend/README.md`](backend/README.md), [`android/README.md`](android/README.md).

## Yasal uyarı

Android uygulaması, YouTube bağlantılarını cihazda indirebilen bir modül içerir. YouTube kullanım şartları kendi sunduğu yollar dışında indirmeyi yasaklar ve içerik telif hakkıyla korunabilir.
Bu yazılım yalnızca hakkına sahip olduğun ya da indirme izni bulunan içerikler için, kişisel kullanım amacıyla sunulmuştur. Kullanımın sorumluluğu kullanıcıya aittir.

## Lisans

Bkz. [LICENSE](LICENSE).
