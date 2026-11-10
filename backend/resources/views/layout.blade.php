<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="theme-color" content="#ffffff">
<title>@yield('title', 'Mekronize')</title>
<link rel="stylesheet" href="{{ asset('css/app.css') }}">
</head>
<body>
<header class="top">
  <a class="brand" href="{{ route('dashboard') }}"><span class="logo"><svg viewBox="0 0 24 24"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg></span>Mekronize</a>
  @auth
  <div class="who"><span>Key</span><b>{{ auth()->user()->sync_key }}</b>
  <form method="post" action="{{ route('logout') }}">@csrf <button class="ghost sm">Çıkış</button></form></div>
  @endauth
</header>
<div class="wrap">
  @if(session('ok'))<div class="msg ok">{{ session('ok') }}</div>@endif
  @if(session('warn'))<div class="msg warn">{{ session('warn') }}</div>@endif
  @if($errors->any())<div class="msg err">@foreach($errors->all() as $e)<div>{{ $e }}</div>@endforeach</div>@endif
  @yield('content')
</div>
@stack('scripts')
</body>
</html>
