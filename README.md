# Rail Scout

Rail Scout is a small server-authoritative Forge 1.20.1 mod. At the end of an
existing railway, a Scout surveys nearby cave floors once per second, presents
three diverse terrain-following rail routes, and builds the route selected with
the crosshair and the `G` key. A Scout placed in the middle of a railway stays
in normal driving mode instead of trying to plan from that location.

The vehicle is an `AbstractMinecart`, so Create's normal minecart coupling works
without Rail Scout owning or interpreting coupled-cart state. Create is optional:
the visible cargo is a brass casing when Create is loaded and a copper block
otherwise.

## Use

- Place the Scout on a rail. Its amber front lamp shows the persistent forward
  direction; repeated forward commands never infer a new direction from yaw.
- Open it only when needed to load tagged rails, furnace fuel, and solid support
  blocks into its 27-slot inventory, or to use the compact manual controls.
- At a rail terminus, aim near a cyan ghost route and press `G` from anywhere
  the server tracks the Scout. The highlighted route and prompt identify the
  selection. Press `G` again to stop; while stopped, press it to clear the route
  and choose another.
- Drive at reverse `0.5`, `0.5x` (`2` blocks/second), `1x` (`4` blocks/second),
  or `2x` (`8` blocks/second). Normal use does not require the GUI.
- The Scout automatically applies its hand brake between automatic phases and
  on slopes. Movement uses bounded acceleration so a Create-coupled passenger
  cart follows without position snapping or jitter from Scout-side corrections.
- Mobs cannot board or shove the Scout. It uses powered-cart collision priority
  against ordinary minecarts so incidental impacts do not reverse its command.
- The default route cap is 64 rails and can be changed in the server config.

The planner only traverses loaded terrain, never excavates or routes underwater,
and can bridge a one-block-deep gap with supplied support blocks. It requires
two straight rails between 90-degree turns and never places a route immediately
beside unrelated existing rails. It accepts vanilla/Forge rail tags plus Rail
Scout's explicit compatibility tag and rejects stale or changed routes before
movement begins.

Validation:

```sh
./gradlew verifyFast
./gradlew verifyFull
```
