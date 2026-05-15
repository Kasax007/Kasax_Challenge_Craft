package net.kasax.challengecraft.util;

/** Exposes the duplicate-spawn guard stored on entities by the mixin layer. */
public interface EntityDoublingAccess {
    void challengecraft$setDoubled(boolean doubled);
    boolean challengecraft$isDoubled();
}
