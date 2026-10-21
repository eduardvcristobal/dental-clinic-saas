#!/usr/bin/env python3
"""M10.C6 evidence report; absence of verifiable artifacts always blocks GO."""
import json, os, pathlib, datetime
out=pathlib.Path(os.getenv("M10_EVIDENCE_OUTPUT","m10-certification-evidence.json"))
keys=["CI_GREEN","DEPENDENCIES","ENCRYPTED_RESTORE_DRILL","MULTI_REPLICA","K6_LOAD","MONITORING_DRILL","SECURITY_SIGNOFF","PRIVACY_SIGNOFF","CLINICAL_SIGNOFF","OPERATIONS_SIGNOFF","FINANCE_SIGNOFF"]
checks={k:{"status":"PASS" if os.environ.get("M10_EVIDENCE_"+k)=="VERIFIED" else "BLOCKED",
           "evidence":os.environ.get("M10_EVIDENCE_REF_"+k,"")} for k in keys}
for k,v in checks.items():
    if not v["evidence"]: v["status"]="BLOCKED"
# An operator can assert evidence references, but this script does not independently authenticate evidence.
doc={"generated_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
     "decision":"NO-GO","verification":"Self-attested inputs; independent human review mandatory",
     "checks":checks,"live_notifications_enabled":False}
out.write_text(json.dumps(doc,indent=2)+"\n",encoding="utf-8")
print(f"Generated {out}; release decision NO-GO until independent approval.")
