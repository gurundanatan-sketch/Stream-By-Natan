#!/usr/bin/env python3
"""
Membuat kunci tanda tangan APK (cukup SEKALI) lalu mencetak isi untuk GitHub Secrets.
Jalankan di Termux:  pkg install openjdk-17 python && python scripts/make_keystore.py

Simpan hasilnya di GitHub > Settings > Secrets and variables > Actions:
  SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
JANGAN unggah file .jks ke repo, dan jangan hilangkan file ini:
semua pembaruan aplikasi harus ditandatangani kunci yang sama.
"""
import base64
import getpass
import os
import secrets
import subprocess
import sys

path = os.path.expanduser("~/streambynatan.jks")
alias = "streambynatan"
if os.path.exists(path):
    print("Kunci sudah ada:", path)
else:
    pw = getpass.getpass("Buat kata sandi kunci (kosongkan untuk dibuat otomatis): ") or secrets.token_urlsafe(18)
    cmd = ["keytool", "-genkeypair", "-v", "-keystore", path, "-alias", alias, "-keyalg", "RSA",
           "-keysize", "2048", "-validity", "10000", "-storepass", pw, "-keypass", pw,
           "-dname", "CN=Stream By Natan, OU=Natan, O=Natan, C=ID"]
    if subprocess.call(cmd) != 0:
        sys.exit("keytool gagal. Pasang dulu: pkg install openjdk-17")
    print("\nSIGNING_STORE_PASSWORD dan SIGNING_KEY_PASSWORD =", pw)
print("SIGNING_KEY_ALIAS =", alias)
with open(path, "rb") as f:
    b64 = base64.b64encode(f.read()).decode()
out = path + ".base64.txt"
with open(out, "w") as f:
    f.write(b64)
print("Isi SIGNING_KEYSTORE_BASE64 disimpan di:", out)
