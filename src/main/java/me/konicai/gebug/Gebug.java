package me.konicai.gebug;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.EnumArgument;
import cloud.commandframework.arguments.standard.FloatArgument;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.paper.PaperCommandManager;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class Gebug extends JavaPlugin {

    private static final Map<String, LevelEventType> LEVEL_EVENT_TYPES = new HashMap<>();
    private static final List<String> LEVEL_EVENT_SUGGESTIONS;

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
            .collect(Collectors.toList());
    }

    private GeyserImpl geyser;
    private Server server;

    @Override
    public void onEnable() {
        geyser = GeyserImpl.getInstance();
        server = getServer();

        CommandManager<CommandSender> commandManager;
        try {
            commandManager = PaperCommandManager.createNative(this, CommandExecutionCoordinator.simpleCoordinator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Command.Builder<CommandSender> builder = commandManager.commandBuilder("gebug");

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
                packet.setIdentifier(context.get("identifier"));

                boolean sent = forAllSessions((session, player) -> {
                    packet.setPosition(Vector3f.from(convertVector(player.getLocation())));
                    session.sendUpstreamPacket(packet);
                });

                if (!sent) {
                    context.getSender().sendMessage("No bedrock players found");
                }
            })
        );
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
