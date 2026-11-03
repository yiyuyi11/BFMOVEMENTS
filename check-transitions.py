from pathlib import Path
import subprocess,zipfile,json
root=Path(__file__).resolve().parent
java=Path('C:/Program Files/Java/jdk-21/bin');dest=root/'build/transition-sim';dest.mkdir(exist_ok=True)
subprocess.run([str(java/'javac.exe'),'--release','21','-encoding','UTF-8','-d',str(dest),str(root/'src/main/java/cn/bfm/StanceTransition.java'),str(root/'sim/StanceTransitionSim.java')],check=True)
result=subprocess.run([str(java/'java.exe'),'-cp',str(dest),'cn.bfm.StanceTransitionSim'],capture_output=True,text=True,check=True)
print(result.stdout.strip())
jar=root/'build/libs/battlefield-movement-1.5.7-neoforge-1.21.1.jar'
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None
 assert 'version = "1.5.7"' in z.read('META-INF/neoforge.mods.toml').decode('utf-8')
 for p in (root/'build/classes-local-1.5.7').rglob('*.class'):assert z.read(p.relative_to(root/'build/classes-local-1.5.7').as_posix())==p.read_bytes()
 for p in (root/'src/main/resources').rglob('*'):
  if p.is_file():assert z.read(p.relative_to(root/'src/main/resources').as_posix())==p.read_bytes()
payload=subprocess.run([str(java/'javap.exe'),'-p','-cp',str(jar),'cn.bfm.network.ProneSyncPayload'],capture_output=True,text=True,check=True).stdout
assert 'float fromEye' in payload and 'yaw' not in payload
registrar=subprocess.run([str(java/'javap.exe'),'-c','-p','-cp',str(jar),'cn.bfm.BfmNetwork'],capture_output=True,text=True,check=True).stdout
assert '// String 3' in registrar
report={'transition_sim':result.stdout.strip(),'protocol':3,'packaged_classes_and_resources':'all match current build','runtime_game_test':False}
(root/'build/transition-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('Protocol 3 and complete JAR verification passed')
