from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
work=root/'build/combat-sim';work.mkdir(parents=True,exist_ok=True)
# Small world/player doubles exercise the actual deadline implementation without a graphics client.
files={
'net/minecraft/world/entity/player/Player.java':"""package net.minecraft.world.entity.player;
public class Player {
 public final java.util.UUID id; public final World world; public boolean alive=true;
 public Player(java.util.UUID id,boolean client){this.id=id;world=new World(client);}
 public java.util.UUID getUUID(){return id;} public World level(){return world;}
 public boolean isAlive(){return alive;} public boolean isSpectator(){return false;}
 public static class World{public long now;public final boolean client;World(boolean c){client=c;}public boolean isClientSide(){return client;}public long getGameTime(){return now;}}
}""",
'cn/bfm/BattlefieldMovement.java':"package cn.bfm;public class BattlefieldMovement{public static boolean enabled=true;public static boolean enabled(){return enabled;}}",
'cn/bfm/BfmConfig.java':"package cn.bfm;public class BfmConfig{public static Value PRONE_FIRE_DELAY=new Value(1),STAND_FIRE_DELAY=new Value(.7);public static class Value{public double value;Value(double v){value=v;}public double get(){return value;}}}",
'cn/bfm/client/ServerEyeSettings.java':"package cn.bfm.client;public class ServerEyeSettings{public static Settings tuning(){return new Settings();}public static class Settings{public double proneFireDelay(){return cn.bfm.BfmConfig.PRONE_FIRE_DELAY.get();}public double standFireDelay(){return cn.bfm.BfmConfig.STAND_FIRE_DELAY.get();}}}",
'cn/bfm/FireLockSim.java':"""package cn.bfm;
import net.minecraft.world.entity.player.Player;
public class FireLockSim{
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("fire lock check "+checks);}
 public static void main(String[] args){
  var id=java.util.UUID.randomUUID();var server=new Player(id,false);var client=new Player(id,true);
  StanceFireLock.begin(server,true);check(!StanceFireLock.blocked(client));
  for(int i=0;i<20;i++){server.world.now=i;check(StanceFireLock.blocked(server));}
  server.world.now=20;check(!StanceFireLock.blocked(server));
  StanceFireLock.begin(server,false);
  for(int i=20;i<34;i++){server.world.now=i;check(StanceFireLock.blocked(server));}
  server.world.now=34;check(!StanceFireLock.blocked(server));
  StanceFireLock.begin(client,true);check(!StanceFireLock.blocked(server));check(StanceFireLock.blocked(client));
  StanceFireLock.clearClient();check(!StanceFireLock.blocked(client));
  server.world.now=100;StanceFireLock.begin(server,true);server.world.now=101;StanceFireLock.begin(server,false);
  server.world.now=119;check(StanceFireLock.blocked(server));server.world.now=120;check(!StanceFireLock.blocked(server));
  StanceFireLock.begin(server,true);BattlefieldMovement.enabled=false;check(!StanceFireLock.blocked(server));BattlefieldMovement.enabled=true;
  StanceFireLock.clear(server);check(!StanceFireLock.blocked(server));
  BfmConfig.PRONE_FIRE_DELAY.value=0;StanceFireLock.begin(server,true);check(!StanceFireLock.blocked(server));
  BfmConfig.STAND_FIRE_DELAY.value=0;StanceFireLock.begin(server,false);check(!StanceFireLock.blocked(server));
  System.out.println("FireLockSim: "+checks+" server/client, deadline, reversal and disable checks passed");
 }
}"""}
sources=[]
for name,body in files.items():
 p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');sources.append(str(p))
sources.extend(str(root/'src/main/java/cn/bfm'/name) for name in ('StanceFireLock.java','StanceTransition.java'))
java=Path('C:/Program Files/Java/jdk-21/bin')
subprocess.run([str(java/'javac.exe'),'--release','21','-encoding','UTF-8','-d',str(work),*sources],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(work),'cn.bfm.FireLockSim'],check=True)
