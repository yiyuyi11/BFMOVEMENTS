from pathlib import Path
import subprocess,zipfile,json,tomllib
root=Path(__file__).resolve().parent
java=Path('C:/Program Files/Java/jdk-21/bin');dest=root/'build/transition-sim';dest.mkdir(exist_ok=True)
subprocess.run([str(java/'javac.exe'),'--release','21','-encoding','UTF-8','-d',str(dest),str(root/'src/main/java/cn/bfm/StanceTransition.java'),str(root/'sim/StanceTransitionSim.java')],check=True)
result=subprocess.run([str(java/'java.exe'),'-cp',str(dest),'cn.bfm.StanceTransitionSim'],capture_output=True,text=True,check=True)
print(result.stdout.strip())
metadata='META-INF/neoforge.mods.toml'
version=tomllib.loads((root/'src/main/resources'/metadata).read_text(encoding='utf-8'))['mods'][0]['version']
jar=root/f'build/libs/battlefield-movement-{version}-neoforge-1.21.1.jar'
classes=root/f'build/classes-local-{version}'
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None
 assert tomllib.loads(z.read(metadata).decode('utf-8'))['mods'][0]['version']==version
 for p in classes.rglob('*.class'):assert z.read(p.relative_to(classes).as_posix())==p.read_bytes()
 for p in (root/'src/main/resources').rglob('*'):
  if p.is_file():assert z.read(p.relative_to(root/'src/main/resources').as_posix())==p.read_bytes()
payload=subprocess.run([str(java/'javap.exe'),'-p','-cp',str(jar),'cn.bfm.network.ProneSyncPayload'],capture_output=True,text=True,check=True).stdout
assert 'float fromEye' in payload and 'yaw' not in payload
registrar=subprocess.run([str(java/'javap.exe'),'-c','-p','-cp',str(jar),'cn.bfm.BfmNetwork'],capture_output=True,text=True,check=True).stdout
assert '// String 7' in registrar
report={'transition_sim':result.stdout.strip(),'protocol':7,'packaged_classes_and_resources':'all match current build','runtime_game_test':False}
(root/'build/transition-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Protocol 7 and complete JAR verification passed')
