package net.borisshoes.endernexus;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.config.ConfigManager;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.utils.TextUtils;
import net.borisshoes.endernexus.cca.DataFixer;
import net.borisshoes.endernexus.storage.HomesStorage;
import net.borisshoes.endernexus.storage.WarpsStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
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
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.getPlayer;

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
         dispatcher.register(EnderNexus.CONFIG.generateCommand("endernexus",""));
         
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
   
   
         dispatcher.register(literal("setwarp").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
               .then(argument("name",word())
                     .executes(ctx -> setWarp(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("delwarp").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
               .then(argument("name",word()).suggests(this::getWarpSuggestions)
                     .executes(ctx -> delWarp(ctx, StringArgumentType.getString(ctx,"name")))));
   
         dispatcher.register(literal("warp")
               .then(argument("name",word()).suggests(this::getWarpSuggestions)
                     .executes(ctx -> warpTp(ctx, StringArgumentType.getString(ctx,"name")))));
   
   
         dispatcher.register(literal("tpa")
               .then(argument("target", EntityArgument.player()).suggests(this::getTpaInitSuggestions)
                     .executes(ctx -> tpaInit(ctx, getPlayer(ctx, "target"), false))));
         
         dispatcher.register(literal("tpahere")
               .then(argument("target", EntityArgument.player()).suggests(this::getTpaInitSuggestions)
                     .executes(ctx -> tpaInit(ctx, getPlayer(ctx, "target"), true))));
   
         dispatcher.register(literal("tpaaccept")
               .then(argument("target", EntityArgument.player()).suggests(this::getTpaTargetSuggestions)
                     .executes(ctx -> tpaAccept(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaAccept(ctx, null)));
   
         dispatcher.register(literal("tpadeny")
               .then(argument("target", EntityArgument.player()).suggests(this::getTpaTargetSuggestions)
                     .executes(ctx -> tpaDeny(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaDeny(ctx, null)));
   
         dispatcher.register(literal("tpacancel")
               .then(argument("target", EntityArgument.player()).suggests(this::getTpaSenderSuggestions)
                     .executes(ctx -> tpaCancel(ctx, getPlayer(ctx, "target"))))
               .executes(ctx -> tpaCancel(ctx, null)));
         
         dispatcher.register(literal("endernexus")
               .then(literal("cleanse").requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(this::cleanse)));
      });
   }
   
   public static List<RequestTimer> getRequests(){
      return SERVER_TIMER_CALLBACKS.stream().filter(timer -> timer instanceof RequestTimer).map(timer -> (RequestTimer)timer).toList();
   }
   
   public static List<TeleportTimer> getTeleports(){
      return SERVER_TIMER_CALLBACKS.stream().filter(timer -> timer instanceof TeleportTimer).map(timer -> (TeleportTimer)timer).toList();
   }
   
   private int cleanse(CommandContext<CommandSourceStack> ctx){
      SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer || timer instanceof RequestTimer);
      RECENT_TELEPORTS.clear();
   
      CustomBossEvents bbm = ctx.getSource().getServer().getCustomBossEvents();
      bbm.getEvents().stream().filter(b -> b.getTextId().toString().contains("standstill-")).toList().forEach(b -> {
         b.removeAllPlayers();
         bbm.remove(b);
      });
      ctx.getSource().sendSuccess(() -> Component.translatable("text.endernexus.cleansed"),true);
      return 1;
   }
   
   @Nullable
   private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   private CompletableFuture<Suggestions> getHomeSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      if(ctx.getSource().isPlayer()){
         ServerPlayer player = ctx.getSource().getPlayer();
         List<String> homeOptions = DataAccess.getPlayer(player.getUUID(), HomesStorage.KEY).getHomes().stream().map(Destination::getName).toList();
         return filterSuggestionsByInput(builder, homeOptions);
      }
      return filterSuggestionsByInput(builder, new ArrayList<>());
   }
   
   private CompletableFuture<Suggestions> getWarpSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      List<String> warpOptions = DataAccess.getGlobal(WarpsStorage.KEY).getWarps().stream().map(Destination::getName).toList();
      return filterSuggestionsByInput(builder, warpOptions);
   }
   
   private CompletableFuture<Suggestions> getTpaInitSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      CommandSourceStack scs = context.getSource();
      List<RequestTimer> activeRequests = getRequests();
      
      List<String> activeTargets = Stream.concat(
            activeRequests.stream().map(requestTimer -> requestTimer.tTo().getName().getString()),
            activeRequests.stream().map(requestTimer -> requestTimer.tFrom().getName().getString())
      ).toList();
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getTextName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTpaTargetSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> activeTargets = getRequests().stream().map(tpaRequest -> tpaRequest.tFrom().getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTpaSenderSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> activeTargets = getRequests().stream().map(TPARequest -> TPARequest.tTo().getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private int interruptTp(CommandContext<CommandSourceStack> ctx){
      if(!ctx.getSource().isPlayer()){
         ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      
      final ServerPlayer player = ctx.getSource().getPlayer();
      
      boolean active = getTeleports().stream().anyMatch(tp ->  tp.player.getUUID().equals(player.getUUID()));
      if(!active){
         player.displayClientMessage(Component.translatable("text.endernexus.not_channeling").withStyle(ChatFormatting.RED),false);
         return 0;
      }else{
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer tp && tp.player.getUUID().equals(player.getUUID()));
         
         CustomBossEvents bbm = ctx.getSource().getServer().getCustomBossEvents();
         bbm.getEvents().stream().filter(b -> b.getTextId().toString().contains("standstill-") && b.getTextId().toString().contains(player.getStringUUID())).toList().forEach(b -> {
            b.removeAllPlayers();
            bbm.remove(b);
         });
      }
      return 1;
   }
   
   
   public int tpaInit(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo, boolean tpahere){
      if(!ctx.getSource().isPlayer()){
         ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      final ServerPlayer tFrom = ctx.getSource().getPlayer();
      
      if(tpahere && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tFrom.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpahere")).withStyle(ChatFormatting.RED),false);
         return -1;
      }else if(!tpahere && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tFrom.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpa")).withStyle(ChatFormatting.RED),false);
         return -1;
      }
      
      if (tFrom.equals(tTo)) {
         tFrom.displayClientMessage(Component.translatable("text.endernexus.cannot_tpa_self").withStyle(ChatFormatting.RED), false);
         return -1;
      }
   
      if(checkCooldown(TPType.TPA,tFrom)) return -1;
      if(activeChannels(tFrom)) return -1;
      
      RequestTimer tpa = new RequestTimer(tFrom, tTo, tpahere);
      if (getRequests().stream().anyMatch(req -> req.equals(tpa))) {
         tFrom.displayClientMessage(Component.translatable("text.endernexus.already_requested").withStyle(ChatFormatting.RED), false);
         return 1;
      }
      BorisLib.addTickTimerCallback(tpa);
      
      
      
      MutableComponent senderText = tpahere ?
            Component.translatable("text.endernexus.requested_tpahere", Component.literal(tTo.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.LIGHT_PURPLE):
            Component.translatable("text.endernexus.requested_tpa", Component.literal(tTo.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.LIGHT_PURPLE);
      
      
      tFrom.displayClientMessage(senderText.append(Component.translatable("text.endernexus.requested_tpa_2",
                  Component.literal("/tpacancel [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpacancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpacancel " + tTo.getName().getString())))
                              .withColor(ChatFormatting.GREEN)),
                  TextUtils.readableDouble(EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT),2)
            ).withStyle(ChatFormatting.LIGHT_PURPLE)),false);

      tTo.displayClientMessage(
            Component.translatable(tpahere ? "text.endernexus.requested_tpahere_to" : "text.endernexus.requested_tpa_to",
                  tFrom.getName().getString().formatted(ChatFormatting.AQUA),
                  Component.literal("/tpaaccept [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpaaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpaaccept " + tFrom.getName().getString())))
                              .withColor(ChatFormatting.GREEN)),
                  Component.literal("/tpadeny [<player>]").withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpadeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpadeny " + tFrom.getName().getString())))
                              .withColor(ChatFormatting.GREEN)),
                  TextUtils.readableDouble(EnderNexus.CONFIG.getDouble(EnderNexusRegistry.TPA_TIMEOUT),2)
            ).withStyle(ChatFormatting.LIGHT_PURPLE),false);
      return 1;
   }
   
   public int tpaAccept(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom) {
      if(!ctx.getSource().isPlayer()){
         ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      final ServerPlayer tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tTo().equals(tTo)).toList();
         if (candidates.size() > 1) {
            MutableComponent text = Component.translatable("text.endernexus.multiple_tpas_accept").withStyle(ChatFormatting.LIGHT_PURPLE);
            candidates.stream().map(req -> req.tFrom().getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpaaccept " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpaaccept " + name)))
                              .withColor(ChatFormatting.GREEN)).append(" ")));
            tTo.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tTo.displayClientMessage(Component.translatable("text.endernexus.no_tpas").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tFrom = candidates.getFirst().tFrom();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.ACCEPT);
      if (tr == null) return 1;
      
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpa")).withStyle(ChatFormatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpahere")).withStyle(ChatFormatting.RED),false);
         return -1;
      }
      
      ServerPlayer finalTFrom = tFrom;
      if(tr.isTPAhere()){
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.TPA,tTo,new TeleportTransition(finalTFrom.level(), finalTFrom.position(), Vec3.ZERO, tTo.getYRot(),tTo.getXRot(), TeleportTransition.DO_NOTHING)));
      }else{
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.TPA,tFrom,new TeleportTransition(tTo.level(), tTo.position(), Vec3.ZERO, finalTFrom.getYRot(),finalTFrom.getXRot(), TeleportTransition.DO_NOTHING)));
      }
      
      tr.cancelTimeout();
      tr.tTo().displayClientMessage(Component.translatable("text.endernexus.you_accepted_tpa").withStyle(ChatFormatting.GREEN), false);
      tr.tFrom().displayClientMessage(Component.translatable("text.endernexus.they_accepted_tpa",(Component.literal(tr.tTo().getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GREEN)), false);
      return 1;
   }
   
   
   public int tpaDeny(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom){
      if(!ctx.getSource().isPlayer()){
         ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      final ServerPlayer tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tTo().equals(tTo)).toList();
         if (candidates.size() > 1) {
            MutableComponent text = Component.translatable("text.endernexus.multiple_tpas_deny").withStyle(ChatFormatting.GREEN);
            candidates.stream().map(TPARequest -> TPARequest.tFrom().getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpadeny " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpadeny " + name)))
                              .withColor(ChatFormatting.GREEN))).append(" "));
            tTo.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tTo.displayClientMessage(Component.translatable("text.endernexus.no_tpas").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tFrom = candidates.getFirst().tFrom();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.DENY);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpa")).withStyle(ChatFormatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpahere")).withStyle(ChatFormatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      tr.tTo().displayClientMessage(Component.translatable("text.endernexus.you_cancel_tpa").withStyle(ChatFormatting.RED), false);
      tr.tFrom().displayClientMessage(Component.translatable("text.endernexus.they_cancel_tpa",
            Component.literal(tr.tTo().getName().getString()).withStyle(ChatFormatting.AQUA)
      ).withStyle(ChatFormatting.RED), false);
      return 1;
   }
   
   public int tpaCancel(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo) throws CommandSyntaxException {
      if(!ctx.getSource().isPlayer()){
         ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
         return -1;
      }
      final ServerPlayer tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         List<RequestTimer> candidates = getRequests().stream().filter(req -> req.tFrom().equals(tFrom)).toList();
         if (candidates.size() > 1) {
            MutableComponent text = Component.translatable("text.endernexus.multiple_tpas_cancel").withStyle(ChatFormatting.GREEN);
            candidates.stream().map(TPARequest -> TPARequest.tTo().getName().getString()).forEach(name ->
                  text.append(Component.literal(name).withStyle(s ->
                        s.withClickEvent(new ClickEvent.RunCommand("/tpacancel " + name))
                              .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tpacancel " + name)))
                              .withColor(ChatFormatting.GREEN))).append(" "));
            tFrom.displayClientMessage(text, false);
            return 1;
         }
         if (candidates.isEmpty()) {
            tFrom.displayClientMessage(Component.translatable("text.endernexus.no_tpas").withStyle(ChatFormatting.RED), false);
            return 1;
         }
         tTo = candidates.getFirst().tTo();
      }
      
      RequestTimer tr = getTPARequest(tFrom, tTo, TPAAction.CANCEL);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAS_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpa")).withStyle(ChatFormatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.TPAHERE_ENABLED)){
         tTo.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.tpahere")).withStyle(ChatFormatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      tr.tFrom().displayClientMessage(Component.translatable("text.endernexus.you_cancel_tpa").withStyle(ChatFormatting.RED), false);
      tr.tTo().displayClientMessage(Component.translatable("text.endernexus.they_cancel_tpa",
            Component.literal(tr.tFrom().getName().getString()).withStyle(ChatFormatting.AQUA)
      ).withStyle(ChatFormatting.RED), false);
      return 1;
   }
   
   private int spawnTp(CommandContext<CommandSourceStack> ctx){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.SPAWN_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.spawn")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.SPAWN,player)) return -1;
         if(activeChannels(player)) return -1;
         
         ServerLevel world = player.level().getServer().findRespawnDimension();
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.SPAWN,player,new TeleportTransition(world, world.getRespawnData().pos().getBottomCenter(), Vec3.ZERO, world.getRespawnData().yaw(),world.getRespawnData().pitch(), TeleportTransition.DO_NOTHING)));
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int randomTp(CommandContext<CommandSourceStack> ctx){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.RTP_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.rtp")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.RTP,player)) return -1;
         if(activeChannels(player)) return -1;
         ServerLevel world = player.level();
         
         int maxRange = EnderNexus.CONFIG.getInt(EnderNexusRegistry.RTP_MAX_RANGE);
         int minRange = EnderNexus.CONFIG.getInt(EnderNexusRegistry.RTP_MIN_RANGE);
         int tries = 0;
         
         while(tries < 100){
            double angle = 2 * Math.PI * Math.random();
            double r = (maxRange-minRange)*Math.sqrt(Math.random()) + minRange;
            int x = (int) (r * Math.cos(angle)) + world.getRespawnData().pos().getX();
            int z = (int) (r * Math.sin(angle)) + world.getRespawnData().pos().getZ();
            
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
            Vec3 pos = locations.get(0).getCenter();
            
            BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.RTP,player,new TeleportTransition(world, pos, Vec3.ZERO, player.getYRot(),player.getXRot(), TeleportTransition.DO_NOTHING)));
            return 1;
         }
         
         player.displayClientMessage(Component.translatable("text.endernexus.no_rtp_spot").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC),false);
         return 0;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setHome(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.home_set_needs_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.home")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUUID(), HomesStorage.KEY);
         Set<Destination> homes = storage.getHomes();
         if(homes.size() >= EnderNexus.CONFIG.getInt(EnderNexusRegistry.HOMES_MAX)){
            player.displayClientMessage(Component.translatable("text.endernexus.max_homes").withStyle(ChatFormatting.RED),false);
            return -1;
         }else if(!homes.isEmpty() && name == null){
            player.displayClientMessage(Component.translatable("text.endernexus.specify_home_name").withStyle(ChatFormatting.RED),false);
            return -1;
         }
         if(name == null) name = "main";
   
         String finalName = name;
         if(homes.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalName.toLowerCase(Locale.ROOT)))){
            player.displayClientMessage(Component.translatable("text.endernexus.already_home",name).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         
         boolean success = storage.addHome(new Destination(name,player.position(),player.getRotationVector(),player.level().dimension().identifier().toString()));
         if(success){
            player.displayClientMessage(Component.translatable("text.endernexus.succeed_home_add",name).withStyle(ChatFormatting.GREEN),false);
            return 1;
         }else{
            player.displayClientMessage(Component.translatable("text.endernexus.fail_home_add").withStyle(ChatFormatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delHome(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.home_delete_needs_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.home")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUUID(), HomesStorage.KEY);
         Set<Destination> homes = storage.getHomes();
         if(homes.size() > 1 && name == null){
            player.displayClientMessage(Component.translatable("text.endernexus.specify_home_delete").withStyle(ChatFormatting.RED),false);
            return -1;
         }else if(homes.size() == 1 && name == null){
            name = homes.iterator().next().getName();
         }
         
         String finalName = name;
         Optional<Destination> foundHome = homes.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(finalName.toLowerCase(Locale.ROOT))).findFirst();
         if(foundHome.isEmpty()){
            player.displayClientMessage(Component.translatable("text.endernexus.no_home",name).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         
         boolean success = storage.removeHome(foundHome.get());
         if(success){
            player.displayClientMessage(Component.translatable("text.endernexus.succeed_home_remove").withStyle(ChatFormatting.GREEN),false);
            return 1;
         }else{
            player.displayClientMessage(Component.translatable("text.endernexus.fail_home_remove").withStyle(ChatFormatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int homeTp(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.HOMES_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.home")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         HomesStorage storage = DataAccess.getPlayer(player.getUUID(), HomesStorage.KEY);
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
            player.displayClientMessage(Component.translatable("text.endernexus.no_home",name).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.HOME,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination home = foundHome.get();
         ServerLevel world = home.getWorld(ctx.getSource().getServer());
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.HOME,player,new TeleportTransition(world, home.getPos(), Vec3.ZERO, home.getRotation().y,home.getRotation().x, TeleportTransition.DO_NOTHING)));
         
         player.displayClientMessage(Component.translatable("text.endernexus.teleporing_home",name).withStyle(ChatFormatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setWarp(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.warp_set_needs_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.warp")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         if(warps.stream().anyMatch(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))){
            player.displayClientMessage(Component.translatable("text.endernexus.already_warp",name).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         
         boolean success = storage.addWarp(new Destination(name,player.position(),player.getRotationVector(),player.level().dimension().identifier().toString()));
         if(success) {
            player.displayClientMessage(Component.translatable("text.endernexus.succeed_warp_add",name).withStyle(ChatFormatting.GREEN),false);
            return 1;
         }else{
            player.displayClientMessage(Component.translatable("text.endernexus.fail_warp_add").withStyle(ChatFormatting.RED),false);
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delWarp(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            ctx.getSource().sendSystemMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.warp")).withStyle(ChatFormatting.RED));
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))).findFirst();
         if(foundWarp.isEmpty()){
            ctx.getSource().sendSystemMessage(Component.translatable("text.endernexus.no_warp",name).withStyle(ChatFormatting.RED));
            return -1;
         }
         
         boolean success = storage.removeWarp(foundWarp.get());
         if(success){
            ctx.getSource().sendSystemMessage(Component.translatable("text.endernexus.succeed_warp_remove",name).withStyle(ChatFormatting.GREEN));
            return 1;
         }else{
            ctx.getSource().sendSystemMessage(Component.translatable("text.endernexus.fail_warp_remove").withStyle(ChatFormatting.RED));
            return -1;
         }
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int warpTp(CommandContext<CommandSourceStack> ctx, String name){
      try{
         if(!ctx.getSource().isPlayer()){
            ctx.getSource().sendFailure(Component.translatable("text.endernexus.teleports_need_player").withStyle(ChatFormatting.RED));
            return -1;
         }
         ServerPlayer player = ctx.getSource().getPlayer();
         if(!EnderNexus.CONFIG.getBoolean(EnderNexusRegistry.WARPS_ENABLED)){
            player.displayClientMessage(Component.translatable("text.endernexus.is_disabled", Component.translatable("text.endernexus.warp")).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         WarpsStorage storage = DataAccess.getGlobal(WarpsStorage.KEY);
         Set<Destination> warps = storage.getWarps();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))).findFirst();
         if(foundWarp.isEmpty()){
            player.displayClientMessage(Component.translatable("text.endernexus.no_warp",name).withStyle(ChatFormatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.WARP,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination warp = foundWarp.get();
         ServerLevel world = warp.getWorld(ctx.getSource().getServer());
         BorisLib.addTickTimerCallback(TeleportTimer.startTeleport(TPType.WARP,player,new TeleportTransition(world, warp.getPos(), Vec3.ZERO, warp.getRotation().y,warp.getRotation().x, TeleportTransition.DO_NOTHING)));
         player.displayClientMessage(Component.translatable("text.endernexus.now_warping",name).withStyle(ChatFormatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   public static void playerDied(ServerPlayer player){
      try{
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof TeleportTimer tp && tp.player.getUUID().equals(player.getUUID()));
         SERVER_TIMER_CALLBACKS.removeIf(timer -> timer instanceof RequestTimer req && (req.tFrom().getUUID().equals(player.getUUID()) || req.tTo().getUUID().equals(player.getUUID())));
   
         CustomBossEvents bbm = player.level().getServer().getCustomBossEvents();
         bbm.getEvents().stream().filter(b -> b.getTextId().toString().contains("standstill-"+player.getUUID())).toList().forEach(b -> {
            b.removeAllPlayers();
            bbm.remove(b);
         });
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   private boolean activeChannels(ServerPlayer player){
      boolean active = getTeleports().stream().anyMatch(tp ->  tp.player.getUUID().equals(player.getUUID()));
      if(active){
         player.displayClientMessage(Component.translatable("text.endernexus.already_teleporting").withStyle(ChatFormatting.RED),false);
      }
      return active;
   }
   
   private boolean checkCooldown(TPType type, ServerPlayer player){
      for(Teleport tp : RECENT_TELEPORTS){
         if(!tp.player.getStringUUID().equals(player.getStringUUID())) continue;
         if(tp.type != type) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(type) * 1000L){
            int remaining = (int) (((readConfigCooldown(type) * 1000L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            player.displayClientMessage(Component.translatable("text.endernexus.cannot_teleport_for",type.label,remaining).withStyle(ChatFormatting.RED),false);
            return true;
         }
      }
      return false;
   }
   
   private boolean checkTPAHereCooldown(ServerPlayer tFrom, ServerPlayer tTo){
      for(Teleport tp : RECENT_TELEPORTS){
         if(!tp.player.getStringUUID().equals(tFrom.getStringUUID())) continue;
         if(tp.type != TPType.TPA) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(TPType.TPA) * 1000L){
            int remaining = (int) (((readConfigCooldown(TPType.TPA) * 1000L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            tTo.displayClientMessage(Component.translatable("text.endernexus.cannot_tpa_for",TPType.TPA.label,remaining).withStyle(ChatFormatting.RED),false);
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
   
   public record Teleport(ServerPlayer player, TPType type, long timestamp) {
   }
   
   private RequestTimer getTPARequest(ServerPlayer tFrom, ServerPlayer tTo, TPAAction action) {
      Optional<RequestTimer> otr = getRequests().stream().filter(tpaRequest -> tpaRequest.tFrom().equals(tFrom) && tpaRequest.tTo().equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TPAAction.CANCEL) {
            
            tFrom.displayClientMessage(Component.translatable("text.endernexus.no_ongoing_request").withStyle(ChatFormatting.RED), false);
         } else {
            tTo.displayClientMessage(Component.translatable("text.endernexus.no_ongoing_request").withStyle(ChatFormatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   private static int getY(BlockGetter blockView, int maxY, Vec3 pos) {
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.x, maxY + 1, pos.z);
      boolean bl = blockView.getBlockState(mutable).isAir();
      mutable.move(Direction.DOWN);
      boolean bl2 = blockView.getBlockState(mutable).isAir();
      while (mutable.getY() > blockView.getMinY()) {
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
   
   public static boolean isSafe(BlockGetter world, int maxY, Vec3 pos) {
      BlockPos blockPos = BlockPos.containing(pos.x, getY(world, maxY, pos) - 1, pos.z);
      BlockState blockState = world.getBlockState(blockPos);
      FluidState fluidState = world.getFluidState(blockPos);
      boolean invalid = blockState.is(Blocks.WITHER_ROSE) || blockState.is(Blocks.SWEET_BERRY_BUSH) || blockState.is(Blocks.CACTUS) || blockState.is(Blocks.POWDER_SNOW) || blockState.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) || WalkNodeEvaluator.isBurningBlock(blockState);
      return blockPos.getY() < maxY && fluidState.isEmpty() && !invalid;
   }
   
   public static ArrayList<BlockPos> makeSpawnLocations(int num, int range, int maxY, ServerLevel world, BlockPos center){
      ArrayList<BlockPos> positions = new ArrayList<>();
      for(int i = 0; i < num; i++){
         int tries = 0;
         int x,z;
         do{
            x = center.getX() + (int) (Math.random() * range * 2 - range);
            z = center.getZ() + (int) (Math.random() * range * 2 - range);
            tries++;
         }while(!isSafe(world,maxY, new Vec3(x,0,z)) && tries < 10000);
         positions.add(BlockPos.containing(x,getY(world,maxY,new Vec3(x,0,z)),z));
      }
      return positions;
   }
}
