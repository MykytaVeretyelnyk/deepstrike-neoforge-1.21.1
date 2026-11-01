# MCSP 1.0.10 on Superb Warfare 0.8.9.2

MCSP 1.0.10 is built against a newer Superb Warfare. On SBW 0.8.9.2 placing the TOS-1A throws
`NoSuchFieldError: Tos1aEntity.BARREL_ANIM` (and would then fail on `DamageModifier.custom(Function2)`).

`PatchTos1a.java` (ASM) rewrites only `tech/mcsp/mcsp/entity/Tos1aEntity.class`:
adds the `BARREL_ANIM` accessor and routes `custom(Function2)` through an adapter class.

    javac -cp asm-9.x.jar PatchTos1a.java
    java -cp .;asm-9.x.jar PatchTos1a MCSP-original.jar MCSP-patched.jar

MCSP is GPL-3.0; a patched build must be marked as modified if redistributed. Not yet confirmed in-game.
