# Product

<!-- impeccable:product-schema 1 -->

Scope: the Android app of the GoldenEye 007 PC port (`android/`). The PC
port itself is described by the repository's README and docs.

## Platform

android

## Users

People who own a GoldenEye 007 cartridge dump and play the port on their
own Android phone, held in landscape with both thumbs (inferred from the
touch layout and the tests so far). They open the in-game options
**mid-mission**, to flip a cheat or adjust the touch controls and get back
to the game (confirmed).

## Product Purpose

Play GoldenEye 007 on a phone from the player's own ROM: first-run import
and on-device conversion, touch controls drawn over the game, savestates,
the game's own options and the PC port's F10 options. Success for the
options panel: a change made in seconds without losing track of the
mission.

## Operating Context

- The game keeps running behind the panels; the panels are views inside
  the game's window (a dialog or another activity takes focus from SDL's
  surface and pauses the game).
- Landscape only, phones first (tested on a 2340x1080, 420 dpi phone).
- Text follows the phone's language: English and French today.

## Capabilities and Constraints

- Options panel (gear) sections: Game (invincibility, infinite ammo, all
  guns, auto-aim, unlock everything, difficulty note), Touch controls
  (look sensitivity, vertical look, invert vertical, opacity, size,
  vibration, keep touch with controllers), App (active ROM, change ROM,
  licences), actions (defaults, move buttons, close). Every one of these
  stays (confirmed). Added since: an optional second fire button on the
  left (an open item of docs/android.md).
- Saves panel (disk): the automatic slot (load) and three slots (save,
  load); saving only during a solo mission.
- Must keep: the game visible behind the panel, every current setting, FR
  and EN, everything reachable with the thumbs in landscape (confirmed).
- Built with framework Android Views in Java, no XML layouts and no
  AndroidX/Material Components library (`android.useAndroidX=false`).
- The F10 overlay is the PC port's own UI, drawn by the game in its watch
  palette; it is shared with PC and is not part of this app's design.

## Brand Commitments

- The app is called "GoldenEye 007". GoldenEye 007 and James Bond are
  trademarks of their rights holders; the project is a non-affiliated fan
  port, so no official logos, title artwork or trademarked emblems in the
  app's UI.

## Evidence on Hand

- No screenshots, artwork or game assets ship in the APK; everything
  game-derived comes from the player's ROM at run time.

## Product Principles

1. The mission comes first: options are reached and left in a tap.
2. The phone is held in landscape with two thumbs; nothing needs a third
   hand.
3. Same behaviour as the PC port wherever both exist; Android adds only
   what touch needs.
