## Summary

## New car map checklist (when changing `maps/**`)

- [ ] `maps/<make>/<model>/<region>/<firmware>/` contains `map.json`, `car.json`, and `README.md`.
- [ ] `car.json` has the exact `ro.build.product`, onebin, Android version, and MD5 for every mapped stock file.
- [ ] Every module's `hookpoints.required` resolves and verifies; unresolved optional IDs are listed.
- [ ] `./tools/verify-map maps/<make>/<model>/<region>/<firmware>` and `make test` pass.
- [ ] Host and on-car maps were compared; differences and on-car verification evidence are described below.
- [ ] No Car Report, stock binary, raw smali, VIN, serial, network detail, or other personal data is included.

### Mapping evidence

Product:  
Onebin:  
Android version:  
Mapped / verified / failed:  
Unresolved point IDs:  
Host/on-car differences:  
On-car module/revert checks:
