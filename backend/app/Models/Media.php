<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Facades\Storage;

#[Fillable(['user_id', 'title', 'type', 'source', 'youtube_id', 'thumbnail_url', 'file_path', 'size', 'sha256', 'duration'])]
class Media extends Model
{
    protected $table = 'media';

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    protected static function booted(): void
    {
        static::deleted(function (Media $m) {
            if ($m->file_path) {
                Storage::disk('local')->delete($m->file_path);
            }
        });
    }
}
