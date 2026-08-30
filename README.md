# Rail Scout

Rail Scout is a small server-authoritative Forge 1.20.1 mod. A Scout surveys
nearby cave floors, presents three diverse terrain-following rail routes, and
builds the route selected with the crosshair and the G key.

The vehicle is an `AbstractMinecart`, so Create's normal minecart coupling works
without Rail Scout owning or interpreting coupled-cart state.

## Use

- Place the Scout on a rail and open it to load rails, furnace fuel, and solid
  support blocks into its 27-slot inventory.
- Wait for three cyan ghost routes, aim at one, and press `G` to select it.
- Use the GUI for stop, forward, slow reverse, and the manual hand-brake lock.
  The Scout automatically holds itself still while planning, ready, paused, or
  complete, including on sloped rails.
- The default route cap is 64 rails and can be changed in the server config.

The planner only traverses loaded terrain, never excavates, avoids fluids, and
can bridge a one-block-deep gap with supplied support blocks.

Validation:

```sh
./gradlew verifyFast
./gradlew verifyFull
```
