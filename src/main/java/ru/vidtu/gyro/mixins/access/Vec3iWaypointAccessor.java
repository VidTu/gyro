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

package ru.vidtu.gyro.mixins.access;

import net.minecraft.core.Vec3i;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@code vector} in {@link TrackedWaypoint.Vec3iWaypoint}.
 *
 * @author VidTu
 * @apiNote Internal use only
 */
// @ApiStatus.Internal // Can't annotate this without logging in the console.
@Mixin(TrackedWaypoint.Vec3iWaypoint.class)
@NullMarked
public interface Vec3iWaypointAccessor {
    /**
     * Gets the vector.
     *
     * @return Waypoint exact position vector
     */
    @Contract(pure = true)
    @Accessor("vector")
    Vec3i gyro_vector();
}
