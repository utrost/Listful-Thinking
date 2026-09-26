#!/usr/bin/env python3
"""Check resolved Maven runtime components against OSV. Only public coordinates leave the host."""
import json
import pathlib
import sys
import urllib.request

bom = json.loads(pathlib.Path(sys.argv[1]).read_text())
components = [component for component in bom.get("components", []) if component.get("purl", "").startswith("pkg:maven/")]
if not components:
    raise SystemExit("No resolved Maven components found; refusing an empty security scan.")
queries = [{"package": {"ecosystem": "Maven", "name": component["group"] + ":" + component["name"]}, "version": component["version"]} for component in components]
request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps({"queries": queries}).encode(), headers={"Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=60) as response:
    results = json.load(response)["results"]
if len(results) != len(queries):
    raise SystemExit("Incomplete advisory response; security scan failed.")
findings = []
for query, result in zip(queries, results):
    if result.get("vulns"):
        findings.append({"package": query["package"]["name"], "version": query["version"], "advisories": [v["id"] for v in result["vulns"]]})
print(json.dumps({"resolved_components_checked": len(queries), "components_with_advisories": len(findings), "findings": findings}, indent=2))
raise SystemExit(1 if findings else 0)
