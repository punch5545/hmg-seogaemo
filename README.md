# HMG-Seogaemo

HMG-Seogaemo is a Gen5W head-unit app and NEMESIS package with toggleable Wi-Fi client, wireless ADB, and wireless CarPlay and Android Auto modules. The app also supports OTA updates from GitHub Releases.

## Install

Download `hmg-seogaemo-<v>-nemesis-usb.zip` from [Releases](https://github.com/seogaemo/hmg-seogaemo/releases), unzip it to an empty FAT32 or exFAT USB stick, and install it through NEMESIS. Remove HMG-API and HMG Leafia Plus before installing.

## Build

```sh
make
make test
make package
```

## Screenshots

### Home

![Home](assets/home.png)

### Settings — General

![Settings — General](assets/settings-general.png)

### Module management

![Module management](assets/settings-modules.png)

### Wi-Fi client

![Wi-Fi client](assets/settings-wifi-client.png)

### Wireless ADB

![Wireless ADB](assets/settings-wireless-adb.png)

### Wireless CarPlay and Android Auto

![Wireless CarPlay and Android Auto](assets/settings-wireless-carplay-and-aa.png)

### Logs

![Logs](assets/settings-logs.png)

## License

HMG-Seogaemo is licensed under [GNU AGPL-3.0](LICENSE). See [NOTICE.md](NOTICE.md) for upstream credits.
