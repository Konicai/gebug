package me.konicai.gebug;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.EnumArgument;
import cloud.commandframework.arguments.standard.FloatArgument;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.arguments.standard.StringArrayArgument;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.paper.PaperCommandManager;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.CameraShakeAction;
import org.cloudburstmc.protocol.bedrock.data.CameraShakeType;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.DimensionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class Gebug extends JavaPlugin implements Listener {

    private static final Map<String, LevelEventType> LEVEL_EVENT_TYPES = new HashMap<>();
    private static final List<String> LEVEL_EVENT_SUGGESTIONS;

    private static final Map<String, ClientboundGameEventPacket.Type> GAME_EVENTS = new HashMap<>();
    private static final List<String> GAME_EVENT_SUGGESTIONS;

    static {
        for (LevelEvent event : LevelEvent.values()) {
            LEVEL_EVENT_TYPES.put(event.name(), event);
        }
        for (ParticleType particle : ParticleType.values()) {
            LEVEL_EVENT_TYPES.put(particle.name(), particle);
        }

        LEVEL_EVENT_SUGGESTIONS = LEVEL_EVENT_TYPES.keySet()
            .stream()
            .sorted()
            .toList();

        int i = 0;
        Class<?> clazz = ClientboundGameEventPacket.class;
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && ClientboundGameEventPacket.Type.class.isAssignableFrom(field.getType())) {
                try {
                    // use MCPL enum just for the friendly name - it isn't reobfuscated
                    GAME_EVENTS.put(GameEvent.from(i++).name(), (ClientboundGameEventPacket.Type) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        GAME_EVENT_SUGGESTIONS = GAME_EVENTS.keySet()
            .stream()
            .sorted()
            .toList();
    }

    private GeyserImpl geyser;
    private Server server;

    @Override
    public void onEnable() {
        geyser = GeyserImpl.getInstance();
        server = getServer();

        server.getPluginManager().registerEvents(this, this);

        CommandManager<CommandSender> commandManager;
        try {
            commandManager = PaperCommandManager.createNative(this, CommandExecutionCoordinator.simpleCoordinator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Command.Builder<CommandSender> builder = commandManager.commandBuilder("gebug");

        commandManager.command(builder
            .literal("gamevent")
            .argument(StringArgument.<CommandSender>builder("event")
                .withSuggestionsProvider(($, $$) -> GAME_EVENT_SUGGESTIONS)
                .build())
            .argument(FloatArgument.optional("value", 0f))
            .handler(context -> {
                CommandSender sender = context.getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Not a player.");
                    return;
                }
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

                String key = context.get("event");
                ClientboundGameEventPacket.Type type = GAME_EVENTS.get(key);
                float value = context.get("value");


                ClientboundGameEventPacket packet = new ClientboundGameEventPacket(type, value);
                serverPlayer.connection.send(packet);
            }));

        commandManager.command(builder
            .literal("playsound")
            .argument(StringArgument.of("sound"))
            .argument(FloatArgument.optional("volume", 1))
            .argument(FloatArgument.optional("pitch", 1))
            .handler(context -> {
                PlaySoundPacket packet = new PlaySoundPacket();
                packet.setSound(context.get("sound"));
                packet.setVolume(context.get("volume"));
                packet.setPitch(context.get("pitch"));

                boolean played = forAllSessions((session, player) -> {
                    packet.setPosition(Vector3f.from(convertVector(player.getLocation())));
                    session.sendUpstreamPacket(packet);
                });

                if (!played) {
                    context.getSender().sendMessage("No bedrock players found");
                }
            })
        );

        commandManager.command(builder
            .literal("levelevent")
            .argument(StringArgument.<CommandSender>builder("type")
                .withSuggestionsProvider(($, $$) -> LEVEL_EVENT_SUGGESTIONS)
                .build())
            .argument(IntegerArgument.optional("data", 0))
            .handler(context -> {
                String typeArg = context.get("type");
                LevelEventType type = LEVEL_EVENT_TYPES.get(typeArg);
                if (type == null) {
                    context.getSender().sendMessage("No LevelEventType for " + typeArg);
                    return;
                }

                LevelEventPacket packet = new LevelEventPacket();
                packet.setType(type);
                packet.setData(context.get("data"));

                boolean sent = forAllSessions((session, player) -> {
                    packet.setPosition(Vector3f.from(convertVector(player.getLocation())));
                    session.sendUpstreamPacket(packet);
                });

                if (!sent) {
                    context.getSender().sendMessage("No bedrock players found");
                }
            })
        );

        commandManager.command(builder
            .literal("soundevent")
            .argument(EnumArgument.of(SoundEvent.class, "name"))
            .argument(IntegerArgument.optional("extraData", 0))
            .argument(StringArgument.optional("identifier", ""))
            .handler(context -> {
                LevelSoundEventPacket packet = new LevelSoundEventPacket();
                packet.setSound((context.get("name")));
                packet.setExtraData(context.get("extraData"));
                packet.setIdentifier(context.getOrDefault("identifier", ":")); // arguments with default of "" aren't stored i guess

                boolean sent = forAllSessions((session, player) -> {
                    packet.setPosition(convertVector(player.getLocation()));
                    session.sendUpstreamPacket(packet);
                });

                if (!sent) {
                    context.getSender().sendMessage("No bedrock players found");
                }
            })
        );

        commandManager.command(builder
            .literal("particle")
            .argument(StringArgument.of("id"))
            .handler(context -> {
                SpawnParticleEffectPacket packet = new SpawnParticleEffectPacket();
                packet.setIdentifier(context.get("id"));
                packet.setMolangVariablesJson(Optional.empty());

                boolean sent = forAllSessions((session, player) -> {
                    packet.setDimensionId(DimensionUtils.javaToBedrock(session.getDimension()));
                    packet.setPosition(convertVector(player.getLocation()));
                    session.sendUpstreamPacket(packet);
                });

                if (!sent) {
                    context.getSender().sendMessage("No bedrock players found");
                }
            })
        );

        commandManager.command(builder
            .literal("shake")
            .argument(FloatArgument.of("intensity"))
            .argument(FloatArgument.of("duration"))
            .argument(EnumArgument.of(CameraShakeType.class, "type"))
            .argument(EnumArgument.of(CameraShakeAction.class, "action"))
            .handler(context -> {
                float intensity = context.get("intensity");
                float duration = context.get("duration");
                CameraShakeType type = context.get("type");
                CameraShakeAction action = context.get("action");

                forAllSessions((session, player) -> {
                    CameraShakePacket packet = new CameraShakePacket();
                    packet.setIntensity(intensity);
                    packet.setDuration(duration);
                    packet.setShakeType(type);
                    packet.setShakeAction(action);
                    session.sendUpstreamPacket(packet);
                });
            }));

        commandManager.command(builder
            .literal("fog")
            .literal("add")
            .argument(StringArrayArgument.of("identifiers", ($, $$) -> Collections.emptyList()))
            .handler(context -> {
                String[] ids = context.get("identifiers");
                forAllSessions((session, player) -> session.sendFog(ids));
            }));

        commandManager.command(builder
            .literal("fog")
            .literal("remove")
            .argument(StringArrayArgument.optional("identifiers", ($, $$) -> Collections.emptyList()))
            .handler(context -> {
                String[] ids = context.getOrDefault("identifiers", new String[0]);
                forAllSessions((session, player) -> session.removeFog(ids));
            }));

        commandManager.command(builder
            .literal("fog")
            .handler(context -> {
                forAllSessions((session, player) -> {
                    player.sendMessage("fog you see: " + session.fogEffects());
                });
            }));
    }

    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim();
        String[] args = command.split(" ");
        if (args.length == 2) {
            if (args[0].equals("/playsound")) {
                event.setMessage("%s player %s".formatted(command, event.getPlayer().getName()));
            }
        }
    }

    private static Vector3f convertVector(Location vector) {
        return Vector3f.from(vector.getX(), vector.getY(), vector.getZ());
    }

    private boolean forAllSessions(BiConsumer<GeyserSession, Player> consumer) {
        boolean found = false;
        for (GeyserSession session : geyser.getSessionManager().getAllSessions()) {
            consumer.accept(session, server.getPlayer(session.javaUuid()));
            found = true;
        }
        return found;
    }
}
