package lock.mixin;

/** Duck interface so the render state can carry the charm tint from extract to render. */
public interface CharmState {
    int lock$getCharm();
    void lock$setCharm(int argb);
}
