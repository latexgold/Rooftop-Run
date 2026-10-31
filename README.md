# Rooftop Run

An original 2D rooftop platformer for desktop and Android, built with Godot 4.3+. You play a courier crossing the city roofline. This repository is the first playable chapter and a reusable base for a larger campaign.

## Play locally

1. Install Godot 4.3 or newer.
2. Clone/download this repository and open `project.godot` in Godot.
3. Press **F6/F7** or click **Run Project**.

### Controls

- Move: **A/D** or **←/→**
- Jump: **Space** or **W** (short tap = short hop)
- Dash: **Shift** or **X** (unlocks in level 5)
- Pause: **Esc** or **P**
- Touch: on-screen left/right, jump, and dash controls; toggle in Settings.

## In this first chapter

- 12 stages across Old Town, Glass Market, and North Spire
- Progressive movement: jump, wall-jump, then dash
- Rooftop tokens, checkpoints, hazards, level map, pause screen, saved progress
- Responsive touch controls, keyboard controls, settings for touch controls and camera shake
- Original vector-drawn courier and procedural city skyline

The route geometry is generated from per-stage parameters for this prototype. The next production pass should hand-author and playtest each stage, add more movement mechanics and hazards, and expand the campaign toward five districts and 60 authored levels. This source-first build has not yet been opened in the Godot editor or tested on Android hardware.

## Export to Android

Use Godot's Android export workflow. Install OpenJDK 17 and the Android SDK, install the matching Godot export templates, then add an Android export preset under **Project → Export**. For local installation, export an APK; an Android App Bundle is the store-upload format. See [Godot's Android export guide](https://docs.godotengine.org/en/stable/tutorials/export/exporting_for_android.html) and [Android Developers' Godot export guide](https://developer.android.com/games/engines/godot/godot-export).

## Project layout

- `project.godot`: project settings and input mappings
- `scenes/main.tscn`: main scene
- `scripts/main.gd`: menus, level generation, checkpoints, UI, saves
- `scripts/courier.gd`: movement, coyote time, buffered jumps, dash and character drawing
