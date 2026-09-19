package io.github.vedant7007.katori.ml.vision

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Where an [ImageRef] comes from, and what it resolves to.
 *
 * The camera layer captures a frame and [hold]s it; the engine that consumes the ref [take]s it.
 * A ref resolves exactly once, so a frame is never processed twice by accident and never leaks
 * past its one use. The contract on [ImageRef] says callers do not inspect it, and they do not
 * need to: the id is just a key into this map.
 *
 * Bitmaps are the currency because every capture path can produce one (`ImageProxy.toBitmap()`
 * on CameraX, `BitmapFactory` on a file) and ML Kit accepts one directly. The rotation is
 * carried alongside because CameraX does NOT bake it into the bitmap: a report photographed in
 * portrait arrives as landscape pixels plus a 90, and ML Kit needs to be told.
 */
class FrameStore {

    class Frame(val bitmap: Bitmap, val rotationDegrees: Int)

    private val frames = ConcurrentHashMap<String, Frame>()
    private val next = AtomicLong()

    fun hold(bitmap: Bitmap, rotationDegrees: Int = 0): ImageRef {
        val ref = ImageRef("frame-" + next.incrementAndGet())
        frames[ref.id] = Frame(bitmap, rotationDegrees)
        return ref
    }

    /** Removes and returns the frame, or null if the ref was never held or was already taken. */
    fun take(ref: ImageRef): Frame? = frames.remove(ref.id)
}
