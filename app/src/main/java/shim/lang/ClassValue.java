package shim.lang;

/**
 * Same-length stand-in for {@code java.lang.ClassValue} (20 UTF-8 bytes:
 * {@code java/lang/ClassValue} → {@code shim/lang/ClassValue}).
 * kotlinx.coroutines still ships {@code ClassValueCtorCache}; ART verifies that
 * inner class on API 21–33 where the platform type does not exist. Build-time
 * rewriting points the coroutines class at this type instead.
 */
public abstract class ClassValue<T> {
    protected ClassValue() {}

    protected abstract T computeValue(Class<?> type);

    public T get(Class<?> type) {
        return computeValue(type);
    }

    public void remove(Class<?> type) {
        // coroutines only needs compute/get
    }
}
