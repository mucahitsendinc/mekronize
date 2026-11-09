<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('media', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('title');
            $table->string('type', 5);              // audio | video
            $table->string('source', 8);            // upload | youtube
            $table->string('youtube_id', 20)->nullable();
            $table->string('thumbnail_url')->nullable();
            $table->string('file_path')->nullable();
            $table->unsignedBigInteger('size')->nullable();
            $table->string('sha256', 64)->nullable();
            $table->unsignedInteger('duration')->nullable();
            $table->timestamps();
            $table->index(['user_id', 'updated_at']);
        });

        Schema::create('playlists', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('name');
            $table->timestamps();
        });

        Schema::create('playlist_media', function (Blueprint $table) {
            $table->foreignId('playlist_id')->constrained()->cascadeOnDelete();
            $table->foreignId('media_id')->constrained('media')->cascadeOnDelete();
            $table->unsignedInteger('position')->default(0);
            $table->primary(['playlist_id', 'media_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('playlist_media');
        Schema::dropIfExists('playlists');
        Schema::dropIfExists('media');
    }
};
