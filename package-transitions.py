from pathlib import Path
import hashlib,json,shutil,zipfile
root=Path(__file__).resolve().parent
out=root.parent/'deliverables/bf1-movement-transitions';out.mkdir(exist_ok=True)
jar=root/'build/libs/battlefield-movement-1.5.7-neoforge-1.21.1.jar'
target=out/jar.name;shutil.copy2(jar,target)
notes=out/'安装与动作衔接说明.md';shutil.copy2(root/'FIX-1.5.7.md',notes)
report=json.loads((root/'build/transition-validation.json').read_text(encoding='utf-8'))
report['jar']={'name':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
report['source_backup']=str(root.parent/'backups/battlefield-movement-before-1.5.7')
before=root.parent/'backups/battlefield-movement-before-1.5.7/src/main'
report['changed_sources']=[p.relative_to(root/'src/main').as_posix() for p in (root/'src/main').rglob('*') if p.is_file() and (not (before/p.relative_to(root/'src/main')).exists() or p.read_bytes()!=(before/p.relative_to(root/'src/main')).read_bytes())]
(out/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
with zipfile.ZipFile(out/'battlefield-movement-1.5.7-transitions.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in [target,notes,out/'validation.json']:z.write(p,p.name)
with zipfile.ZipFile(out/'battlefield-movement-1.5.7-transitions.zip') as z:assert z.testzip() is None
print(json.dumps(report,ensure_ascii=False,indent=2))
