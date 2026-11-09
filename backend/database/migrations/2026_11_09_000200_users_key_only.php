<?php

use App\Models\User;
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Ad / e-posta / parola kalkıyor: kullanıcı yalnızca 6 haneli sayısal key ile tanımlanır.
 * Eski 20 karakterlik key'ler geçersiz olur; mevcut kullanıcılara yeni 6 haneli key atanır
 * (öğrenmek için: php artisan mekronize:keys).
 */
return new class extends Migration
{
    public function up(): void
    {
        // Önce yeni key'leri ata (sütun hâlâ geniş).
        $used = [];
        foreach (DB::table('users')->orderBy('id')->pluck('id') as $id) {
            do {
                $key = User::randomKey();
            } while (isset($used[$key]));
            $used[$key] = true;
            DB::table('users')->where('id', $id)->update(['sync_key' => $key]);
        }

        Schema::table('users', function (Blueprint $table) {
            $table->dropUnique('users_email_unique');
        });
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['name', 'email', 'email_verified_at', 'password', 'remember_token']);
            $table->string('sync_key', 6)->change();
            $table->unsignedTinyInteger('key_changes')->default(0);
            $table->timestamp('key_window_start')->nullable();
        });
    }

    public function down(): void
    {
        throw new RuntimeException('Geri alınamaz: ad/e-posta/parola verisi silindi.');
    }
};
