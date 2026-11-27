"""Turn JUnit XML results into GitHub annotations.

Raw job logs are not downloadable from every environment, but annotations are
available through the REST API, so failing test names/messages surface here.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

results_dir = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"
failed = 0
total = 0
for path in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        total += 1
        for kind in ("failure", "error"):
            for node in case.findall(kind):
                failed += 1
                text = (node.get("message") or node.text or "").strip()
                first = text.splitlines()[0][:300] if text else kind
                name = "{}.{}".format(case.get("classname", "?"), case.get("name", "?"))
                print("::error title={}::{}".format(name, first))
if total:
    print("tests: {} run, {} failed".format(total, failed))
