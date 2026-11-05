Historical: incident and discovery notes removed from `wireless-carplay-and-aa.md`. The present rules live there; do not implement from this file.

# Wireless CarPlay and AA: past findings

- Doc review date was 2026-11-03; the pre-install Car Report is `tmp/car-reports/car-report-neev-kor-260902-20261102-2115.json`.
- 2026-11-04 permission repair: recipes sat under a 0700 ancestor, so DiPO (UID 1000) could not read them and native patches were never registered. Fix: apply reconciles recipes and ancestors, status checks world-read/search bits.
- Dalvik cross-dex rule, learned on the car 2026-11-04: a mods class implementing a new `hmgapi.hook` interface failed with "Class resolved by unexpected DEX" (`NoClassDefFoundError`) and took down DiPO's whole part (`part.dipo=failed`).
- Lane guidance: redirecting 0x5204 to libiap2session's lane handler crashed `libIap2Acc.so` in `convertSubGroup` on the car, hence the stock parser stays disabled.
- `CpTurns.turn()` once used icon 0 to stop everything (no turn, HUD, trip) for an unmapped maneuver; it now uses `fallbackIcon`.
- HUD list resend (owner report, 2026-11-05): with guidance started right at CarPlay connect, the HUD later froze its arrow and road text while its distance kept counting down and the cluster stayed correct; restarting the route fixed it. The 0xF2 list had been sent only on change. The exact trigger (missed frame vs HUD restart) is unproven.
