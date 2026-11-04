Status: proposal (2026-11-04), not implemented. Companion to `module-plugin-design.md`.

# Module resource names

Modules declare what they use with `uses=<resource>:<mode>,...` in `module.properties`.
The runtime refuses to enable a module whose `uses` conflicts with a module that is on.
The owner asked for a broad catalogue so authors rarely need a custom name.

## Rules

1. Names are dotted and hierarchical. A resource overlaps its parents and children:
   `wifi:only` conflicts with any `wifi.radio:*`; `audio.output:only` conflicts with
   `audio.output.navi:*`.
2. Modes:
   - `use`: shared, any number of modules (default when no mode is written).
   - `only`: exclusive; conflicts with every other mode on the same resource.
   - Named modes (table below): two different named modes conflict, the same named mode
     is shared, and a named mode does not conflict with `use`.
   - `read`: never conflicts; documents a dependency for the review screen.
3. Custom names: `x.<author>.<name>` are accepted without validation, so third-party
   authors can coordinate among their own modules. `hsm validate` warns on any other
   unknown name to catch typos.
4. A module that names no resource still gets automatic conflicts from its touched list.
5. New catalogue names are added in this file and in the core list the runtime ships;
   names are never removed or renamed, only marked deprecated.

## Catalogue

### Connectivity

| Resource | Named modes | Notes |
| --- | --- | --- |
| `wifi.radio` | `ap`, `sta`, `off` | one radio: AP for projection, STA for client |
| `wifi.ap.config` | | hotspot SSID, password, band, channel |
| `wifi.sta.config` | | saved networks, auto-join |
| `bt.radio` | `on`, `off` | |
| `bt.profile.a2dp` | | media audio |
| `bt.profile.hfp` | | calls |
| `bt.profile.pbap` | | contacts sync |
| `bt.profile.avrcp` | | media control |
| `bt.pairing` | | pairing flow, device list |
| `usb.host` | `projection`, `storage`, `adb` | front USB port role |
| `usb.gadget` | | device-side USB |
| `adbd` | `usb`, `tcp`, `off` | ADB daemon transport |
| `network.routing` | | default route, NAT, tethering |
| `network.dns` | | |
| `network.firewall` | | iptables rules |
| `modem` | | telematics modem, if present |
| `gps` | | position source, NMEA |
| `time.sync` | | network or GPS time |

### Phone projection

| Resource | Named modes | Notes |
| --- | --- | --- |
| `projection.carplay` | `wired`, `wireless` | |
| `projection.androidauto` | `wired`, `wireless` | |
| `projection.mirror` | | other screen-mirroring stacks |
| `projection.session` | | session start/stop hooks, shared by both |
| `projection.audio` | | projection audio routing |
| `projection.tbt` | | turn-by-turn data to cluster/HUD |

### Audio

| Resource | Named modes | Notes |
| --- | --- | --- |
| `audio.output` | | whole output path |
| `audio.output.media` | | |
| `audio.output.navi` | | guidance prompts |
| `audio.output.phone` | | call audio |
| `audio.output.alert` | | chimes, warnings |
| `audio.focus` | | focus and ducking policy |
| `audio.volume` | | volume curves, per-source levels |
| `audio.eq` | | equaliser, sound field |
| `audio.codec` | | decoder libraries, formats |
| `audio.mic` | | microphone input |
| `audio.voice` | | voice recognition engine |

### Display and UI

| Resource | Named modes | Notes |
| --- | --- | --- |
| `display.brightness` | | day/night curves |
| `display.overlay` | | anything drawn over other apps |
| `display.resolution` | | density, layout scaling |
| `ui.home` | | home screen and widgets |
| `ui.launcher` | | app list |
| `ui.statusbar` | | |
| `ui.softkeys` | | on-screen system keys |
| `ui.theme` | | colours, skins |
| `ui.font` | | |
| `ui.wallpaper` | | |
| `ui.clock` | | clock style and screen |
| `ui.boot` | | boot logo and animation |
| `ui.consent` | | startup consent or disclaimer screen |
| `ui.locale` | | language and region |
| `ui.notification` | | popups, toasts |

### Input

| Resource | Named modes | Notes |
| --- | --- | --- |
| `input.keys.steering` | | steering wheel keys |
| `input.keys.headunit` | | head-unit hard keys and knobs |
| `input.keys.custom` | | user-programmable button |
| `input.touch` | | touch handling, gestures |
| `input.voice_key` | | push-to-talk key |

### Vehicle

| Resource | Named modes | Notes |
| --- | --- | --- |
| `vehicle.can` | `read`, `write` | vehicle bus access |
| `vehicle.speed_lock` | | driving restrictions on UI and video |
| `vehicle.camera.rear` | | |
| `vehicle.camera.surround` | | |
| `vehicle.cluster` | | instrument cluster output |
| `vehicle.hud` | | head-up display output |
| `vehicle.diagnostics` | | engineering and factory menus |
| `vehicle.factory` | | factory Binder services |
| `vehicle.charging` | | EV charging screens and schedules |
| `vehicle.climate` | | climate screens |
| `vehicle.settings` | | vehicle settings screens |

### Stock apps

| Resource | Named modes | Notes |
| --- | --- | --- |
| `app.navi` | | navigation app |
| `app.radio` | | FM/AM/DAB |
| `app.dmb` | | terrestrial TV |
| `app.media` | | USB and BT media player |
| `app.phone` | | |
| `app.settings` | | system settings app |
| `app.telematics` | | connected-car service app |
| `app.voice` | | voice assistant app |
| `app.manual` | | on-screen manual |
| `app.packages` | | installing or replacing APKs |

### System

| Resource | Named modes | Notes |
| --- | --- | --- |
| `system.app_process` | | normally `only`; owned by the core hook host |
| `system.zygote` | | zygote-wide preloads |
| `system.hook.<package>` | | per-app hooks, e.g. `system.hook.com.mnsoft.navi` |
| `system.framework` | | framework jars and resources |
| `system.feature` | | feature XML and `featurexml.cfg` |
| `system.permissions` | | permission XML |
| `system.properties` | | broad property changes |
| `system.init` | | init services |
| `system.boot` | | boot sequence and boot-completed timing |
| `system.dalvik` | | Dalvik cache |
| `system.update` | | firmware update behaviour |
| `system.log` | | logging levels, log capture |
| `system.storage` | | mounts, partitions |
| `system.security` | | SELinux mode, verification |

### Power

| Resource | Named modes | Notes |
| --- | --- | --- |
| `power.sleep` | | sleep and quick-boot behaviour |
| `power.wake` | | wake triggers |
| `power.acc` | | accessory-on/off handling |
| `power.screen` | | screen-off behaviour |

## Current modules

| Module | `uses` |
| --- | --- |
| `wireless-carplay-and-aa` | `wifi.radio:ap,projection.carplay:wireless,projection.androidauto:wireless,system.app_process:only,system.feature` |
| `wifi-client` | `wifi.radio:sta,system.feature` |
| `wireless-adb` | `adbd:tcp` |
| `skip-consent-screen` | `ui.consent,vehicle.factory` |
| `user-button-shortcut` | `input.keys.steering,input.keys.custom` |
| `user-button-head-unit` | `input.keys.headunit,input.keys.custom` |

The `user-button-*` assignments are provisional; confirm against each module's notes
before converting it.
