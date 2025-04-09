/*
 * MIT License
 *
 * Copyright (c) 2025 VidTu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * SPDX-License-Identifier: MIT
 */

package ru.vidtu.gyro.mixins;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vidtu.gyro.Gyro;
import ru.vidtu.gyro.GyroData;
import ru.vidtu.gyro.GyroRender;
import ru.vidtu.gyro.mixins.access.AzimuthWaypointAccessor;
import ru.vidtu.gyro.mixins.access.ChunkWaypointAccessor;
import ru.vidtu.gyro.mixins.access.Vec3iWaypointAccessor;

import java.util.Map;
import java.util.UUID;

/**
 * A mixin for {@link ClientPacketListenerMixin} which intercepts incoming waypoints and leaks/calculates their positions.
 *
 * @author VidTu
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(ClientPacketListener.class)
@NullMarked
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    /**
     * Player lookup map used in {@link #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)}.
     */
    @Shadow
    @Final
    private Map<UUID, PlayerInfo> playerInfoMap;

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     * @deprecated Always throws
     */
    @Deprecated(forRemoval = true)
    @Contract(value = "-> fail", pure = true)
    public ClientPacketListenerMixin() {
        super(null, null, null);
        throw new AssertionError("No instances.");
    }

    /**
     * Handles the waypoint data and uses its data to extract the position.
     *
     * @param packet Packet to extract the data from
     * @param ci     Callback data
     */
    @Inject(method = "handleWaypoint", at = @At("RETURN"))
    public void gyro_handleWaypoint_return(ClientboundTrackedWaypointPacket packet, CallbackInfo ci) {
        // Schedule it on the minecraft thread to avoid threading issues. Don't use ensureRunningOnSameThread to avoid compat issues.
        this.minecraft.execute(() -> {
            // Extract the data.
            TrackedWaypoint way = packet.waypoint();
            Either<UUID, String> id = way.id();

            // Depend the action on the waypoint type.
            switch (way) {
                // Vector waypoints basically contain the whole position. Yummy!
                case TrackedWaypoint.Vec3iWaypoint vec -> {
                    // Clear the angles data.
                    Gyro.ANGLES.remove(id);

                    // Extract the position and send the message.
                    Vec3i vector = ((Vec3iWaypointAccessor) vec).gyro_vector();
                    gyro_updateTrackedPosition(way, vector.getX() + 0.5D, vector.getZ() + 0.5D, "vec3i");
                }

                // Chunk waypoint contain the chunk middle position. We'll use the chunk center, no better alternative.
                case TrackedWaypoint.ChunkWaypoint chunk -> {
                    // Clear the angles data.
                    Gyro.ANGLES.remove(id);

                    // Extract the position and send the message.
                    ChunkPos pos = ((ChunkWaypointAccessor) chunk).gyro_chunkPos();
                    gyro_updateTrackedPosition(way, pos.getMiddleBlockX() + 0.5D, pos.getMiddleBlockZ() + 0.5D, "chunk");
                }

                // This is the azimuth/yaw/yRot waypoint. We assume that player stands still and calculate the position.
                case TrackedWaypoint.AzimuthWaypoint azimuth -> {
                    // Capture the current state.
                    LocalPlayer player = this.minecraft.player;
                    if (player == null) break;
                    float curYaw = ((AzimuthWaypointAccessor) azimuth).gyro_angle();
                    double curX = player.getX();
                    double curZ = player.getZ();
                    GyroData curData = new GyroData(curYaw, curX, curZ);

                    // Get the last data, skip if no last data or equal to current data.
                    GyroData lastData = Gyro.ANGLES.putIfAbsent(id, curData);
                    if (lastData == null || lastData.equals(curData)) break;
                    double lastYaw = lastData.angle();
                    double lastX = lastData.x();
                    double lastZ = lastData.z();

                    // Calculate the tangents to the player.
                    double curInvTan = (-1.0D / Math.tan(curYaw));
                    double lastInvTan = (-1.0D / Math.tan(lastYaw));
                    double curCross = (curZ - (curX * curInvTan));
                    double lastCross = (lastZ - (lastX * lastInvTan));

                    // Calculate the position based on tangents cross-point.
                    double x = ((lastCross - curCross) / (curInvTan - lastInvTan));
                    double z = ((x * curInvTan) + curCross);

                    // If the position is unrealistic, the other player is probably moving.
                    if (x <= -Level.MAX_LEVEL_SIZE || z <= -Level.MAX_LEVEL_SIZE ||
                            x >= Level.MAX_LEVEL_SIZE || z >= Level.MAX_LEVEL_SIZE) break;

                    // Calculate the Euclidean distance and ignore if it's too close.
                    // This is a hack for moving player that forces THEM to recalculate OUR position.
                    double diffX = (curX - x);
                    double diffZ = (curZ - z);
                    double distSqr = ((diffX * diffX) + (diffZ * diffZ));
                    if (distSqr < 64) break;

                    // Send the message.
                    gyro_updateTrackedPosition(way, x, z, "azimuth");
                }

                // This is the other type of waypoint, we should remove everything.
                default -> {
                    Gyro.ANGLES.remove(id);
                    Gyro.RENDER_POSES.remove(id);
                }
            }
        });
    }

    /**
     * Updates the tracked positions and sends the message to the GUI.
     *
     * @param way  Updated waypoint
     * @param x    Alleged waypoint X position
     * @param z    Alleged waypoint Z position
     * @param type Waypoint update type to show to player
     */
    @Unique
    private void gyro_updateTrackedPosition(TrackedWaypoint way, double x, double z, String type) {
        // Calculate the color and put into rendering positions.
        Either<UUID, String> id = way.id();
        int color = way.icon().color.orElseGet(() -> id.map(
                uid -> ARGB.setBrightness(uid.hashCode(), 0.9F),
                name -> ARGB.setBrightness(name.hashCode(), 0.9F)
        ));
        Gyro.RENDER_POSES.put(id, new GyroRender(x, z, color));

        // Send the message.
        Gui gui = minecraft.gui;
        String eitherToString = id.right().orElseGet(() -> id.orThrow().toString());
        String name = id.left()
                .map(this.playerInfoMap::get)
                .map(PlayerInfo::getProfile)
                .map(GameProfile::getName)
                .orElse("<no player found>");
        gui.getChat().addMessage(Component.literal("Entity ")
                .append(Component.literal(eitherToString).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" assigned to player "))
                .append(Component.literal(name).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" found at "))
                .append(Component.literal(String.format("%.1f", x)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" / "))
                .append(Component.literal(String.format("%.1f", z)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" via "))
                .append(Component.literal(type).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(".")));
    }
}
