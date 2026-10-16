/* Constellation — code as a graph.
 * Colors mirror the CSS tokens in app.css (validated with the dataviz palette
 * validator against the #12131a canvas; node shape is a mandatory secondary
 * encoding — never remove it). */
'use strict';

const CATEGORIES = [
  { id: 'CLASS', label: 'Class', color: '#3987e5', shape: 'ellipse' },
  { id: 'DATA', label: 'Data model', color: '#d95926', shape: 'round-rectangle' },
  { id: 'INTERFACE', label: 'Interface', color: '#199e70', shape: 'diamond' },
  { id: 'ANNOTATION', label: 'Annotation', color: '#c98500', shape: 'hexagon' },
  { id: 'EXTERNAL', label: 'External', color: '#d55181', shape: 'ellipse', dashed: true },
  { id: 'TEST', label: 'Test', color: '#008300', shape: 'rectangle' },
];
const EDGE_TYPES = [
  { id: 'CALLS', label: 'calls', color: '#9085e9', width: 1.5, opacity: 0.6, dashed: false },
  { id: 'EXTENDS', label: 'extends', color: '#e66767', width: 2, opacity: 1, dashed: false },
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
  kinds: new Set(CATEGORIES.map((c) => c.id).filter((c) => c !== 'EXTERNAL' && c !== 'TEST')),
  edgeTypes: new Set(EDGE_TYPES.map((e) => e.id)),
  packages: null, // null = all (no packages filter)
  hideRuntime: true,
  groupByPackage: true,
  collapsed: new Set(), // collapsed package names (view-level, survives refilters)
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
  // The default package travels as the sentinel "(default)" — an empty token would be dropped.
  if (state.packages !== null) {
    const pkgs = [...state.packages].map((p) => (p === '' ? '(default)' : p));
    params.push(`pkgs=${encodeURIComponent(pkgs.join(','))}`);
  }
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
      <span class="line-sample ${t.dashed ? 'dashed' : ''} ${t.id === 'CALLS' ? 'thin' : ''}" style="--sw:${t.color}"></span>
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
  // The label is the package's last segment — the nesting spells the full name,
  // and short labels fit even small containers; a surface-colored background
  // keeps the label readable where it does extend past the box.
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
    'text-wrap': 'ellipsis', 'text-max-width': 140,
    'text-background-color': '#12131a',
    'text-background-opacity': 0.9,
    'text-background-padding': 2,
    'padding': 24,
  } },
  // Collapsed package: one enlarged square of its own color — unmistakably
  // not a class node; tap to expand. (Palette slot 7, validated adjacent to
  // the six category colors; size + label carry identity too.)
  { selector: 'node.collapsed-package', style: {
    'shape': 'rectangle',
    'background-color': '#9085e9',
    'border-color': '#12131a',
    'border-width': 2,
    'width': 40, 'height': 40,
    'label': 'data(simpleName)',
    'font-size': 11,
    'color': INK_2,
    'text-valign': 'bottom', 'text-margin-y': 8,
    'text-wrap': 'ellipsis', 'text-max-width': 130,
  } },
  // Collapsed-view aggregate: "some class in P depends on some class in Q".
  { selector: 'edge[type="DEPENDS"]', style: {
    'line-color': '#898781',
    'width': 2,
    'opacity': 0.85,
    'line-style': 'solid',
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
  { selector: 'node.package-group:hover', style: { 'overlay-opacity': 0 } },
  { selector: 'edge:hover', style: { 'overlay-color': '#ffffff', 'overlay-opacity': 0.08, 'opacity': 1 } },
];

const PKG_ID_PREFIX = 'pkg:';
const DEFAULT_PKG_KEY = '(default)';

function pkgKey(pkg) { return pkg === '' ? DEFAULT_PKG_KEY : pkg; }

/**
 * The OUTERMOST collapsed package on the chain from [pkg] up, or null.
 * Outer swallows inner: with both A and A.B collapsed, classes of A.B
 * resolve to A's node — no stray nested-collapsed nodes outside it.
 */
function collapsedAncestorOf(pkg) {
  if (!state.groupByPackage) return null;
  let current = pkg;
  let outermost = null;
  while (true) {
    if (state.collapsed.has(current)) outermost = current;
    const dot = current.lastIndexOf('.');
    if (dot <= 0) return outermost;
    current = current.substring(0, dot);
  }
}

/**
 * Builds cytoscape elements: leaf class nodes plus (optionally) nested package
 * containers. Collapsed packages become single nodes, and every edge touching
 * their classes aggregates into one "depends" edge between the surviving nodes.
 */
function toElements(graph) {
  const packages = new Map(); // pkg name -> container element, created lazily with ancestors
  const collapsedNodes = new Map(); // pkg name -> collapsed-node element
  const visibleClasses = graph.nodes.filter((n) => collapsedAncestorOf(n.pkg) === null);

  /** Classes directly under [pkg] or any of its subpackages. */
  const classCountUnder = (pkg) => {
    const prefix = pkg === '' ? '' : pkg + '.';
    return graph.nodes.filter((n) => n.pkg === pkg || n.pkg.startsWith(prefix)).length;
  };

  /** Creates (once) the container for [pkg]; recursion builds the ancestor chain. */
  function containerFor(pkg) {
    if (packages.has(pkg)) return packages.get(pkg).data.id;
    const element = {
      group: 'nodes',
      data: {
        id: PKG_ID_PREFIX + pkgKey(pkg),
        pkg: pkg,
        simpleName: pkg === '' ? '(default package)' : pkg.substring(pkg.lastIndexOf('.') + 1),
      },
      classes: ['package-group'],
      position: { x: 0, y: 0 },
    };
    packages.set(pkg, element);
    const dot = pkg.lastIndexOf('.');
    if (dot > 0) {
      element.data.parent = containerFor(pkg.substring(0, dot));
    }
    return element.data.id;
  }

  function collapsedNodeFor(pkg) {
    if (collapsedNodes.has(pkg)) return collapsedNodes.get(pkg).data.id;
    const count = classCountUnder(pkg);
    const element = {
      group: 'nodes',
      data: {
        id: PKG_ID_PREFIX + pkgKey(pkg),
        pkg: pkg,
        simpleName: `${pkg === '' ? '(default)' : pkg.substring(pkg.lastIndexOf('.') + 1)} (${count})`,
      },
      classes: ['collapsed-package'],
      position: { x: 0, y: 0 },
    };
    collapsedNodes.set(pkg, element);
    const dot = pkg.lastIndexOf('.');
    if (dot > 0 && collapsedAncestorOf(pkg.substring(0, dot)) === null) {
      // A collapsed subpackage sits inside its (expanded) parent container.
      element.data.parent = containerFor(pkg.substring(0, dot));
    }
    return element.data.id;
  }

  const nodeById = new Map(graph.nodes.map((n) => [n.id, n]));

  function resolveEndpoint(id) {
    const node = nodeById.get(id);
    if (!node) return id; // already a collapsed-package id from a previous pass
    const collapsed = collapsedAncestorOf(node.pkg);
    return collapsed === null ? id : collapsedNodeFor(collapsed);
  }

  const nodes = visibleClasses.map((n) => {
    const data = { ...n };
    if (state.groupByPackage) data.parent = containerFor(n.pkg);
    return { group: 'nodes', data };
  });

  const edges = [];
  const seenPairs = new Set();
  for (const e of graph.edges) {
    const source = resolveEndpoint(e.from);
    const target = resolveEndpoint(e.to);
    if (source === target) continue; // internal to a collapsed package
    const collapsedEdge = source.startsWith(PKG_ID_PREFIX) || target.startsWith(PKG_ID_PREFIX);
    if (!collapsedEdge) {
      edges.push({ group: 'edges', data: { id: `e${edges.length}`, source, target, type: e.type, methods: e.methods || [] } });
      continue;
    }
    const key = `${source}->${target}`;
    if (seenPairs.has(key)) continue;
    seenPairs.add(key);
    edges.push({ group: 'edges', data: { id: `e${edges.length}`, source, target, type: 'DEPENDS', methods: [] } });
  }

  // Containers must come before their children in the element list.
  return [...packages.values(), ...collapsedNodes.values(), ...nodes, ...edges];
}

/**
 * Force-directed layouts give no hard guarantee that sibling containers (or
 * top-level collapsed nodes) stay apart, so nudge them apart deterministically:
 * within every sibling group, push intersecting/too-close boxes along the
 * smaller overlap axis until nothing intersects (bounded iterations).
 */
function separateOverlappingSiblings(cy) {
  const GAP = 24;
  const groups = new Map();

  const rootKey = 'root';
  cy.nodes().forEach((n) => {
    const key = n.parent().length ? n.parent().id() : rootKey;
    if (!groups.has(key)) groups.set(key, []);
    // Only boxes that can collide at their level: containers and top-level leaves.
    if (n.isParent() || !n.parent().length) groups.get(key).push(n);
  });

  for (const items of groups.values()) {
    if (items.length < 2) continue;
    for (let iteration = 0; iteration < 25; iteration++) {
      let moved = false;
      for (let i = 0; i < items.length; i++) {
        for (let j = i + 1; j < items.length; j++) {
          const a = items[i].boundingBox();
          const b = items[j].boundingBox();
          const overlapX = Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1);
          const overlapY = Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1);
          if (overlapX <= -GAP || overlapY <= -GAP) continue; // clearly apart already
          moved = true;
          if (overlapX <= overlapY) {
            shiftNode(cy, items[j], (b.x1 + b.x2 < a.x1 + a.x2 ? -1 : 1) * (Math.max(overlapX, 0) + GAP), 0);
          } else {
            shiftNode(cy, items[j], 0, (b.y1 + b.y2 < a.y1 + a.y2 ? -1 : 1) * (Math.max(overlapY, 0) + GAP));
          }
        }
      }
      if (!moved) break;
    }
  }
}

