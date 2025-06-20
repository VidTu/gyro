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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
 * @apiNote Internal use only
 * @see Gyro
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(Minecraft.class)
@NullMarked
public abstract class MinecraftMixin extends ReentrantBlockableEventLoop<Runnable> {
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
    private MinecraftMixin() {
        super(null);
        throw new AssertionError("gyro: No instances.");
    }

    /**
     * Clears the {@link Gyro#RENDER_POSES} and {@link Gyro#ANGLES}.
     *
     * @param level New level, {@code null} if was unloaded, ignored
     * @param ci    Callback data, ignored
     * @apiNote Do not call, called by Mixin
     * @see Gyro#RENDER_POSES
     * @see Gyro#ANGLES
     */
    @DoNotCall("Called by Mixin")
    @Inject(method = "updateLevelInEngines", at = @At("RETURN"))
    private void gyro_updateLevelInEngines_return(@Nullable ClientLevel level, CallbackInfo ci) {
        // Validate.
        assert this.isSameThread() : "gyro: Updating level in engines NOT from the main thread. (thread: " + Thread.currentThread() + ", level: " + level + ", game: " + this + ')';

        // Get and push the profiler.
        ProfilerFiller profiler = Profiler.get();
        profiler.push("gyro:clear_data");

        // Log. (**TRACE**)
        GYRO_LOGGER.trace(Gyro.GYRO_MARKER, "gyro: Clearing data... (level: {}, game: {})", level, this);

        // Clear.
        Gyro.ANGLES.clear();
        Gyro.RENDER_POSES.clear();

        // Log. (**DEBUG**)
        GYRO_LOGGER.debug(Gyro.GYRO_MARKER, "gyro: Data has been cleared. (level: {}, game: {})", level, this);

        // Pop the profiler.
        profiler.pop();
    }
}
