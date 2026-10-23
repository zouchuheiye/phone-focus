"""Audit exactly the staged files before GitHub publication; never print secret contents."""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import json
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
raw=subprocess.check_output(['git','ls-files','--cached','-z'],cwd=ROOT)
paths=[p.decode('utf-8') for p in raw.split(b'\0') if p]
deny_parts={'.local','.gradle','__pycache__','node_modules'}
forbidden=[];secret_findings=[]
patterns={
    'github_token':rb'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b',
    'private_key_block':rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    'aws_access_key':rb'\bAKIA[A-Z0-9]{16}\b',
    'openai_key':rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{35,}\b',
}
sizes=[]
for name in paths:
    p=Path(name);parts=set(p.parts)
    if parts&deny_parts or p.name in {'local.properties','signing.json','.env'} or p.suffix.lower() in {'.jks','.keystore','.pyc'} or 'build' in parts or ('qa' in parts and 'classes' in parts):
        forbidden.append(name)
    data=subprocess.check_output(['git','show',':'+name],cwd=ROOT)
    sizes.append(len(data))
    if b'\0' not in data and len(data)<2_000_000:
        for label,pattern in patterns.items():
            if re.search(pattern,data):secret_findings.append({'path':name,'type':label})
assert not forbidden,{'forbidden_paths':forbidden}
assert not secret_findings,{'secret_findings':secret_findings}
assert max(sizes,default=0)<95*1024*1024
result={'evidence_id':'E-20261023-GITHUB-PUBLISH-AUDIT','verified_at':datetime.now(timezone(timedelta(hours=8))).isoformat(),
    'scope':'Exactly current Git index; rerun after staging any new content. Patterns are a bounded credential audit, not a guarantee of absence of all secret types.',
    'staged_file_count':len(paths),'staged_total_bytes':sum(sizes),'largest_file_bytes':max(sizes,default=0),
    'forbidden_paths':forbidden,'credential_pattern_findings':secret_findings,
    'keystore_and_local_state_excluded':True,'publish_visibility':'public, authorized by upload request and promotional display-page scope'}
(ROOT/'qa/publish-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:result[k] for k in ['staged_file_count','staged_total_bytes','forbidden_paths','credential_pattern_findings']},indent=2))
