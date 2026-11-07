package com.minealphaproxy.compat.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minealphaproxy.network.HandshakeHandler;
import com.minealphaproxy.network.VarInts;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

public final class VelocityPlayer implements Player {

    private static final Gson GSON = new Gson();
    private static final GsonComponentSerializer GSON_COMPONENT =
            GsonComponentSerializer.gson();

    private final String username;
    private final UUID uuid;
    private final InetSocketAddress address;
    private final Channel channel;

    public VelocityPlayer(String username, UUID uuid,
                          InetSocketAddress address, Channel channel) {
        this.username = username;
        this.uuid = uuid;
        this.address = address;
        this.channel = channel;
    }

    @Override public String getUsername() { return username; }
    @Override public UUID getUniqueId() { return uuid; }
    @Override public InetSocketAddress getRemoteAddress() { return address; }
    @Override public boolean isActive() {
        return channel != null && channel.isActive();
    }

    @Override
    public void disconnect(Component reason) {
        if (channel != null && channel.isActive()) channel.close();
    }

    @Override
    public void sendMessage(Component message) {
        sendChatPacket(message, false);
    }

    @Override
    public void sendActionBar(Component message) {
        sendChatPacket(message, true);
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    public Channel channel() { return channel; }

    private void sendChatPacket(Component message, boolean overlay) {
        if (channel == null || !channel.isActive()) return;

        Integer protocolObj = channel.attr(HandshakeHandler.PROTOCOL_VERSION).get();
        int protocol = protocolObj != null ? protocolObj : 777;

        try {
            int packetId = systemChatPacketId(protocol);
            ByteBuf out = channel.alloc().buffer();
            VarInts.writeVarInt(out, packetId);

            String json = GSON_COMPONENT.serialize(message);

            if (protocol >= 764) {
                // 1.20.2+ uses Network NBT.
                // We construct the NBT payload and write it with the tag type byte (0x0A).
                CompoundBinaryTag compound = jsonToNbtCompound(json);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                BinaryTagIO.writer().write(compound, (DataOutput) dos);
                dos.flush();
                byte[] fullNbt = baos.toByteArray();

                // fullNbt is: [0x0A] [0x00 0x00] [Payload...]
                // Network NBT requires: [0x0A] [Payload...] (no name length)
                out.writeByte(0x0A); // Tag Type: Compound
                if (fullNbt.length > 3) {
                    out.writeBytes(fullNbt, 3, fullNbt.length - 3);
                }
            } else {
                VarInts.writeString(out, json);
            }

            out.writeBoolean(overlay);
            channel.writeAndFlush(out);
        } catch (Throwable t) {
            System.err.println("[MineAlphaProxy] Failed to send chat: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static int systemChatPacketId(int protocol) {
        if (protocol >= 777) return 0x7C;  // 26.3
        if (protocol >= 776) return 0x79;  // 26.1 - 26.2
        if (protocol >= 773) return 0x77;  // 1.21.9 - 1.21.11
        if (protocol >= 770) return 0x72;  // 1.21.5 - 1.21.8
        if (protocol >= 768) return 0x73;  // 1.21.2 - 1.21.4
        if (protocol >= 767) return 0x6C;  // 1.21 - 1.21.1
        if (protocol >= 765) return 0x69;  // 1.20.3 - 1.20.6
        if (protocol >= 764) return 0x67;  // 1.20.2
        if (protocol >= 761) return 0x64;  // 1.19.4
        if (protocol >= 759) return 0x60;  // 1.19.3
        if (protocol >= 758) return 0x62;  // 1.19.1 - 1.19.2
        if (protocol >= 755) return 0x5F;  // 1.19
        return 0x0F;
    }

    private static CompoundBinaryTag jsonToNbtCompound(String json) {
        JsonElement el = GSON.fromJson(json, JsonElement.class);
        if (el.isJsonObject()) {
            return jsonObjectToCompound(el.getAsJsonObject());
        }
        return CompoundBinaryTag.builder()
                .put("text", StringBinaryTag.stringBinaryTag(el.getAsString()))
                .build();
    }

    private static CompoundBinaryTag jsonObjectToCompound(JsonObject obj) {
        CompoundBinaryTag.Builder b = CompoundBinaryTag.builder();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            b.put(e.getKey(), jsonToTag(e.getValue()));
        }
        return b.build();
    }

    private static BinaryTag jsonToTag(JsonElement el) {
        if (el.isJsonObject()) {
            return jsonObjectToCompound(el.getAsJsonObject());
        }
        if (el.isJsonArray()) {
            ListBinaryTag.Builder<CompoundBinaryTag> b =
                    ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
            for (JsonElement child : el.getAsJsonArray()) {
                if (child.isJsonObject()) {
                    b.add(jsonObjectToCompound(child.getAsJsonObject()));
                } else {
                    b.add(CompoundBinaryTag.builder()
                            .put("text", StringBinaryTag.stringBinaryTag(
                                    child.getAsString()))
                            .build());
                }
            }
            return b.build();
        }
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isString()) return StringBinaryTag.stringBinaryTag(p.getAsString());
            if (p.isBoolean()) return ByteBinaryTag.byteBinaryTag(
                    (byte) (p.getAsBoolean() ? 1 : 0));
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)
                        && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return IntBinaryTag.intBinaryTag((int) d);
                }
                return DoubleBinaryTag.doubleBinaryTag(d);
            }
        }
        return StringBinaryTag.stringBinaryTag("");
    }
}