package net.borisshoes.endernexus;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.PlayerConnectionCallback;
import net.borisshoes.borislib.config.ConfigManager;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.utils.TextUtils;
import net.borisshoes.endernexus.cca.DataFixer;
import net.borisshoes.endernexus.storage.HomesStorage;
import net.borisshoes.endernexus.storage.WarpsStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.boss.BossBarManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.TeleportTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.borisshoes.borislib.BorisLib.SERVER_TIMER_CALLBACKS;
import static net.borisshoes.endernexus.EnderNexusRegistry.CONFIG_SETTINGS;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EnderNexus implements ModInitializer {
   
   public static final Logger LOGGER = LogManager.getLogger("Ender Nexus");
   private static final String CONFIG_NAME = "EnderNexus.properties";
   public static final String MOD_ID = "endernexus";
   public static boolean hasCarpet;
   
   public static ConfigManager CONFIG;
   
   public static final ArrayList<Teleport> RECENT_TELEPORTS = new ArrayList<>();
   
   @Override
   public void onInitialize(){
      LOGGER.info("Ender Nexus is Warping In!");
      
      hasCarpet = FabricLoader.getInstance().isModLoaded("carpet");
      
      CONFIG = new ConfigManager(MOD_ID,"Ancestral Archetypes",CONFIG_NAME,CONFIG_SETTINGS);
      
      ServerLifecycleEvents.SERVER_STARTED.register(DataFixer::serverStarted);
      ServerPlayConnectionEvents.JOIN.register(DataFixer::onPlayerJoin);
   
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
         dispatcher.register(EnderNexus.CONFIG.generateCommand("endernexus","config"));
         
         dispatcher.register(literal("spawntp")
               .executes(this::spawnTp));
         
         if(!hasCarpet){
            dispatcher.register(literal("spawn")
                  .executes(this::spawnTp));
         }
         
         dispatcher.register(literal("tpinterrupt")
               .executes(this::interruptTp));
         
         dispatcher.register(literal("rtp")
               .executes(this::randomTp));
         
         dispatcher.register(literal("randomtp")
               .executes(this::randomTp));
   
         dispatcher.register(literal("sethome")
               .executes(ctx -> setHome(ctx,null))
               .then(argument("name",word())
                     .executes(ctx -> setHome(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("delhome")
               .executes(ctx -> delHome(ctx,null))
               .then(argument("name",word()).suggests(this::getHomeSuggestions)
                     .executes(ctx -> delHome(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("home")
               .executes(ctx -> homeTp(ctx,null))
               .then(argument("name",word()).suggests(this::getHomeSuggestions)
                     .executes(ctx -> homeTp(ctx, StringArgumentType.getString(ctx,"name")))));
   
   
         dispatcher.register(literal("setwarp").requires(source -> source.hasPermissionLevel(2))
               .then(argument("name",word())
                     .executes(ctx -> setWarp(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("delwarp").requires(source -> source.hasPermissionLevel(2))
               .then(argument("name",word()).suggests(this::getWarpSuggestions)
                     .executes(ctx -> delWarp(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("warp")
               .then(argument("name",word()).suggests(this::getWarpSuggestions)
                     .executes(ctx -> warpTp(ctx, StringArgumentType.getString(ctx,"name")))));
   
   
         dispatcher.register(literal("tpa")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTpaInitSuggestions)
                     .executes(ctx -> tpaInit(ctx, getPlayer(ctx, "target"), false))));
         
         dispatcher.register(literal("tpahere")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTpaInitSuggestions)
                     .executes(ctx -> tpaInit(ctx, getPlayer(ctx, "target"), true))));
   
         dispatcher.register(literal("tpaaccept")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTpaTargetSuggestions)
                     .executes(ctx -> tpaAccept(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaAccept(ctx, null)));
   
         dispatcher.register(literal("tpadeny")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTpaTargetSuggestions)
                     .executes(ctx -> tpaDeny(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaDeny(ctx, null)));
   
         dispatcher.register(literal("tpacancel")
               .then(argument("target", EntityArgumentType.player()).suggests(this::getTpaSenderSuggestions)
                     .executes(ctx -> tpaCancel(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaCancel(ctx, null)));
         
         dispatcher.register(literal("endernexus")
               .then(literal("cleanse").requires(source -> source.hasPermissionLevel(2)).executes(this::cleanse)));
      });
   }
   
   public static List<RequestTimer> getRequests(){
      return SERVER_TIMER_CALLBACKS.stream().filter(timer -> timer instanceof RequestTimer).map(timer -> (RequestTimer)timer).toList();
   }
   
   public static List<TeleportTimer> getTeleports(){
      return SERVER_TIMER_CALLBACKS.stream().filter(timer -> timer instanceof TeleportTimer).map(timer -> (TeleportTimer)timer).toList();
   }
   
   private int cleanse(CommandContext<ServerCommandSource> ctx){
      SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer || timer instanceof RequestTimer);
      RECENT_TELEPORTS.clear();
   
      BossBarManager bbm = ctx.getSource().getServer().getBossBarManager();
      bbm.getAll().stream().filter(b -> b.getId().toString().contains("standstill-")).toList().forEach(b -> {
         b.clearPlayers();
         bbm.remove(b);
      });
      ctx.getSource().sendFeedback(() -> Text.translatable("text.endernexus.cleansed"),true);
      return 1;
   }
   
   @Nullable
   private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   private CompletableFuture<Suggestions> getHomeSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
      if(ctx.getSource().isExecutedByPlayer()){
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         List<String> homeOptions = DataAccess.getPlayer(player.getUuid(), HomesStorage.KEY).getHomes().stream().map(Destination::getName).toList();
         return filterSuggestionsByInput(builder, homeOptions);
      }
      return filterSuggestionsByInput(builder, new ArrayList<>());
   }
   
   private CompletableFuture<Suggestions> getWarpSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
      List<String> warpOptions = DataAccess.getGlobal(WarpsStorage.KEY).getWarps().stream().map(Destination::getName).toList();
      return filterSuggestionsByInput(builder, warpOptions);
   }
   
   private CompletableFuture<Suggestions> getTpaInitSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      ServerCommandSource scs = context.getSource();
      List<RequestTimer> activeRequests = getRequests();
      
      List<String> activeTargets = Stream.concat(
            activeRequests.stream().map(requestTimer -> requestTimer.tTo().getName().getString()),
            activeRequests.stream().map(requestTimer -> requestTimer.tFrom().getName().getString())
      ).toList();
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTpaTargetSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = getRequests().stream().map(tpaRequest -> tpaRequest.tFrom().getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTpaSenderSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = getRequests().stream().map(TPARequest -> TPARequest.tTo().getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private int interruptTp(CommandContext<ServerCommandSource> ctx){
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
         return -1;
      }
      
      final ServerPlayerEntity player = ctx.getSource().getPlayer();
      
      boolean active = getTeleports().stream().anyMatch(tp ->  tp.player.getUuid().equals(player.getUuid()));
      if(!active){
         player.sendMessage(Text.translatable("text.endernexus.not_channeling").formatted(Formatting.RED),false);
         return 0;
      }else{
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer tp && tp.player.getUuid().equals(player.getUuid()));
         
         BossBarManager bbm = ctx.getSource().getServer().getBossBarManager();
         bbm.getAll().stream().filter(b -> b.getId().toString().contains("standstill-") && b.getId().toString().contains(player.getUuidAsString())).toList().forEach(b -> {
            b.clearPlayers();
            bbm.remove(b);
         });
      }
      return 1;
   }
   
   
   public int tpaInit(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo, boolean tpahere){
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if(tpahere && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tFrom.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpahere")).formatted(Formatting.RED),false);
         return -1;
      }else if(!tpahere && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tFrom.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpa")).formatted(Formatting.RED),false);
         return -1;
      }
      
      if (tFrom.equals(tTo)) {
         tFrom.sendMessage(Text.translatable("text.endernexus.cannot_tpa_self").formatted(Formatting.RED), false);
         return -1;
      }
   
      if(checkCooldown(TPType.TPA,tFrom)) return -1;
      if(activeChannels(tFrom)) return -1;
      
      RequestTimer tpa = new RequestTimer(tFrom, tTo, tpahere);
      if (getRequests().stream().anyMatch(req -> req.equals(tpa))) {
         tFrom.sendMessage(Text.translatable("text.endernexus.already_requested").formatted(Formatting.RED), false);
         return 1;
      }
      BorisLib.addTickTimerCallback(tpa);
      
      
      
      MutableText senderText = tpahere ?
            Text.translatable("text.endernexus.requested_tpahere",Text.literal(tTo.getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.LIGHT_PURPLE):
            Text.translatable("text.endernexus.requested_tpa",Text.literal(tTo.getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.LIGHT_PURPLE);
      
      
      tFrom.sendMessage(senderText.append(Text.translatable("text.endernexus.requested_tpa_2",
                  Text.literal("/tpacancel [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpacancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpacancel " + tTo.getName().getString())))
                              .withColor(Formatting.GREEN)),
                  TextUtils.readableDouble(EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT),2)
            ).formatted(Formatting.LIGHT_PURPLE)),false);

      tTo.sendMessage(
            Text.translatable(tpahere ? "text.endernexus.requested_tpahere_to" : "text.endernexus.requested_tpa_to",
                  tFrom.getName().getString().formatted(Formatting.AQUA),
                  Text.literal("/tpaaccept [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpaaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpaaccept " + tFrom.getName().getString())))
                              .withColor(Formatting.GREEN)),
                  Text.literal("/tpadeny [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpadeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpadeny " + tFrom.getName().getString())))
                              .withColor(Formatting.GREEN)),
                  TextUtils.readableDouble(EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT),2)
            ).formatted(Formatting.LIGHT_PURPLE),false);
      return 1;
   }
   
   public int tpaAccept(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) {
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tTo().equals(tTo)).toList();
         if (candidates.size() > 1) {
            MutableText text = Text.translatable("text.endernexus.multiple_tpas_accept").formatted(Formatting.LIGHT_PURPLE);
            candidates.stream().map(req -> req.tFrom().getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpaaccept " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpaaccept " + name)))
                              .withColor(Formatting.GREEN)).append(" ")));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tTo.sendMessage(Text.translatable("text.endernexus.no_tpas").formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates.getFirst().tFrom();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.ACCEPT);
      if (tr == null) return 1;
      
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpa")).formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpahere")).formatted(Formatting.RED),false);
         return -1;
      }
      
      ServerPlayerEntity finalTFrom = tFrom;
      if(tr.isTPAhere()){
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.TPA,tTo,new TeleportTarget(finalTFrom.getEntityWorld(), finalTFrom.getEntityPos(), Vec3d.ZERO, tTo.getYaw(),tTo.getPitch(), TeleportTarget.NO_OP)));
      }else{
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.TPA,tFrom,new TeleportTarget(tTo.getEntityWorld(), tTo.getEntityPos(), Vec3d.ZERO, finalTFrom.getYaw(),finalTFrom.getPitch(), TeleportTarget.NO_OP)));
      }
      
      tr.cancelTimeout();
      tr.tTo().sendMessage(Text.translatable("text.endernexus.you_accepted_tpa").formatted(Formatting.GREEN), false);
      tr.tFrom().sendMessage(Text.translatable("text.endernexus.they_accepted_tpa",(Text.literal(tr.tTo().getName().getString()).formatted(Formatting.AQUA)).formatted(Formatting.GREEN)), false);
      return 1;
   }
   
   
   public int tpaDeny(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom){
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tTo().equals(tTo)).toList();
         if (candidates.size() > 1) {
            MutableText text = Text.translatable("text.endernexus.multiple_tpas_deny").formatted(Formatting.GREEN);
            candidates.stream().map(TPARequest -> TPARequest.tFrom().getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpadeny " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpadeny " + name)))
                              .withColor(Formatting.GREEN))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tTo.sendMessage(Text.translatable("text.endernexus.no_tpas").formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates.getFirst().tFrom();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.DENY);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpa")).formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpahere")).formatted(Formatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      tr.tTo().sendMessage(Text.translatable("text.endernexus.you_cancel_tpa").formatted(Formatting.RED), false);
      tr.tFrom().sendMessage(Text.translatable("text.endernexus.they_cancel_tpa",
            Text.literal(tr.tTo().getName().getString()).formatted(Formatting.AQUA)
      ).formatted(Formatting.RED), false);
      return 1;
   }
   
   public int tpaCancel(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tFrom().equals(tFrom)).toList();
         if (candidates.size() > 1) {
            MutableText text = Text.translatable("text.endernexus.multiple_tpas_cancel").formatted(Formatting.GREEN);
            candidates.stream().map(TPARequest -> TPARequest.tTo().getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpacancel " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Text.literal("/tpacancel " + name)))
                              .withColor(Formatting.GREEN))).append(" "));
            tFrom.sendMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tFrom.sendMessage(Text.translatable("text.endernexus.no_tpas").formatted(Formatting.RED), false);
            return 1;
         }
         tTo = candidates.getFirst().tTo();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.CANCEL);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpa")).formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.tpahere")).formatted(Formatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      tr.tFrom().sendMessage(Text.translatable("text.endernexus.you_cancel_tpa").formatted(Formatting.RED), false);
      tr.tTo().sendMessage(Text.translatable("text.endernexus.they_cancel_tpa",
            Text.literal(tr.tFrom().getName().getString()).formatted(Formatting.AQUA)
      ).formatted(Formatting.RED), false);
      return 1;
   }
   
   private int spawnTp(CommandContext<ServerCommandSource> ctx){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.SPAWN_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.spawn")).formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.SPAWN,player)) return -1;
         if(activeChannels(player)) return -1;
         
         ServerWorld world = player.getEntityWorld().getServer().getSpawnWorld();
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.SPAWN,player,new TeleportTarget(world, world.getSpawnPoint().getPos().toBottomCenterPos(), Vec3d.ZERO, world.getSpawnPoint().yaw(),world.getSpawnPoint().pitch(), TeleportTarget.NO_OP)));
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int randomTp(CommandContext<ServerCommandSource> ctx){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.RTP_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.rtp")).formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.RTP,player)) return -1;
         if(activeChannels(player)) return -1;
         ServerWorld world = player.getEntityWorld();
         
         int maxRange = EnderNexus.CONFIG.getInt(EnderNexusRegistry.RTP_MAX_RANGE);
         int minRange = EnderNexus.CONFIG.getInt(EnderNexusRegistry.RTP_MIN_RANGE);
         int tries = 0;
         
         while(tries < 100){
            double angle = 2 * Math.PI * Math.random();
            double r = (maxRange-minRange)*Math.sqrt(Math.random()) + minRange;
            int x = (int) (r * Math.cos(angle)) + world.getSpawnPoint().getPos().getX();
            int z = (int) (r * Math.sin(angle)) + world.getSpawnPoint().getPos().getZ();
            
            int placeTries = 0; int spread = 4;
            ArrayList<BlockPos> locations;
            do{
               locations = makeSpawnLocations(1,spread, world.getLogicalHeight()-10, world, new BlockPos(x,world.getLogicalHeight(),z));
               placeTries++; spread++; // Expand search area
            }while(locations.isEmpty() && placeTries < 5);
            if(locations.isEmpty()){
               tries++;
               continue;
            }
            Vec3d pos = locations.get(0).toCenterPos();
            
            BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.RTP,player,new TeleportTarget(world, pos, Vec3d.ZERO, player.getYaw(),player.getPitch(), TeleportTarget.NO_OP)));
            return 1;
         }
         
         player.sendMessage(Text.translatable("text.endernexus.no_rtp_spot").formatted(Formatting.RED,Formatting.ITALIC),false);
         return 0;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setHome(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.home_set_needs_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.home")).formatted(Formatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUuid(), HomesStorage.KEY);
         Set<Destination> homes = storage.getHomes();
         if(homes.size() >= EnderNexus.CONFIG.getInt(EnderNexusRegistry.HOMES_MAX)){
            player.sendMessage(Text.translatable("text.endernexus.max_homes").formatted(Formatting.RED),false);
            return -1;
         }else if(!homes.isEmpty() && name == null){
            player.sendMessage(Text.translatable("text.endernexus.specify_home_name").formatted(Formatting.RED),false);
            return -1;
         }
         if(name == null) name = "main";
   
         String finalName = name;
         if(homes.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalName.toLowerCase(Locale.ROOT)))){
            player.sendMessage(Text.translatable("text.endernexus.already_home",name).formatted(Formatting.RED),false);
            return -1;
         }
         
         boolean success = storage.addHome(new Destination(name,player.getEntityPos(),player.getRotationClient(),player.getEntityWorld().getRegistryKey().getValue().toString()));
         if(success){
            player.sendMessage(Text.translatable("text.endernexus.succeed_home_add",name).formatted(Formatting.GREEN),false);
            return 1;
         }else{
            player.sendMessage(Text.translatable("text.endernexus.fail_home_add").formatted(Formatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delHome(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.home_delete_needs_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.home")).formatted(Formatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUuid(), HomesStorage.KEY);
         Set<Destination> homes = storage.getHomes();
         if(homes.size() > 1 && name == null){
            player.sendMessage(Text.translatable("text.endernexus.specify_home_delete").formatted(Formatting.RED),false);
            return -1;
         }else if(homes.size() == 1 && name == null){
            name = homes.iterator().next().getName();
         }
         
         String finalName = name;
         Optional<Destination> foundHome = homes.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalName.toLowerCase(Locale.ROOT))).findFirst();
         if(foundHome.isEmpty()){
            player.sendMessage(Text.translatable("text.endernexus.no_home",name).formatted(Formatting.RED),false);
            return -1;
         }
         
         boolean success = storage.removeHome(foundHome.get());
         if(success){
            player.sendMessage(Text.translatable("text.endernexus.succeed_home_remove").formatted(Formatting.GREEN),false);
            return 1;
         }else{
            player.sendMessage(Text.translatable("text.endernexus.fail_home_remove").formatted(Formatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int homeTp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.home")).formatted(Formatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUuid(), HomesStorage.KEY);
         Set<Destination> homes = storage.getHomes();
         if(name == null){
            if(homes.size() == 1){
               name = homes.iterator().next().getName();
            }else if(homes.size() > 1){
               name = "main";
            }
         }
   
         String finalName = name;
         Optional<Destination> foundHome = homes.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalName.toLowerCase(Locale.ROOT))).findFirst();
         if(foundHome.isEmpty()){
            player.sendMessage(Text.translatable("text.endernexus.no_home",name).formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.HOME,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination home = foundHome.get();
         ServerWorld world = home.getWorld(ctx.getSource().getServer());
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.HOME,player,new TeleportTarget(world, home.getPos(), Vec3d.ZERO, home.getRotation().y,home.getRotation().x, TeleportTarget.NO_OP)));
         
         player.sendMessage(Text.translatable("text.endernexus.teleporing_home",name).formatted(Formatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setWarp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.warp_set_needs_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.warp")).formatted(Formatting.RED),false);
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         if(warps.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))){
            player.sendMessage(Text.translatable("text.endernexus.already_warp",name).formatted(Formatting.RED),false);
            return -1;
         }
         
         boolean success = storage.addWarp(new Destination(name,player.getEntityPos(),player.getRotationClient(),player.getEntityWorld().getRegistryKey().getValue().toString()));
         if(success) {
            player.sendMessage(Text.translatable("text.endernexus.succeed_warp_add",name).formatted(Formatting.GREEN),false);
            return 1;
         }else{
            player.sendMessage(Text.translatable("text.endernexus.fail_warp_add").formatted(Formatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delWarp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            ctx.getSource().sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.warp")).formatted(Formatting.RED));
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))).findFirst();
         if(foundWarp.isEmpty()){
            ctx.getSource().sendMessage(Text.translatable("text.endernexus.no_warp",name).formatted(Formatting.RED));
            return -1;
         }
         
         boolean success = storage.removeWarp(foundWarp.get());
         if(success){
            ctx.getSource().sendMessage(Text.translatable("text.endernexus.succeed_warp_remove",name).formatted(Formatting.GREEN));
            return 1;
         }else{
            ctx.getSource().sendMessage(Text.translatable("text.endernexus.fail_warp_remove").formatted(Formatting.RED));
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int warpTp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.translatable("text.endernexus.teleports_need_player").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            player.sendMessage(Text.translatable("text.endernexus.is_disabled",Text.translatable("text.endernexus.warp")).formatted(Formatting.RED),false);
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))).findFirst();
         if(foundWarp.isEmpty()){
            player.sendMessage(Text.translatable("text.endernexus.no_warp",name).formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.WARP,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination warp = foundWarp.get();
         ServerWorld world = warp.getWorld(ctx.getSource().getServer());
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.WARP,player,new TeleportTarget(world, warp.getPos(), Vec3d.ZERO, warp.getRotation().y,warp.getRotation().x, TeleportTarget.NO_OP)));
         player.sendMessage(Text.translatable("text.endernexus.now_warping",name).formatted(Formatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   public static void playerDied(ServerPlayerEntity player){
      try{
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer tp && tp.player.getUuid().equals(player.getUuid()));
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof RequestTimer req && (req.tFrom().getUuid().equals(player.getUuid()) || req.tTo().getUuid().equals(player.getUuid())));
   
         BossBarManager bbm = player.getEntityWorld().getServer().getBossBarManager();
         bbm.getAll().stream().filter(b -> b.getId().toString().contains("standstill-"+player.getUuid())).toList().forEach(b -> {
            b.clearPlayers();
            bbm.remove(b);
         });
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   private boolean activeChannels(ServerPlayerEntity player){
      boolean active = getTeleports().stream().anyMatch(tp ->  tp.player.getUuid().equals(player.getUuid()));
      if(active){
         player.sendMessage(Text.translatable("text.endernexus.already_teleporting").formatted(Formatting.RED),false);
      }
      return active;
   }
   
   private boolean checkCooldown(TPType type, ServerPlayerEntity player){
      for(Teleport tp : RECENT_TELEPORTS){
         if(!tp.player.getUuidAsString().equals(player.getUuidAsString())) continue;
         if(tp.type != type) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(type) * 1000L){
            int remaining = (int) (((readConfigCooldown(type) * 1000L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            player.sendMessage(Text.translatable("text.endernexus.cannot_teleport_for",type.label,remaining).formatted(Formatting.RED),false);
            return true;
         }
      }
      return false;
   }
   
   private boolean checkTPAHereCooldown(ServerPlayerEntity tFrom, ServerPlayerEntity tTo){
      for(Teleport tp : RECENT_TELEPORTS){
         if(!tp.player.getUuidAsString().equals(tFrom.getUuidAsString())) continue;
         if(tp.type != TPType.TPA) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(TPType.TPA) * 1000L){
            int remaining = (int) (((readConfigCooldown(TPType.TPA) * 1000L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            tTo.sendMessage(Text.translatable("text.endernexus.cannot_tpa_for",TPType.TPA.label,remaining).formatted(Formatting.RED),false);
            return true;
         }
      }
      return false;
   }
   
   public static double readConfigCooldown(TPType type){
      return switch(type){
         case TPA -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_COOLDOWN);
         case HOME -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.HOMES_COOLDOWN);
         case WARP -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.WARPS_COOLDOWN);
         case SPAWN -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.SPAWN_COOLDOWN);
         case RTP -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.RTP_COOLDOWN);
      };
   }
   
   public static double readConfigWarmup(TPType type){
      return switch(type){
         case TPA -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_WARMUP);
         case HOME -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.HOMES_WARMUP);
         case WARP -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.WARPS_WARMUP);
         case SPAWN -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.SPAWN_WARMUP);
         case RTP -> EnderNexus.CONFIG.getDouble(EnderNexusRegistry.RTP_WARMUP);
      };
   }
   
   public enum TPType{
      HOME("home"), TPA("tpa"), WARP("warp"), SPAWN("spawn"), RTP("rtp");
   
      public final String label;
      
      TPType(String name){
         this.label = name;
      }
   }
   
   private enum TPAAction {
      ACCEPT, DENY, CANCEL
   }
   
   public record Teleport(ServerPlayerEntity player, TPType type, long timestamp) {
   }
   
   private RequestTimer getTPARequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, TPAAction action) {
      Optional<RequestTimer> otr = getRequests().stream().filter(tpaRequest -> tpaRequest.tFrom().equals(tFrom) && tpaRequest.tTo().equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TPAAction.CANCEL) {
            
            tFrom.sendMessage(Text.translatable("text.endernexus.no_ongoing_request").formatted(Formatting.RED), false);
         } else {
            tTo.sendMessage(Text.translatable("text.endernexus.no_ongoing_request").formatted(Formatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   private static int getY(BlockView blockView, int maxY, Vec3d pos) {
      BlockPos.Mutable mutable = new BlockPos.Mutable(pos.x, maxY + 1, pos.z);
      boolean bl = blockView.getBlockState(mutable).isAir();
      mutable.move(Direction.DOWN);
      boolean bl2 = blockView.getBlockState(mutable).isAir();
      while (mutable.getY() > blockView.getBottomY()) {
         mutable.move(Direction.DOWN);
         boolean bl3 = blockView.getBlockState(mutable).isAir();
         if (!bl3 && bl2 && bl) {
            return mutable.getY() + 1;
         }
         bl = bl2;
         bl2 = bl3;
      }
      return maxY + 1;
   }
   
   public static boolean isSafe(BlockView world, int maxY, Vec3d pos) {
      BlockPos blockPos = BlockPos.ofFloored(pos.x, getY(world, maxY, pos) - 1, pos.z);
      BlockState blockState = world.getBlockState(blockPos);
      FluidState fluidState = world.getFluidState(blockPos);
      boolean invalid = blockState.isOf(Blocks.WITHER_ROSE) || blockState.isOf(Blocks.SWEET_BERRY_BUSH) || blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.POWDER_SNOW) || blockState.isIn(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || LandPathNodeMaker.isFireDamaging(blockState);
      return blockPos.getY() < maxY && fluidState.isEmpty() && !invalid;
   }
   
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, int maxY, ServerWorld world, BlockPos center){
      ArrayList<BlockPos> positions = new ArrayList<>();
      for(int i = 0; i < num; i++){
         int tries = 0;
         int x,z;
         do{
            x = center.getX() + (int) (Math.random() * range * 2 - range);
            z = center.getZ() + (int) (Math.random() * range * 2 - range);
            tries++;
         }while(!isSafe(world,maxY, new Vec3d(x,0,z)) && tries < 10000);
         positions.add(BlockPos.ofFloored(x,getY(world,maxY,new Vec3d(x,0,z)),z));
      }
      return positions;
   }
}
