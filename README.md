# gyro

Abuses the newly introduced (1.21.6) Minecraft waypoint system to get player positions.

## Dependencies

- Fabric Loader (>=0.16.13)
- Minecraft (25w15a)

## Download

- [GitHub](https://github.com/VidTu/gyro/releases)

## About

Minecraft 1.21.6[^1] (25w15a) added a new player locator bar. Mojang made the smart decision NOT to send other players'
locations directly to everyone on the server, instead opting for a more secure system. The senders of waypoints
are called "*sources*" (not an official name), and the receivers are called "*receivers*." (not an official name, too)
The waypoint sending process is called a "*connection*" (that's actually an official name). This system works like this:

1. If the *receiver* can directly see the chunk in which the *source* is located, the whole X/Y/Z position is sent
   directly to the client. This is called a *vector position* or a *vec3i position* (for "vector, 3 integers").
   This can be easily obtained by any packet listener just by getting the X/Y/Z from the packet. This is precise.
2. Otherwise, if the *receiver* is located "*not really far away*" (an official term, which means the player is located
   within the `far_dist` waypoint property, 332 blocks by default) from the *source*, they receive only the chunk
   position where the *source* is located. This is called a *chunk position*. This loses some information
   but allows us to find the *source*'s chunk, and from there the *vector position* will allow us to find the *source*.
3. If the *receiver* is located *really far away*, the angle (aka yaw, aka the rotation) between the *source* and the
   *receiver* is calculated server-side and sent to the client. That loses all valuable information from this,
   and it shouldn't be possible to recover the player position, right?

Actually, nope. We can use some basic trigonometry already used by the speedrunning community to find strongholds
if we assume that the *source* is standing still or doesn't move too much. The *receiver* can move around a bit,
calculate the two tans and find their crossing point, and can pretty much find the exact *source* location.
See this image[^2], if you're confused about how this works:

![an image of two tans crossing](taninfo.png)

[^1]: Not yet confirmed to be 1.21.6.
[^2]: Background map from [minecraft.wiki](https://minecraft.wiki/index.php?curid=122350).

## License

This project is provided under the MIT License.
Check out [LICENSE](https://github.com/VidTu/gyro/blob/main/LICENSE) for more information.

## Building

1. Have 4 GB of free RAM, 10 GB of free disk space, and an active internet connection.
2. Install Java 21 and dump it into PATH and/or JAVA_HOME.
3. Run `./gradlew build` from the terminal/PowerShell.
4. Grab the JAR from the `./build/libs/` folder.
