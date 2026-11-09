<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Media;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Facades\URL;

class SyncController extends Controller
{
    /** Cihaz key'ini gönderir; kullanıcının tüm kütüphanesinin manifestini alır. */
    public function manifest(Request $request): JsonResponse
    {
        $request->validate(['key' => 'required|string|max:64']);
        $bucket = 'badkey:'.$request->ip();
        if (RateLimiter::tooManyAttempts($bucket, 20)) {
            return response()->json(['error' => 'Çok fazla hatalı deneme.'], 429);
        }
        $key = User::normalizeKey($request->input('key'));
        $user = User::validKeyFormat($key) ? User::where('sync_key', $key)->first() : null;
        if (! $user) {
            RateLimiter::hit($bucket, 3600);

            return response()->json(['error' => 'Geçersiz senkron key.'], 404);
        }

        $items = $user->media()->orderBy('id')->get()->map(function (Media $m) {
            $row = [
                'id' => $m->id,
                'title' => $m->title,
                'type' => $m->type,
                'source' => $m->source,
                'updated_at' => $m->updated_at->timestamp,
            ];
            if ($m->source === 'youtube') {
                $row['youtube_id'] = $m->youtube_id;
                $row['thumbnail'] = $m->thumbnail_url;
            } else {
                $row['size'] = $m->size;
                $row['sha256'] = $m->sha256;
                $row['ext'] = pathinfo($m->file_path, PATHINFO_EXTENSION);
                $row['url'] = URL::temporarySignedRoute('api.download', now()->addHours(12), ['media' => $m->id]);
            }

            return $row;
        })->values();

        $playlists = $user->playlists()->with('media:id')->orderBy('id')->get()->map(fn ($p) => [
            'id' => $p->id,
            'name' => $p->name,
            'media_ids' => $p->media->pluck('id')->values(),
        ])->values();

        return response()->json([
            'items' => $items,
            'playlists' => $playlists,
        ]);
    }

    /** İmzalı URL ile dosya indirme. Range destekli (kaldığı yerden devam). */
    public function download(Request $request, Media $media)
    {
        abort_unless($media->source === 'upload' && $media->file_path, 404);
        $disk = Storage::disk('local');
        abort_unless($disk->exists($media->file_path), 404);

        return response()->file($disk->path($media->file_path), [
            'Cache-Control' => 'private, no-store',
            'Accept-Ranges' => 'bytes',
        ]);
    }
}
