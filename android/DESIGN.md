---
name: GoldenEye 007 (Android)
description: In-game panels set like a spy film's opening credits, over the running mission.
colors:
  titles-black: "#07070a"
  ivory: "#f2e6c9"
  credit-grey: "#8a8170"
  hairline: "#2a2620"
  molten-gold: "#d9a441"
  confirm-red: "#8c1c13"
  pressed: "#16140f"
  scrim: "#07070a9e"
  tile-field: "#07070aa6"
  tile-hairline: "#f2e6c9b3"
typography:
  display:
    fontFamily: "Michroma, sans-serif"
    fontSize: "30sp"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "0.42em"
  title:
    fontFamily: "Michroma, sans-serif"
    fontSize: "11sp"
    fontWeight: 400
    lineHeight: 1
    letterSpacing: "0.32em"
  label-credit:
    fontFamily: "Michroma, sans-serif"
    fontSize: "10sp"
    fontWeight: 400
    letterSpacing: "0.28em"
  body:
    fontFamily: "sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    fontFeature: "tnum"
  value:
    fontFamily: "sans-serif-medium"
    fontSize: "15sp"
    fontWeight: 500
    fontFeature: "tnum"
  button:
    fontFamily: "sans-serif-medium"
    fontSize: "14sp"
    fontWeight: 500
  subtitle:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    letterSpacing: "0.2em"
    fontFeature: "tnum"
  note:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: 1.15
    fontFeature: "tnum"
  status:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: 1.15
    fontFeature: "tnum"
rounded:
  none: "0dp"
spacing:
  hair: "1dp"
  rule: "2dp"
  xs: "8dp"
  sm: "16dp"
  md: "24dp"
  lg: "32dp"
  tile: "40dp"
  target: "48dp"
  slot: "64dp"
  inset: "88dp"
  rail: "216dp"
components:
  panel:
    backgroundColor: "{colors.titles-black}"
    rounded: "{rounded.none}"
    padding: "24dp 32dp 0 32dp"
  rail:
    width: "{spacing.rail}"
    padding: "24dp 0 16dp 0"
  rail-credit:
    textColor: "{colors.credit-grey}"
    typography: "{typography.title}"
    height: "{spacing.target}"
    padding: "0 16dp 0 24dp"
  rail-credit-active:
    textColor: "{colors.ivory}"
    typography: "{typography.title}"
    height: "{spacing.target}"
  button-primary:
    backgroundColor: "transparent"
    textColor: "{colors.molten-gold}"
    typography: "{typography.label-credit}"
    rounded: "{rounded.none}"
    padding: "0 16dp"
    height: "{spacing.target}"
  button-primary-pressed:
    backgroundColor: "{colors.molten-gold}"
    textColor: "{colors.titles-black}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.ivory}"
    typography: "{typography.button}"
    rounded: "{rounded.none}"
    padding: "0 16dp"
    height: "{spacing.target}"
  button-secondary-pressed:
    backgroundColor: "{colors.pressed}"
    textColor: "{colors.ivory}"
  button-disabled:
    backgroundColor: "transparent"
    textColor: "{colors.credit-grey}"
  button-confirm-armed:
    backgroundColor: "{colors.confirm-red}"
    textColor: "{colors.ivory}"
    rounded: "{rounded.none}"
    height: "{spacing.target}"
  switch-off:
    backgroundColor: "transparent"
    rounded: "{rounded.none}"
    width: "44dp"
    height: "24dp"
  switch-on:
    backgroundColor: "{colors.molten-gold}"
    rounded: "{rounded.none}"
    width: "44dp"
    height: "24dp"
  slider:
    backgroundColor: "{colors.hairline}"
    height: "{spacing.target}"
  slider-thumb:
    backgroundColor: "{colors.ivory}"
    rounded: "{rounded.none}"
    size: "16dp"
  slider-thumb-held:
    backgroundColor: "{colors.molten-gold}"
    size: "16dp"
  settings-row:
    textColor: "{colors.ivory}"
    typography: "{typography.body}"
    height: "{spacing.target}"
  slot-row:
    textColor: "{colors.ivory}"
    typography: "{typography.body}"
    height: "{spacing.slot}"
    padding: "8dp 0"
  shortcut-tile:
    backgroundColor: "{colors.tile-field}"
    textColor: "{colors.ivory}"
    rounded: "{rounded.none}"
    size: "{spacing.tile}"
  shortcut-tile-on:
    backgroundColor: "{colors.molten-gold}"
    textColor: "{colors.titles-black}"
    rounded: "{rounded.none}"
    size: "{spacing.tile}"
