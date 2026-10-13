---
version: 1
slug: "zeptoniator-ge007-touchsettingspanel-java-c90f062c"
primary_target: "app/src/main/java/io/github/zeptoniator/ge007/TouchSettingsPanel.java"
related_targets: ["app/src/main/java/io/github/zeptoniator/ge007/SaveStatesPanel.java"]
---

# Surface: Options panel (gear), Android

Scope: the in-game Options overlay (TouchSettingsPanel), landscape phone, Operate mode.
Audience/job: a player mid-mission flips a cheat or retunes the touch controls and goes back to the mission in seconds.
Content: Game (invincibility, infinite ammo, all guns, auto-aim, unlock everything, difficulty note); Touch controls (look sensitivity, vertical look, invert vertical, opacity, size, vibration, second fire button on the left, keep touch with controllers, move buttons, defaults with a two-step confirm, touch keys only); App (ROM in use, change ROM, licences). The left fire button is the open item "a left-hand fire button" of docs/android.md, built after the user's "go" on the owed work. Sibling surface in the same world: the Saves panel (SaveStatesPanel), user-pinned "same".
Adaptations of the first viewport, recorded: rail 216 dp, not 200 (the French credit APPLICATION needs 144 dp at 11 sp plus the active row's 28 dp rule); the title is 30 sp, fitted down per line.
Constraints: game visible behind; every current setting; FR and EN; everything within thumb reach in landscape; framework Views in Java, no AndroidX.
Chosen direction: Opening Titles (assigned by the roll, picked by the user).

## Direction contract

THESIS: The options read like a spy film's opening credits: section names set as gold-ruled credit titles on black, the settings beneath them in a clean list. It refuses the grey Android settings card and the neon gaming HUD.

OWN-WORLD: Near-black field #07070a, warm ivory text #f2e6c9, muted credit grey #8a8170, hairline #2a2620, molten gold #d9a441 reserved for what is on, active or the primary action, deep red #8c1c13 only for a destructive state. Michroma, wide-tracked capitals, for credits and titles only; Roboto for labels and values, values in tabular figures with their unit. Square flat controls on an 8 dp grid; no gradient, glow, grain or rounded pill.

STORY: The player sees at once which section they are in and which cheats are on (gold), changes one with a thumb, and taps Reprendre to return.

FIRST VIEWPORT: Scrim over the running game, 60 % black. A panel inset 88 dp from both sides, gold 2 dp top rule. Left rail 200 dp: three credits JEU / COMMANDES / APPLICATION (48 dp rows, active one ivory with a 16 dp gold rule), REPRENDRE outlined in gold at the rail's foot under the left thumb. Right: the section title in Michroma 30 sp, tracked, a one-line subtitle, then 48 dp rows with flat square switches (gold when on) or sliders with a tabular value.

FORM: Spy-film opening titles, position 7 of the grounded list, seed key 1effa7b3. Signature move: switching section replays the title as a credit: the new title fades in with its letter-spacing closing from wide to set (220 ms), the list crossfades under it.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
