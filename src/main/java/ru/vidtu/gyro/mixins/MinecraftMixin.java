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

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vidtu.gyro.Gyro;

/**
 * Mixin that clears {@link Gyro#RENDER_POSES} and {@link Gyro#ANGLES} on world switching.
 *
 * @author VidTu
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(Minecraft.class)
@NullMarked
public final class MinecraftMixin {
    /**
     * Logger for this class.
     */
    @Unique
    private static final Logger GYRO_LOGGER = LoggerFactory.getLogger("gyro/MinecraftMixin");

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     * @deprecated Always throws
     */
    @Deprecated(forRemoval = true)
    @Contract(value = "-> fail", pure = true)
    public MinecraftMixin() {
        throw new AssertionError("No instances.");
    }

    /**
     * Clears the {@link Gyro#RENDER_POSES}.
     *
     * @param level New level, {@code null} if was unloaded, ignored
     * @param ci    Callback data, ignored
     */
    @Inject(method = "updateLevelInEngines", at = @At("RETURN"))
    private void gyro_updateLevelInEngines_return(@Nullable ClientLevel level, CallbackInfo ci) {
        // Validate.
        assert ci != null : "gyro: Parameter 'ci' is null. (level: " + level + ", game: " + this + ')';

        // Get the profiler.
        ProfilerFiller profiler = Profiler.get();
        profiler.push("gyro:clear_data");

        // Log. (**TRACE**)
        if (GYRO_LOGGER.isTraceEnabled()) {
            GYRO_LOGGER.trace("gyro: Clearing data... (level: {}, ci: {}, game: {})", level, ci, this);
        }

        // Clear.
        Gyro.ANGLES.clear();
        Gyro.RENDER_POSES.clear();

        // Log. (**DEBUG**)
        if (GYRO_LOGGER.isDebugEnabled()) {
            GYRO_LOGGER.debug("gyro: Data has been cleared. (level: {}, ci: {}, game: {})", level, ci, this);
        }

        // Pop the profiler.
        profiler.pop();
    }
}
