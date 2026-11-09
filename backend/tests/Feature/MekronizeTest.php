<?php

namespace Tests\Feature;

use App\Models\Media;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

class MekronizeTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $key = null): User
    {
        return User::create(['sync_key' => $key ?? User::generateSyncKey()]);
    }

    public function test_new_login_creates_six_digit_key_and_logs_in(): void
    {
        $this->get('/giris')->assertOk()->assertSee('Yeni giriş');
        $this->post('/yeni')->assertRedirect('/panel');
        $u = User::first();
        $this->assertMatchesRegularExpression('/^\d{6}$/', $u->sync_key);
        $this->get('/panel')->assertOk()->assertSee($u->sync_key);
    }

    public function test_login_with_existing_key(): void
    {
        $u = $this->user('012345');
        $this->post('/giris', ['sync_key' => '999999'])->assertSessionHasErrors('sync_key');
        $this->assertGuest();
        $this->post('/giris', ['sync_key' => '012 345'])->assertRedirect('/panel');
        $this->assertAuthenticatedAs($u);
    }

    public function test_bad_keys_are_locked_after_20_attempts(): void
    {
        $u = $this->user('111111');
        for ($i = 0; $i < 20; $i++) {
            \Illuminate\Support\Facades\RateLimiter::hit('badkey:127.0.0.1', 3600);
        }
        // Doğru key bile kilitliyken girmez.
        $this->post('/giris', ['sync_key' => '111111'])->assertSessionHasErrors('sync_key');
        $this->assertGuest();
        $this->postJson('/api/v1/sync', ['key' => '111111'])->assertStatus(429);
    }

    public function test_key_change_limited_to_four_per_hour(): void
    {
        $u = $this->user('222222');
        $seen = ['222222'];
        for ($i = 0; $i < 4; $i++) {
            $this->actingAs($u)->post('/key')->assertSessionHasNoErrors();
            $u->refresh();
            $this->assertMatchesRegularExpression('/^\d{6}$/', $u->sync_key);
            $this->assertNotContains($u->sync_key, $seen);
            $seen[] = $u->sync_key;
        }
        $last = $u->sync_key;
        $this->actingAs($u)->post('/key')->assertSessionHasErrors('key');
        $this->assertSame($last, $u->fresh()->sync_key);

        // 1 saat sonra hak yenilenir.
        $this->travel(61)->minutes();
        $this->actingAs($u)->post('/key')->assertSessionHasNoErrors();
        $this->assertNotSame($last, $u->fresh()->sync_key);
        $this->assertSame(1, $u->fresh()->key_changes);
    }

    public function test_youtube_add_and_sync_manifest(): void
    {
        Http::fake(['*oembed*' => Http::response(['title' => 'Şarkı', 'thumbnail_url' => 'https://i.ytimg.com/x.jpg'])]);
        $u = $this->user();
        $this->actingAs($u)->post('/media/youtube', ['links' => "https://youtu.be/dQw4w9WgXcQ\nhttps://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3\nsaçma", 'type' => 'audio'])->assertRedirect();
        $this->assertSame(1, $u->media()->count());

        $r = $this->postJson('/api/v1/sync', ['key' => $u->sync_key])->assertOk();
        $r->assertJsonPath('items.0.youtube_id', 'dQw4w9WgXcQ')->assertJsonPath('items.0.type', 'audio');
        $this->postJson('/api/v1/sync', ['key' => '000000'])->assertNotFound();
    }

    public function test_chunk_upload_download_and_isolation(): void
    {
        Storage::fake('local');
        $u = $this->user();
        $id = 'abcdef1234567890abcd';
        $data = random_bytes(3000);
        $this->actingAs($u)->postJson('/media/chunk', ['upload_id' => $id, 'index' => 0, 'total' => 2, 'filename' => 'a.mp3', 'chunk' => UploadedFile::fake()->createWithContent('c', substr($data, 0, 1500))])->assertJson(['done' => false]);
        $this->actingAs($u)->postJson('/media/chunk', ['upload_id' => $id, 'index' => 1, 'total' => 2, 'filename' => 'a.mp3', 'chunk' => UploadedFile::fake()->createWithContent('c', substr($data, 1500))])->assertJson(['done' => true]);
        $m = Media::first();
        $this->assertSame(3000, $m->size);
        $this->assertSame(hash('sha256', $data), $m->sha256);

        $url = $this->postJson('/api/v1/sync', ['key' => $u->sync_key])->json('items.0.url');
        $this->get($url)->assertOk();
        $this->get('/api/v1/media/'.$m->id.'/download')->assertForbidden();

        $other = $this->user();
        $this->actingAs($other)->delete('/media/'.$m->id)->assertNotFound();
        $this->actingAs($u)->postJson('/media/chunk', ['upload_id' => $id.'x', 'index' => 0, 'total' => 1, 'filename' => 'x.php', 'chunk' => UploadedFile::fake()->create('c', 1)])->assertStatus(422);
    }

    public function test_playlist_order_in_manifest(): void
    {
        $u = $this->user();
        $a = $u->media()->create(['title' => 'a', 'type' => 'audio', 'source' => 'youtube', 'youtube_id' => 'aaaaaaaaaaa']);
        $b = $u->media()->create(['title' => 'b', 'type' => 'audio', 'source' => 'youtube', 'youtube_id' => 'bbbbbbbbbbb']);
        $res = $this->actingAs($u)->post('/playlists', ['name' => 'P']);
        $pl = $u->playlists()->first();
        $this->actingAs($u)->put('/playlists/'.$pl->id, ['name' => 'P2', 'media_ids' => [$b->id, $a->id]])->assertRedirect();
        $this->postJson('/api/v1/sync', ['key' => $u->sync_key])->assertJsonPath('playlists.0.media_ids', [$b->id, $a->id])->assertJsonPath('playlists.0.name', 'P2');
    }

    public function test_pages_render(): void
    {
        $this->get('/giris')->assertOk();
        $this->get('/panel')->assertRedirect('/giris');
        $u = $this->user();
        $u->media()->create(['title' => 'x', 'type' => 'video', 'source' => 'youtube', 'youtube_id' => 'ccccccccccc']);
        $this->actingAs($u)->get('/panel')->assertOk()->assertSee($u->sync_key);
        $pl = $u->playlists()->create(['name' => 'Z']);
        $this->actingAs($u)->get('/playlists/'.$pl->id)->assertOk();
    }
}
