(() => {
  'use strict';

  const CATEGORY_ES = {
    Outfit: 'Armadura', 'Hunter Bow': 'Arco de cazador', 'Warrior Bow': 'Arco de guerrero', 'Sharpshot Bow': 'Arco de precisión',
    Blastsling: 'Hondero', Tripcaster: 'Tripcaster', Ropecaster: 'Ropecaster', 'Shredder Gauntlet': 'Guantelete triturador',
    Boltblaster: 'Boltblaster', 'Spike Thrower': 'Lanzapúas', Spear: 'Lanza', Special: 'Arma especial',
  };
  const ORIGIN_ES = { base: 'Juego base', arena: 'Arena', ngplus: 'New Game+', dlc: 'Burning Shores', preorder: 'Reserva o edición especial' };
  const ARMOR_ES = { Hunter: 'Cazador', Warrior: 'Guerrero', Infiltrator: 'Infiltrador', Survivor: 'Superviviente', Trapper: 'Trampero', 'Machine Master': 'Maestro de máquinas', 'Mixed Gear': 'Mixta' };
  const LEVEL_ES = { max: 'Stats a nivel 5', base: 'Stats sin mejorar' };
  const RARITY_ES = { Uncommon: 'Poco común', Rare: 'Raro', 'Very Rare': 'Muy raro', Legendary: 'Legendario' };

  const PHASE_ART = { early: 'img/phase-early.webp', mid: 'img/phase-mid.webp', late: 'img/phase-late.webp', post: 'img/phase-post.webp', ngplus: 'img/phase-ngplus.webp' };
  const CLASS_ART = {
    hunter: 'img/class-hunter.webp', warrior: 'img/class-warrior.webp', survivor: 'img/class-survivor.webp',
    'machine-master': 'img/class-machine-master.webp', infiltrator: 'img/class-infiltrator.webp', trapper: 'img/class-trapper.webp',
  };

  const $ = (id) => document.getElementById(id);
  const state = { meta: null, phase: 'early', classCode: null };

  // Crea elementos sin usar innerHTML
  function h(tag, props = {}, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const c of children.flat()) if (c != null) el.append(c.nodeType ? c : document.createTextNode(c));
    return el;
  }
  const api = async (url) => {
    const res = await fetch(url);
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.error || 'No se pudo cargar la información.');
    return body;
  };
  const showError = (msg) => {
    const box = $('build'); box.hidden = false; box.replaceChildren(h('p', { class: 'status', role: 'alert', text: msg }));
  };

  // ---------- Estado en la URL (para compartir) ----------
  function readHash() {
    const p = new URLSearchParams(location.hash.slice(1));
    if (p.get('fase')) state.phase = p.get('fase');
    state.classCode = p.get('clase') || null;
  }
  function writeHash() {
    const p = new URLSearchParams();
    p.set('fase', state.phase);
    if (state.classCode) p.set('clase', state.classCode);
    history.replaceState(null, '', '#' + p.toString());
  }

  // ---------- Selector ----------
  function renderPhases() {
    const box = $('phases'); box.replaceChildren();
    for (const p of state.meta.phases) {
      box.append(h('button', {
        type: 'button', 'aria-pressed': String(p.code === state.phase), text: p.name,
        onclick: () => { state.phase = p.code; renderPhases(); if (state.classCode) loadBuild(); else writeHash(); goTo($('class-step'), $('class-title')); },
      }));
    }
    setBackdrop(state.phase);
    $('phase-hint').textContent = (state.meta.phases.find((p) => p.code === state.phase) || {}).hint || '';
  }
  // Fondo del artbook: una capa por imagen y fundido entre ellas al cambiar de fase.
  const backdrop = { on: 0, url: null, pending: null };
  function setBackdrop(code) {
    const url = PHASE_ART[code]; const layers = document.querySelectorAll('.backdrop-layer');
    if (!url || layers.length < 2 || url === backdrop.url) return;
    backdrop.pending = url;
    const img = new Image();
    img.onload = () => {
      if (backdrop.pending !== url) return; // llegó tarde: ya se pidió otra fase
      const next = layers[backdrop.on ^ 1], cur = layers[backdrop.on];
      next.style.backgroundImage = `url(${url})`; next.classList.add('is-on'); cur.classList.remove('is-on');
      backdrop.on ^= 1; backdrop.url = url;
    };
    img.src = url;
  }

  // Lleva la vista al siguiente paso. El foco pasa al título para que lectores de pantalla sepan dónde están.
  function goTo(el, focusEl) {
    if (!el) return;
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (el.scrollIntoView) el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
    if (focusEl && focusEl.focus) focusEl.focus({ preventScroll: true });
  }

  // ---------- Clases ----------
  function renderClasses() {
    const list = $('classes'); list.replaceChildren();
    for (const c of state.meta.classes) {
      list.append(h('li', {}, h('button', {
        type: 'button', class: 'class-row', 'aria-current': c.code === state.classCode ? 'true' : null,
        onclick: async () => { state.classCode = c.code; renderClasses(); await loadBuild(); goTo($('build'), $('build-title')); },
      }, h('span', { class: 'class-name', text: c.name }), h('span', { class: 'class-tag', text: c.tagline }))));
    }
  }

  // ---------- Detalle de la build ----------
  const rarityClass = (r) => 'r-' + r.replace(' ', '-');
  function statsBlock(i) {
    if (!i.stats || !i.stats.length) return i.note ? h('p', { class: 'item-note', text: i.note }) : null;
    return h('details', { class: 'item-stats' },
      h('summary', { text: LEVEL_ES[i.statsLevel] || 'Datos del objeto' }),
      h('ul', {}, i.stats.map((s) => h('li', { text: s }))),
      i.baseStats && i.baseStats.length ? h('p', { class: 'sub', text: 'Al comprarla (nivel 0)' }) : null,
      i.baseStats && i.baseStats.length ? h('ul', {}, i.baseStats.map((s) => h('li', { text: s }))) : null,
      i.note ? h('p', { class: 'item-note', text: i.note }) : null);
  }
  function itemRow(i) {
    const kind = i.armorType ? `${CATEGORY_ES[i.category] || i.category} ${ARMOR_ES[i.armorType] || i.armorType}` : (CATEGORY_ES[i.category] || i.category);
    return h('li', { class: 'item' },
      h('div', { class: 'item-top' },
        h('span', { class: 'item-name', text: i.name }),
        h('span', { class: 'rarity ' + rarityClass(i.rarity), text: RARITY_ES[i.rarity] || i.rarity })),
      h('span', { class: 'item-cat', text: i.origin && i.origin !== 'base' ? `${kind} · ${ORIGIN_ES[i.origin] || i.origin}` : kind }),
      h('span', { class: 'item-how', text: i.howToGet }),
      statsBlock(i));
  }
  function surgeCard(v, withBadge) {
    return h('div', { class: 'surge-card' + (v.isPrimary ? ' primary' : '') },
      h('div', { class: 'surge-top' },
        h('span', { class: 'surge-name', text: v.name }),
        h('span', { class: 'tier t-' + v.tier, text: v.tier, title: 'Nivel ' + v.tier }),
        withBadge ? h('span', { class: 'tag' + (v.isPrimary ? '' : ' alt'), text: v.isPrimary ? 'Recomendado' : 'Alternativa' }) : null),
      h('p', { text: v.description }));
  }

  async function loadBuild() {
    writeHash();
    const box = $('build');
    try {
      const b = await api(`/api/builds/${encodeURIComponent(state.classCode)}/${encodeURIComponent(state.phase)}`);
      const cls = state.meta.classes.find((c) => c.code === b.classCode);
      const phase = state.meta.phases.find((p) => p.code === b.phaseCode);
      const outfits = b.items.filter((i) => i.category === 'Outfit');
      const weapons = b.items.filter((i) => i.category !== 'Outfit');
      const main = b.skills.filter((s) => !s.followUp);
      const follow = b.skills.filter((s) => s.followUp);

      const skillsBlock = h('div', { class: 'block' }, h('h3', { text: 'Habilidades' }),
        b.skillNote ? h('p', { class: 'note', text: b.skillNote }) : null,
        main.length ? h('ol', { class: 'skill-list' }, main.map((s) => h('li', { text: s.name }))) : null,
        follow.length ? [h('p', { class: 'sub', text: 'Después, prioriza' }),
          h('ul', { class: 'skill-chips' }, follow.map((s) => h('li', { text: s.name })))] : null);

      const modsBlock = h('div', { class: 'block' }, h('h3', { text: 'Mejoras recomendadas' }),
        b.weaponMods.length ? [h('p', { class: 'sub', text: 'Para las armas' }), h('ul', { class: 'mod-chips' }, b.weaponMods.map((m) => h('li', { text: m })))] : null,
        b.outfitMods.length ? [h('p', { class: 'sub', text: 'Para la armadura' }), h('ul', { class: 'mod-chips' }, b.outfitMods.map((m) => h('li', { text: m })))] : null,
        !b.weaponMods.length && !b.outfitMods.length ? h('p', { text: 'Esta build no indica mejoras.' }) : null);

      box.hidden = false;
      box.replaceChildren(
        h('div', { class: 'build-head', style: CLASS_ART[b.classCode] ? `--art:url(${CLASS_ART[b.classCode]})` : null },
          h('div', { class: 'tags' }, h('span', { class: 'tag', text: cls.name }), h('span', { class: 'tag alt', text: 'Fase: ' + phase.name })),
          h('h2', { id: 'build-title', tabindex: '-1', text: b.name }),
          h('p', { text: b.summary })),
        h('div', { class: 'build-grid' },
          h('div', {},
            h('div', { class: 'block' }, h('h3', { text: 'Equipo' }),
              outfits.length ? [h('p', { class: 'sub', text: 'Armadura' }), h('ul', { class: 'items' }, outfits.map(itemRow))] : null,
              weapons.length ? [h('p', { class: 'sub', text: 'Armas' }), h('ul', { class: 'items' }, weapons.map(itemRow))] : null),
            modsBlock),
          h('div', {},
            h('div', { class: 'block' }, h('h3', { text: 'Valor Surge' }),
              h('div', { class: 'surge-cards' }, b.valorSurges.map((v) => surgeCard(v, b.valorSurges.length > 1)))),
            skillsBlock)),
        b.phaseCode === 'ngplus'
          ? h('p', { class: 'build-source', text: 'Build propia: combina el catálogo de equipo de Game8 con los stats de las capturas del jugador. No es una build de Game8.' })
          : h('p', { class: 'build-source' }, 'Fuente: ', h('a', { href: b.sourceUrl, target: '_blank', rel: 'noopener', text: 'Best Builds Guide de Game8' })));
    } catch (e) { showError(e.message); }
  }

  // ---------- Valor Surges ----------
  async function renderSurges() {
    try {
      const list = await api('/api/valor-surges');
      const grid = $('surges'); grid.replaceChildren();
      for (const v of list) {
        const cls = state.meta.classes.find((c) => c.code === v.classCode);
        grid.append(h('div', { class: 'surge-card' },
          h('div', { class: 'surge-top' }, h('span', { class: 'surge-name', text: v.name }),
            h('span', { class: 'tier t-' + v.tier, text: v.tier, title: 'Nivel ' + v.tier }),
            h('span', { class: 'tag', text: cls ? cls.name : '' })),
          h('p', { text: v.description })));
      }
    } catch { /* la lista es secundaria */ }
  }

  // ---------- Catálogo de equipo ----------
  const gear = { items: [], category: '', rarity: '', origin: '', q: '' };
  function option(value, label) { return h('option', { value, text: label }); }
  function filterSelect(id, label, options, key) {
    const sel = h('select', { id, onchange: (e) => { gear[key] = e.target.value; renderGearList(); } },
      option('', 'Todos'), options.map(([v, l]) => option(v, l)));
    return h('label', { class: 'gear-field', for: id }, h('span', { text: label }), sel);
  }
  function renderGearList() {
    const q = gear.q.trim().toLowerCase();
    const shown = gear.items.filter((i) => (!gear.category || i.category === gear.category) && (!gear.rarity || i.rarity === gear.rarity)
      && (!gear.origin || i.origin === gear.origin) && (!q || i.name.toLowerCase().includes(q)));
    $('gear-count').textContent = shown.length === gear.items.length ? `${shown.length} objetos` : `${shown.length} de ${gear.items.length} objetos`;
    $('gear-list').replaceChildren(...shown.map(itemRow));
    $('gear-empty').hidden = shown.length > 0;
  }
  async function renderGear() {
    try {
      gear.items = (await api('/api/items')).items;
      const cats = [...new Set(gear.items.map((i) => i.category))];
      const order = ['Outfit', ...Object.keys(CATEGORY_ES).filter((c) => c !== 'Outfit')];
      cats.sort((x, y) => order.indexOf(x) - order.indexOf(y));
      const rars = Object.keys(RARITY_ES).filter((r) => gear.items.some((i) => i.rarity === r)).reverse();
      const orgs = Object.keys(ORIGIN_ES).filter((o) => gear.items.some((i) => i.origin === o));
      $('gear-filters').replaceChildren(
        h('label', { class: 'gear-field gear-search', for: 'gear-q' }, h('span', { text: 'Buscar por nombre' }),
          h('input', { id: 'gear-q', type: 'search', autocomplete: 'off', oninput: (e) => { gear.q = e.target.value; renderGearList(); } })),
        filterSelect('gear-cat', 'Tipo', cats.map((c) => [c, CATEGORY_ES[c] || c]), 'category'),
        filterSelect('gear-rar', 'Rareza', rars.map((r) => [r, RARITY_ES[r]]), 'rarity'),
        filterSelect('gear-org', 'Dónde se consigue', orgs.map((o) => [o, ORIGIN_ES[o]]), 'origin'));
      renderGearList();
    } catch (e) { $('gear-count').textContent = e.message; }
  }

  // ---------- Arranque ----------
  (async function init() {
    try {
      state.meta = await api('/api/meta');
      readHash();
      if (!state.meta.phases.some((p) => p.code === state.phase)) state.phase = state.meta.phases[0].code;
      if (!state.meta.classes.some((c) => c.code === state.classCode)) state.classCode = null;
      renderPhases(); renderClasses(); renderSurges(); renderGear();
      if (state.classCode) await loadBuild(); else writeHash();
      setTimeout(() => Object.values({ ...PHASE_ART, ...CLASS_ART }).forEach((u) => { new Image().src = u; }), 2000);
    } catch (e) { showError(e.message); }
  })();
})();
