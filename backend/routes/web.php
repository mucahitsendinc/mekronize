<?php

use App\Http\Controllers\AuthController;
use App\Http\Controllers\MediaController;
use App\Http\Controllers\PlaylistController;
use Illuminate\Support\Facades\Route;

Route::redirect('/', '/panel');

Route::middleware('guest')->group(function () {
    Route::get('giris', [AuthController::class, 'showLogin'])->name('login');
    Route::post('giris', [AuthController::class, 'login'])->middleware('throttle:10,1');
    // Hesap oluşturma kötüye kullanımına karşı: IP başına saatte 10 yeni giriş.
    Route::post('yeni', [AuthController::class, 'createNew'])->middleware('throttle:10,60')->name('register');
});

Route::middleware('auth')->group(function () {
    Route::post('cikis', [AuthController::class, 'logout'])->name('logout');
    Route::post('key', [AuthController::class, 'updateKey'])->middleware('throttle:30,60')->name('key.update');

    Route::get('panel', [MediaController::class, 'dashboard'])->name('dashboard');
    Route::post('media/youtube', [MediaController::class, 'addYoutube'])->name('media.youtube');
    Route::post('media/chunk', [MediaController::class, 'uploadChunk'])->name('media.chunk');
    Route::put('media/{media}', [MediaController::class, 'update'])->name('media.update');
    Route::delete('media/{media}', [MediaController::class, 'destroy'])->name('media.destroy');

    Route::post('playlists', [PlaylistController::class, 'store'])->name('playlists.store');
    Route::get('playlists/{playlist}', [PlaylistController::class, 'edit'])->name('playlists.edit');
    Route::put('playlists/{playlist}', [PlaylistController::class, 'update'])->name('playlists.update');
    Route::delete('playlists/{playlist}', [PlaylistController::class, 'destroy'])->name('playlists.destroy');
});
