# Rail Beetle

Rail Beetle is a server-authoritative Forge 1.20.1 mod. At the end of an
existing railway, a Beetle surveys nearby cave floors once per second, presents
three core terrain-following rail routes plus up to four more when their
destinations are well separated. Up to three nearby Route Beacons add shortest
legal routes ending beside those blocks. The Beetle builds the route selected with
the crosshair and the `G` key. A Beetle placed in the middle of a railway stays
in normal driving mode instead of trying to plan from that location.

The vehicle is an `AbstractMinecart`, so Create's normal minecart coupling works
without replacing vanilla rail physics. Its industrial automaton renderer is
larger than its standard minecart hitbox: black steel and aged brass enclose a
timber cargo bed, while rail-setting jaws, survey booms, split machinery covers,
and six working tool assemblies form the Beetle silhouette. Installed modules
and the selected engine alter visible machinery. Create and every alternate
power mod are optional.

## Use

- Place the Beetle on a rail. Its amber front lamp shows the persistent forward
  direction; repeated forward commands never infer a new direction from yaw.
- Open it to load tagged rails, furnace fuel, and solid support blocks into its
  unchanged 27-slot cargo hold, use the compact controls, or service its one
  engine cradle and six module bays while stopped with the brakes applied.
- At a rail terminus, aim near any colored ghost route and press `G` from anywhere
  the server tracks the Beetle. The highlighted route and prompt identify the
  selection. Press `G` again to stop; while stopped, press it to clear the route
  and choose another. Missing rails, supports, or startup fuel are shown in red
  on the prompt without preventing selection.
- Drive at reverse `0.5`, `0.5x` (`2` blocks/second), or `1x` (`4`
  blocks/second) immediately. High-Speed Governors add `2x` (`8`) and `3x`
  (`12`) without withholding useful baseline movement.
- The Beetle automatically applies its hand brake between automatic phases and
  on slopes. Its `0.4` blocks/tick² traction is shared across a Create-coupled
  consist so trailing carts keep climbing without pulling the Beetle backward.
- Outside Neutral, collisions cannot slow or displace the Beetle; it shoves
  movable entities clear and clamps itself to terminal rail centers. Neutral
  clears routes, releases the brake, and restores ordinary pushing and towing.
- The baseline route cap is 64 rails; survey modules raise it to 128 and 192.
  The server config is an upper safety limit, not an upgrade bypass.
- When choosing supports, it preserves fuel by using non-burnable blocks first,
  then blocks with lower furnace burn time before more valuable fuel blocks.

Every active operation consumes work: motion scales with speed squared and adds
grade/consist penalties, while surveying, rail/support placement, clearing,
remote commands, and a lit searchlight have explicit costs. UI use, Stop, and
fail-safe braking are free. With no engine—or an empty installed engine—the
built-in firebox burns cargo fuel. Optional drives store their native resource
on the engine item: Create steam, Forge Energy, Ars Source, Blood Magic life
essence, PneumaticCraft air, Goety soul energy, or Malum spirits. The stopped
Beetle exposes cargo plus Forge Energy/fluid docking where applicable.

Ten module families cover speed, efficiency, brakes, adhesion, acceleration,
drawgear, survey range, dispatch control, trestle construction, and a dynamic
searchlight. Duplicate families cannot be installed. Dispatch I works within
128 blocks; Dispatch II reaches any already-loaded Beetle in the same dimension
and never adds chunk tickets. Sodium/Embeddium Dynamic Lights supplies true
light when present; the bright searchlight model remains as the fallback.

The planner only traverses loaded terrain, never excavates solid blocks or routes
underwater, and clears tagged dry vegetation, leaves, and snow layers with normal
block drops as it builds. Baseline trestles span one rail and build supports up
to four blocks deep. Trestle modules raise those limits to 5/12 and 12/32.
Columns are placed bottom-up. If real block physics later removes a support, the
Beetle halts without stabilizing or refunding the player's failed structure. It
never substitutes a down/up slope. It requires
two straight rails between 90-degree turns and never places a route immediately
beside unrelated existing rails. It accepts vanilla/Forge rail tags plus Rail
Beetle's explicit compatibility tag and rejects stale or changed routes before
movement begins.

Validation:

```sh
./gradlew verifyFast
./gradlew verifyFull
```
