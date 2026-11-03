#!/usr/bin/env python3
"""Host-side signature, hash, version, rollback, and ZIP extraction tests."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from package import ROOT

JAVA = r'''
import com.seogaemo.app.core.update.UpdateVerifier;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
public final class TestUpdate {
    public static void main(String[] args) throws Exception {
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
            new X509EncodedKeySpec(Files.readAllBytes(new File(args[0]).toPath())));
        File dir = new File(args[1]);
        if (args.length == 4) UpdateVerifier.extract(dir, args[2], new File(args[3]), key);
        else System.out.println(UpdateVerifier.verify(dir, args[2], key));
    }
}
'''


def run():
    with tempfile.TemporaryDirectory() as temp:
        temp = Path(temp)
        private = temp / "private.pem"
        public = temp / "public.der"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["openssl", "pkey", "-in", str(private), "-pubout", "-outform", "DER", "-out", str(public)], check=True)
        classes = temp / "classes"
        classes.mkdir()
        (temp / "TestUpdate.java").write_text(JAVA)
        subprocess.run(["javac", "-source", "7", "-target", "7", "-d", str(classes), str(ROOT / "app/src/com/seogaemo/app/core/update/UpdateVerifier.java"), str(temp / "TestUpdate.java")], check=True, stdout=subprocess.DEVNULL)
        version = "1.2.3"
        name = f"hmg-seogaemo-{version}-ota.zip"
        stage = temp / "stage"
        stage.mkdir()
        with zipfile.ZipFile(stage / name, "w") as archive:
            archive.writestr("VERSION", version + "\n")
            archive.writestr("app/Seogaemo.apk", b"apk")
            archive.writestr("modules.list", "wifi-client\n")
            archive.writestr("modules.graph", "wifi-client\tdepends=\texcludes=\n")
        manifest = stage / f"hmg-seogaemo-{version}-manifest.json"
        signature = stage / (manifest.name + ".sig")

        def sign(hash_override=None):
            digest = hashlib.sha256((stage / name).read_bytes()).hexdigest()
            assets = []
            for suffix in (".apk", "-nemesis-package.zip", "-nemesis-usb.zip", "-ota.zip"):
                asset_name = f"hmg-seogaemo-{version}{suffix}"
                assets.append({"name": asset_name, "size": (stage / name).stat().st_size if suffix == "-ota.zip" else 1,
                               "sha256": hash_override if suffix == "-ota.zip" and hash_override else digest})
            data = {"schema": 1, "version": version, "versionCode": 1002003,
                    "target": {"androidApi": 19, "models": "neev_kr", "os": "NEEV.KOR.*.260902"},
                    "assets": sorted(assets, key=lambda x: x["name"])}
            manifest.write_text(json.dumps(data, sort_keys=True, separators=(",", ":")) + "\n")
            with signature.open("wb") as output:
                subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(private), str(manifest)], check=True, stdout=output)

        def java(installed="1.2.2", dest=None):
            args = ["java", "-cp", str(classes), "TestUpdate", str(public), str(stage), installed]
            if dest: args.append(str(dest))
            return subprocess.run(args, capture_output=True, text=True)

        sign()
        result = java()
        assert result.returncode == 0, result.stderr
        assert result.stdout.strip() == version
        out = temp / "extracted"
        result = java(dest=out)
        assert result.returncode == 0, result.stderr
        assert (out / "VERSION").read_text().strip() == version
        old_dir, new_dir = temp / "old", temp / "new"
        old_dir.mkdir(); new_dir.mkdir()
        current, next_link = temp / "current", temp / "next"
        current.symlink_to(old_dir, target_is_directory=True)
        next_link.symlink_to(new_dir, target_is_directory=True)
        result = subprocess.run(["java", "-cp", str(classes), "com.seogaemo.app.core.update.UpdateVerifier", "switch", str(next_link), str(current)], capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        assert current.resolve() == new_dir
        assert java(installed=version).returncode != 0
        bad = bytearray(signature.read_bytes()); bad[0] ^= 1; signature.write_bytes(bad)
        assert java().returncode != 0
        sign("0" * 64)
        assert java().returncode != 0
        sign()
        wrong = stage / "hmg-seogaemo-1.2.4-manifest.json"
        manifest.rename(wrong)
        signature.rename(stage / (wrong.name + ".sig"))
        assert java().returncode != 0
    print("update verifier tests passed")


if __name__ == "__main__":
    run()
