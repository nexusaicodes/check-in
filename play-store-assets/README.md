# Play Store assets

Graphics for the Google Play listing of **CheckIn - Solopreneur Tracker**. See
`../PLAY_STORE_CONSOLE_ANSWERS.md` §10 for the listing text and where each asset goes.

| File | Spec | Use |
|---|---|---|
| `../app/src/main/ic_launcher-playstore.png` | 512×512 PNG | App icon (already in the app module) |
| `feature-graphic.png` | 1024×500 PNG | Feature graphic |
| `generate_feature_graphic.py` | — | Regenerates `feature-graphic.png` |

## Regenerating the feature graphic

Requires Pillow (`pip install Pillow`). Run from the repo root:

```bash
python3 play-store-assets/generate_feature_graphic.py
```

It composites the real launcher icon over a brand-indigo (`#3F51B5`) gradient with the
"CheckIn / Solopreneur Tracker" wordmark and privacy tagline. Fonts fall back across common
macOS system faces.

## Still TODO (need the running app)

- **Phone screenshots** — check-in timer, attendance calendar, reports/deficit (≥1080 px/side).
- **7″ + 10″ tablet screenshots** — the two-pane Attendance layout (required, since the app lists
  for tablets/foldables).
