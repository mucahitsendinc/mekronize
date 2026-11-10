@extends('layout')
@section('title','Panel · Mekronize')
@section('content')

@if(session('playlist_prompt'))
@php($pp = session('playlist_prompt'))
<div class="card ask">
  <h2>Oynatma listesi bulundu</h2>
  <p class="sub">Linkte oynatma listesi var. Hepsini eklememi ister misin?</p>
  @foreach($pp['lists'] as $l)
    <div class="pl"><b>{{ $l['title'] }}</b>
      <div class="mut">@if($l['ok']){{ $l['count'] }} video @if($l['truncated'])· ilk 300 video eklenir @endif @else Liste okunamadı (özel ya da silinmiş olabilir) @endif</div></div>
  @endforeach
  <form method="post" action="{{ route('media.youtube') }}">@csrf
    <input type="hidden" name="links" value="{{ $pp['links'] }}">
    <input type="hidden" name="type" value="{{ $pp['type'] }}">
    <label style="display:flex;gap:8px;align-items:center;margin:12px 0;color:var(--txt);font-size:14px">
      <input type="checkbox" name="make_playlist" value="1" checked style="width:auto"> Mekronize playlist'i olarak da oluştur
    </label>
    <div class="row">
      <button name="decision" value="all" class="fit">Evet, hepsini ekle</button>
      @if(collect($pp['lists'])->contains('has_video', true))
        <button name="decision" value="single" class="ghost fit">Sadece bu videoyu ekle</button>
      @endif
      <a href="{{ route('dashboard') }}" class="fit mut">Vazgeç</a>
    </div>
  </form>
</div>
@endif

<div class="stats">
  <div class="stat"><small>Toplam içerik</small><b>{{ $media->count() }}</b></div>
  <div class="stat"><small>Müzik</small><b>{{ $media->where('type','audio')->count() }}</b></div>
  <div class="stat"><small>Video</small><b>{{ $media->where('type','video')->count() }}</b></div>
  <div class="stat"><small>Playlist</small><b>{{ $playlists->count() }}</b></div>
</div>

<div class="cols">
<div class="col">

<div class="card">
  <h2><span class="ic"><svg viewBox="0 0 24 24"><path d="M10 14a4 4 0 1 1 3.5-6H21v3h-2v2h-3v-2h-2.5A4 4 0 0 1 10 14z"/></svg></span> YouTube linki ekle</h2>
  <p class="sub">Sunucuda dosya saklanmaz; telefon senkron sırasında kendisi indirir. Her satıra bir link; oynatma listesi ve mix linkleri de olur.</p>
  <form method="post" action="{{ route('media.youtube') }}">@csrf
    <textarea name="links" placeholder="https://www.youtube.com/watch?v=…&#10;https://www.youtube.com/playlist?list=…" required>{{ old('links') }}</textarea>
    <div class="row" style="margin-top:12px">
      <select name="type"><option value="audio" @selected(old('type')==='audio')>Müzik (sadece ses)</option><option value="video" @selected(old('type')==='video')>Video</option></select>
      <button class="fit">Ekle</button>
    </div>
  </form>
</div>

<div class="card">
  <h2><span class="ic"><svg viewBox="0 0 24 24"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M4 16v3h16v-3"/></svg></span> Dosya yükle</h2>
  <p class="sub">mp3, m4a, aac, ogg, wav, flac, mp4, mov, webm…</p>
  <div class="dz" id="dz">
    <svg viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/></svg>
    <b>Dosyaları buraya sürükle</b>
    <small>ya da tıklayıp seç · birden fazla dosya olur</small>
    <input type="file" id="files" multiple accept=".mp3,.m4a,.aac,.ogg,.oga,.opus,.wav,.flac,.mp4,.m4v,.mov,.webm,.mkv,.3gp">
  </div>
  <ul class="queue" id="queue"></ul>
</div>