---

# Design System: GoldenEye 007 (Android)

## Overview

**Creative North Star: "Opening Titles"**

The app's in-game panels read like a spy film's opening credits: a near-black field laid over the still-running mission, section names set as wide-tracked capital credits, and a clean list of settings beneath them. Gold is light catching the screen: it marks only what is on, what is active, and the one action that returns the player to the mission. Everything else is ivory on black or a muted credit grey.

Density is calm but quick. Every target is 48 dp, every row is a single line of plain sans text with its control at the right edge, and the panel leaves the game visible on both sides. Depth comes from black on a dimmed game, never from shadows; form is square and flat throughout. The world refuses the grey Android settings card and the neon gaming HUD.

Scope: this system covers the two in-game panels, Options (gear, `TouchSettingsPanel`) and Saves (`SaveStatesPanel`), and the touch overlay's five corner shortcuts (`TouchOverlay`: options, F10, saves, gadget, start / watch), all built from the shared tokens in `Titles.java`. The rest of the touch overlay (the round action buttons, the stick, the menu OK / BACK and the edit-mode pills) keeps its own look and is not in this world; only its placement on the watch screen follows this system. The setup screen (`SetupActivity`) and the licences dialog (`Licences`) are not yet in this world either. The PC port's F10 overlay is the game's own UI and is outside this system.

**Key Characteristics:**
- Near-black panel over a 62 % scrim of the running game, gold 2 dp rule along its top edge.
- Michroma credits (wide-tracked capitals) for section titles, rail entries and the primary action only.
- System sans for everything read; values in tabular figures with their unit.
- Gold reserved for on, active and the primary action; red only for a destructive action awaiting confirmation.
- Square, flat, outlined controls on an 8 dp grid; 48 dp targets.
- One signature motion: a title replays as a credit, its tracking closing from wide to set.
- Over the running game, the menus are reached through flat square keys (Shortcut Tiles), never round discs: square says "menu", round says "action".

## Colors

A warm, almost monochrome credits palette: ivory and grey on near-black, one metal accent, one alarm.

