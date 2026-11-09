<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\RateLimiter;

class AuthController extends Controller
{
    /** Yanlış key denemesi: IP başına saatte en fazla bu kadar. */
    private const MAX_BAD_KEYS = 20;

    public function showLogin()
    {
        return view('auth.login');
    }

    /** Mevcut key ile giriş. */
    public function login(Request $request): RedirectResponse
    {
        $bucket = 'badkey:'.$request->ip();
        if (RateLimiter::tooManyAttempts($bucket, self::MAX_BAD_KEYS)) {
            return back()->withErrors(['sync_key' => 'Çok fazla hatalı deneme. Bir süre sonra tekrar dene.']);
        }

        $key = User::normalizeKey((string) $request->input('sync_key'));
        $user = User::validKeyFormat($key) ? User::where('sync_key', $key)->first() : null;
        if (! $user) {
            RateLimiter::hit($bucket, 3600);

            return back()->withErrors(['sync_key' => 'Geçersiz key. 6 haneli sayıyı doğru yaz.']);
        }

        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->intended(route('dashboard'));
    }

    /** İlk giriş: yeni key üretilir ve otomatik giriş yapılır. */
    public function createNew(Request $request): RedirectResponse
    {
        $user = User::create(['sync_key' => User::generateSyncKey()]);
        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->route('dashboard')->with('ok', 'Yeni key oluşturuldu. Bu key ile uygulamadan senkron yapabilirsin; not et.');
    }

    public function logout(Request $request): RedirectResponse
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('login');
    }

    /** Key'i yenile: saatte en fazla 4 kez. */
    public function updateKey(Request $request): RedirectResponse
    {
        $user = $request->user();
        if (! $user->changeKey()) {
            $min = $user->keyChangeStatus()['retry_minutes'];

            return back()->withErrors(['key' => "Key'i saatte en fazla ".User::KEY_CHANGES_PER_HOUR." kez değiştirebilirsin. Yaklaşık {$min} dk sonra tekrar dene."]);
        }
        $left = $user->keyChangeStatus()['left'];

        return back()->with('ok', "Key değişti. Cihazlarda yeni key ile tekrar senkron yap. Bu saat için kalan hak: {$left}.");
    }
}
