/*
 * gyro is a third-party mod for Minecraft Java Edition that abuses
 * the newly introduced waypoint system to get player positions.
 *
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

import com.google.errorprone.annotations.DoNotCall;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
 * @see Gyro
 * @see GyroData
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
     * A "not found" component for {@link #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)} without dimming.
     *
     * @see #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)
     */
    @Unique
    private static final Component GYRO_NULL_PLAYER = Component.translatableWithFallback("gyro.null.player", "<not found>").withStyle(ChatFormatting.YELLOW);

    /**
     * A "not found" component for {@link #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)} with dimming.
     *
     * @see #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)
     */
    @Unique
    private static final Component GYRO_NULL_PLAYER_DIM = Component.translatableWithFallback("gyro.null.player", "<not found>").withStyle(ChatFormatting.DARK_GRAY);

    /**
     * A "not found" component for {@link #gyro_waypointEntityInfo(TrackedWaypoint, boolean)} without dimming.
     *
     * @see #gyro_waypointEntityInfo(TrackedWaypoint, boolean)
     */
    @Unique
    private static final Component GYRO_NULL_ENTITY = Component.translatableWithFallback("gyro.null.entity", "<not found>").withStyle(ChatFormatting.YELLOW);

    /**
     * A "not found" component for {@link #gyro_waypointEntityInfo(TrackedWaypoint, boolean)} with dimming.
     *
     * @see #gyro_waypointEntityInfo(TrackedWaypoint, boolean)
     */
    @Unique
    private static final Component GYRO_NULL_ENTITY_DIM = Component.translatableWithFallback("gyro.null.entity", "<not found>").withStyle(ChatFormatting.DARK_GRAY);

    /**
     * Player lookup map used in {@link #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)}.
     */
    @Shadow
    @Final
    private final Map<UUID, PlayerInfo> playerInfoMap;

    /**
     * Current level, {@code null} if none.
     */
    @Shadow
    @Nullable
    private ClientLevel level;

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
        throw new AssertionError("gyro: No instances.");
    }

    /**
     * Handles the waypoint data and uses its data to extract the position.
     *
     * @param packet Packet to extract the data from
     * @param ci     Callback data, ignored
     * @apiNote Do not call, called by Mixin
     * @see GyroData
     * @see #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)
     */
    @DoNotCall("Called by Mixin")
    @Inject(method = "handleWaypoint", at = @At("RETURN"))
    private void gyro_handleWaypoint_return(ClientboundTrackedWaypointPacket packet, CallbackInfo ci) {
        // Validate.
        assert packet != null : "gyro: Parameter 'packet' is null. (listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Receiving waypoint NOT on the main thread. (thread: " + Thread.currentThread() + ", packet: " + packet + ", listener: " + this + ')';

        // Log. (**TRACE**)
        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Received a waypoint. (packet: {}, listener: {})", packet, this);

        // Get and push the profiler.
        ProfilerFiller profiler = Profiler.get();
        profiler.push("gyro:handle_waypoint_packet");

        // Extract the data.
        TrackedWaypoint way = packet.waypoint(); // Implicit NPE for 'packet'

        // Depend the action on the waypoint type.
        switch (way) {
            // Vector waypoints basically contain the whole position. Yummy!
            case TrackedWaypoint.Vec3iWaypoint vec -> this.gyro_handleVectorWaypoint(vec);

            // Chunk waypoint contain the chunk middle position. We'll use the chunk center, no better alternative.
            case TrackedWaypoint.ChunkWaypoint chunk -> this.gyro_handleChunkWaypoint(chunk);

            // This is the azimuth/yaw/yRot waypoint. We assume that player stands still and calculate the position.
            case TrackedWaypoint.AzimuthWaypoint azimuth -> this.gyro_handleAzimuthWaypoint(azimuth);

            // This is the other type of waypoint, we should remove everything.
            default -> this.gyro_handleRemovedWaypoint(way);
        }

        // Pop the profiler.
        profiler.pop();
    }

    /**
     * Handles the vector waypoint.
     *
     * @param way Target waypoint
     * @see #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)
     * @see #gyro_handleChunkWaypoint(TrackedWaypoint.ChunkWaypoint)
     * @see #gyro_handleAzimuthWaypoint(TrackedWaypoint.AzimuthWaypoint)
     * @see #gyro_handleRemovedWaypoint(TrackedWaypoint)
     */
    @Unique
    private void gyro_handleVectorWaypoint(TrackedWaypoint.Vec3iWaypoint way) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Handling vector waypoint NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", listener: " + this + ')';

        // Log. (**TRACE**)
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Vector waypoint received, adding... (way: {}, id: {}, listener: {})", way, id, this);
        }

        // Clear the angles data.
        Gyro.ANGLES.remove(id);

        // Send the status message.
        Vec3i vector = ((Vec3iWaypointAccessor) way).gyro_vector();
        int vx = vector.getX();
        int vy = vector.getY();
        int vz = vector.getZ();
        Component info = this.gyro_waypointInfo(way, /*dim=*/true);
        Component player = this.gyro_waypointPlayerInfo(way, /*dim=*/true);
        Component entity = this.gyro_waypointEntityInfo(way, /*dim=*/true);
        Component x = Component.literal(Integer.toString(vx)).withStyle(ChatFormatting.ITALIC);
        Component y = Component.literal(Integer.toString(vy)).withStyle(ChatFormatting.ITALIC);
        Component z = Component.literal(Integer.toString(vz)).withStyle(ChatFormatting.ITALIC);
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.status.vector",
                        "Received vector waypoint %s (player: %s, entity: %s) with position %s / %s / %s.", info, player, entity, x, y, z)
                .withStyle(ChatFormatting.GRAY));

        // Send the tracked message.
        this.gyro_updateTrackedPosition(way, vx, vz, "vector");

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Added Vector waypoint. (way: {}, id: {}, vector: {}, info: {}, player: {}, entity: {}, listener: {})", way, id, vector, info, player, entity, this);
    }

    /**
     * Handles the chunk waypoint.
     *
     * @param way Target waypoint
     * @see #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)
     * @see #gyro_handleVectorWaypoint(TrackedWaypoint.Vec3iWaypoint)
     * @see #gyro_handleAzimuthWaypoint(TrackedWaypoint.AzimuthWaypoint)
     * @see #gyro_handleRemovedWaypoint(TrackedWaypoint)
     */
    @Unique
    private void gyro_handleChunkWaypoint(TrackedWaypoint.ChunkWaypoint way) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Handling chunk waypoint NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", listener: " + this + ')';

        // Log. (**TRACE**)
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Chunk waypoint received, adding... (way: {}, id: {}, listener: {})", way, id, this);
        }

        // Clear the angles data.
        Gyro.ANGLES.remove(id);

        // Send the status message.
        ChunkPos pos = ((ChunkWaypointAccessor) way).gyro_chunkPos();
        int mx = pos.getMiddleBlockX();
        int mz = pos.getMiddleBlockZ();
        Component info = this.gyro_waypointInfo(way, /*dim=*/true);
        Component player = this.gyro_waypointPlayerInfo(way, /*dim=*/true);
        Component entity = this.gyro_waypointEntityInfo(way, /*dim=*/true);
        Component x = Component.literal(Integer.toString(mx)).withStyle(ChatFormatting.ITALIC);
        Component z = Component.literal(Integer.toString(mz)).withStyle(ChatFormatting.ITALIC);
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.status.chunk",
                        "Received chunk waypoint %s (player: %s, entity: %s) with location %s / %s.", info, player, entity, x, z)
                .withStyle(ChatFormatting.GRAY));

        // Send the tracked message.
        this.gyro_updateTrackedPosition(way, mx, mz, "chunk");

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Added Chunk waypoint. (way: {}, id: {}, pos: {}, mx: {}, mz: {}, info: {}, player: {}, entity: {}, listener: {})", way, id, pos, mx, mz, info, player, entity, this);
    }

    /**
     * Handles the azimuth waypoint.
     *
     * @param way Target waypoint
     * @see #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)
     * @see #gyro_handleVectorWaypoint(TrackedWaypoint.Vec3iWaypoint)
     * @see #gyro_handleChunkWaypoint(TrackedWaypoint.ChunkWaypoint)
     * @see #gyro_handleRemovedWaypoint(TrackedWaypoint)
     */
    @Unique
    private void gyro_handleAzimuthWaypoint(TrackedWaypoint.AzimuthWaypoint way) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Handling azimuth waypoint NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", listener: " + this + ')';

        // Log. (**TRACE**)
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Azimuth waypoint received, calculating... (way: {}, id: {}, listener: {})", way, id, this);
        }

        // Send the status message.
        float curYaw = ((AzimuthWaypointAccessor) way).gyro_angle();
        Component info = this.gyro_waypointInfo(way, /*dim=*/true);
        Component player = this.gyro_waypointPlayerInfo(way, /*dim=*/true);
        Component entity = this.gyro_waypointEntityInfo(way, /*dim=*/true);
        Component angle = Component.literal("%.1f".formatted(Math.toDegrees(curYaw))).withStyle(ChatFormatting.ITALIC);
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.status.azimuth",
                        "Received azimuth waypoint %s (player: %s, entity: %s) with angle %s.", info, player, entity, angle)
                .withStyle(ChatFormatting.GRAY));

        // Get the player.
        Entity camera = this.minecraft.cameraEntity;
        if (camera == null) {
            // Log, stop. (**DEBUG**)
            if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
            GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, no camera found. (way: {}, id: {}, info: {}, player: {}, entity: {}, angle: {}, listener: {})", way, id, info, player, entity, angle, this);
            return;
        }

        // Capture the current state.
        double curX = camera.getX();
        double curZ = camera.getZ();
        GyroData curData = new GyroData(curYaw, curX, curZ);

        // Get the last data, skip if no last data or equal to current data.
        GyroData lastData = Gyro.ANGLES.put(id, curData);
        if ((lastData == null) || lastData.equals(curData)) {
            // Log, stop. (**DEBUG**)
            if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
            GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, last data is the same or missing. (way: {}, id: {}, info: {}, player: {}, entity: {}, angle: {}, curData: {}, lastData: {}, listener: {})", way, id, info, player, entity, angle, curData, lastData, this);
            return;
        }
        float lastYaw = lastData.angle();
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
            if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
            GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, the calculated position is unrealistic. (way: {}, id: {}, info: {}, player: {}, entity: {}, angle: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, listener: {})", way, id, info, player, entity, angle, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, this);
            return;
        }

        // Calculate the Euclidean distance and ignore if it's too close.
        // This is a hack for moving player that forces THEM to recalculate OUR position.
        double diffX = (curX - x);
        double diffZ = (curZ - z);
        double distSqr = ((diffX * diffX) + (diffZ * diffZ));
        if (distSqr <= (8 * 8)) {
            // Log, stop. (**DEBUG**)
            if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
            GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Skipping adding Azimuth waypoint, the calculated position is too close. (way: {}, id: {}, info: {}, player: {}, entity: {}, angle: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, diffX: {}, diffZ: {}, distSqr: {}, listener: {})", way, id, info, player, entity, angle, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, diffX, diffZ, distSqr, this);
            return;
        }

        // Send the tracked message.
        this.gyro_updateTrackedPosition(way, x, z, "azimuth");

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Calculated and added Azimuth waypoint. (way: {}, id: {}, info: {}, player: {}, entity: {}, angle: {}, curData: {}, lastData: {}, curInvTan: {}, lastInvTan: {}, curCross: {}, lastCross: {}, x: {}, z: {}, diffX: {}, diffZ: {}, distSqr: {}, listener: {})", way, id, info, player, entity, angle, curData, lastData, curInvTan, lastInvTan, curCross, lastCross, x, z, diffX, diffZ, distSqr, this);
    }

    /**
     * Handles the removed waypoint.
     *
     * @param way Target waypoint
     * @see #gyro_updateTrackedPosition(TrackedWaypoint, double, double, String)
     * @see #gyro_handleVectorWaypoint(TrackedWaypoint.Vec3iWaypoint)
     * @see #gyro_handleChunkWaypoint(TrackedWaypoint.ChunkWaypoint)
     * @see #gyro_handleAzimuthWaypoint(TrackedWaypoint.AzimuthWaypoint)
     */
    @Unique
    private void gyro_handleRemovedWaypoint(TrackedWaypoint way) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Handling removed waypoint NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", listener: " + this + ')';

        // Log. (**TRACE**)
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        if (GYRO_LOGGER.isTraceEnabled(Gyro.GYRO_MARKER)) {
            GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Unknown (empty?) waypoint received, removing... (way: {}, id: {}, listener: {})", way, id, this);
        }

        // Remove.
        Gyro.ANGLES.remove(id);
        Gyro.RENDER_POSES.remove(id);

        // Send the status message.
        Component info = this.gyro_waypointInfo(way, /*dim=*/true);
        Component player = this.gyro_waypointPlayerInfo(way, /*dim=*/true);
        Component entity = this.gyro_waypointEntityInfo(way, /*dim=*/true);
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.status.null",
                        "Deleted waypoint %s (player: %s, entity: %s).", info, player, entity)
                .withStyle(ChatFormatting.GRAY));

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Removed unknown waypoint. (way: {}, id: {}, info: {}, player: {}, entity: {}, listener: {})", way, id, info, player, entity, this);
    }

    /**
     * Updates the tracked positions and sends the message to the GUI.
     *
     * @param way  Updated waypoint
     * @param x    An alleged waypoint X position
     * @param z    An alleged waypoint Z position
     * @param type Waypoint update type to show to player
     * @see #gyro_waypointInfo(TrackedWaypoint, boolean)
     * @see #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)
     * @see #gyro_waypointEntityInfo(TrackedWaypoint, boolean)
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
        GyroRender render = new GyroRender(x, z, color);
        Gyro.RENDER_POSES.put(id, render);

        // Send the message.
        Component info = this.gyro_waypointInfo(way, /*dim=*/false);
        Component player = this.gyro_waypointPlayerInfo(way, /*dim=*/false);
        Component entity = this.gyro_waypointEntityInfo(way, /*dim=*/false);
        this.minecraft.gui.getChat().addMessage(Component.translatableWithFallback("gyro.found",
                "Waypoint %s (player: %s, entity: %s) found at %s / %s via %s.",
                info, player, entity,
                Component.literal("%.1f".formatted(x)).withStyle(ChatFormatting.GREEN),
                Component.literal("%.1f".formatted(z)).withStyle(ChatFormatting.GREEN),
                Component.translatableWithFallback("gyro.found." + type, type).withStyle(ChatFormatting.GREEN)));

        // Log. (**DEBUG**)
        if (!GYRO_LOGGER.isDebugEnabled(Gyro.GYRO_MARKER)) return;
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Updated tracker position. (way: {}, x: {}, z: {}, type: {}, id: {}, color: {}, render: {}, info: {}, player: {}, entity: {}, listener: {})", way, x, z, type, id, color, render, info, player, entity, this);
    }

    /**
     * Create the component from the waypoint info.
     *
     * @param way Target waypoint
     * @param dim Whether the styling should be dim
     * @return A new waypoint component
     * @see #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)
     * @see #gyro_waypointEntityInfo(TrackedWaypoint, boolean)
     */
    @Contract(pure = true)
    @Unique
    private Component gyro_waypointInfo(TrackedWaypoint way, boolean dim) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (dim: " + dim + ", listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Getting waypoint info NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", dim: " + dim + ", listener: " + this + ')';

        // Return.
        Either<UUID, String> id = way.id(); // Implicit NPE for 'way'
        return Component.literal(id.right().orElseGet(() -> id.orThrow().toString())).withStyle(dim ? ChatFormatting.ITALIC : ChatFormatting.GREEN);
    }

    /**
     * Gets the player info by a waypoint, if available.
     *
     * @param way Target waypoint
     * @param dim Whether the styling should be dim
     * @return Player username component or a "not found" component
     * @see #gyro_waypointInfo(TrackedWaypoint, boolean)
     * @see #gyro_waypointEntityInfo(TrackedWaypoint, boolean)
     * @see #GYRO_NULL_PLAYER
     * @see #GYRO_NULL_PLAYER_DIM
     */
    @Contract(pure = true)
    @Unique
    private Component gyro_waypointPlayerInfo(TrackedWaypoint way, boolean dim) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (dim: " + dim + ", listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Getting waypoint player info NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", dim: " + dim + ", listener: " + this + ')';

        // Return.
        return way.id().left() // Implicit NPE for 'way'
                .map(this.playerInfoMap::get)
                .map(info -> (Component) Component.literal(info.getProfile().getName()).withStyle(dim ? ChatFormatting.ITALIC : ChatFormatting.GREEN))
                .orElse(dim ? GYRO_NULL_PLAYER_DIM : GYRO_NULL_PLAYER);
    }

    /**
     * Gets the entity info by a waypoint, if available.
     *
     * @param way Target waypoint
     * @param dim Whether the styling should be dim
     * @return Entity display name component or a "not found" component
     * @see #gyro_waypointInfo(TrackedWaypoint, boolean)
     * @see #gyro_waypointPlayerInfo(TrackedWaypoint, boolean)
     * @see #GYRO_NULL_ENTITY
     * @see #GYRO_NULL_ENTITY_DIM
     */
    @Contract(pure = true)
    @Unique
    private Component gyro_waypointEntityInfo(TrackedWaypoint way, boolean dim) {
        // Validate.
        assert way != null : "gyro: Parameter 'way' is null. (dim: " + dim + ", listener: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Getting waypoint entity info NOT from the main thread. (thread: " + Thread.currentThread() + ", way: " + way + ", dim: " + dim + ", listener: " + this + ')';

        // Return.
        ClientLevel level = this.level;
        return way.id().left() // Implicit NPE for 'way'
                .map(uuid -> (level != null) ? level.getEntity(uuid) : null)
                .map(Entity::getDisplayName)
                .map(name -> (Component) name.copy().withStyle(dim ? ChatFormatting.ITALIC : ChatFormatting.GREEN))
                .orElse(dim ? GYRO_NULL_ENTITY_DIM : GYRO_NULL_ENTITY);
    }
}
