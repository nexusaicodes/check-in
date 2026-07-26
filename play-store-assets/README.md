# Play Store assets

Graphics for the Google Play listing of **CheckIn - Solopreneur Tracker**.

| File | Spec | Use |
|---|---|---|
| `generate_icons.py` | — | **Source of truth for the brand mark.** Emits the `pathData` for the app's vector drawables and renders the 512px Play icon |
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (generated — do not hand-edit) |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic (generated) |
| `generate_feature_graphic.py` | — | Regenerates `feature-graphic.png` |

Both scripts need Pillow (`pip install Pillow`) and run from the repo root.

## The brand mark

One unbroken stroke: an open progress ring whose terminal resolves into a check. The ring is
the day in progress; it doesn't close, it resolves.

`generate_icons.py` holds the geometry and emits everything derived from it:

```bash
python3 play-store-assets/generate_icons.py
```

It prints the `pathData` for `res/drawable/ic_launcher_foreground.xml` and
`ic_stat_checkin.xml`, and writes the 512px Play icon. If you change the geometry, paste the
printed paths into those two drawables — they are the only copies, and the script's `verify()`
guards the adaptive-icon safe circle on every run.

`ic_launcher_foreground.xml` does triple duty: launcher foreground, themed (monochrome) layer
and splash icon all point at it, so they cannot drift apart.

Two sizing rules are encoded there and are easy to get wrong by hand:

- The mark is sized against the **72dp window the launcher actually shows**, not the 66dp safe
  circle. Filling the safe circle is legal but reads oversized next to other icons.
- Arcs are emitted as cubic Béziers, not SVG `A` commands. Android's `PathParser` accepts both,
  but arc flags are the one place its behaviour diverges from browser renderers.

## Regenerating the feature graphic

```bash
python3 play-store-assets/generate_feature_graphic.py
```

It composites the real 512px icon over a deep-indigo gradient with the wordmark and privacy
tagline. The gradient sits **below** the launcher indigo (`#3F51B5`) in value on purpose — the
icon tile is flat brand indigo, so a same-value field would swallow it. Fonts fall back across
common macOS system faces.

## Still TODO (need the running app)

- **Phone screenshots** — check-in timer, attendance calendar, reports (≥1080 px/side).
- **7″ + 10″ tablet screenshots** — the two-pane Attendance layout (required, since the app
  lists for tablets/foldables).
