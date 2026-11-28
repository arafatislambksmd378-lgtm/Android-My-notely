"""Optional four-secret production signing. No credential value appears in output."""
import base64
import os
from pathlib import Path

names = ['NOTELY_KEYSTORE_BASE64', 'NOTELY_KEYSTORE_PASSWORD', 'NOTELY_KEY_ALIAS', 'NOTELY_KEY_PASSWORD']
configured = [bool(os.environ.get(name)) for name in names]
if any(configured) and not all(configured):
    raise SystemExit('Partial signing configuration: configure all four NOTELY signing secrets or remove all four.')
if all(configured):
    raw = base64.b64decode(os.environ[names[0]], validate=True)
    if not 1 <= len(raw) <= 8 * 1024 * 1024:
        raise SystemExit('Invalid keystore size')
    path = Path(os.environ['RUNNER_TEMP']) / 'notely-release.jks'
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, 'wb') as output:
        output.write(raw)
    with open(os.environ['GITHUB_ENV'], 'a') as output:
        output.write(f'NOTELY_KEYSTORE_PATH={path}\n')
    print('::notice title=Signing::Production signing credentials configured; signature will be verified against the configured alias.')
else:
    print('::notice title=Signing::No signing credentials configured. Release will remain UNSIGNED; debug APK remains installable.')
