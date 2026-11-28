"""Expose exact instrumentation counts and failures through API-readable annotations."""
import glob
import xml.etree.ElementTree as ET
files = glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True)
tests = failures = skipped = 0
for path in files:
    root = ET.parse(path).getroot()
    if root.tag != 'testsuite':
        continue
    tests += int(root.get('tests', 0))
    failures += int(root.get('failures', 0)) + int(root.get('errors', 0))
    skipped += int(root.get('skipped', 0))
    for case in root.iter('testcase'):
        for node in list(case.findall('failure')) + list(case.findall('error')):
            message = (node.get('message') or node.text or 'failure')[:1800].replace('\n', '%0A').replace('\r', '')
            print(f"::error title={case.get('classname')}.{case.get('name')}::{message}")
print(f'::notice title=Android test totals::{tests} run, {failures} failed, {skipped} skipped')
