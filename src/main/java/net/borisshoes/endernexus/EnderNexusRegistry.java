package net.borisshoes.endernexus;

import com.mojang.serialization.Lifecycle;
import net.borisshoes.borislib.config.ConfigSetting;
import net.borisshoes.borislib.config.IConfigSetting;
import net.borisshoes.borislib.config.values.BooleanConfigValue;
import net.borisshoes.borislib.config.values.DoubleConfigValue;
import net.borisshoes.borislib.config.values.IntConfigValue;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.borisshoes.endernexus.EnderNexus.MOD_ID;

public class EnderNexusRegistry {
   public static final Registry<IConfigSetting<?>> CONFIG_SETTINGS = new SimpleRegistry<>(RegistryKey.ofRegistry(Identifier.of(MOD_ID,"config_settings")), Lifecycle.stable());
   
   public static final IConfigSetting<?> HOMES_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("homes", true)));
   public static final IConfigSetting<?> SPAWN_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("spawn", true)));
   public static final IConfigSetting<?> TPAS_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("tpas", true)));
   public static final IConfigSetting<?> TPAHERE_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("tpaheres", true)));
   public static final IConfigSetting<?> WARPS_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("warps", true)));
   public static final IConfigSetting<?> RTP_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("randomtps", true)));
   
   public static final IConfigSetting<?> BOSSBAR_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("bossbar", true)));
   public static final IConfigSetting<?> PARTICLES_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("particles", true)));
   public static final IConfigSetting<?> SOUND_ENABLED = registerConfigSetting(new ConfigSetting<>(
         new BooleanConfigValue("sound", true)));
   
   public static final IConfigSetting<?> HOMES_WARMUP = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("homesWarmup", 5.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> HOMES_COOLDOWN = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("homesCooldown", 90.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> HOMES_MAX = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("homesMax", 2, new IntConfigValue.IntLimits(1))));
   
   public static final IConfigSetting<?> TPA_WARMUP = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("tpaWarmup", 10.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> TPA_COOLDOWN = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("tpaCooldown", 120.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> TPA_TIMEOUT = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("tpaTimeout", 60.0, new DoubleConfigValue.DoubleLimits(0))));
   
   public static final IConfigSetting<?> SPAWN_WARMUP = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("spawnWarmup", 5.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> SPAWN_COOLDOWN = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("spawnCooldown", 60.0, new DoubleConfigValue.DoubleLimits(0))));
   
   public static final IConfigSetting<?> WARPS_WARMUP = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("warpsWarmup", 5.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> WARPS_COOLDOWN = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("warpsCooldown", 60.0, new DoubleConfigValue.DoubleLimits(0))));
   
   public static final IConfigSetting<?> RTP_WARMUP = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("rtpWarmup", 10.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> RTP_COOLDOWN = registerConfigSetting(new ConfigSetting<>(
         new DoubleConfigValue("rtpCooldown", 600.0, new DoubleConfigValue.DoubleLimits(0))));
   public static final IConfigSetting<?> RTP_MIN_RANGE = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("rtpMinRange", 250, new IntConfigValue.IntLimits(1))));
   public static final IConfigSetting<?> RTP_MAX_RANGE = registerConfigSetting(new ConfigSetting<>(
         new IntConfigValue("rtpMaxRange", 1000, new IntConfigValue.IntLimits(1))));
   
   private static IConfigSetting<?> registerConfigSetting(IConfigSetting<?> setting){
      Registry.register(CONFIG_SETTINGS,Identifier.of(MOD_ID,setting.getId()),setting);
      return setting;
   }
}
