/* Constellation — code as a graph.
 * Colors mirror the CSS tokens in app.css (validated with the dataviz palette
 * validator against the #12131a canvas; node shape is a mandatory secondary
 * encoding — never remove it). */
'use strict';

const CATEGORIES = [
  { id: 'CLASS', label: 'Class', color: '#3987e5', shape: 'ellipse' },
  { id: 'POJO', label: 'POJO', color: '#d95926', shape: 'round-rectangle' },
  { id: 'INTERFACE', label: 'Interface', color: '#199e70', shape: 'diamond' },
  { id: 'ANNOTATION', label: 'Annotation', color: '#c98500', shape: 'hexagon' },
  { id: 'EXTERNAL', label: 'External', color: '#d55181', shape: 'ellipse', dashed: true },
];
const EDGE_TYPES = [
  { id: 'CALL', label: 'calls', color: '#9085e9', width: 1.5, opacity: 0.6, dashed: false },
  { id: 'INHERITANCE', label: 'extends', color: '#e66767', width: 2, opacity: 1, dashed: false },
  { id: 'IMPLEMENTS', label: 'implements', color: '#008300', width: 2, opacity: 1, dashed: true },
];
const INK_2 = '#c3c2b7';
const INK_3 = '#898781';
const HAIRLINE = '#2c2c2a';

/** Runtime/stdlib noise kept out of the graph unless the user opts in. */
const RUNTIME_PACKAGES = [
  'java.*', 'javax.*', 'jakarta.*', 'jdk.*', 'sun.*', 'com.sun.*',
  'kotlin.*', 'kotlinx.*', 'org.jetbrains.annotations',
  // picked up whenever the analyzed path is a repo root that contains the wrapper jar
  'org.gradle.wrapper',
];

const state = {
  kinds: new Set(CATEGORIES.map((c) => c.id).filter((c) => c !== 'EXTERNAL')),
  edgeTypes: new Set(EDGE_TYPES.map((e) => e.id)),
  packages: null, // null = all (no packages filter)
  hideRuntime: true,
  groupByPackage: true,
  meta: null,
  graph: null,
  cy: null,
  layoutNeeded: true,
};