/** Moves a node — for containers, via their descendants (a parent's position is derived). */
function shiftNode(cy, node, dx, dy) {
  (node.isParent() ? node.descendants() : cy.collection(node)).forEach((n) => {
    n.position({ x: n.position('x') + dx, y: n.position('y') + dy });
  });
}

function updateElements(graph, { randomize = false } = {}) {
  const cy = state.cy;
  const previous = {};
  cy.nodes().forEach((n) => (previous[n.id()] = n.position()));
  const zoom = cy.zoom();
  const pan = cy.pan();

  // Build the new element list BEFORE clearing: a failure in toElements must
  // never leave the canvas empty.
  const elements = toElements(graph);
  cy.elements().remove();
  cy.add(elements);

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
  layout.on('layoutstop', () => {
    separateOverlappingSiblings(cy);
    cy.fit(undefined, 30);
  });
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

const CARRIER_LABEL = {
  JAVA_BEAN: 'java bean',
  KOTLIN_DATA_CLASS: 'kotlin data class',
  JAVA_RECORD: 'record',
  ENUM: 'enum',
};

/** Provider properties rendered generically; known payloads get nicer labels. */
function propsBadge(node) {
  if (!node.props || node.props.length === 0) return '';
  return node.props.map((p) => {
    if (p.id === 'constellation.data-model') {
      return ` · ${CARRIER_LABEL[p.carrier] || p.carrier}`;
    }
    return ` · ${p.id}`;
  }).join('');
}

function originLines(node) {
  const o = node.origin;
  if (!o || o.type !== 'project') return '';
  return `<div class="kv">module: <code>${escapeHtml(o.module)}</code>` +
    ` · source set: <code>${escapeHtml(o.sourceSet)}</code></div>`;
}


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
      ${e.type === 'CALLS' && e.methods.length ? `<div class="methods">${e.methods.map((m) => escapeHtml(m.display)).join(', ')}</div>` : ''}
    </div>`).join('');

  sb.innerHTML = `
    <button class="close" title="Close">✕</button>
    <h3>${escapeHtml(node.id)}</h3>
    <span class="badge">${swatchHtml({ color: CAT_COLOR[node.category], shape: 'round-rectangle' })}
      ${CAT_LABEL[node.category]}${propsBadge(node)}</span>
    <div class="kv">package: <code>${node.pkg === '' ? '(default)' : escapeHtml(node.pkg)}</code></div>
    ${originLines(node)}
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

  // View sharing / deep links: ?collapse=pkg1,pkg2 pre-collapses packages.
  const collapsed = new URLSearchParams(window.location.search).get('collapse');
  if (collapsed) {
    collapsed.split(',').map((p) => p.trim()).filter(Boolean).forEach((p) => state.collapsed.add(p));
  }

  state.cy = cytoscape({ container: document.getElementById('cy'), elements: [], style: STYLESHEET });
  state.cy.on('tap', 'node', (e) => {
    if (e.target.hasClass('package-group')) return; // collapse handled below
    if (e.target.hasClass('collapsed-package')) return; // expand handled below
    showNodeDetails(e.target.id());
  });
  state.cy.on('tap', 'node.package-group', (e) => {
    const pkg = e.target.data('pkg');
    if (pkg === undefined) return;
    state.collapsed.add(pkg);
    if (state.graph) updateElements(state.graph, { randomize: false });
  });
  state.cy.on('tap', 'node.collapsed-package', (e) => {
    const pkg = e.target.data('pkg');
    if (pkg === undefined) return;
    state.collapsed.delete(pkg);
    if (state.graph) updateElements(state.graph, { randomize: false });
  });
  state.cy.on('tap', (e) => {
    if (e.target === state.cy) document.getElementById('sidebar').hidden = true;
  });

  // Debug/testing hook: reach the view state and render pipeline from the console.
  window.__constellation = { state, updateElements, toElements };

  await updateGraph();
}

init().catch((err) => {
  const el = document.getElementById('empty');
  el.hidden = false;
  el.innerHTML = `<div class="card">Failed to load graph: ${escapeHtml(String(err))}</div>`;
});
