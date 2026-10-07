# Vendored third-party libraries

All files in this directory are vendored as-is — no runtime CDN dependency.
All four are MIT licensed (cytoscape: The Cytoscape Consortium;
cytoscape-fcose / cose-base / layout-base: i-Vis Lab, Bilkent University).

| File | Package | Version | Source |
|---|---|---|---|
| `cytoscape.min.js` | cytoscape | 3.34.3 | https://unpkg.com/cytoscape@3.34.3/dist/cytoscape.min.js |
| `layout-base.js` | layout-base | 2.0.1 | https://unpkg.com/layout-base@2.0.1/layout-base.js |
| `cose-base.js` | cose-base | 2.2.0 | https://unpkg.com/cose-base@2.2.0/cose-base.js |
| `cytoscape-fcose.js` | cytoscape-fcose | 2.2.0 | https://unpkg.com/cytoscape-fcose@2.2.0/cytoscape-fcose.js |

Load order in `index.html` matters (UMD globals):
`layout-base` (sets `layoutBase`) → `cose-base` (sets `coseBase`, needs `layoutBase`)
→ `cytoscape` → `cytoscape-fcose` (auto-registers the `fcose` layout, needs `coseBase` + `cytoscape`).
