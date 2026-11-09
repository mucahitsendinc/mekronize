@extends('layout')
@section('title', $playlist->name.' · Mekronize')
@section('content')
<p><a href="{{ route('dashboard') }}" class="mut">← Panel</a></p>
<form method="post" action="{{ route('playlists.update', $playlist) }}" id="plForm">@csrf @method('PUT')
  <div class="card">
    <label>Playlist adı</label><input name="name" value="{{ $playlist->name }}" required maxlength="120">
  </div>
  <div class="grid2">
    <div class="card"><h2>Playlist içeriği</h2><p class="sub">Sırayı ↑ ↓ ile değiştir.</p><ul class="pl" id="inList"></ul><div id="hidden"></div>
      <p><button>Kaydet</button></p></div>
    <div class="card"><h2>Kütüphane</h2><p class="sub">+ ile playlist'e ekle.</p><input id="q" placeholder="Ara…" style="margin-bottom:6px"><ul class="pl" id="allList"></ul></div>
  </div>
</form>
<form method="post" action="{{ route('playlists.destroy', $playlist) }}" onsubmit="return confirm('Playlist silinsin mi?')">@csrf @method('DELETE')
  <button class="danger">Playlisti sil</button>
</form>
@endsection

@push('scripts')
<script>
const all = @json($all);
let list = @json($inList->pluck('id'));
const byId = Object.fromEntries(all.map(m => [m.id, m]));
const esc = s => s.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
function render(){
  const inEl = document.getElementById('inList'), hid = document.getElementById('hidden');
  inEl.innerHTML = list.map((id,i)=>`<li><span class="t">${esc(byId[id]?.title||'?')}</span>
    <button type="button" class="ghost" data-up="${i}">↑</button><button type="button" class="ghost" data-dn="${i}">↓</button>
    <button type="button" class="danger" data-rm="${i}">×</button></li>`).join('') || '<li class="mut">Boş</li>';
  hid.innerHTML = list.map(id=>`<input type="hidden" name="media_ids[]" value="${id}">`).join('');
  const q = document.getElementById('q').value.toLowerCase();
  document.getElementById('allList').innerHTML = all.filter(m=>!list.includes(m.id) && m.title.toLowerCase().includes(q))
    .map(m=>`<li><span class="t">${esc(m.title)}</span><span class="tag ${m.type==='audio'?'a':'v'}">${m.type==='audio'?'Müzik':'Video'}</span><button type="button" data-add="${m.id}">+</button></li>`).join('');
}
document.addEventListener('click', e => {
  const d = e.target.dataset; if (!d) return;
  if (d.add) list.push(+d.add);
  else if (d.rm !== undefined) list.splice(+d.rm,1);
  else if (d.up !== undefined && +d.up>0) { const i=+d.up; [list[i-1],list[i]]=[list[i],list[i-1]]; }
  else if (d.dn !== undefined && +d.dn<list.length-1) { const i=+d.dn; [list[i+1],list[i]]=[list[i],list[i+1]]; }
  else return;
  render();
});
document.getElementById('q').addEventListener('input', render);
render();
</script>
@endpush
