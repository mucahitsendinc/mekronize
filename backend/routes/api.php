<?php

use App\Http\Controllers\Api\SyncController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->group(function () {
    // Key tahminine karşı: IP başına dakikada 20 istek.
    Route::post('sync', [SyncController::class, 'manifest'])->middleware('throttle:20,1')->name('api.sync');
    Route::get('media/{media}/download', [SyncController::class, 'download'])
        ->middleware(['signed', 'throttle:120,1'])->name('api.download');
});
