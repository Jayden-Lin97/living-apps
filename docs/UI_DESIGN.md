# Living Apps 0.2.0 — Native editing workspace

**English** | [简体中文](UI_DESIGN.zh-CN.md)

Version 0.2.0 extends the 0.1.1 app library and visual system with editing inside the app screen. The top row offers Trial and Done; the second row provides Undo, Redo, Fields and More. The bottom panel scrolls independently while the app remains selectable.

## Editor

The collapsed panel retains the object name. Half-height shows quick adjustments, AI editing and conversation; expanded mode accommodates longer requests. Selection is communicated through an outline, a name and accessibility descriptions. Use, Edit and Trial write to live records, drafts and trial copies respectively, with the current state clearly visible.

| Selection and offline edits | Isolated trial | 320 dp / 1.3× font scale |
|---|---|---|
| ![Selected field](qa/host-draft-selected.png) | ![Trial](qa/host-draft-trial.png) | ![Narrow-screen editor with larger text](qa/host-draft-compact-large-text.png) |

These are host-rendered Android native Views. Narrow-screen checks cover visible top actions and scrollable panels. Keyboard behavior, TalkBack, gestures and real system insets still require device validation.

## Visual rules

| Element | Current implementation |
|---|---|
| Background / text | Pale gray-green `#F6F8F5`; body `#1E2D26`; secondary text `#68756C` |
| Brand / emphasis | Forest green `#1C3F35`; pale lime `#DBEEAB`; sub-apps retain their accent colors |
| Typography | Android system font; page titles 25–29 sp, card titles 15–18 sp, body 13–15 sp, metrics 25–42 sp |
| Layout | 22 dp page margins, 12 dp card gaps, 22–25 dp corner radii; primary and toolbar buttons at least 48 dp |
| Icons | 27 Lucide line-vector icons; generated apps may still display their own icon characters |
| Feedback | Card tap ripples, explicit segmented selection, explanatory empty states, preview before adoption |
| Small screens / larger text | Single-column app cards below 360 dp or above 1.2× font scale; smaller creation illustration and shorter banner copy |

## Implemented screens

- Library: brand header, compact creation card, two-column app cards and offline templates.
- Sub-app: Edit, Versions and More in the toolbar; segmented navigation, totals in the overview and concise record cards.
- Statistics: primary metric, two supporting counts and grouped category bars.
- Creation: three-step guidance, natural-language input, tappable inspiration and a fixed Generate action.
- Preview: app identity, change explanation, real trial form and fixed Adopt/Back actions.
- Growth: brand illustration, waiting-for-observation empty state, actual usage counts and previewable suggestions.
- Settings: grouped AI configuration, optimization switches, backups and licenses, with consistent native controls.

## Actual interface renders

Robolectric's Android native View/graphics runtime generated these images with synthetic test data. They show the implementation, not concept art or device screenshots. These captures use the Chinese UI; the app also supports English and a system-default language setting.

| Library | Create an app | Growth observations |
|---|---|---|
| ![Library](qa/host-home.png) | ![Create app](qa/host-editor.png) | ![Growth observations](qa/host-insights.png) |

| Sub-app overview | Statistics | Version preview |
|---|---|---|
| ![Overview](qa/host-app.png) | ![Statistics](qa/host-stats.png) | ![Preview](qa/host-preview.png) |

| Settings | 320 dp / 1.3× home | 320 dp / 1.3× creation |
|---|---|---|
| ![Settings](qa/host-settings.png) | ![Narrow home with larger text](qa/host-home-compact-large-text.png) | ![Narrow creation with larger text](qa/host-editor-compact-large-text.png) |

Regression flows cover opening an app from the library, entering and saving a record, viewing statistics, navigating to Growth/Settings/Creation, renaming offline, trying a preview and adopting a version. Records survive changes. Soft keyboards, system navigation bars, OEM fonts and other devices still need device acceptance testing.

## Reconstructed interfaces

The new mode reuses the native forest-green/soft-white design system. A source association card, page-picking dock, component list, appearance editor, trial status and original-app exit are explicit parts of the flow. Dynamic lists do not imply access to off-screen data. [Editor render](qa/host-rebuild-editor.png) and [runtime render](qa/host-rebuild-runtime.png) use synthetic framework windows, not a physical phone or QQ Music. See [Rebuild mode](REBUILD_MODE.md).
