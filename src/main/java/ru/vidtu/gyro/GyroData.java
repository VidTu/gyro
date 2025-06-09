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

package ru.vidtu.gyro;

import com.google.errorprone.annotations.Immutable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/**
 * A player azimuth data.
 * <p>
 * This class is immutable.
 *
 * @param angle Player locator angle in radians
 * @param x     Player X position (at the moment of receiving the angle)
 * @param z     Player Z position (at the moment of receiving the angle)
 * @author VidTu
 * @apiNote Internal use only
 */
@ApiStatus.Internal
@Immutable
@NullMarked
public record GyroData(float angle, double x, double z) {
    /**
     * Creates a new data.
     *
     * @param angle Player locator angle in radians
     * @param x     Player X position (at the moment of receiving the angle)
     * @param z     Player Z position (at the moment of receiving the angle)
     */
    @Contract(pure = true)
    public GyroData {
        // Validate.
        assert (angle >= -Mth.PI) && (angle <= Mth.PI) : "gyro: Parameter 'angle' is not in the [-PI..PI] range. (angle: " + angle + ", x: " + x + ", z: " + z + ", data: " + this + ')';
        assert (x >= -Level.MAX_LEVEL_SIZE) && (x <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'x' is not in the [-30_000_000..30_000_000] range. (angle: " + angle + ", x: " + x + ", z: " + z + ", data: " + this + ')';
        assert (z >= -Level.MAX_LEVEL_SIZE) && (z <= Level.MAX_LEVEL_SIZE) : "gyro: Parameter 'z' is not in the [-30_000_000..30_000_000] range. (angle: " + angle + ", x: " + x + ", z: " + z + ", data: " + this + ')';
    }

    @Contract(pure = true)
    @Override
    public String toString() {
        return "gyro/GyroData{" +
                "angle=" + this.angle + " (degrees=" + Math.toDegrees(this.angle) + ')' +
                ", x=" + this.x +
                ", z=" + this.z +
                '}';
    }
}