<div class="card">
  <h2><span class="ic"><svg viewBox="0 0 24 24"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg></span> Kütüphane <span class="mut" style="font-weight:400">({{ $media->count() }})</span></h2>
  <div class="tabs" id="tabs">
    <button type="button" class="on" data-f="all">Tümü</button>
    <button type="button" data-f="audio">Müzik</button>
    <button type="button" data-f="video">Video</button>
  </div>
  <div class="lib" id="lib">
    @forelse($media as $m)
    <div class="item" data-type="{{ $m->type }}">
      @if($m->thumbnail_url && str_starts_with($m->thumbnail_url, 'http'))<img class="thumb" src="{{ $m->thumbnail_url }}" loading="lazy" alt="">
      @else<span class="thumb"><svg viewBox="0 0 24 24"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg></span>@endif
      <form method="post" action="{{ route('media.update', $m) }}" class="row" style="flex-wrap:nowrap">@csrf @method('PUT')
        <input class="t" name="title" value="{{ $m->title }}" maxlength="190" title="Adı değiştirmek için yaz, Enter'a bas">
      </form>
      <div class="tags"><span class="tag {{ $m->type === 'audio' ? 'a' : 'v' }}">{{ $m->type === 'audio' ? 'Müzik' : 'Video' }}</span><span class="tag">{{ $m->source === 'youtube' ? 'YouTube' : 'Yükleme' }}</span></div>
      <form method="post" action="{{ route('media.destroy', $m) }}" onsubmit="return confirm('Silinsin mi?')">@csrf @method('DELETE')<button class="danger sm">Sil</button></form>
    </div>
    @empty
    <div class="empty">Henüz içerik yok. Bir YouTube linki ekle ya da dosya sürükle.</div>
    @endforelse
  </div>
</div>

</div>
<div class="col">

<div class="card keycard">
  <h2><span class="ic"><svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="10" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg></span> Senkron key'in</h2>
  <p class="sub">Android uygulamasında senkron ekranına bu key'i gir. Web'e tekrar girmek için de aynı key kullanılır.</p>
  <div class="keybox">
    <span class="keywrap"><span class="key" id="keyText">{{ $user->sync_key }}</span></span>
    <button class="ghost" type="button" id="copyKey">Kopyala</button>
  </div>
  @php($ks = $user->keyChangeStatus())
  <details><summary>Key'i yenile</summary>
    <form method="post" action="{{ route('key.update') }}" class="row" style="margin-top:10px"
          onsubmit="return confirm('Key değişecek; cihazlarda yeni key ile tekrar senkron yapman gerekir. Devam?')">@csrf
      <span class="mut fit" style="font-size:13px">Bu saat için kalan hak: {{ $ks['left'] }}/{{ \App\Models\User::KEY_CHANGES_PER_HOUR }}@if($ks['retry_minutes']) · ~{{ $ks['retry_minutes'] }} dk sonra @endif</span>
      <button class="ghost sm fit" @if($ks['left'] < 1) disabled @endif>Yeni key üret</button>
    </form>
  </details>
</div>

<div class="card">
  <h2><span class="ic"><svg viewBox="0 0 24 24"><path d="M4 6h12M4 12h12M4 18h8M18 15v6m-3-3h6"/></svg></span> Playlistler</h2>
  <form method="post" action="{{ route('playlists.store') }}" class="row" style="margin-top:10px">@csrf
    <input name="name" placeholder="Yeni playlist adı" required maxlength="120"><button class="fit">Oluştur</button>
  </form>
  @if($playlists->count())
  <div class="chips">
    @foreach($playlists as $p)<a class="chip" href="{{ route('playlists.edit', $p) }}">{{ $p->name }} <em>{{ $p->media_count }} parça</em></a>@endforeach
  </div>
  @endif
</div>

</div>
</div>
@endsection