### Primary
- **Molten Gold** (molten-gold): the panel's top rule, the active rail credit's 16 dp rule, the primary button's outline and label (filled when pressed), a switch that is on, the slider's filled track and held thumb, slider values, the focus outline of buttons, a Shortcut Tile while held (and Start's tile while the watch it opened is up). Nothing decorative is gold.

### Secondary
- **Confirm Red** (confirm-red): the fill of a destructive button between its first and second tap (Defaults, 3 s window). Never a resting colour.

### Neutral
- **Titles Black** (titles-black): the panel field; the thumb of a switch that is on; the label of a pressed primary button; the pictogram on a gold (held) Shortcut Tile.
- **Scrim** (scrim, titles-black at 62 %): laid over the running game around the panel; tapping it closes the panel.
- **Ivory** (ivory): section titles, the active rail credit, row labels, secondary button labels, the slider thumb at rest, the focus outline of switches, Shortcut Tile pictograms.
- **Tile Field** (tile-field, titles-black at 65 %): the resting Shortcut Tile, translucent so the game shows through while the key still reads on snow.
- **Tile Hairline** (tile-hairline, ivory at 70 %): the resting Shortcut Tile's 1 dp outline, so the key reads at night.
- **Credit Grey** (credit-grey): rail credits not selected, subtitles, notes, the rail's status line, a switch's outline and thumb when off, secondary button outlines, disabled labels.
- **Hairline** (hairline): the 1 dp rail divider and row separators, the empty slider track, a disabled button's outline.
- **Pressed** (pressed): touch and focus feedback behind rows, rail credits and secondary buttons.

### Named Rules
**The Gold Means On Rule.** Gold appears only on what is on, active, or the primary action (plus the panel's top rule, which opens the sequence). If gold is on something that is none of these, it is wrong.

**The Red Is a Question Rule.** Red appears only while a destructive action waits for its confirming tap, and reverts on its own.

## Typography

**Display Font:** Michroma (bundled, SIL OFL 1.1, `assets/fonts/Michroma-Regular.ttf`; falls back to sans-serif)
**Body Font:** the system sans (`sans-serif` / `sans-serif-medium`: Roboto on stock Android, the maker's sans on One UI)

**Character:** Michroma's wide, square capitals carry the credits; the system sans does all the reading, so labels stay native, legible and font-scale aware.

### Hierarchy
- **Display** (Michroma 400, 30 sp, tracking 0.42, all caps, one line): the section or panel title. Fitted down per line so the longest translation never clips.
- **Title** (Michroma 400, 11 sp, tracking 0.32, all caps): the three rail credits. All three share one fitted size, so the active one never shrinks alone.
- **Label credit** (Michroma 400, 10 sp, tracking 0.28, all caps, auto-sized to 7 sp): the primary button only.
- **Body** (sans 400, 15 sp, tabular figures): row labels and slot names.
- **Value** (sans medium 500, 15 sp, tabular figures): slider values in gold with their unit ("64 %"); read-only values in ivory.
- **Button** (sans medium 500, 14 sp, sentence case): secondary buttons.
- **Subtitle** (sans 400, 12 sp, tracking 0.2, all caps, grey): the one line under the display title.
- **Note / Status** (sans 400, 12 sp / 13 sp, line spacing 1.15, grey): explanatory notes below rows; the Saves rail's status line.

### Named Rules
**The Credits Only Rule.** Michroma is for credit titles, rail credits and the primary action, plus one sanctioned key cap: "F10" on its Shortcut Tile (the PC key's name, sized to 0.18 of the tile side, tracking 0.05). A label, value, note or secondary button in Michroma is wrong.

**The Steady Figures Rule.** Every text view the system builds sets tabular figures, so a value changing under a thumb never jitters.

## Layout

Landscape phone, one full-window overlay. The panel is inset 88 dp left and right and 16 dp top and bottom, so the game stays visible on both sides. Inside: a 2 dp gold rule, then a horizontal split: a 216 dp rail on the left (under the left thumb), a 1 dp hairline divider, and a body that takes the rest.

- **Rail:** 24 dp top padding, 16 dp bottom; entries in 48 dp rows; a flexible gap; the primary Resume button (48 dp tall, full rail width minus 24 dp side margins) pinned at the foot. The Saves rail replaces the credits with a status line (24 dp side padding, 32 dp top).
- **Body:** 32 dp side padding, 24 dp top. Title (min 40 dp), subtitle (min 24 dp), then a scrolling list (8 dp top, 16 dp bottom padding) with no scrollbar and a 24 dp fading edge into the black where more lies above or below.
- **Rows:** 48 dp minimum (64 dp for save slots), each followed by a 1 dp hairline. Action buttons sit in a line 16 dp below the last row, 8 dp apart.

**Touch overlay (over the running game, full screen):**
- **Corner shortcuts:** two groups on the top edge, 24 dp in from the side and top edges. Left, from the edge: options, F10, saves. Right, from the edge: start (the watch in a mission, pause in the front end), then gadget (in a mission only). Tiles are 40 dp x the overlay's size setting with 16 dp gaps, so the pitch grows with the size setting; each has a square hit area of at least the 48 dp target, 8 dp from its neighbour at any size. A tile is pushed sideways only if it would overlap a camera cutout.
- **Watch screen:** while the watch is open, the menu OK and BACK move to the right margin, stacked (BACK 64 dp from the edge at 40 % of the height, OK 72 dp at 72 %), and the resting stick is not drawn; the stick still appears under a thumb. The watch face stays clear.

Spacing sits on an 8 dp grid (8, 16, 24, 32, 48, 64, 88), with 1 dp and 2 dp reserved for hairlines and rules. The few in-component offsets (12 dp gap after the active credit's rule, 2 dp between slot name and description) are local, not scale steps.

## Elevation & Depth

Flat. No shadow, glow, gradient or blur anywhere: the panel is a black plane on a 62 % scrim of the game, separated by value alone. Structure comes from the gold top rule, 1 dp hairlines and the rail divider. Over the game, Shortcut Tiles are flat translucent squares (65 % field, 70 % hairline) whose opacity also follows the overlay's opacity setting; they never cast a shadow.

### Named Rules
**The Black Plane Rule.** Depth is black over dimmed game; nothing casts a shadow and nothing glows.

## Shapes

Square everywhere: 0 dp radius on the panel, buttons, switch track and thumb, slider thumb and track. Outlines are 1 dp. Rules are straight 2 dp lines: the full-width gold rule on the panel and the 16 dp gold dash before the active rail credit. Shortcut Tiles are square too, and their pictograms share one geometric hand: 1.6 dp strokes, mitred corners, butt caps, square teeth and bars, spanning the middle half of the tile. A circle may appear inside a pictogram (the gear's ring, the watch face); it is drawing, not shape.

## Components

### Buttons
Outlined, flat, 48 dp tall, 16 dp side padding, square.
- **Primary** (one per panel: Resume): gold 1 dp outline, gold Michroma label; pressed fills gold with a black label; focused shows a pressed fill with the gold outline.
- **Secondary** (Move buttons, Defaults, Change ROM, Licences, Save, Load): grey 1 dp outline, ivory sans-medium label; pressed fills with the pressed tone; focused adds the gold outline.
- **Disabled:** hairline outline, grey label.
- **Two-step destructive** (Defaults): the first tap turns the button solid red with an ivory "Confirm?" label for 3 s; a second tap acts, otherwise it reverts.

### Navigation (rail credits)
Three Michroma credits in 48 dp rows, 24 dp left padding. Inactive: grey. Active: ivory, preceded by a 16 dp x 2 dp gold rule. Pressed or focused: the pressed tone behind the row. The panel reopens on the last section used. System Back closes the panel, as does a tap on the scrim.

### Square Switch
A custom `CompoundButton` (the platform's role is "switch"), 44 x 24 dp. Off: grey 1 dp outline, 16 dp grey square thumb at the left (4 dp inset). On: solid gold, black thumb at the right. The thumb slides and the fill crossfades in 150 ms. Focused: a 1 dp ivory outline. The whole row toggles it; an unavailable row drops to 40 % opacity.

### Slider
2 dp track: hairline when empty, gold up to the value. A 16 dp square thumb, ivory at rest, gold while held. The slider sits in its own 48 dp band under a label line (min 32 dp) whose right edge carries the value in gold, 64 dp wide, right-aligned.

### Settings Row
Ivory 15 sp label on the left (12 dp vertical padding), control or value on the right, 48 dp minimum, pressed tone on touch, hairline beneath. Notes follow rows in 12 sp grey with 8 dp vertical padding.

### Save Slot Row
64 dp minimum: slot name (15 sp ivory) over its description (12 sp grey), then Save and Load secondary buttons at the right, 8 dp apart. The automatic slot has Load only. Unavailable actions are disabled, never hidden.

### Shortcut Tile
The panels' square keys set into the game: one per corner shortcut, all five the same size (40 dp x size setting), 0 dp radius.
- **Rest:** tile field fill, 1 dp tile-hairline outline, ivory pictogram.
- **On:** solid gold fill and outline, black pictogram, while held, and on Start's tile while the watch it opened is up.
- **Edit mode (moving buttons):** a white dashed 1 dp outline.
- **Pictograms:** options, a ring with eight square teeth; F10, the key name in Michroma; saves, a floppy with its cut corner, shutter and label; gadget, a detonator with its antenna and a step arrow; start, the watch (square straps, round face, two hands) in a mission and two square pause bars in the front end.
- **Size and hit:** see Layout; the hit square is never smaller than 48 dp.

### Opening and Credit Motion (signature)
- **Panel opening:** the panel fades up from 0 and the gold top rule draws in from the left (scale X from 0), both 220 ms, decelerate (factor 2).
- **Credit replay:** on section change (and on Saves opening) the title fades in while its tracking closes from +0.5 em above set to set, 220 ms, decelerate (factor 2). While the tracking is open, glyphs are condensed horizontally so the line never exceeds its width; every title replays, none clips. A second tap cancels the first replay.
- **List change:** the new section's list crossfades in over 160 ms.
- All motion is instant when the system has animations off.

## Do's and Don'ts

### Do:
- **Do** keep the game visible: panel inset 88 dp from both sides over the 62 % scrim.
- **Do** build every target at 48 dp and keep the primary action at the foot of the left rail, under the thumb.
- **Do** use gold only for on, active and the primary action; one primary button per panel.
- **Do** set titles in Michroma and fit them to their line; set everything read in the system sans with tabular figures.
- **Do** make destructive actions take two taps, the armed state in red for 3 s.
- **Do** honour the system's animation setting: every transition has an instant path.
- **Do** set every corner shortcut as a Shortcut Tile, one shared size, and keep the watch face clear (OK / BACK to the right margin, no resting stick) while the watch is up.

### Don't:
- **Don't** round anything: no pills, no rounded cards, no circular thumbs, no round shortcut discs (a circle drawn inside a pictogram is allowed).
- **Don't** add shadows, glows, gradients or grain.
- **Don't** use Michroma for labels, values, notes or secondary buttons.
- **Don't** use red for anything but a destructive action awaiting confirmation.
- **Don't** fall back to the grey Android settings card or a neon gaming HUD.
