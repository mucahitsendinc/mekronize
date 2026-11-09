@extends('layout')
@section('title','Giriş · Mekronize')
@section('content')
<div class="auth card">
  <h2 style="text-align:center;font-size:20px">Hoş geldin</h2>
  <p class="sub" style="text-align:center">Giriş için sadece 6 haneli key yeterli.</p>
  <form method="post" action="{{ url('giris') }}">@csrf
    <label>Mevcut key'im var</label>
    <input class="keyin" name="sync_key" value="{{ old('sync_key') }}" inputmode="numeric" pattern="[0-9 ]*" maxlength="7"
           placeholder="••••••" autocomplete="off" autofocus required>
    <p style="margin:14px 0 0"><button style="width:100%">Giriş yap</button></p>
  </form>
  <div class="or">veya</div>
  <form method="post" action="{{ url('yeni') }}">@csrf
    <button class="ghost" style="width:100%">Yeni giriş · ilk kez mi? Key oluştur</button>
  </form>
</div>
@endsection