@push('scripts')
<script>
// --- key kopyala
document.getElementById('copyKey')?.addEventListener('click', async e => {
  try { await navigator.clipboard.writeText(document.getElementById('keyText').textContent.trim()); e.target.textContent = 'Kopyalandı ✓'; setTimeout(() => e.target.textContent = 'Kopyala', 1600); } catch (_) {}
});
// --- kütüphane filtresi
document.getElementById('tabs')?.addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  document.querySelectorAll('#tabs button').forEach(x => x.classList.toggle('on', x === b));
  document.querySelectorAll('#lib .item').forEach(i => i.style.display = (b.dataset.f === 'all' || i.dataset.type === b.dataset.f) ? '' : 'none');
});
// --- sürükle-bırak parçalı yükleme
const CHUNK = 4 * 1024 * 1024, csrf = @json(csrf_token()), url = @json(route('media.chunk'));
const dz = document.getElementById('dz'), input = document.getElementById('files'), queue = document.getElementById('queue');
const OK = /\.(mp3|m4a|aac|ogg|oga|opus|wav|flac|mp4|m4v|mov|webm|mkv|3gp)$/i;
let busy = false, pending = [], doneAny = false;
const rid = () => Array.from(crypto.getRandomValues(new Uint8Array(12)), b => b.toString(16).padStart(2, '0')).join('');
const size = n => n > 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB';
dz.addEventListener('click', () => input.click());
['dragenter', 'dragover'].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); dz.classList.add('over'); }));
['dragleave', 'drop'].forEach(ev => dz.addEventListener(ev, e => { e.preventDefault(); dz.classList.remove('over'); }));
dz.addEventListener('drop', e => add([...e.dataTransfer.files]));
input.addEventListener('change', () => { add([...input.files]); input.value = ''; });
// sayfaya yanlışlıkla bırakılan dosya tarayıcıda açılmasın
['dragover', 'drop'].forEach(ev => window.addEventListener(ev, e => { if (!dz.contains(e.target)) e.preventDefault(); }));

function add(files) {
  for (const f of files) {
    const li = document.createElement('li');
    li.innerHTML = '<div class="top"><span class="name"></span><span class="st">Sırada</span></div><div class="bar"><i></i></div>';
    li.querySelector('.name').textContent = f.name + ' · ' + size(f.size);
    queue.appendChild(li);
    if (!OK.test(f.name)) { setSt(li, 'Desteklenmeyen tür', 'er'); continue; }
    pending.push({ f, li });
  }
  run();
}
function setSt(li, t, c) { const s = li.querySelector('.st'); s.textContent = t; s.className = 'st ' + (c || ''); }
async function run() {
  if (busy) return; busy = true;
  while (pending.length) {
    const { f, li } = pending.shift(), total = Math.max(1, Math.ceil(f.size / CHUNK)), id = rid(), bar = li.querySelector('.bar i');
    let failed = null;
    for (let i = 0; i < total && !failed; i++) {
      const fd = new FormData();
      fd.append('upload_id', id); fd.append('index', i); fd.append('total', total); fd.append('filename', f.name);
      fd.append('chunk', f.slice(i * CHUNK, (i + 1) * CHUNK), 'c');
      let ok = false;
      for (let t = 0; t < 3 && !ok && !failed; t++) {
        const r = await fetch(url, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf, 'Accept': 'application/json' }, body: fd }).catch(() => null);
        if (r && r.ok) ok = true;
        else if (r && r.status === 422) failed = (await r.json().catch(() => ({}))).error || 'Hata';
        else if (r && r.status === 419) failed = 'Oturum süresi doldu, sayfayı yenile';
      }
      if (!ok && !failed) failed = 'Yükleme başarısız';
      bar.style.width = (((i + 1) / total) * 100) + '%';
      setSt(li, '%' + Math.round(((i + 1) / total) * 100));
    }
    if (failed) setSt(li, failed, 'er'); else { setSt(li, 'Tamam ✓', 'ok'); doneAny = true; }
  }
  busy = false;
  if (doneAny) setTimeout(() => location.reload(), 900);
}
</script>
@endpush
