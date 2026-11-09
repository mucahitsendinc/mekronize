<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

#[Fillable(['user_id', 'name'])]
class Playlist extends Model
{
    public function media(): BelongsToMany
    {
        return $this->belongsToMany(Media::class, 'playlist_media')
            ->withPivot('position')
            ->orderBy('playlist_media.position');
    }
}
