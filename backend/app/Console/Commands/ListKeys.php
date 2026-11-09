<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Command;

class ListKeys extends Command
{
    protected $signature = 'mekronize:keys';

    protected $description = 'Kullanıcıların 6 haneli key\'lerini ve içerik sayılarını listeler (yönetici aracı).';

    public function handle(): int
    {
        $this->table(['ID', 'Key', 'İçerik', 'Playlist'], User::withCount(['media', 'playlists'])->orderBy('id')->get()
            ->map(fn ($u) => [$u->id, $u->sync_key, $u->media_count, $u->playlists_count])->all());

        return self::SUCCESS;
    }
}
