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

import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @apiNote Internal use only
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(ClientPacketListener.class)
@NullMarked
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    /**
     * Logger for this class.
     */
    @Unique
    private static final Logger GYRO_LOGGER = LoggerFactory.getLogger("gyro/ClientPacketListenerMixin");

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
    @SuppressWarnings("DataFlowIssue") // <- Inaccessible constructor.
    @Deprecated(forRemoval = true)
    @Contract(value = "-> fail", pure = true)
    private ClientPacketListenerMixin() {
        super(null, null, null);
        throw new AssertionError("No instances.");
    }

    /**
     * Handles the waypoint data and uses its data to extract the position.
     *
     * @param packet Packet to extract the data from
     * @param ci     Callback data, ignored
     */
    @Inject(method = "handleWaypoint", at = @At("RETURN"))
    private void gyro_handleWaypoint_return(ClientboundTrackedWaypointPacket packet, CallbackInfo ci) {
        // Validate.
        assert packet != null : "gyro: Parameter 'packet' is null. (listener: " + this + ')';

        // Log. (**TRACE**)
        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Received waypoint, delegating to game thread. (packet: {}, listener: {})", packet, this);

        // Schedule it on the minecraft thread to avoid threading issues. Don't use ensureRunningOnSameThread to avoid compat issues.
        this.minecraft.execute(() -> {
            // Get and push the profiler.
            ProfilerFiller profiler = Profiler.get();
            profiler.push("gyro:handle_waypoint_packet");

            // Log. (**TRACE**)
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Got waypoint on the game thread. (packet: {}, listener: {})", packet, this);

            // Extract the data.
            TrackedWaypoint way = packet.waypoint(); // Implicit NPE for 'packet'
            Either<UUID, String> id = way.id();

            // Depend the action on the waypoint type.
            switch (way) {
                // Vector waypoints basically contain the whole position. Yummy!
                case TrackedWaypoint.Vec3iWaypoint vec -> {
                    // Log. (**TRACE**)
                    if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
                        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Vec3i waypoint received, adding... (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                    }

                    // Clear the angles data.
                    Gyro.ANGLES.remove(id);

                    // Extract the position and send the message.
                    Vec3i vector = ((Vec3iWaypointAccessor) vec).gyro_vector();
                    this.gyro_updateTrackedPosition(way, vector.getX(), vector.getZ(), "vector");

                    // Log. (**DEBUG**)
                    if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                    GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Added Vec3i waypoint. (packet: {}, way: {}, id: {}, vector: {}, listener: {})", packet, way, id, vector, this);
                }

                // Chunk waypoint contain the chunk middle position. We'll use the chunk center, no better alternative.
                case TrackedWaypoint.ChunkWaypoint chunk -> {
                    // Log. (**TRACE**)
                    if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
                        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Chunk waypoint received, adding... (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                    }

                    // Clear the angles data.
                    Gyro.ANGLES.remove(id);

                    // Extract the position and send the message.
                    ChunkPos pos = ((ChunkWaypointAccessor) chunk).gyro_chunkPos();
                    this.gyro_updateTrackedPosition(way, pos.getMiddleBlockX(), pos.getMiddleBlockZ(), "chunk");

                    // Log. (**DEBUG**)
                    if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                    GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Added Chunk waypoint. (packet: {}, way: {}, id: {}, pos: {}, listener: {})", packet, way, id, pos, this);
                }

                // This is the azimuth/yaw/yRot waypoint. We assume that player stands still and calculate the position.
                case TrackedWaypoint.AzimuthWaypoint azimuth -> {
                    // Log. (**TRACE**)
                    if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
                        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Azimuth waypoint received, calculating... (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                    }

                    // Get the player.
                    LocalPlayer player = this.minecraft.player;
                    if (player == null) {
                        // Log, stop. (**DEBUG**)
                        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, no player found. (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                        break;
                    }

                    // Capture the current state.
                    float curYaw = ((AzimuthWaypointAccessor) azimuth).gyro_angle();
                    double curX = player.getX();
                    double curZ = player.getZ();
                    GyroData curData = new GyroData(curYaw, curX, curZ);

                    // Get the last data, skip if no last data or equal to current data.
                    GyroData lastData = Gyro.ANGLES.put(id, curData);
                    if ((lastData == null) || lastData.equals(curData)) {
                        // Log, stop. (**DEBUG**)
                        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, last data is the same or missing. (packet: {}, way: {}, id: {}, curData: {}, lastData: {}, listener: {})", packet, way, id, curData, lastData, this);
                        break;
                    }
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
                    if ((x <= -Level.MAX_LEVEL_SIZE) || (z <= -Level.MAX_LEVEL_SIZE) ||
                            (x >= Level.MAX_LEVEL_SIZE) || (z >= Level.MAX_LEVEL_SIZE)) {
                        // Log, stop. (**DEBUG**)
                        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, the calculated position is unrealistic. (packet: {}, way: {}, id: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, listener: {})", packet, way, id, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, this);
                        break;
                    }

                    // Calculate the Euclidean distance and ignore if it's too close.
                    // This is a hack for moving player that forces THEM to recalculate OUR position.
                    double diffX = (curX - x);
                    double diffZ = (curZ - z);
                    double distSqr = ((diffX * diffX) + (diffZ * diffZ));
                    if (distSqr <= 64) {
                        // Log, stop. (**DEBUG**)
                        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, the calculated position is too close. (packet: {}, way: {}, id: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, diffX: {}, diffZ: {}, distSqr: {}, listener: {})", packet, way, id, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, diffX, diffZ, distSqr, this);
                        break;
                    }

                    // Send the message.
                    this.gyro_updateTrackedPosition(way, x, z, "azimuth");

                    // Log. (**DEBUG**)
                    if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                    GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Calculated and added Azimuth waypoint. (packet: {}, way: {}, id: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, diffX: {}, diffZ: {}, distSqr: {}, listener: {})", packet, way, id, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, diffX, diffZ, distSqr, this);
                }

                // This is the other type of waypoint, we should remove everything.
                default -> {
                    // Log. (**TRACE**)
                    if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
                        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Unknown (empty?) waypoint received, removing... (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                    }

                    // Remove.
                    Gyro.ANGLES.remove(id);
                    Gyro.RENDER_POSES.remove(id);

                    // Log. (**DEBUG**)
                    if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) break;
                    GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Removed unknown waypoint. (packet: {}, way: {}, id: {}, listener: {})", packet, way, id, this);
                }
            }

            // Pop the profiler.
            profiler.pop();
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
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (x: " + x + ", z: " + z + ", type: " + type + ", listener: " + this + ')';
        assert (x >= -Level.MAX_LEVEL_SIZE) && (x <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'x' is not in the [-30_000_000..30_000_000] range. (way: " + way + ", x: " + x + ", z: " + z + ", type: " + type + ", listener: " + this + ')';
        assert (z >= -Level.MAX_LEVEL_SIZE) && (z <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'z' is not in the [-30_000_000..30_000_000] range. (way: " + way + ", x: " + x + ", z: " + z + ", type: " + type + ", listener: " + this + ')';
        assert type != null : "gyro: Parameter 'type' is null. (way: " + way + ", x: " + x + ", z: " + z + ", listener: " + this + ')';
        assert !type.isBlank() : "gyro: Invalid type. (way: " + way + ", x: " + x + ", z: " + z + ", type: " + type + ", listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Updating tracked position NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", x: " + x + ", z: " + z + ", type: " + type + ", listener: " + this + ')';

        // Log. (**TRACE**)
        if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Updating tracked position... (way: {}, x: {}, z: {}, type: {}, listener: {})", way, x, z, type, this);
        }

        // Calculate the color and put into rendering positions.
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        int color = way.icon().color.orElseGet(() -> id.map(
                uid -> ARGB.setBrightness(uid.hashCode(), 0.9F),
                name -> ARGB.setBrightness(name.hashCode(), 0.9F)
        ));
        Gyro.RENDER_POSES.put(id, new GyroRender(x, z, color));

        // Send the message.
        String either = id.right().orElseGet(() -> id.orThrow().toString());
        Component name = id.left()
                .map(this.playerInfoMap::get)
                .map(info -> Component.literal(info.getProfile().getName()).withStyle(ChatFormatting.GREEN))
                .orElseGet(() -> Component.translatableWithFallback("gyro.nonplayer", "<no player found>").withStyle(ChatFormatting.YELLOW));
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.found",
                "Entity %s (assigned to player %s) found at %s / %s via %s.",
                Component.literal(either).withStyle(ChatFormatting.GREEN),
                name,
                Component.literal("%.1f".formatted(x)).withStyle(ChatFormatting.GREEN),
                Component.literal("%.1f".formatted(z)).withStyle(ChatFormatting.GREEN),
                Component.translatableWithFallback("gyro." + type, type).withStyle(ChatFormatting.GREEN)));

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Updated tracker position. (way: {}, x: {}, z: {}, type: {}, id: {}, color: {}, either: {}, name: {}, listener: {})", way, x, z, type, id, color, either, name, this);
    }
}
