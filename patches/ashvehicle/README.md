# AshVehicle 1.21.1-4.6-8.9 on Superb Warfare 0.8.9.2

AshVehicle is built against a newer Superb Warfare. On SBW 0.8.9.2 it crashes the client with
`IncompatibleClassChangeError: Expecting non-static method MouseMovementHandler.getMousePos()` and, once aircraft render,
`NoSuchMethodError: VehicleEntity.gearRot(float)`.

`PatchAsh.java` (ASM) rewrites call sites in every class of the jar:

- `MouseMovementHandler.getMousePos()` is static here (the `INSTANCE` receiver is popped)
- `ProjectileTool.causeCustomExplode` takes a `Projectile`; the trailing damage-multiplier float is dropped
- `CustomExplosion.Builder.damageMultiplier` does not exist and is skipped (the nuke bomb loses its x2 multiplier)
- `RenderHelper.isInGui()` does not exist and is replaced by `false`
- `VehicleEntity.gearRot(float)` becomes `getGearRot()` (no partial-tick interpolation of the landing gear)

    javac -cp asm-9.x.jar PatchAsh.java
    java -cp .;asm-9.x.jar PatchAsh ashvehicle-original.jar ashvehicle-patched.jar

Known limits: the `VehicleEntityMixin` (OBB hitboxes) still fails to apply because `getOBB` is missing in SBW 0.8.9.2 and is skipped
(the mixin config is not required). The vehicle JSON files contain keys SBW 0.8.9.2 ignores (`AircraftCameraPos`, `UseFixedCameraPos`,
`IsEnclosed`, `PowerLimits`, ...). Rendering with the Bliss shader pack shows dithered cockpit surfaces; without shaders the cockpit renders normally.
Reported but not fixed or diagnosed: control loss, no reload after firing, low FPS.

AshVehicle is GPL-3.0 (author Arusan); a patched build must be marked as modified if redistributed.