// ---------- API ----------

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} -> HTTP ${res.status}`);
  return res.json();
}

function nothingSelected() {
  return state.kinds.size === 0 ||
    state.edgeTypes.size === 0 ||
    (state.packages !== null && state.packages.size === 0);
}

function buildQuery() {
  const params = [];
  if (state.kinds.size !== CATEGORIES.length) params.push(`kinds=${[...state.kinds].join(',')}`);
  if (state.edgeTypes.size !== EDGE_TYPES.length) params.push(`edges=${[...state.edgeTypes].join(',')}`);
  if (state.packages !== null) params.push(`pkgs=${encodeURIComponent([...state.packages].join(','))}`);
  if (state.hideRuntime) params.push(`xpkgs=${encodeURIComponent(RUNTIME_PACKAGES.join(','))}`);
  return params.length ? `?${params.join('&')}` : '';
}

// ---------- filter panel ----------

function swatchHtml(meta) {
  const cls = ['swatch', `shape-${meta.shape}`, meta.dashed ? 'dashed' : ''].filter(Boolean).join(' ');
  return `<span class="${cls}" style="--sw:${meta.color}"></span>`;
}

function buildFilterPanel() {
  const kindsEl = document.getElementById('kindFilters');
  kindsEl.innerHTML = CATEGORIES.map((c) => `
    <label><input type="checkbox" data-kind="${c.id}" ${state.kinds.has(c.id) ? 'checked' : ''}>
      ${swatchHtml(c)}<span class="name">${c.label}</span>
      <span class="count" data-count="kind-${c.id}"></span></label>`).join('');
  kindsEl.addEventListener('change', (e) => {
    const cb = e.target.closest('input[data-kind]');
    if (!cb) return;
    cb.checked ? state.kinds.add(cb.dataset.kind) : state.kinds.delete(cb.dataset.kind);
    scheduleUpdate();
  });

  const edgesEl = document.getElementById('edgeFilters');
  edgesEl.innerHTML = EDGE_TYPES.map((t) => `
    <label><input type="checkbox" data-edge="${t.id}" ${state.edgeTypes.has(t.id) ? 'checked' : ''}>
      <span class="line-sample ${t.dashed ? 'dashed' : ''} ${t.id === 'CALL' ? 'thin' : ''}" style="--sw:${t.color}"></span>
      <span class="name">${t.label}</span>
      <span class="count" data-count="edge-${t.id}"></span></label>`).join('');
  edgesEl.addEventListener('change', (e) => {
    const cb = e.target.closest('input[data-edge]');
    if (!cb) return;
    cb.checked ? state.edgeTypes.add(cb.dataset.edge) : state.edgeTypes.delete(cb.dataset.edge);
    scheduleUpdate();
  });

  const pkgEl = document.getElementById('pkgFilters');
  const allPkgs = new Set(state.meta.packages.map((p) => p.name));
  pkgEl.innerHTML = state.meta.packages.map((p) => `
    <label title="${escapeAttr(p.name === '' ? '(default package)' : p.name)}">
      <input type="checkbox" data-pkg="${escapeAttr(p.name)}" checked>
      <span class="name">${p.name === '' ? '<span class="unnamed">(default)</span>' : escapeHtml(p.name)}</span>
      <span class="count">${p.count}</span></label>`).join('');
  pkgEl.addEventListener('change', () => {
    const checked = [...pkgEl.querySelectorAll('input[data-pkg]:checked')].map((cb) => cb.dataset.pkg);
    state.packages = checked.length === allPkgs.size ? null : new Set(checked);
    scheduleUpdate();
  });

  document.getElementById('pkgAll').addEventListener('click', () => {
    pkgEl.querySelectorAll('input[data-pkg]').forEach((cb) => (cb.checked = true));
    state.packages = null;
    scheduleUpdate();
  });
  document.getElementById('pkgNone').addEventListener('click', () => {
    pkgEl.querySelectorAll('input[data-pkg]').forEach((cb) => (cb.checked = false));
    state.packages = new Set();
    scheduleUpdate();
  });
  document.getElementById('pkgSearch').addEventListener('input', (e) => {
    const q = e.target.value.trim();
    pkgEl.querySelectorAll('label').forEach((label) => {
      const pkg = label.querySelector('input').dataset.pkg;
      label.style.display = !q || pkg.includes(q) ? '' : 'none';
    });
  });

  document.getElementById('relayout').addEventListener('click', () => {
    state.layoutNeeded = true;
    if (state.graph) updateElements(state.graph, { randomize: true });
  });

  document.getElementById('hideRuntime').addEventListener('change', (e) => {
    state.hideRuntime = e.target.checked;
    scheduleUpdate();
  });
  document.getElementById('groupByPackage').addEventListener('change', (e) => {
    state.groupByPackage = e.target.checked;
    state.layoutNeeded = true; // compound <-> flat is a structural change
    if (state.graph) updateElements(state.graph, { randomize: true });
  });
}

function updateMetaInfo() {
  const m = state.meta;
  document.getElementById('stats').innerHTML =
    `<b>${m.analyzedClasses}</b> classes · <b>${sum(Object.values(m.edgeTypes))}</b> relations` +
    ((m.parseErrors && m.parseErrors.length > 0) ? ` · <span class="warn">${m.parseErrors.length} parse errors</span>` : '');
  for (const c of CATEGORIES) {
    setCount(`kind-${c.id}`, m.kinds[c.id] ?? 0);
  }
  for (const t of EDGE_TYPES) {
    setCount(`edge-${t.id}`, m.edgeTypes[t.id] ?? 0);
  }
}

function setCount(id, value) {
  const el = document.querySelector(`[data-count="${id}"]`);
  if (el) el.textContent = value;
}

function sum(arr) { return arr.reduce((a, b) => a + b, 0); }

// ---------- graph rendering ----------

const STYLESHEET = [
  { selector: 'node', style: {
    'label': 'data(simpleName)',
    'font-size': 10,
    'color': INK_2,
    'width': 24, 'height': 24,
    'text-valign': 'bottom', 'text-margin-y': 6,
    'text-wrap': 'ellipsis', 'text-max-width': 90,
    'border-width': 0,
  } },
  // Package containers: recessive nested boxes, never mistaken for class nodes
  // (no fill, hairline border, muted top-left label, auto-sized to children).
  { selector: 'node.package-group', style: {
    'shape': 'round-rectangle',
    'background-color': 'rgba(255, 255, 255, 0.02)',
    'border-color': HAIRLINE,
    'border-width': 1,
    'border-opacity': 0.9,
    'label': 'data(simpleName)',
    'font-size': 11,
    'color': INK_3,
    'text-valign': 'top', 'text-halign': 'left',
    'text-margin-x': 8, 'text-margin-y': 6,
    'text-wrap': 'ellipsis', 'text-max-width': 220,
    'padding': 24,
    'events': 'no',
  } },
  ...CATEGORIES.map((c) => ({
    selector: `node[category="${c.id}"]`,
    style: c.dashed
      ? { 'background-color': c.color, 'shape': c.shape,
          'border-width': 2, 'border-style': 'dashed', 'border-color': '#12131a' }
      : { 'background-color': c.color, 'shape': c.shape },
  })),
  { selector: 'edge', style: {
    'curve-style': 'bezier',
    'target-arrow-shape': 'triangle',
    'arrow-scale': 0.7,
    'line-color': '#9085e9',
  } },
  ...EDGE_TYPES.map((t) => ({
    selector: `edge[type="${t.id}"]`,
    style: {
      'line-color': t.color,
      'width': t.width,
      'opacity': t.opacity,
      'line-style': t.dashed ? 'dashed' : 'solid',
    },
  })),
  { selector: 'node:selected', style: { 'border-width': 3, 'border-color': '#ffffff', 'border-opacity': 0.9 } },
  { selector: 'node:hover', style: { 'overlay-color': '#ffffff', 'overlay-opacity': 0.10 } },
  { selector: 'edge:hover', style: { 'overlay-color': '#ffffff', 'overlay-opacity': 0.08, 'opacity': 1 } },
];

const PKG_ID_PREFIX = 'pkg:';

/** Builds cytoscape elements: leaf class nodes plus (optionally) nested package containers. */
function toElements(graph) {
  const packages = new Map(); // pkg name -> element, created lazily with the full ancestor chain

  /** Creates (once) the container for [pkg] and its parents; returns the container id. */
  function containerFor(pkg) {
    if (packages.has(pkg)) return packages.get(pkg).data.id;
    const element = {
      group: 'nodes',
      data: {
        id: PKG_ID_PREFIX + (pkg === '' ? '(default)' : pkg),
        simpleName: pkg === '' ? '(default package)' : pkg,
      },
      classes: ['package-group'],
      position: { x: 0, y: 0 },
    };
    packages.set(pkg, element);
    const dot = pkg.lastIndexOf('.');
    if (dot > 0) element.data.parent = containerFor(pkg.substring(0, dot));
    return element.data.id;
  }

  const nodes = graph.nodes.map((n) => {
    const data = { ...n };
    if (state.groupByPackage) data.parent = containerFor(n.pkg);
    return { group: 'nodes', data };
  });
  const edges = graph.edges.map((e, i) => ({
    group: 'edges',
    data: { id: `e${i}`, source: e.from, target: e.to, type: e.type, methods: e.methods || [] },
  }));
  // Containers must come before their children in the element list.
  return [...packages.values(), ...nodes, ...edges];
}

function updateElements(graph, { randomize = false } = {}) {
  const cy = state.cy;
  const previous = {};
  cy.nodes().forEach((n) => (previous[n.id()] = n.position()));
  const zoom = cy.zoom();
  const pan = cy.pan();

  cy.elements().remove();
  cy.add(toElements(graph));

  // Keep the mental map: surviving nodes stay where they were,
  // brand-new nodes get scattered seeds near the current cloud.
  let bbox = { x1: -200, y1: -200, x2: 200, y2: 200 };
  cy.nodes().forEach((n) => {
    if (previous[n.id()]) {
      n.position(previous[n.id()]);
    } else {
      const x = bbox.x1 + Math.random() * (bbox.x2 - bbox.x1);
      const y = bbox.y1 + Math.random() * (bbox.y2 - bbox.y1);
      n.position({ x, y });
    }
  });
  const positioned = cy.nodes().length;
  if (positioned > 0) bbox = cy.nodes().boundingBox();

  cy.zoom(zoom);
  cy.pan(pan);

  const layout = cy.layout({
    name: 'fcose',
    quality: 'default',
    nodeRepulsion: 6500,
    idealEdgeLength: () => 70,
    edgeElasticity: 0.45,
    gravity: 0.25,
    numIter: 9000,
    fit: true,
    padding: 30,
    // First paint and explicit relayout: deterministic, no animation (positions
    // apply synchronously — also keeps headless rendering testable). Animated
    // morphing only when preserving positions across a refilter.
    animate: !randomize,
    animationDuration: 700,
    randomize,
    initialEnergyOnIncremental: 0.1,
    nodeDimensionsIncludeLabels: true,
    packComponents: true,
  });
  // fcose's own fit can leave the packed components off-center (especially with
  // compound containers) — an explicit fit on completion guarantees centering.
  layout.on('layoutstop', () => cy.fit(undefined, 30));
  layout.run();

  document.getElementById('counts').textContent =
    `${graph.nodes.length} nodes · ${graph.edges.length} edges`;

  const emptyEl = document.getElementById('empty');
  const analyzed = state.meta ? state.meta.analyzedClasses : 0;
  emptyEl.hidden = graph.nodes.length !== 0;
  if (graph.nodes.length === 0) {
    emptyEl.innerHTML = `<div class="card">${
      analyzed === 0
        ? 'No classes found in the analyzed paths.<br>Build the target project first, then point Constellation at its <code>build/classes</code> directory.'
        : 'No classes match the current filters.'
    }</div>`;
  }
}

// ---------- details sidebar ----------

const TYPE_LABEL = Object.fromEntries(EDGE_TYPES.map((t) => [t.id, t.label]));
const CAT_LABEL = Object.fromEntries(CATEGORIES.map((c) => [c.id, c.label]));
const CAT_COLOR = Object.fromEntries(CATEGORIES.map((c) => [c.id, c.color]));

function showNodeDetails(id) {
  const node = state.graph.nodes.find((n) => n.id === id);
  const sb = document.getElementById('sidebar');
  if (!node) { sb.hidden = true; return; }

  const out = state.graph.edges.filter((e) => e.from === id);
  const inc = state.graph.edges.filter((e) => e.to === id);

  const edgeList = (edges, dir) => edges.map((e) => `
    <div class="edge-group">
      <div class="edge-head"><span class="dir">${dir === 'out' ? '→' : '←'}</span>
        <span class="other">${escapeHtml(dir === 'out' ? e.to : e.from)}</span>
        <span class="dir">${TYPE_LABEL[e.type]}</span></div>
      ${e.type === 'CALL' && e.methods.length ? `<div class="methods">${e.methods.map((m) => escapeHtml(m.display)).join(', ')}</div>` : ''}
    </div>`).join('');

  sb.innerHTML = `
    <button class="close" title="Close">✕</button>
    <h3>${escapeHtml(node.id)}</h3>
    <span class="badge">${swatchHtml({ color: CAT_COLOR[node.category], shape: 'round-rectangle' })}
      ${CAT_LABEL[node.category]}${node.pojo ? ' (pojo)' : ''}</span>
    <div class="kv">package: <code>${node.pkg === '' ? '(default)' : escapeHtml(node.pkg)}</code></div>
    ${node.module ? `<div class="kv">module: <code>${escapeHtml(node.module)}</code></div>` : ''}
    ${node.sourceFile ? `<div class="kv">source: <code>${escapeHtml(node.sourceFile)}</code></div>` : ''}
    <h4>Methods (${node.methods.length})</h4>
    <ul>${node.methods.slice(0, 40).map((m) => `<li><code>${escapeHtml(m.display)}</code></li>`).join('')}
        ${node.methods.length > 40 ? `<li>… ${node.methods.length - 40} more</li>` : ''}</ul>
    <h4>Outgoing (${out.length})</h4>${out.length ? edgeList(out, 'out') : '<div class="kv">none</div>'}
    <h4>Incoming (${inc.length})</h4>${inc.length ? edgeList(inc, 'in') : '<div class="kv">none</div>'}`;
  sb.hidden = false;
  sb.querySelector('.close').addEventListener('click', () => (sb.hidden = true));
}

// ---------- updates ----------

let updateTimer = null;
function scheduleUpdate() {
  clearTimeout(updateTimer);
  updateTimer = setTimeout(updateGraph, 300);
}

async function updateGraph() {
  try {
    // An empty selection on any axis is a client-side empty state;
    // the API treats an absent/empty parameter as "no restriction".
    if (nothingSelected()) {
      state.graph = { nodes: [], edges: [] };
      updateElements(state.graph);
      return;
    }
    const graph = await fetchJson(`/api/graph${buildQuery()}`);
    state.graph = graph;
    const first = !state.cy || state.cy.nodes().length === 0;
    updateElements(graph, { randomize: state.layoutNeeded || first });
    state.layoutNeeded = false;
    const selected = state.cy.nodes(':selected');
    if (selected.length) showNodeDetails(selected.first().id());
    else document.getElementById('sidebar').hidden = true;
  } catch (err) {
    console.error('updateGraph failed', err);
  }
}

// ---------- boot ----------

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function escapeAttr(s) { return escapeHtml(s); }

async function init() {
  state.meta = await fetchJson('/api/meta');
  updateMetaInfo();
  buildFilterPanel();

  state.cy = cytoscape({ container: document.getElementById('cy'), elements: [], style: STYLESHEET });
  state.cy.on('tap', 'node', (e) => showNodeDetails(e.target.id()));
  state.cy.on('tap', (e) => {
    if (e.target === state.cy) document.getElementById('sidebar').hidden = true;
  });

  await updateGraph();
}

init().catch((err) => {
  const el = document.getElementById('empty');
  el.hidden = false;
  el.innerHTML = `<div class="card">Failed to load graph: ${escapeHtml(String(err))}</div>`;
});
