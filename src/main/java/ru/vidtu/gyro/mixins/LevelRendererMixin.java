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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vidtu.gyro.Gyro;
import ru.vidtu.gyro.GyroRender;

import java.util.Collection;

/**
 * Mixin that renders beacon beams on {@link Gyro#RENDER_POSES}.
 *
 * @author VidTu
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(LevelRenderer.class)
@NullMarked
public class LevelRendererMixin {
    /**
     * A beacon beam texture.
     *
     * @see BeaconRenderer
     * @see BeaconRenderer#renderBeaconBeam(PoseStack, MultiBufferSource, ResourceLocation, float, float, long, int, int, int, float, float)
     */
    @Unique
    private static final ResourceLocation GYRO_BEACON_BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");

    /**
     * Current level, used to retrieve game time, which is basically useless for our needs but is an argument for
     * beacon beam renderer by convention even tho our beams are eternal. It's probably for animation too, not tested.
     *
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
    public LevelRendererMixin() {
        throw new AssertionError("No instances.");
    }

    /**
     * Handles the block entity rendering.
     *
     * @param pose                 The current pose stack
     * @param source               Buffer rendering source
     * @param blockBreakAnimSource Buffer rendering source for rendering the block breaking overlay, ignored
     * @param camera               Current camera data
     * @param tickDelta            Current tick data
     * @param ci                   Callback data, ignored
     */
    @Inject(method = "renderBlockEntities", at = @At("RETURN"))
    private void gyro_renderBlockEntities_return(PoseStack pose, MultiBufferSource.BufferSource source,
                                                 MultiBufferSource.BufferSource blockBreakAnimSource,
                                                 Camera camera, float tickDelta, CallbackInfo ci) {
        // Validate.
        assert pose != null : "Gyro: Parameter 'pose' is null. (source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';
        assert source != null : "Gyro: Parameter 'source' is null. (pose: " + pose + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';
        assert blockBreakAnimSource != null : "Gyro: Parameter 'blockBreakAnimSource' is null. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';
        assert camera != null : "Gyro: Parameter 'camera' is null. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';
        assert (tickDelta >= 0.0F) && (tickDelta <= 1.0F) : "Gyro: Parameter 'tickDelta' is not in the [0..1] range. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';
        assert ci != null : "Gyro: Parameter 'ci' is null. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", renderer: " + this + ')';

        // Get the poses to render, skip if none.
        Collection<GyroRender> poses = Gyro.RENDER_POSES.values();
        if (poses.isEmpty()) return;

        // Disable the fog temporarily.
        FogParameters fog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);

        // Get the camera position and offset by it.
        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(-cam.x(), 0.0D, -cam.z());

        // Extract the level.
        ClientLevel level = this.level;
        assert level != null : "Gyro: Rendering block entities without a client level. (pose: " + pose + ", source: " + source + ", blockBreakAnimSource: " + blockBreakAnimSource + ", camera: " + camera + ", tickDelta: " + tickDelta + ", ci: " + ci + ", renderer: " + this + ')';

        // Render each pos as a beam.
        for (GyroRender pos : poses) {
            pose.pushPose();
            pose.translate(pos.x(), 0.0D, pos.z());
            BeaconRenderer.renderBeaconBeam(pose, source, GYRO_BEACON_BEAM, tickDelta, /*scale=*/1.0F, level.getGameTime(), /*verticalOffset=*/-1024, /*beamHeight=*/2048, pos.color(), /*solidSize=*/0.15F, /*transparentSize=*/0.175F);
            pose.popPose();
        }

        // Pop the stack from camera offsetting.
        pose.popPose();

        // Re-enable the fog.
        RenderSystem.setShaderFog(fog);
    }
}
