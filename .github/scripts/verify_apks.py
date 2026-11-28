"""Fail on incorrect package/version/signature, save public metadata only."""
import datetime
import glob
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

tools = Path(os.environ['ANDROID_SDK_ROOT']) / 'build-tools/34.0.0'
metadata = {'commit': os.environ['GITHUB_SHA'], 'run': os.environ['GITHUB_RUN_ID'],
            'buildNumber': os.environ['GITHUB_RUN_NUMBER'], 'builtAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'apks': []}
files = sorted(glob.glob('app/build/outputs/apk/**/*.apk', recursive=True))
if len(files) != 2:
    raise SystemExit('Expected exactly debug and release APKs')
for name in files:
    apk = Path(name)
    if apk.stat().st_size == 0:
        raise SystemExit('Empty APK')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None or 'AndroidManifest.xml' not in archive.namelist():
            raise SystemExit('Invalid APK archive')
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', name], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    expected = 'com.notely.app.debug' if apk.name == 'app-debug.apk' else 'com.notely.app'
    if not match or match.groups() != (expected, '2', '1.0.1'):
        raise SystemExit('APK identity/version mismatch')
    verify = subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', name], capture_output=True, text=True)
    signed = apk.name == 'app-debug.apk' or bool(os.environ.get('NOTELY_KEYSTORE_PATH'))
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', verify.stdout)
    if signed and (verify.returncode != 0 or cert is None):
        raise SystemExit('APK signature validation failed')
    if not signed and verify.returncode == 0:
        raise SystemExit('Unexpected signature on unsigned candidate')
    if signed and expected == 'com.notely.app':
        public_der = subprocess.check_output(['keytool', '-exportcert', '-keystore', os.environ['NOTELY_KEYSTORE_PATH'],
            '-storepass:env', 'NOTELY_KEYSTORE_PASSWORD', '-alias', os.environ['NOTELY_KEY_ALIAS']], stderr=subprocess.DEVNULL)
        if hashlib.sha256(public_der).hexdigest() != cert.group(1):
            raise SystemExit('Signed APK does not match configured keystore alias')
    record = {'file': apk.name, 'package': expected, 'versionCode': 2, 'versionName': '1.0.1', 'bytes': apk.stat().st_size,
              'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'signing': 'debug' if apk.name == 'app-debug.apk' else ('configured-release' if signed else 'UNSIGNED'),
              'certificateSha256': cert.group(1) if signed else None}
    metadata['apks'].append(record)
    print(f"::notice title=Verified APK::{json.dumps(record)}")
Path('apk-metadata.json').write_text(json.dumps(metadata, indent=2))
