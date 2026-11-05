Historical: moved from `.agents/cluster-lane-frame.md` section 6 (stock engine lane log, owner drive 2026-11-04). Current format and field meanings are kept in that doc.

Source: `tmp/car-sessions/2026-11-04-tbt/live/logcat-mac.log`. There was no parallel CarPlay 0x5204 (dev mode off on the first drive; the lane recipe crashed iap2server on the second).

Observed samples:
- `(dir,idx)` pairs seen: (0,1) dominant in through lanes = straight; (1,2), (3,3), (6,5), (6,10), (9,7), (9,14), (11,8), (0,16)+(3,18).
- `vari:1` was on the turn lane in these samples.
- A 12-lane toll-plaza set (`rd:294`) showed `lane:2 valid:2` lanes interleaved with `skip:1`.
- A supplied native log `rd:216 [lane:0 level:0 vari:1 over:1 skip:0 dir:9 idx:7 valid:1]` has no matching field-to-byte construction in the smali.
