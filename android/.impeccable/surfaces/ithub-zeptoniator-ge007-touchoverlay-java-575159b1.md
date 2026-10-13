---
version: 1
slug: "ithub-zeptoniator-ge007-touchoverlay-java-575159b1"
primary_target: "app/src/main/java/io/github/zeptoniator/ge007/TouchOverlay.java"
related_targets: []
---

# Surface: touch overlay corner shortcuts and the watch screen, Android

Scope: the five corner shortcuts of the touch overlay (options, F10 / PC options, saves, gadget, start / watch) and the overlay's layout while the watch is open. The round action buttons (FIRE, AIM, USE, RELOAD, NEXT, crouch) and the stick's look are out of scope (user's answer), except their placement on the watch screen.
Audience/job: a player mid-mission reaches the panels and the watch with a thumb; on snow and in the dark.
Constraints: readable over the game (user's answer); every function kept; Java only, framework Canvas.
User choices: proposal A "Square tiles" (.impeccable/mocks/icons/tiles.html) picked on the decision page among four plus the round canon. User report "the watch icon does nothing" (it sent B, the gadget cycle): the watch moved to Start's tile in a mission, B shows a detonator. User request "improve the UI when I open the watch": OK and BACK leave the face, the resting stick hides.

## Direction contract

THESIS: The corner shortcuts are the panels' square keys set into the game: small flat tiles, never round discs, that say "menu" where the round buttons say "action".

OWN-WORLD: Inherits android/DESIGN.md (Opening Titles): tile #07070a at 65 %, 1 dp ivory #f2e6c9 hairline at 70 %, ivory pictograms in one 1.6 dp mitred hand with butt caps, gold #d9a441 fill and black icon when held or when Start's watch is up; F10 set in Michroma.

STORY: The player sees five quiet keys at the top corners, knows the watch is the watch, and finds the watch face unobstructed once it is up.

FIRST VIEWPORT: Left corner: options, F10, saves; right corner: gadget (in a mission), start (the watch in a mission, pause bars in the front end). 40 dp x size tiles, 24 dp from the edges, 16 dp gaps, 48 dp hit squares 8 dp apart. Watch open: OK and BACK stacked in the right margin (64-72 dp from the edge), no resting stick.

FORM: Square tiles, the user's pick (proposal A); extension of the established world, no roll.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
