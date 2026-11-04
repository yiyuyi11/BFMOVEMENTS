package cn.bfm;
import cn.bfm.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;

public final class BfmNetwork {
 private static final String VERSION="forge1201-3";
 private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("battlefield_movement","main"),()->VERSION,VERSION::equals,VERSION::equals);
 public static void register(){
  CHANNEL.messageBuilder(ProneRequestPayload.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(ProneRequestPayload::encode).decoder(ProneRequestPayload::decode).consumerMainThread((p,ctx)->{
   var player=ctx.get().getSender();if(player!=null)ProneManager.request(player,p.down(),p.instant());ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(SlidePayload.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(SlidePayload::encode).decoder(SlidePayload::decode).consumerMainThread((p,ctx)->{
   var player=ctx.get().getSender();if(player!=null){if(p.active())sendEyeSettings(player);SlideServer.accept(player,p);}ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(DivePayload.class,2,NetworkDirection.PLAY_TO_SERVER).encoder(DivePayload::encode).decoder(DivePayload::decode).consumerMainThread((p,ctx)->{
   var player=ctx.get().getSender();if(player!=null){if(p.active())sendEyeSettings(player);DiveServer.accept(player,p);}ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(ProneSyncPayload.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder(ProneSyncPayload::encode).decoder(ProneSyncPayload::decode).consumerMainThread((p,ctx)->{
   if(FMLEnvironment.dist==Dist.CLIENT)cn.bfm.client.ClientNetwork.onProneSync(p);ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(SlideSyncPayload.class,4,NetworkDirection.PLAY_TO_CLIENT).encoder(SlideSyncPayload::encode).decoder(SlideSyncPayload::decode).consumerMainThread((p,ctx)->{
   if(FMLEnvironment.dist==Dist.CLIENT)cn.bfm.client.ClientNetwork.onSlideSync(p);ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(DiveSyncPayload.class,5,NetworkDirection.PLAY_TO_CLIENT).encoder(DiveSyncPayload::encode).decoder(DiveSyncPayload::decode).consumerMainThread((p,ctx)->{
   if(FMLEnvironment.dist==Dist.CLIENT)cn.bfm.client.ClientNetwork.onDiveSync(p);ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(cn.bfm.network.MasterStatePayload.class,7,NetworkDirection.PLAY_TO_CLIENT).encoder(cn.bfm.network.MasterStatePayload::encode).decoder(cn.bfm.network.MasterStatePayload::decode).consumerMainThread((p,ctx)->{
   if(FMLEnvironment.dist==Dist.CLIENT)BattlefieldMovement.acceptRemote(p.enabled());ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(cn.bfm.network.MasterTogglePayload.class,8,NetworkDirection.PLAY_TO_SERVER).encoder(cn.bfm.network.MasterTogglePayload::encode).decoder(cn.bfm.network.MasterTogglePayload::decode).consumerMainThread((p,ctx)->{
   var player=ctx.get().getSender();if(player!=null){
    if(!player.hasPermissions(2)){MasterSwitch.sendTo(player);}
    else{if(BattlefieldMovement.enabled()!=p.enabled())BattlefieldMovement.toggleConfig();MasterSwitch.broadcast();}
   }ctx.get().setPacketHandled(true);
  }).add();
  CHANNEL.messageBuilder(MovementSettingsPayload.class,6,NetworkDirection.PLAY_TO_CLIENT).encoder(MovementSettingsPayload::encode).decoder(MovementSettingsPayload::decode).consumerMainThread((p,ctx)->{
   if(FMLEnvironment.dist==Dist.CLIENT)cn.bfm.client.ServerEyeSettings.accept(p.slideEyeHeight(),p.diveEyeHeight());ctx.get().setPacketHandled(true);
  }).add();
 }
 public static void sendToServer(Object packet){CHANNEL.sendToServer(packet);}
 public static void sendToPlayer(ServerPlayer player,Object packet){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
 public static void sendToPlayersTrackingEntity(Entity entity,Object packet){CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(()->entity),packet);}
 public static void sendToPlayersTrackingEntityAndSelf(Entity entity,Object packet){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),packet);}
 public static void sendEyeSettings(ServerPlayer player){sendToPlayer(player,new MovementSettingsPayload(ActionEyeHeight.slideHeight(),ActionEyeHeight.diveHeight()));}
 private BfmNetwork(){}
}
