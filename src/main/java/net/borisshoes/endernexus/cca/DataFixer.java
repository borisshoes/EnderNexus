package net.borisshoes.endernexus.cca;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.endernexus.Destination;
import net.borisshoes.endernexus.EnderNexus;
import net.borisshoes.endernexus.storage.HomesStorage;
import net.borisshoes.endernexus.storage.WarpsStorage;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.borisshoes.endernexus.cca.PlayerComponentInitializer.HOMES;
import static net.borisshoes.endernexus.cca.WorldDataComponentInitializer.WARPS;

public class DataFixer {
   public static void serverStarted(MinecraftServer server){
      List<Destination> oldWarps = WARPS.get(server.getOverworld()).getDestinations();
      if(BorisLib.SERVER == null) BorisLib.SERVER = server; // TODO should be able to remove in a later borislib version
      WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
      Set<Destination> warps = storage.getWarps();
      int converted = 0;
      
      for(Destination oldWarp : oldWarps){
         String newName = oldWarp.getName();
         int affix = 1;
         do{
            String finalNewName = newName;
            if(warps.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalNewName.toLowerCase(Locale.ROOT)))){
               newName = oldWarp.getName() + "_" + affix;
               affix++;
            }else{
               storage.addWarp(new Destination(newName,oldWarp.getPos(),oldWarp.getRotation(), oldWarp.getWorldKey()));
               converted++;
               break;
            }
         }while(true);
      }
      if(converted > 0){
         oldWarps.clear();
         EnderNexus.LOGGER.info("Ender Nexus has converted {} old Warps into BorisLib data format. This operation should not repeat itself. You are now OK to upgrade to future versions without Warp data loss.", converted);
      }
   }
   
   public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender packetSender, MinecraftServer server){
      ServerPlayerEntity player = handler.getPlayer();
      List<Destination> oldHomes = HOMES.get(player).getDestinations();
      HomesStorage storage = DataAccess.getPlayer(player.getUuid(),HomesStorage.KEY);
      Set<Destination> homes = storage.getHomes();
      int converted = 0;
      
      for(Destination oldHome : oldHomes){
         String newName = oldHome.getName();
         int affix = 1;
         do{
            String finalNewName = newName;
            if(homes.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalNewName.toLowerCase(Locale.ROOT)))){
               newName = oldHome.getName() + "_" + affix;
               affix++;
            }else{
               storage.addHome(new Destination(newName,oldHome.getPos(),oldHome.getRotation(), oldHome.getWorldKey()));
               converted++;
               break;
            }
         }while(true);
      }
      if(converted > 0){
         oldHomes.clear();
         EnderNexus.LOGGER.info("Ender Nexus has converted {} old Homes for player {} into BorisLib data format. This operation should not repeat itself for this player.", converted, player.getNameForScoreboard());
      }
   }
}
