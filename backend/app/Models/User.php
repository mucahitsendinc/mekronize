<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;

/**
 * Hesap = 6 haneli sayısal key. Ad, e-posta, parola yok.
 */
#[Fillable(['sync_key', 'key_changes', 'key_window_start'])]
class User extends Authenticatable
{
    /** Bir kullanıcı key'ini saatte en fazla bu kadar değiştirebilir. */
    public const KEY_CHANGES_PER_HOUR = 4;

    protected function casts(): array
    {
        return ['key_window_start' => 'datetime'];
    }

    /** "Beni hatırla" belirteci kullanılmıyor. */
    public function getRememberTokenName(): string
    {
        return '';
    }

    public function media(): HasMany
    {
        return $this->hasMany(Media::class);
    }

    public function playlists(): HasMany
    {
        return $this->hasMany(Playlist::class);
    }

    public static function randomKey(): string
    {
        return str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
    }

    /** Kullanılmayan yeni bir 6 haneli key üretir. */
    public static function generateSyncKey(): string
    {
        for ($i = 0; $i < 100; $i++) {
            $key = static::randomKey();
            if (! static::where('sync_key', $key)->exists()) {
                return $key;
            }
        }
        throw new \RuntimeException('Boş key bulunamadı.');
    }

    /** Yalnızca rakamları bırakır ("123 456" → "123456"). */
    public static function normalizeKey(string $key): string
    {
        return preg_replace('/\D/', '', $key);
    }

    public static function validKeyFormat(string $key): bool
    {
        return (bool) preg_match('/^\d{6}$/', $key);
    }

    /** Pencereyi gerekirse sıfırlayıp kalan hak ve bekleme süresini döndürür. */
    public function keyChangeStatus(): array
    {
        $start = $this->key_window_start;
        if (! $start || $start->lte(now()->subHour())) {
            return ['left' => self::KEY_CHANGES_PER_HOUR, 'retry_minutes' => 0];
        }
        $left = max(0, self::KEY_CHANGES_PER_HOUR - $this->key_changes);

        return ['left' => $left, 'retry_minutes' => $left > 0 ? 0 : max(1, (int) ceil(now()->diffInMinutes($start->copy()->addHour(), false)))];
    }

    /** Hak varsa key'i değiştirir ve sayacı artırır; yoksa false döner. */
    public function changeKey(): bool
    {
        $status = $this->keyChangeStatus();
        if ($status['left'] <= 0) {
            return false;
        }
        $fresh = ! $this->key_window_start || $this->key_window_start->lte(now()->subHour());
        $this->forceFill([
            'sync_key' => static::generateSyncKey(),
            'key_window_start' => $fresh ? now() : $this->key_window_start,
            'key_changes' => $fresh ? 1 : $this->key_changes + 1,
        ])->save();

        return true;
    }
}
