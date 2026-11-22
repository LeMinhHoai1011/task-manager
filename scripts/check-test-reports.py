"""Fail CI on missing results or a skipped test outside the explicit allowlist."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

# Existing Ollama-only tests and empty placeholders. LoginFlowIT, StorageIT
# and ConcurrencyIT exercise the corresponding real CI paths.
BACKEND_SKIP = {
    "vn.smarttask.ai.AiLatencyBenchmarkTest.reportP50P95WithinBudget",
    "vn.smarttask.ai.HttpOllamaClientLiveTest.generateReturnsNonBlank",
    "vn.smarttask.files.StreamBytesIT.streamReturnsCommittedBytesOnce",
    "vn.smarttask.files.StreamBytesIT.streamMissingObjectIs409",
    "vn.smarttask.files.StreamTicketConcurrencyIT.concurrentDoubleConsumeYieldsSingleWinner",
    "vn.smarttask.identity.AuthSessionFlowIT.loginThenCallApiWithToken",
}
BACKEND_REQUIRED = {
    "vn.smarttask.itg.StorageIT",
    "vn.smarttask.itg.LoginFlowIT",
    "vn.smarttask.itg.RbacIT",
    "vn.smarttask.db.FlywayMigrationValidationIT",
    "vn.smarttask.db.F21IntegrityContainerIT",
}

parser = argparse.ArgumentParser()
parser.add_argument("suite", choices=["web", "backend"])
parser.add_argument("reports", nargs="+", type=Path)
args = parser.parse_args()
passed = set()
errors = []
count = 0
for report in args.reports:
    for case in ET.parse(report).iter("testcase"):
        suite = case.attrib["classname"]
        name = f"{suite}.{case.attrib['name']}"
        if case.find("skipped") is not None:
            if args.suite != "backend" or name not in BACKEND_SKIP:
                errors.append(f"Unexpected skipped test: {name}")
        elif case.find("failure") is not None or case.find("error") is not None:
            errors.append(f"Failed test: {name}")
        else:
            passed.add(suite)
            count += 1
if not count:
    errors.append("No passing tests found")
if args.suite == "backend":
    errors.extend(f"Required suite did not run: {suite}" for suite in sorted(BACKEND_REQUIRED - passed))
if errors:
    raise SystemExit("\n".join(errors))
print(f"{args.suite}: {count} passing tests and no unexpected skips")
