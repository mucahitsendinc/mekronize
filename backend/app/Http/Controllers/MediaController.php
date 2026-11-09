<?php

namespace App\Http\Controllers;

use App\Models\Media;
use App\Services\YouTube;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

class MediaController extends Controller
{
    private const AUDIO = ['mp3', 'm4a', 'aac', 'ogg', 'oga', 'opus', 'wav', 'flac'];

    private const VIDEO = ['mp4', 'm4v', 'mov', 'webm', 'mkv', '3gp'];

    public function dashboard(Request $request)
    {
        $user = $request->user();

        return view('dashboard', [
            'user' => $user,
            'media' => $user->media()->latest()->get(),
            'playlists' => $user->playlists()->withCount('media')->latest()->get(),
        ]);
    }

    /**
     * YouTube linki ekle (her satıra bir link). Dosya saklanmaz, sadece bağlantı.
     * Oynatma listesi linkinde önce kullanıcıya sorulur (decision: all | single).
     */
    public function addYoutube(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'links' => 'required|string|max:5000',
            'type' => 'required|in:audio,video',
            'decision' => 'nullable|in:all,single',
            'make_playlist' => 'nullable|boolean',
        ]);
        $user = $request->user();
        $lines = array_values(array_filter(array_map('trim', preg_split('/\R+/', $data['links']))));

        // 1) Oynatma listesi var mı? Karar yoksa sor.
        $found = [];
        foreach ($lines as $line) {
            if ($listId = YouTube::extractPlaylistId($line)) {
                $found[$listId] = ['list' => $listId, 'has_video' => (bool) YouTube::extractId($line)];
            }
        }
        if ($found && empty($data['decision'])) {
            $prompts = [];
            foreach ($found as $f) {
                $pl = YouTube::playlist($f['list']);
                $prompts[] = $f + ['title' => $pl['title'] ?? 'YouTube listesi', 'count' => $pl ? count($pl['items']) : 0, 'truncated' => $pl['truncated'] ?? false, 'ok' => (bool) $pl];
            }

            return back()->withInput()->with('playlist_prompt', ['links' => $data['links'], 'type' => $data['type'], 'lists' => $prompts]);
        }

        $added = 0;
        $bad = 0;
        $playlistsMade = 0;
        $addVideo = function (string $id, ?string $title = null, ?string $thumb = null) use ($user, $data, &$added): ?Media {
            if ($user->media()->where('youtube_id', $id)->where('type', $data['type'])->exists()) {
                return $user->media()->where('youtube_id', $id)->where('type', $data['type'])->first();
            }
            if ($title === null) {
                $meta = YouTube::meta($id);
                $title = $meta['title'];
                $thumb = $meta['thumbnail'];
            }
            $added++;

            return $user->media()->create([
                'title' => $title, 'type' => $data['type'], 'source' => 'youtube',
                'youtube_id' => $id, 'thumbnail_url' => $thumb,
            ]);
        };

        $handledLists = [];
        foreach ($lines as $line) {
            $listId = YouTube::extractPlaylistId($line);
            $videoId = YouTube::extractId($line);

            if ($listId && ($data['decision'] ?? null) === 'all') {
                if (isset($handledLists[$listId])) {
                    continue;
                }
                $handledLists[$listId] = true;
                $pl = YouTube::playlist($listId);
                if (! $pl || ! $pl['items']) {
                    $bad++;

                    continue;
                }
                $ids = [];
                foreach ($pl['items'] as $it) {
                    if ($m = $addVideo($it['id'], $it['title'], $it['thumbnail'])) {
                        $ids[] = $m->id;
                    }
                }
                if ($request->boolean('make_playlist') && $ids) {
                    $created = $user->playlists()->create(['name' => $pl['title']]);
                    $sync = [];
                    foreach ($ids as $pos => $mid) {
                        $sync[$mid] = ['position' => $pos];
                    }
                    $created->media()->sync($sync);
                    $playlistsMade++;
                }

                continue;
            }
            if ($videoId) {
                $addVideo($videoId);
            } else {
                $bad++;
            }
        }

        $msg = "{$added} kayıt eklendi.".($playlistsMade ? " {$playlistsMade} playlist oluşturuldu." : '').($bad ? " {$bad} link tanınmadı ya da okunamadı." : '');

        return back()->with($added || $playlistsMade ? 'ok' : 'warn', $msg);
    }

    /** Parçalı (chunk) yükleme: 4MB'lık parçalar cPanel PHP limitlerine takılmaz. */
    public function uploadChunk(Request $request): JsonResponse
    {
        $request->validate([
            'upload_id' => ['required', 'regex:/^[A-Za-z0-9]{16,40}$/'],
            'index' => 'required|integer|min:0|max:5000',
            'total' => 'required|integer|min:1|max:5000',
            'filename' => 'required|string|max:200',
            'chunk' => 'required|file',
        ]);

        $user = $request->user();
        $ext = Str::lower(pathinfo($request->input('filename'), PATHINFO_EXTENSION));
        if (! in_array($ext, array_merge(self::AUDIO, self::VIDEO), true)) {
            return response()->json(['error' => 'Desteklenmeyen dosya türü.'], 422);
        }

        $dir = "chunks/{$user->id}/".$request->input('upload_id');
        $request->file('chunk')->storeAs($dir, (string) $request->integer('index'), 'local');

        $index = $request->integer('index');
        $total = $request->integer('total');
        if ($index + 1 < $total) {
            return response()->json(['done' => false]);
        }

        // Son parça: birleştir.
        $disk = Storage::disk('local');
        for ($i = 0; $i < $total; $i++) {
            if (! $disk->exists("{$dir}/{$i}")) {
                return response()->json(['error' => 'Eksik parça var, tekrar deneyin.'], 422);
            }
        }
        $name = Str::random(40).'.'.$ext;
        $relative = "media/{$user->id}/{$name}";
        $disk->makeDirectory("media/{$user->id}");
        $target = $disk->path($relative);
        $out = fopen($target, 'wb');
        for ($i = 0; $i < $total; $i++) {
            $in = fopen($disk->path("{$dir}/{$i}"), 'rb');
            stream_copy_to_stream($in, $out);
            fclose($in);
        }
        fclose($out);
        $disk->deleteDirectory($dir);

        $original = pathinfo($request->input('filename'), PATHINFO_FILENAME);
        $media = $user->media()->create([
            'title' => mb_substr($original, 0, 190),
            'type' => in_array($ext, self::VIDEO, true) ? 'video' : 'audio',
            'source' => 'upload',
            'file_path' => $relative,
            'size' => filesize($target),
            'sha256' => hash_file('sha256', $target),
        ]);

        return response()->json(['done' => true, 'id' => $media->id]);
    }

    public function update(Request $request, Media $media): RedirectResponse
    {
        abort_unless($media->user_id === $request->user()->id, 404);
        $data = $request->validate(['title' => 'required|string|max:190']);
        $media->update(['title' => $data['title']]);

        return back()->with('ok', 'Güncellendi.');
    }

    public function destroy(Request $request, Media $media): RedirectResponse
    {
        abort_unless($media->user_id === $request->user()->id, 404);
        $media->delete();

        return back()->with('ok', 'Silindi. Cihazlar bir sonraki senkronda kaldırır.');
    }
}
