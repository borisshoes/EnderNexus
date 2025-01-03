package net.borisshoes.endernexus;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.endernexus.utils.ConfigUtils;
import net.borisshoes.endernexus.utils.GenericTimer;
import net.borisshoes.endernexus.utils.TeleportTimer;
import net.borisshoes.endernexus.utils.TeleportUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.boss.BossBarManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.borisshoes.endernexus.cca.PlayerComponentInitializer.HOMES;
import static net.borisshoes.endernexus.cca.WorldDataComponentInitializer.WARPS;
import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EnderNexus implements ModInitializer {
   
   public static final Logger LOGGER = LogManager.getLogger("Ender Nexus");
   private static final String CONFIG_NAME = "EnderNexus.properties";
   public static final HashMap<UUID,GenericTimer> SERVER_TIMER_CALLBACKS = new HashMap<>();
   public static final HashMap<UUID,GenericTimer> SERVER_TIMER_CALLBACKS_QUEUE = new HashMap<>();
   public static boolean hasCarpet;
   
   public static ConfigUtils config;
   
   private static final HashMap<UUID,TPARequest> activeTpas = new HashMap<>();
   private static final ArrayList<Teleport> recentTeleports = new ArrayList<>();
   
   @Override
   public void onInitialize(){
      LOGGER.info("Ender Nexus is Warping In!");
   
      ServerTickEvents.END_SERVER_TICK.register(EnderNexus::onTick);
      hasCarpet = FabricLoader.getInstance().isModLoaded("carpet");
   
      config = new ConfigUtils(FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME).toFile(), LOGGER, Arrays.asList(new ConfigUtils.IConfigValue[] {
            new ConfigUtils.BooleanConfigValue("homes", true,
                  new ConfigUtils.Command("Homes allowed: %s", "Homes now allowed: %s")),
            new ConfigUtils.BooleanConfigValue("spawn", true,
                  new ConfigUtils.Command("Spawn allowed: %s", "Spawn now allowed: %s")),
            new ConfigUtils.BooleanConfigValue("tpas", true,
                  new ConfigUtils.Command("TPAs allowed: %s", "TPAs now allowed: %s")),
            new ConfigUtils.BooleanConfigValue("tpaheres", true,
                  new ConfigUtils.Command("TPA Here allowed: %s", "TPA Here now allowed: %s")),
            new ConfigUtils.BooleanConfigValue("warps", true,
                  new ConfigUtils.Command("Warps allowed: %s", "Warps now allowed: %s")),
            new ConfigUtils.BooleanConfigValue("randomtps", true,
                  new ConfigUtils.Command("Random TPs allowed: %s", "Random TPs now allowed: %s")),
      
            new ConfigUtils.BooleanConfigValue("bossbar", true,
                  new ConfigUtils.Command("Show Bossbar: %s", "Show Bossbar is now: %s")),
            new ConfigUtils.BooleanConfigValue("particles", true,
                  new ConfigUtils.Command("Show Particles: %s", "Show Particles is now: %s")),
            new ConfigUtils.BooleanConfigValue("sound", true,
                  new ConfigUtils.Command("Play Sound: %s", "Play Sound is now: %s")),
      
            new ConfigUtils.IntegerConfigValue("homes-warmup", 160, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Home Warmup time: %s ticks", "Home Warmup time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("homes-cooldown", 1800, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Home Cooldown time: %s ticks", "Home Cooldown time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("max-homes", 2, new ConfigUtils.IntegerConfigValue.IntLimits(1,100),
                  new ConfigUtils.Command("Max Homes: %s", "Max Homes set to: %s")),
      
            new ConfigUtils.IntegerConfigValue("tpa-warmup", 200, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("TPA Warmup time: %s ticks", "TPA Warmup time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("tpa-cooldown", 2400, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("TPA Cooldown time: %s ticks", "TPA Cooldown time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("tpa-timeout", 1200, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("TPA Request Timeout: %s ticks", "TPA Request Timeout set to: %s ticks")),
      
            new ConfigUtils.IntegerConfigValue("spawn-warmup", 120, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Spawn Warmup time: %s ticks", "Spawn Warmup time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("spawn-cooldown", 1200, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Spawn Cooldown time: %s ticks", "Spawn Cooldown time set to: %s ticks")),
      
            new ConfigUtils.IntegerConfigValue("warps-warmup", 120, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Warps Warmup time: %s ticks", "Warps Warmup time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("warps-cooldown", 1200, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Warps Cooldown time: %s ticks", "Warps Cooldown time set to: %s ticks")),
            
            new ConfigUtils.IntegerConfigValue("rtp-warmup", 200, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Random TP Warmup time: %s ticks", "Random TP Warmup time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("rtp-cooldown", 12000, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Random TP Cooldown time: %s ticks", "Random TP Cooldown time set to: %s ticks")),
            new ConfigUtils.IntegerConfigValue("rtp-range", 1000, new ConfigUtils.IntegerConfigValue.IntLimits(1,1000000),
                  new ConfigUtils.Command("Random TP Range: %s blocks", "Random TP Range set to: %s blocks")),
      }));
   
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
         dispatcher.register(config.generateCommand("endernexus"));
         
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
   
   private int cleanse(CommandContext<ServerCommandSource> ctx){
      SERVER_TIMER_CALLBACKS.clear();
      SERVER_TIMER_CALLBACKS_QUEUE.clear();
      activeTpas.clear();
      recentTeleports.clear();
   
      BossBarManager bbm = ctx.getSource().getServer().getBossBarManager();
      bbm.getAll().stream().filter(b -> b.getId().toString().contains("standstill-")).toList().forEach(b -> {
         b.clearPlayers();
         bbm.remove(b);
      });
      
      ctx.getSource().sendFeedback(() -> Text.literal("Cleansed Ender Nexus Teleports"),true);
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
         List<String> homeOptions = HOMES.get(player).getDestinations().stream().map(Destination::getName).toList();
         return filterSuggestionsByInput(builder, homeOptions);
      }
      return filterSuggestionsByInput(builder, new ArrayList<>());
   }
   
   private CompletableFuture<Suggestions> getWarpSuggestions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
      List<String> warpOptions = WARPS.get(ctx.getSource().getServer().getWorld(ServerWorld.OVERWORLD)).getDestinations().stream().map(Destination::getName).toList();
      return filterSuggestionsByInput(builder, warpOptions);
   }
   
   private CompletableFuture<Suggestions> getTpaInitSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      ServerCommandSource scs = context.getSource();
      
      List<String> activeTargets = Stream.concat(
            activeTpas.values().stream().map(TPARequest -> TPARequest.tTo.getName().getString()),
            activeTpas.values().stream().map(TPARequest -> TPARequest.tFrom.getName().getString())
      ).toList();
      List<String> others = Arrays.stream(scs.getServer().getPlayerNames())
            .filter(s -> !s.equals(scs.getName()) && !activeTargets.contains(s))
            .collect(Collectors.toList());
      return filterSuggestionsByInput(builder, others);
   }
   
   private CompletableFuture<Suggestions> getTpaTargetSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = activeTpas.values().stream().map(tpaRequest -> tpaRequest.tFrom.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private CompletableFuture<Suggestions> getTpaSenderSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
      List<String> activeTargets = activeTpas.values().stream().map(TPARequest -> TPARequest.tTo.getName().getString()).collect(Collectors.toList());
      return filterSuggestionsByInput(builder, activeTargets);
   }
   
   private int interruptTp(CommandContext<ServerCommandSource> ctx){
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
         return -1;
      }
      
      final ServerPlayerEntity player = ctx.getSource().getPlayer();
      
      boolean active = SERVER_TIMER_CALLBACKS.entrySet().stream().anyMatch(t -> (t.getValue() instanceof TeleportTimer tp) && tp.player.getUuid().equals(player.getUuid()));
      if(!active){
         player.sendMessage(Text.literal("You are not channeling a teleport").formatted(Formatting.RED),false);
         return 0;
      }else{
         Optional<Map.Entry<UUID, GenericTimer>> activeTP = SERVER_TIMER_CALLBACKS.entrySet().stream().filter(t -> (t.getValue() instanceof TeleportTimer tp) && tp.player.getUuid().equals(player.getUuid())).findFirst();
         activeTP.ifPresent(uuidGenericTimerEntry -> SERVER_TIMER_CALLBACKS.remove(uuidGenericTimerEntry.getKey()));
         
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
         ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if(tpahere && !(boolean) config.getValue("tpaheres")){
         tFrom.sendMessage(Text.literal("TPA Heres are disabled!").formatted(Formatting.RED),false);
         return -1;
      }else if(!tpahere && !(boolean) config.getValue("tpas")){
         tFrom.sendMessage(Text.literal("TPAs are disabled!").formatted(Formatting.RED),false);
         return -1;
      }
      
      if (tFrom.equals(tTo)) {
         tFrom.sendMessage(Text.literal("You cannot request to TPA to yourself!").formatted(Formatting.RED), false);
         return -1;
      }
   
      if(checkCooldown(TPType.TPA,tFrom)) return -1;
      if(activeChannels(tFrom)) return -1;
      
      TPARequest tpa = new TPARequest(tFrom, tTo, tpahere);
      if (activeTpas.values().stream().anyMatch(TPARequest -> TPARequest.equals(tpa))) {
         tFrom.sendMessage(Text.literal("There is already an ongoing request like this!").formatted(Formatting.RED), false);
         return 1;
      }
      
      tpa.setTimeoutCallback();
      activeTpas.put(tFrom.getUuid(),tpa);
      
      MutableText senderText = tpahere ?
            Text.literal("You have requested that ").formatted(Formatting.LIGHT_PURPLE)
                  .append(Text.literal(tTo.getName().getString()).formatted(Formatting.AQUA))
                  .append(Text.literal(" TPA to you ").formatted(Formatting.LIGHT_PURPLE)):
            Text.literal("You have requested to TPA to ").formatted(Formatting.LIGHT_PURPLE)
                  .append(Text.literal(tTo.getName().getString()).formatted(Formatting.AQUA));
      
      tFrom.sendMessage(senderText
                  .append(Text.literal("\nTo cancel type ").formatted(Formatting.LIGHT_PURPLE))
                  .append(Text.literal("/tpacancel [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpacancel " + tTo.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpacancel " + tTo.getName().getString())))
                              .withColor(Formatting.GREEN)))
                  .append(Text.literal("\nThis request will timeout in " + ((int)config.getValue("tpa-timeout"))/20 + " seconds.").formatted(Formatting.LIGHT_PURPLE)),
            false);
      
      tTo.sendMessage(
            Text.literal(tFrom.getName().getString()).formatted(Formatting.AQUA)
                  .append(Text.literal(tpahere ? " has requested that you TPA to them!" : " has requested to TPA to you!").formatted(Formatting.LIGHT_PURPLE))
                  .append(Text.literal("\nTo accept type ").formatted(Formatting.LIGHT_PURPLE))
                  .append(Text.literal("/tpaaccept [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaaccept " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpaaccept " + tFrom.getName().getString())))
                              .withColor(Formatting.GREEN)))
                  .append(Text.literal("\nTo deny type ").formatted(Formatting.LIGHT_PURPLE))
                  .append(Text.literal("/tpadeny [<player>]").styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny " + tFrom.getName().getString()))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpadeny " + tFrom.getName().getString())))
                              .withColor(Formatting.GREEN)))
                  .append(Text.literal("\nThis request will timeout in " + ((int)config.getValue("tpa-timeout"))/20 + " seconds.").formatted(Formatting.LIGHT_PURPLE)),
            false);
      return 1;
   }
   
   public int tpaAccept(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom) {
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TPARequest[] candidates;
         candidates = activeTpas.values().stream().filter(TPARequest -> TPARequest.tTo.equals(tTo)).toArray(TPARequest[]::new);
         if (candidates.length > 1) {
            MutableText text = Text.literal("You currently have multiple active TPA requests! Please specify whose request to accept.\n").formatted(Formatting.LIGHT_PURPLE);
            Arrays.stream(candidates).map(TPARequest -> TPARequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaaccept " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpaaccept " + name)))
                              .withColor(Formatting.GREEN)).append(" ")));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(Text.literal("You currently don't have any TPA requests!").formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
      
      TPARequest tr = getTPARequest(tFrom, tTo, TPAAction.ACCEPT);
      if (tr == null) return 1;
      
      if(!tr.isTPAhere() && !(boolean) config.getValue("tpas")){
         tTo.sendMessage(Text.literal("TPAs are disabled!").formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !(boolean) config.getValue("tpaheres")){
         tTo.sendMessage(Text.literal("TPA Heres are disabled!").formatted(Formatting.RED),false);
         return -1;
      }
      
      ServerPlayerEntity finalTFrom = tFrom;
      if(tr.isTPAhere()){
         TeleportUtils.genericTeleport(
               (boolean) config.getValue("bossbar"),
               (boolean) config.getValue("particles"),
               (boolean) config.getValue("sound"),
               (int) config.getValue("spawn-warmup"),
               tTo, () -> {
                  tTo.teleportTo(new TeleportTarget(finalTFrom.getServerWorld(), finalTFrom.getPos(), Vec3d.ZERO, tTo.getYaw(),tTo.getPitch(), TeleportTarget.NO_OP));
                  recentTeleports.add(new Teleport(finalTFrom,TPType.TPA,System.currentTimeMillis()));
               });
      }else{
         TeleportUtils.genericTeleport(
               (boolean) config.getValue("bossbar"),
               (boolean) config.getValue("particles"),
               (boolean) config.getValue("sound"),
               (int) config.getValue("spawn-warmup"),
               tFrom, () -> {
                  finalTFrom.teleportTo(new TeleportTarget(tTo.getServerWorld(), tTo.getPos(), Vec3d.ZERO, finalTFrom.getYaw(),finalTFrom.getPitch(), TeleportTarget.NO_OP));
                  recentTeleports.add(new Teleport(finalTFrom,TPType.TPA,System.currentTimeMillis()));
               });
      }
      
      tr.cancelTimeout();
      activeTpas.remove(tFrom.getUuid());
      tr.tTo.sendMessage(Text.literal("You have accepted the TPA request!").formatted(Formatting.GREEN), false);
      tr.tFrom.sendMessage(Text.literal(tr.tTo.getName().getString()).formatted(Formatting.AQUA)
            .append(Text.literal(" has accepted the TPA request!").formatted(Formatting.GREEN)), false);
      return 1;
   }
   
   
   public int tpaDeny(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tFrom){
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tTo = ctx.getSource().getPlayer();
      
      if (tFrom == null) {
         TPARequest[] candidates;
         candidates = activeTpas.values().stream().filter(TPARequest -> TPARequest.tTo.equals(tTo)).toArray(TPARequest[]::new);
         if (candidates.length > 1) {
            MutableText text = Text.literal("You currently have multiple active TPA requests! Please specify whose request to deny.\n").formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(TPARequest -> TPARequest.tFrom.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpadeny " + name)))
                              .withColor(Formatting.GREEN))).append(" "));
            tTo.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tTo.sendMessage(Text.literal("You currently don't have any TPA requests!").formatted(Formatting.RED), false);
            return 1;
         }
         tFrom = candidates[0].tFrom;
      }
      
      TPARequest tr = getTPARequest(tFrom, tTo, TPAAction.DENY);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !(boolean) config.getValue("tpas")){
         tTo.sendMessage(Text.literal("TPAs are disabled!").formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !(boolean) config.getValue("tpaheres")){
         tTo.sendMessage(Text.literal("TPA Heres are disabled!").formatted(Formatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      activeTpas.remove(tFrom.getUuid());
      tr.tTo.sendMessage(Text.literal("You have cancelled the TPA request!").formatted(Formatting.RED), false);
      tr.tFrom.sendMessage(Text.literal(tr.tTo.getName().getString()).formatted(Formatting.AQUA)
            .append(Text.literal(" has cancelled the TPA request!").formatted(Formatting.RED)), false);
      return 1;
   }
   
   public int tpaCancel(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity tTo) throws CommandSyntaxException {
      if(!ctx.getSource().isExecutedByPlayer()){
         ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
         return -1;
      }
      final ServerPlayerEntity tFrom = ctx.getSource().getPlayer();
      
      if (tTo == null) {
         TPARequest[] candidates;
         candidates = activeTpas.values().stream().filter(TPARequest -> TPARequest.tFrom.equals(tFrom)).toArray(TPARequest[]::new);
         if (candidates.length > 1) {
            MutableText text = Text.literal("You currently have multiple active TPA requests! Please specify which request to cancel.\n").formatted(Formatting.GREEN);
            Arrays.stream(candidates).map(TPARequest -> TPARequest.tTo.getName().getString()).forEach(name ->
                  text.append(Text.literal(name).styled(s ->
                        s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpacancel " + name))
                              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/tpacancel " + name)))
                              .withColor(Formatting.GREEN))).append(" "));
            tFrom.sendMessage(text, false);
            return 1;
         }
         if (candidates.length < 1) {
            tFrom.sendMessage(Text.literal("You currently don't have any TPA requests!").formatted(Formatting.RED), false);
            return 1;
         }
         tTo = candidates[0].tTo;
      }
      
      TPARequest tr = getTPARequest(tFrom, tTo, TPAAction.CANCEL);
      if (tr == null) return 1;
      if(!tr.isTPAhere() && !(boolean) config.getValue("tpas")){
         tTo.sendMessage(Text.literal("TPAs are disabled!").formatted(Formatting.RED),false);
         return -1;
      }else if(tr.isTPAhere() && !(boolean) config.getValue("tpaheres")){
         tTo.sendMessage(Text.literal("TPA Heres are disabled!").formatted(Formatting.RED),false);
         return -1;
      }
      
      tr.cancelTimeout();
      activeTpas.remove(tFrom.getUuid());
      tr.tFrom.sendMessage(Text.literal("You have cancelled the TPA request!").formatted(Formatting.RED), false);
      tr.tTo.sendMessage(Text.literal(tr.tFrom.getName().getString()).formatted(Formatting.AQUA)
            .append(Text.literal(" has cancelled the TPA request!").formatted(Formatting.RED)), false);
      return 1;
   }
   
   private int spawnTp(CommandContext<ServerCommandSource> ctx){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("spawn")){
            player.sendMessage(Text.literal("/spawn is disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.SPAWN,player)) return -1;
         if(activeChannels(player)) return -1;
         
         ServerWorld world = player.getServer().getWorld(ServerWorld.OVERWORLD);
         TeleportUtils.genericTeleport(
               (boolean) config.getValue("bossbar"),
               (boolean) config.getValue("particles"),
               (boolean) config.getValue("sound"),
               (int) config.getValue("spawn-warmup"),
               player, () -> {
                  player.teleportTo(new TeleportTarget(world, world.getSpawnPos().toBottomCenterPos(), Vec3d.ZERO, world.getSpawnAngle(),0.0f, TeleportTarget.NO_OP));
                  recentTeleports.add(new Teleport(player,TPType.SPAWN,System.currentTimeMillis()));
               });
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int randomTp(CommandContext<ServerCommandSource> ctx){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("randomtps")){
            player.sendMessage(Text.literal("/randomtp is disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.RTP,player)) return -1;
         if(activeChannels(player)) return -1;
         ServerWorld world = player.getServerWorld();
         
         int range = (int) config.getValue("rtp-range");
         int tries = 0;
         
         while(tries < 100){
            double angle = 2 * Math.PI * Math.random();
            double r = range * Math.sqrt(Math.random());
            int x = (int) (r * Math.cos(angle)) + world.getSpawnPos().getX();
            int z = (int) (r * Math.sin(angle)) + world.getSpawnPos().getZ();
            
            int placeTries = 0; int spread = 4;
            ArrayList<BlockPos> locations;
            do{
               locations = TeleportUtils.makeSpawnLocations(1,spread, world.getLogicalHeight()-10, world, new BlockPos(x,world.getLogicalHeight(),z));
               placeTries++; spread++; // Expand search area
            }while(locations.isEmpty() && placeTries < 5);
            if(locations.isEmpty()){
               tries++;
               continue;
            }
            Vec3d pos = locations.get(0).toCenterPos();
            
            TeleportUtils.genericTeleport(
                  (boolean) config.getValue("bossbar"),
                  (boolean) config.getValue("particles"),
                  (boolean) config.getValue("sound"),
                  (int) config.getValue("rtp-warmup"),
                  player, () -> {
                     player.teleportTo(new TeleportTarget(world, pos, Vec3d.ZERO, player.getYaw(),player.getPitch(), TeleportTarget.NO_OP));
                     recentTeleports.add(new Teleport(player,TPType.RTP,System.currentTimeMillis()));
                  });
            return 1;
         }
         
         player.sendMessage(Text.literal("Could not find a valid RTP spot!").formatted(Formatting.RED,Formatting.ITALIC),false);
         return 0;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setHome(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Homes must be set by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("homes")){
            player.sendMessage(Text.literal("Homes are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> homes = HOMES.get(player).getDestinations();
         if(homes.size() >= (int) config.getValue("max-homes")){
            player.sendMessage(Text.literal("You already have the max amount of homes").formatted(Formatting.RED),false);
            return -1;
         }else if(homes.size() > 0 && name == null){
            player.sendMessage(Text.literal("You must specify a name for your new home").formatted(Formatting.RED),false);
            return -1;
         }
         if(name == null) name = "main";
   
         String finalName = name;
         if(homes.stream().anyMatch(h -> h.getName().equals(finalName))){
            player.sendMessage(Text.literal("You already have a home named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         
         homes.add(new Destination(name,player.getPos(),player.getRotationClient(),player.getWorld().getRegistryKey().getValue().toString()));
         player.sendMessage(Text.literal("Home '"+name+"' added successfully!").formatted(Formatting.GREEN),false);
         
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delHome(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Homes must be deleted by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("homes")){
            player.sendMessage(Text.literal("Homes are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> homes = HOMES.get(player).getDestinations();
         if(homes.size() > 1 && name == null){
            player.sendMessage(Text.literal("You must specify which home to delete").formatted(Formatting.RED),false);
            return -1;
         }else if(homes.size() == 1 && name == null){
            name = homes.get(0).getName();
         }
         
         String finalName = name;
         Optional<Destination> foundHome = homes.stream().filter(h -> h.getName().equals(finalName)).findFirst();
         if(foundHome.isEmpty()){
            player.sendMessage(Text.literal("You have no home named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         
         homes.remove(foundHome.get());
         player.sendMessage(Text.literal("Home '"+name+"' removed successfully!").formatted(Formatting.GREEN),false);
         
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int homeTp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("homes")){
            player.sendMessage(Text.literal("Homes are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> homes = HOMES.get(player).getDestinations();
         if(homes.size() == 1 && name == null){
            name = homes.get(0).getName();
         }
   
         String finalName = name;
         Optional<Destination> foundHome = homes.stream().filter(h -> h.getName().equals(finalName)).findFirst();
         if(foundHome.isEmpty()){
            player.sendMessage(Text.literal("You have no home named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.HOME,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination home = foundHome.get();
         ServerWorld world = home.getWorld(ctx.getSource().getServer());
         TeleportUtils.genericTeleport(
               (boolean) config.getValue("bossbar"),
               (boolean) config.getValue("particles"),
               (boolean) config.getValue("sound"),
               (int) config.getValue("homes-warmup"),
               player, () -> {
                  player.teleportTo(new TeleportTarget(world, home.getPos(), Vec3d.ZERO, home.getRotation().y,home.getRotation().x, TeleportTarget.NO_OP));
                  recentTeleports.add(new Teleport(player,TPType.HOME,System.currentTimeMillis()));
               });
   
         player.sendMessage(Text.literal("Now teleporting to home '"+name+"'").formatted(Formatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int setWarp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Warps must be set by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("warps")){
            player.sendMessage(Text.literal("Warps are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> warps = WARPS.get(ctx.getSource().getServer().getWorld(ServerWorld.OVERWORLD)).getDestinations();
         
         if(warps.stream().anyMatch(h -> h.getName().equals(name))){
            player.sendMessage(Text.literal("There already is a warp named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         
         warps.add(new Destination(name,player.getPos(),player.getRotationClient(),player.getWorld().getRegistryKey().getValue().toString()));
         player.sendMessage(Text.literal("Warp '"+name+"' added successfully!").formatted(Formatting.GREEN),false);
         
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int delWarp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Warps must be deleted by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("warps")){
            player.sendMessage(Text.literal("Warps are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> warps = WARPS.get(ctx.getSource().getServer().getWorld(ServerWorld.OVERWORLD)).getDestinations();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().equals(name)).findFirst();
         if(foundWarp.isEmpty()){
            player.sendMessage(Text.literal("There is no warp named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         
         warps.remove(foundWarp.get());
         player.sendMessage(Text.literal("Warp '"+name+"' removed successfully!").formatted(Formatting.GREEN),false);
         
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private int warpTp(CommandContext<ServerCommandSource> ctx, String name){
      try{
         if(!ctx.getSource().isExecutedByPlayer()){
            ctx.getSource().sendError(Text.literal("Teleports must be done by a player!").formatted(Formatting.RED));
            return -1;
         }
         ServerPlayerEntity player = ctx.getSource().getPlayer();
         if(!(boolean) config.getValue("warps")){
            player.sendMessage(Text.literal("Warps are disabled!").formatted(Formatting.RED),false);
            return -1;
         }
         List<Destination> warps = WARPS.get(ctx.getSource().getServer().getWorld(ServerWorld.OVERWORLD)).getDestinations();
         
         Optional<Destination> foundWarp = warps.stream().filter(h -> h.getName().equals(name)).findFirst();
         if(foundWarp.isEmpty()){
            player.sendMessage(Text.literal("There is no warp named '"+name+"'").formatted(Formatting.RED),false);
            return -1;
         }
         if(checkCooldown(TPType.WARP,player)) return -1;
         if(activeChannels(player)) return -1;
         
         Destination warp = foundWarp.get();
         ServerWorld world = warp.getWorld(ctx.getSource().getServer());
         TeleportUtils.genericTeleport(
               (boolean) config.getValue("bossbar"),
               (boolean) config.getValue("particles"),
               (boolean) config.getValue("sound"),
               (int) config.getValue("warps-warmup"),
               player, () -> {
                  player.teleportTo(new TeleportTarget(world, warp.getPos(), Vec3d.ZERO, warp.getRotation().y,warp.getRotation().x, TeleportTarget.NO_OP));
                  recentTeleports.add(new Teleport(player,TPType.WARP,System.currentTimeMillis()));
               });
         player.sendMessage(Text.literal("Now warping to '"+name+"'").formatted(Formatting.LIGHT_PURPLE),false);
         return 1;
      }catch(Exception e){
         e.printStackTrace();
      }
      return -1;
   }
   
   private static void onTick(MinecraftServer minecraftServer){
      try{
         // Tick Timer Callbacks
         ArrayList<UUID> toRemove = new ArrayList<>();
         
         Iterator<Map.Entry<UUID, GenericTimer>> itr = SERVER_TIMER_CALLBACKS.entrySet().iterator();
         
         while(itr.hasNext()){
            Map.Entry<UUID, GenericTimer> entry = itr.next();
            GenericTimer t = entry.getValue();
            if(t.decreaseTimer() == 0){
               t.onTimer();
               if(t.autoRemove || t.isTrash()) toRemove.add(entry.getKey());
            }
         }
         if(SERVER_TIMER_CALLBACKS_QUEUE.size() > 0){
            SERVER_TIMER_CALLBACKS.putAll(SERVER_TIMER_CALLBACKS_QUEUE);
            SERVER_TIMER_CALLBACKS_QUEUE.clear();
         }
         
         for(UUID uuid : toRemove){
            SERVER_TIMER_CALLBACKS.remove(uuid);
         }
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   public static void playerDied(ServerPlayerEntity player){
      try{
         activeTpas.entrySet().removeIf(entry -> {
            TPARequest req = entry.getValue();
            if(req.tFrom.getUuid().equals(player.getUuid())){
               req.cancelTimeout();
               return true;
            }
            if(req.tTo.getUuid().equals(player.getUuid())){
               req.cancelTimeout();
               return true;
            }
            return false;
         });
         SERVER_TIMER_CALLBACKS.entrySet().removeIf(t -> (t.getValue() instanceof TeleportTimer tp) && tp.player.getUuid().equals(player.getUuid()));
         SERVER_TIMER_CALLBACKS_QUEUE.entrySet().removeIf(t -> (t.getValue() instanceof TeleportTimer tp) && tp.player.getUuid().equals(player.getUuid()));
   
         BossBarManager bbm = player.getServer().getBossBarManager();
         bbm.getAll().stream().filter(b -> b.getId().toString().contains("standstill-"+player.getUuid())).toList().forEach(b -> {
            b.clearPlayers();
            bbm.remove(b);
         });
      }catch(Exception e){
         e.printStackTrace();
      }
   }
   
   private boolean activeChannels(ServerPlayerEntity player){
      boolean active = SERVER_TIMER_CALLBACKS.entrySet().stream().anyMatch(t -> (t.getValue() instanceof TeleportTimer tp) && tp.player.getUuid().equals(player.getUuid()));
      if(active){
         player.sendMessage(Text.literal("You are already channeling a teleport!").formatted(Formatting.RED),false);
      }
      return active;
   }
   
   private boolean checkCooldown(TPType type, ServerPlayerEntity player){
      for(Teleport tp : recentTeleports){
         if(!tp.player.getUuidAsString().equals(player.getUuidAsString())) continue;
         if(tp.type != type) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(type) * 50L){
            int remaining = (int) (((readConfigCooldown(type) * 50L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            player.sendMessage(Text.literal("You cannot /"+type.label+" for another "+remaining+" seconds.").formatted(Formatting.RED),false);
            return true;
         }
      }
      return false;
   }
   
   private boolean checkTPAHereCooldown(ServerPlayerEntity tFrom, ServerPlayerEntity tTo){
      for(Teleport tp : recentTeleports){
         if(!tp.player.getUuidAsString().equals(tFrom.getUuidAsString())) continue;
         if(tp.type != TPType.TPA) continue;
         if(System.currentTimeMillis() - tp.timestamp < readConfigCooldown(TPType.TPA) * 50L){
            int remaining = (int) (((readConfigCooldown(TPType.TPA) * 50L) - (System.currentTimeMillis() - tp.timestamp)) / 1000);
            tTo.sendMessage(Text.literal("That player cannot /"+TPType.TPA.label+" for another "+remaining+" seconds.").formatted(Formatting.RED),false);
            return true;
         }
      }
      return false;
   }
   
   private int readConfigCooldown(TPType type){
      return switch(type){
         case TPA -> (int)config.getValue("tpa-cooldown");
         case HOME -> (int)config.getValue("homes-cooldown");
         case WARP -> (int)config.getValue("warps-cooldown");
         case SPAWN -> (int)config.getValue("spawn-cooldown");
         case RTP -> (int)config.getValue("rtp-cooldown");
      };
   }
   
   private enum TPType{
      HOME("home"), TPA("tpa"), WARP("warp"), SPAWN("spawn"), RTP("rtp");
   
      public final String label;
      
      TPType(String name){
         this.label = name;
      }
   }
   
   private enum TPAAction {
      ACCEPT, DENY, CANCEL
   }
   
   record Teleport(ServerPlayerEntity player, TPType type, long timestamp) {
   }
   
   private TPARequest getTPARequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, TPAAction action) {
      Optional<TPARequest> otr = activeTpas.values().stream()
            .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom) && tpaRequest.tTo.equals(tTo)).findFirst();
      
      if (otr.isEmpty()) {
         if (action == TPAAction.CANCEL) {
            tFrom.sendMessage(Text.literal("No ongoing request!").formatted(Formatting.RED), false);
         } else {
            tTo.sendMessage(Text.literal("No ongoing request!").formatted(Formatting.RED), false);
         }
         return null;
      }
      
      return otr.get();
   }
   
   static class TPARequest {
      ServerPlayerEntity tFrom;
      ServerPlayerEntity tTo;
      boolean tpahere;
      
      UUID timerId;
      
      public TPARequest(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, boolean tpahere) {
         this.tFrom = tFrom;
         this.tTo = tTo;
         this.tpahere = tpahere;
      }
      
      String getStr(){
         return tpahere ? "TPA Here" : "TPA";
      }
      
      public boolean isTPAhere(){
         return tpahere;
      }
      
      void setTimeoutCallback() {
         GenericTimer timer = new GenericTimer((int)config.getValue("tpa-timeout"), new TimerTask() {
            @Override
            public void run(){
               activeTpas.remove(tFrom.getUuid());
               tFrom.sendMessage(Text.literal("Your "+getStr()+" request to " + tTo.getName().getString() + " has timed out!").formatted(Formatting.RED), false);
               tTo.sendMessage(Text.literal(getStr()+" request from " + tFrom.getName().getString() + " has timed out!").formatted(Formatting.RED), false);
            }
         });
         
         timerId = UUID.randomUUID();
         SERVER_TIMER_CALLBACKS.put(timerId,timer);
      }
      
      void cancelTimeout() {
         SERVER_TIMER_CALLBACKS.remove(timerId);
      }
      
      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         TPARequest that = (TPARequest) o;
         return this.tFrom.equals(that.tFrom) && this.tTo.equals(that.tTo);
      }
      
      @Override
      public int hashCode() {
         return Objects.hash(tFrom, tTo);
      }
      
      @Override
      public String toString() {
         return "TPARequest{" + "tFrom=" + tFrom +
               ", tTo=" + tTo +
               '}';
      }
      
      public void refreshPlayers() {
         this.tFrom = tFrom.server.getPlayerManager().getPlayer(tFrom.getUuid());
         this.tTo = tTo.server.getPlayerManager().getPlayer(tTo.getUuid());
         assert tFrom != null && tTo != null;
      }
   }
}
