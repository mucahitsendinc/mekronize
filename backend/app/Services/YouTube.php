<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;

class YouTube
{
    /** Çeşitli YouTube link biçimlerinden 11 karakterlik video kimliğini çıkarır. */
    public static function extractId(string $url): ?string
    {
        $url = trim($url);
        if ($url === '') {
            return null;
        }
        if (preg_match('/^[A-Za-z0-9_-]{11}$/', $url)) {
            return $url;
        }
        $parts = parse_url(str_contains($url, '://') ? $url : 'https://'.$url);
        if (! $parts || empty($parts['host'])) {
            return null;
        }
        $host = strtolower(preg_replace('/^(www\.|m\.|music\.)/', '', $parts['host']));
        $path = $parts['path'] ?? '';

        if ($host === 'youtu.be') {
            $id = trim($path, '/');
        } elseif (in_array($host, ['youtube.com', 'youtube-nocookie.com'], true)) {
            if (preg_match('#^/(shorts|embed|live|v)/([^/?]+)#', $path, $m)) {
                $id = $m[2];
            } else {
                parse_str($parts['query'] ?? '', $q);
                $id = $q['v'] ?? '';
            }
        } else {
            return null;
        }

        return preg_match('/^[A-Za-z0-9_-]{11}$/', $id) ? $id : null;
    }

    /** Resmi oEmbed ucundan başlık ve küçük resmi alır (anahtar gerektirmez). */
    public static function meta(string $id): array
    {
        $fallback = ['title' => 'YouTube '.$id, 'thumbnail' => "https://i.ytimg.com/vi/{$id}/hqdefault.jpg"];
        try {
            $r = Http::timeout(8)->acceptJson()->get('https://www.youtube.com/oembed', [
                'url' => "https://www.youtube.com/watch?v={$id}",
                'format' => 'json',
            ]);
            if ($r->ok() && $r->json('title')) {
                return [
                    'title' => mb_substr($r->json('title'), 0, 190),
                    'thumbnail' => $r->json('thumbnail_url') ?: $fallback['thumbnail'],
                ];
            }
        } catch (\Throwable) {
        }

        return $fallback;
    }

    /** Linkteki oynatma listesi kimliği (list=...). Otomatik "mix" (RD...) listeleri desteklenmez. */
    public static function extractPlaylistId(string $url): ?string
    {
        $parts = parse_url(trim(str_contains($url, '://') ? $url : 'https://'.$url));
        if (! $parts || empty($parts['query'])) {
            return null;
        }
        parse_str($parts['query'], $q);
        $id = $q['list'] ?? '';
        if (! is_string($id) || ! preg_match('/^[A-Za-z0-9_-]{10,64}$/', $id) || str_starts_with($id, 'RD')) {
            return null;
        }

        return $id;
    }

    private const WEB = ['clientName' => 'WEB', 'clientVersion' => '2.20250101.00.00', 'hl' => 'tr', 'gl' => 'TR'];

    private static function browse(array $body): ?array
    {
        try {
            $r = Http::timeout(15)->withHeaders([
                'User-Agent' => 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
                'Accept-Language' => 'tr-TR,tr;q=0.9',
            ])->asJson()->post('https://www.youtube.com/youtubei/v1/browse?prettyPrint=false', $body + ['context' => ['client' => self::WEB]]);

            return $r->ok() ? $r->json() : null;
        } catch (\Throwable) {
            return null;
        }
    }

    /** Yanıtın içinden video girdilerini ve devam belirtecini çıkarır (eski ve yeni YouTube düzenleri). */
    private static function scan(mixed $node, array &$items, ?string &$token): void
    {
        if (! is_array($node)) {
            return;
        }
        if (isset($node['playlistVideoRenderer']['videoId'])) {
            $v = $node['playlistVideoRenderer'];
            $items[] = ['id' => $v['videoId'], 'title' => $v['title']['runs'][0]['text'] ?? $v['title']['simpleText'] ?? null];
        } elseif (($node['lockupViewModel']['contentType'] ?? '') === 'LOCKUP_CONTENT_TYPE_VIDEO' && isset($node['lockupViewModel']['contentId'])) {
            $v = $node['lockupViewModel'];
            $items[] = ['id' => $v['contentId'], 'title' => $v['metadata']['lockupMetadataViewModel']['title']['content'] ?? null];
        }
        // Devam belirteci: eski düzende continuationItemRenderer, yenisinde continuationItemViewModel altında.
        if (isset($node['continuationCommand']['token']) && is_string($node['continuationCommand']['token'])) {
            $token = $node['continuationCommand']['token'];
        }
        foreach ($node as $child) {
            self::scan($child, $items, $token);
        }
    }

    /**
     * Oynatma listesini çeker (anahtar gerektirmez). En fazla $max video.
     *
     * @return array{title:string,items:array<int,array{id:string,title:string,thumbnail:string}>,truncated:bool}|null
     */
    public static function playlist(string $listId, int $max = 300): ?array
    {
        $data = self::browse(['browseId' => 'VL'.$listId]);
        if (! $data) {
            return null;
        }
        $title = $data['metadata']['playlistMetadataRenderer']['title'] ?? 'YouTube listesi';
        $items = [];
        $token = null;
        self::scan($data['contents'] ?? [], $items, $token);

        for ($page = 0; $token && count($items) < $max && $page < 10; $page++) {
            $next = self::browse(['continuation' => $token]);
            if (! $next) {
                break;
            }
            $token = null;
            self::scan($next['onResponseReceivedActions'] ?? $next, $items, $token);
        }

        $seen = [];
        $out = [];
        foreach ($items as $it) {
            if (! preg_match('/^[A-Za-z0-9_-]{11}$/', (string) $it['id']) || isset($seen[$it['id']])) {
                continue;
            }
            $seen[$it['id']] = true;
            $out[] = [
                'id' => $it['id'],
                'title' => mb_substr($it['title'] ?: 'YouTube '.$it['id'], 0, 190),
                'thumbnail' => "https://i.ytimg.com/vi/{$it['id']}/hqdefault.jpg",
            ];
        }
        $truncated = count($out) > $max || (bool) $token;

        return ['title' => mb_substr($title, 0, 120), 'items' => array_slice($out, 0, $max), 'truncated' => $truncated];
    }
}
