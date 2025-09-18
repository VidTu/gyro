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

import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.DoNotCall;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vidtu.gyro.Gyro;
import ru.vidtu.gyro.GyroRender;
import ru.vidtu.gyro.mixins.access.GameRendererAccessor;

import java.util.Collection;

/**
 * Mixin that renders beacon beams on {@link Gyro#RENDER_POSES}.
 *
 * @author VidTu
 * @apiNote Internal use only
 * @see Gyro
 * @see GyroRender
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(LevelRenderer.class)
@NullMarked
public final class LevelRendererMixin {
    /**
     * Maximum distance to the beacon beam in blocks,
     * after which its apparent size will be fixed.
     * <p>
     * Equals to {@code 48} blocks.
     *
     * @see BeaconRenderer
     * @see #gyro_submitBlockEntities_return(PoseStack, LevelRenderState, SubmitNodeStorage, CallbackInfo)
     */
    @Unique
    @CompileTimeConstant
    private static final double GYRO_BEACON_BEAM_SCALE_THRESHOLD = 48.0D;

    /**
     * Time of one beacon beam full (360 degree) rotation in ticks.
     * <p>
     * Equals to {@code 40} ticks.
     *
     * @see BeaconRenderer#submitBeaconBeam(PoseStack, SubmitNodeCollector, ResourceLocation, float, float, int, int, int, float, float)
     * @see #gyro_submitBlockEntities_return(PoseStack,  LevelRenderState, SubmitNodeStorage, CallbackInfo)
     */
    @Unique
    @CompileTimeConstant
    private static final int GYRO_BEACON_BEAM_FULL_ROTATION_TICKS = 40;

    /**
     * Game instance. Used for thread checking.
     *
     * @see #gyro_submitBlockEntities_return(PoseStack, LevelRenderState, SubmitNodeStorage, CallbackInfo)
     */
    @Shadow
    @Final
    private final Minecraft minecraft;

    /**
     * Current level, used to retrieve game time, which is basically useless for our needs but is an argument for
     * beacon beam renderer by convention even tho our beams are eternal. It's probably for animation too, not tested.
     *
     * @see #gyro_submitBlockEntities_return(PoseStack, LevelRenderState, SubmitNodeStorage, CallbackInfo)
     * @see Level#getGameTime()
     * @see BeaconRenderer#submitBeaconBeam(PoseStack, SubmitNodeCollector, ResourceLocation, float, float, int, int, int, float, float)
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
    @Deprecated(forRemoval = true)
    @Contract(value = "-> fail", pure = true)
    private LevelRendererMixin() {
        throw new AssertionError("gyro: No instances.");
    }

    /**
     * Handles the beacon beam rendering for {@link Gyro#RENDER_POSES}.
     *
     * @param pose  The current pose stack
     * @param state Current game rendering state
     * @param ci    Callback data, ignored
     * @apiNote Do not call, called by Mixin
     * @see BeaconRenderer#BEAM_LOCATION
     * @see BeaconRenderer#submitBeaconBeam(PoseStack, SubmitNodeCollector, ResourceLocation, float, float, int, int, int, float, float)
     * @see Gyro#RENDER_POSES
     */
    @DoNotCall("Called by Mixin")
    @Inject(method = "submitBlockEntities", at = @At("RETURN"))
    private void gyro_submitBlockEntities_return(PoseStack pose, LevelRenderState state, SubmitNodeStorage storage, CallbackInfo ci) {
        // Validate.
        assert pose != null : "gyro: Parameter 'pose' is null. (state: " + state + ", storage: " + storage + ", renderer: " + this + ')';
        assert state != null : "gyro: Parameter 'state' is null. (pose: " + pose + ", storage: " + storage + ", renderer: " + this + ')';
        assert storage != null : "gyro: Parameter 'storage' is null. (pose: " + pose + ", state: " + state + ", renderer: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Rendering block entities NOT from the main thread. (thread: " + Thread.currentThread() + ", pose: " + pose + ", state: " + state + ", storage: " + storage + ", renderer: " + this + ')';

        // Get and push the profiler.
        ProfilerFiller profiler = Profiler.get();
        profiler.push("gyro:render_beams");

        // Get the poses to render, skip if none.
        Collection<GyroRender> poses = Gyro.RENDER_POSES.values();
        if (poses.isEmpty()) {
            // Pop, stop.
            profiler.pop();
            return;
        }

        // Disable the fog temporarily.
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        FogRenderer fogRenderer = ((GameRendererAccessor) this.minecraft.gameRenderer).gyro_fogRenderer();
        RenderSystem.setShaderFog(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));

        // Get the camera position.
        Vec3 cam = state.cameraRenderState.pos; // Implicit NPE for 'state'
        double cx = cam.x();
        double cz = cam.z();

        // Extract the level.
        ClientLevel level = this.level;
        assert level != null : "gyro: Rendering block entities without a client level. (pose: " + pose + ", state: " + state + ", storage: " + storage + ", renderer: " + this + ')';
        // This is not working properly on:
        // 1. /tick freeze
        // 2. /tick rate 10000
        // Vanilla also doesn't work properly. Well, this is vanilla logic, actually.
        float animationTime = Math.floorMod(level.getGameTime(), GYRO_BEACON_BEAM_FULL_ROTATION_TICKS) + this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(/*unaffectedByTickFreeze=*/false);

        // Render each pos as a beam.
        for (GyroRender pos : poses) {
            // Push and offset.
            pose.pushPose(); // Implicit NPE for 'pose'
            double x = (pos.x() - cx);
            double z = (pos.z() - cz);
            pose.translate(x, 0.0D, z);

            // Scale and render.
            float scale = Math.max(1.0F, (float) (Math.sqrt((x * x) + (z * z)) / GYRO_BEACON_BEAM_SCALE_THRESHOLD));
            BeaconRenderer.submitBeaconBeam(pose, storage, BeaconRenderer.BEAM_LOCATION, /*textureDensity=*/1.0F, animationTime, -(BeaconRenderer.MAX_RENDER_Y / 2), BeaconRenderer.MAX_RENDER_Y, pos.color(), BeaconRenderer.SOLID_BEAM_RADIUS * scale, BeaconRenderer.BEAM_GLOW_RADIUS * scale); // Implicit NPE for 'source', 'level'

            // Pop.
            pose.popPose();
        }

        // Re-enable the fog.
        RenderSystem.setShaderFog(fog);

        // Pop the profiler.
        profiler.pop();
    }
}
