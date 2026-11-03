from pathlib import Path
import subprocess, zipfile, hashlib, re

root = Path(__file__).resolve().parent
version = re.search(r"version = '([^']+)'", (root/'build.gradle').read_text(encoding='utf-8')).group(1)
cp = (root.parent/'BattlefieldOps/build/compile-classpath.txt').read_text(encoding='utf-8').strip()
patched = root.parent/'.gradle-home/caches/neoformruntime/intermediate_results/compiledWithNeoForge_486fb49562d4bdeb2cbe6cc5306042b692e61ac1_output.jar'
cp = str(patched) + ';' + cp
mods = Path('D:/USTBL/.minecraft/versions/Mechanomania-test-new/mods')
for pattern in ('player-animation*.jar', 'tacz-neoforge-*.jar'):
    cp += ';' + str(next(mods.glob(pattern)))
classes = root/f'build/classes-local-{version}'
classes.mkdir(parents=True, exist_ok=True)
args = ['--release','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',str(classes)]
args += [str(p) for p in sorted((root/'src/main/java').rglob('*.java'))]
argfile = root/f'build/javac-local-{version}.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
subprocess.run(['C:/Program Files/Java/jdk-21/bin/javac.exe','@'+str(argfile)],check=True)
dist = root/'build/libs';dist.mkdir(exist_ok=True)
jar = dist/f'battlefield-movement-{version}-neoforge-1.21.1.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
    for folder in (classes,root/'src/main/resources'):
        for p in sorted(folder.rglob('*')):
            if p.is_file(): archive.write(p,p.relative_to(folder).as_posix())
print('Compiled:',jar)
print('SHA256:',hashlib.sha256(jar.read_bytes()).hexdigest())
with zipfile.ZipFile(dist/f'battlefield-movement-{version}-fix.zip','w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(jar,jar.name)
    archive.write(root/f'FIX-{version}.md','修复说明.md')
    if (root/'JVM崩溃临时规避.txt').exists(): archive.write(root/'JVM崩溃临时规避.txt','JVM崩溃临时规避.txt')
