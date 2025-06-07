/*
 * gyro is a third-party mod for Minecraft Java Edition that abuses the newly introduced waypoint system to get player positions.
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

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
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
     * Game instance. Used for thread checking.
     *
     * @see #gyro_renderBlockEntities_return(PoseStack, MultiBufferSource.BufferSource, MultiBufferSource.BufferSource, Camera, float, CallbackInfo)
     */
    @Shadow
    @Final
    private final Minecraft minecraft;

    /**
     * Current level, used to retrieve game time, which is basically useless for our needs but is an argument for
     * beacon beam renderer by convention even tho our beams are eternal. It's probably for animation too, not tested.
     *
     * @see #gyro_renderBlockEntities_return(PoseStack, MultiBufferSource.BufferSource, MultiBufferSource.BufferSource, Camera, float, CallbackInfo)
     * @see Level#getGameTime()
     * @see BeaconRenderer#renderBeaconBeam(PoseStack, MultiBufferSource, ResourceLocation, float, float, long, int, int, int, float, float)
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
     * @param pose                 The current pose stack
     * @param source               Buffer rendering source
     * @param blockBreakAnimSource Buffer rendering source for rendering the block breaking overlay, ignored
     * @param camera               Current camera data
     * @param partialTick          Current partial tick (not to be confused with the tick delta)
     * @param ci                   Callback data, ignored
     * @see BeaconRenderer#BEAM_LOCATION
     * @see BeaconRenderer#renderBeaconBeam(PoseStack, MultiBufferSource, ResourceLocation, float, float, long, int, int, int, float, float)
     * @see Gyro#RENDER_POSES
     */
    @Inject(method = "renderBlockEntities", at = @At("RETURN"))
    private void gyro_renderBlockEntities_return(PoseStack pose, MultiBufferSource.BufferSource source,
                                                 MultiBufferSource.BufferSource blockBreakAnimSource,
                                                 Camera camera, float partialTick, CallbackInfo ci) {
        // Validate.
        assert pose != null : "gyro: Parameter 'pose' is null. (source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';
        assert source != null : "gyro: Parameter 'source' is null. (pose: " + pose + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';
        assert blockBreakAnimSource != null : "gyro: Parameter 'blockBreakAnimSource' is null. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';
        assert camera != null : "gyro: Parameter 'camera' is null. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", partialTick: " + partialTick + ", renderer: " + this + ')';
        assert (partialTick >= 0.0F) && (partialTick <= 1.0F) : "gyro: Parameter 'partialTick' is not in the [0..1] range. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';
        assert this.minecraft.isSameThread() : "gyro: Rendering block entities NOT from the main thread. (thread: " + Thread.currentThread() + ", pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';

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
        Vec3 cam = camera.getPosition(); // Implicit NPE for 'camera'
        double cx = cam.x();
        double cz = cam.z();

        // Extract the level.
        ClientLevel level = this.level;
        assert level != null : "gyro: Rendering block entities without a client level. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", partialTick: " + partialTick + ", renderer: " + this + ')';

        // Render each pos as a beam.
        for (GyroRender pos : poses) {
            // Push and offset.
            pose.pushPose(); // Implicit NPE for 'pose'
            double x = (pos.x() - cx);
            double z = (pos.z() - cz);
            pose.translate(x, 0.0D, z);

            // Scale and render.
            float scale = Math.max(1.0F, (float) (Math.sqrt((x * x) + (z * z)) / 48.0D));
            BeaconRenderer.renderBeaconBeam(pose, source, BeaconRenderer.BEAM_LOCATION, partialTick, /*textureDensity=*/1.0F, level.getGameTime(), -(BeaconRenderer.MAX_RENDER_Y / 2), BeaconRenderer.MAX_RENDER_Y, pos.color(), BeaconRenderer.SOLID_BEAM_RADIUS * scale, BeaconRenderer.BEAM_GLOW_RADIUS * scale); // Implicit NPE for 'source', 'level'

            // Pop.
            pose.popPose();
        }

        // Re-enable the fog.
        RenderSystem.setShaderFog(fog);

        // Pop the profiler.
        profiler.pop();
    }
}
