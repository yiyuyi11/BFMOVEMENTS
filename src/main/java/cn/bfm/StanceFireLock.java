package cn.bfm;

import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Client prediction and authoritative server deadlines are isolated in integrated singleplayer. */
public final class StanceFireLock {
    private static final Map<UUID,Long> SERVER=new ConcurrentHashMap<>(), CLIENT=new ConcurrentHashMap<>();
    private static Map<UUID,Long> states(Player player){return player.level().isClientSide()?CLIENT:SERVER;}
    public static void begin(Player player,boolean down){
        double seconds=player.level().isClientSide()
                ?(down?cn.bfm.client.ServerEyeSettings.tuning().proneFireDelay():cn.bfm.client.ServerEyeSettings.tuning().standFireDelay())
                :(down?BfmConfig.PRONE_FIRE_DELAY.get():BfmConfig.STAND_FIRE_DELAY.get());
        int ticks=StanceTransition.fireDelayTicks(seconds);
        if(ticks>0)states(player).merge(player.getUUID(),player.level().getGameTime()+ticks,Math::max);
    }
    public static boolean blocked(Player player){
        if(player==null||!BattlefieldMovement.enabled()||!player.isAlive()||player.isSpectator())return false;
        Map<UUID,Long> map=states(player);Long until=map.get(player.getUUID());
        if(until==null)return false;
        if(player.level().getGameTime()<until)return true;
        map.remove(player.getUUID(),until);return false;
    }
    public static void clear(Player player){states(player).remove(player.getUUID());}
    public static void clearClient(){CLIENT.clear();}
    private StanceFireLock(){}
}
