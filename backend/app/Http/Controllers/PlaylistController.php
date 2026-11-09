<?php

namespace App\Http\Controllers;

use App\Models\Playlist;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class PlaylistController extends Controller
{
    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate(['name' => 'required|string|max:120']);
        $playlist = $request->user()->playlists()->create(['name' => $data['name']]);

        return redirect()->route('playlists.edit', $playlist);
    }

    public function edit(Request $request, Playlist $playlist)
    {
        abort_unless($playlist->user_id === $request->user()->id, 404);

        return view('playlist', [
            'playlist' => $playlist,
            'inList' => $playlist->media()->get(['media.id', 'media.title', 'media.type'])->values(),
            'all' => $request->user()->media()->orderBy('title')->get(['id', 'title', 'type']),
        ]);
    }

    public function update(Request $request, Playlist $playlist): RedirectResponse
    {
        abort_unless($playlist->user_id === $request->user()->id, 404);
        $data = $request->validate([
            'name' => 'required|string|max:120',
            'media_ids' => 'array|max:2000',
            'media_ids.*' => 'integer',
        ]);

        $owned = $request->user()->media()->whereIn('id', $data['media_ids'] ?? [])->pluck('id')->all();
        $sync = [];
        $pos = 0;
        foreach (array_unique($data['media_ids'] ?? []) as $id) {
            if (in_array((int) $id, $owned, true)) {
                $sync[(int) $id] = ['position' => $pos++];
            }
        }
        $playlist->update(['name' => $data['name']]);
        $playlist->media()->sync($sync);
        $playlist->touch();

        return back()->with('ok', 'Playlist kaydedildi.');
    }

    public function destroy(Request $request, Playlist $playlist): RedirectResponse
    {
        abort_unless($playlist->user_id === $request->user()->id, 404);
        $playlist->delete();

        return redirect()->route('dashboard')->with('ok', 'Playlist silindi.');
    }
}
