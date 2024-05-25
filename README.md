# Ender Nexus

An all-in-one server-sided mod to give players various common teleport commands.

##### This mod should only be installed on a server.

### Player Commands
* ```/spawntp``` Teleports the player to spawn
* ```/spawn``` Teleports the player to spawn (disabled when Carpet is present)
* ```/home [name]``` Teleports the player to the specified or default home
* ```/sethome [name]``` Sets a home at the current location with optional name
* ```/delhome [name]``` Deletes a player's home with optional name
* ```/warp <name>``` Teleports the player to the specified warp
* ```/tpa <player>``` Requests to teleport to the specified player
* ```/tpaaccept [player]``` Accepts an incoming TPA request from the specified player
* ```/tpadeny [player]``` Denies an incoming TPA request from the specified player
* ```/tpacancel [player]``` Cancels an outgoing TPA request to the specified player

### Admin Commands & Configuration
Configuration can be done through the properties file generated when loaded on a server or through commands.
* ```/setwarp <name>``` Sets a warp at the current location with the specified name
* ```/delwarp <name>``` Deletes the specified warp
* ```/endernexus``` Gets the currently configured settings
* ```/endernexus bossbar <true/false>``` Enables/Disables the teleport warmup bar
* ```/endernexus particles <true/false>``` Enables/Disables teleportation warmup particles
* ```/endernexus sound <true/false>``` Enables/Disables the teleport warmup sounds
* ```/endernexus homes <true/false>``` Enables/Disables the Homes feature
* ```/endernexus homes-warmup <1+>``` Sets the number of ticks it takes to teleport to a home
* ```/endernexus homes-cooldown <1+>``` Sets the number of ticks before a player can teleport to a home again
* ```/endernexus max-homes <1-100>``` Sets the number of homes a player is allowed to set
* ```/endernexus warps <true/false>``` Enables/Disables the Warps feature
* ```/endernexus warps-warmup <1+>``` Sets the number of ticks it takes to teleport to a warp
* ```/endernexus warps-cooldown <1+>``` Sets the number of ticks before a player can teleport to a warp again
* ```/endernexus spawn <true/false>``` Enables/Disables the Spawn feature
* ```/endernexus spawn-warmup <1+>``` Sets the number of ticks it takes to teleport to spawn
* ```/endernexus spawn-cooldown <1+>``` Sets the number of ticks before a player can teleport to spawn again
* ```/endernexus tpas <true/false>``` Enables/Disables the TPAs feature
* ```/endernexus tpa-warmup <1+>``` Sets the number of ticks it takes to teleport to another player
* ```/endernexus tpa-cooldown <1+>``` Sets the number of ticks before a player can teleport to another player again
* ```/endernexus tpa-timeout <1+>``` Sets the number of ticks before a tpa request expires
* ```/endernexus cleanse``` Clears the cache (useful if someone gets stuck with a teleport bar that doesn't clear)

### LICENSE NOTICE
By using this project in any form, you hereby give your "express assent" for the terms of the license of this project, and acknowledge that I, BorisShoes, have fulfilled my obligation under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to the terms of this License.
