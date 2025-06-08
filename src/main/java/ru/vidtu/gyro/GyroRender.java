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

package ru.vidtu.gyro;

import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import ru.vidtu.gyro.mixins.LevelRendererMixin;

/**
 * A waypoint render data for {@link LevelRendererMixin}.
 *
 * @param x     Waypoint alleged X position
 * @param z     Waypoint alleged Z position
 * @param color Waypoint ARGB color (always opaque)
 * @author VidTu
 * @apiNote Internal use only
 */
@ApiStatus.Internal
@NullMarked
public record GyroRender(double x, double z, int color) {
    /**
     * Creates a new render.
     *
     * @param x     Waypoint alleged X position
     * @param z     Waypoint alleged Z position
     * @param color Waypoint ARGB color
     */
    @Contract(pure = true)
    public GyroRender(double x, double z, int color) {
        // Validate.
        assert (x >= -Level.MAX_LEVEL_SIZE) && (x <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'x' is not in the [-30_000_000..30_000_000] range. (x: " + x + ", z: " + z + ", color: " + color + ", data: " + this + ')';
        assert (z >= -Level.MAX_LEVEL_SIZE) && (z <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'z' is not in the [-30_000_000..30_000_000] range. (x: " + x + ", z: " + z + ", color: " + color + ", data: " + this + ')';

        // Assign.
        this.x = x;
        this.z = z;
        this.color = ARGB.opaque(color);
    }

    @Contract(pure = true)
    @Override
    public String toString() {
        return "gyro/GyroRender{" +
                "x=" + this.x +
                ", z=" + this.z +
                ", color=" + this.color + " (r=" + ARGB.red(this.color) + ", g=" + ARGB.green(this.color) + ", b=" + ARGB.blue(this.color) + ')' +
                '}';
    }
}
