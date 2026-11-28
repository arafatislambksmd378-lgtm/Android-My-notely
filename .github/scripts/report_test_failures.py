"""Turn JUnit XML results into GitHub annotations (raw logs aren't always reachable)."""
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
                text = (node.text or node.get("message") or "").strip()
                frames = [
                    line.strip()
                    for line in text.splitlines()
                    if "com.notely" in line or line.strip().startswith("Caused")
                ]
                head = (node.get("message") or text.splitlines()[0] if text else kind)[:250]
                name = "{}.{}".format(case.get("classname", "?"), case.get("name", "?"))
                print("::error title={}::{}".format(name, head))
                if frames:
                    print("::error title={} @frames::{}".format(name, " | ".join(frames[:6])[:900]))
if total:
    print("tests: {} run, {} failed".format(total, failed))
