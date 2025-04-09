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

package ru.vidtu.gyro;

import com.mojang.datafixers.util.Either;
import net.fabricmc.api.ClientModInitializer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.gyro.mixins.ClientPacketListenerMixin;
import ru.vidtu.gyro.mixins.LevelRendererMixin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main gyro class.
 *
 * @author VidTu
 */
@ApiStatus.Internal
@NullMarked
public final class Gyro implements ClientModInitializer {
    /**
     * A map of waypoint identifiers mapped to their render data. Used for rendering via {@link LevelRendererMixin}.
     */
    public static final Map<Either<UUID, String>, GyroRender> RENDER_POSES = new HashMap<>(0);

    /**
     * A map of waypoint identifiers mapped to their calculation data. Used by {@link ClientPacketListenerMixin}.
     */
    public static final Map<Either<UUID, String>, GyroData> ANGLES = new HashMap<>(0);

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Gyro.class);

    /**
     * Creates a new mod.
     */
    @Contract(pure = true)
    public Gyro() {
        // Empty
    }

    /**
     * Initializes the client. Logs the message.
     */
    @Override
    public void onInitializeClient() {
        LOGGER.info("Gyro: Hi!");
    }
}
