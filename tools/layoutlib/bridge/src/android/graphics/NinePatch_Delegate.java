/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.layoutlib.bridge.impl.GcSnapshot;
import com.android.ninepatch.NinePatchChunk;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.graphics.drawable.NinePatchDrawable;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Delegate implementing the native methods of android.graphics.NinePatch
 *
 * Through the layoutlib_create tool, the original native methods of NinePatch have been replaced
 * by calls to methods of the same name in this delegate class.
 *
 * Because it's a stateless class to start with, there's no need to keep a {@link DelegateManager}
 * around to map int to instance of the delegate.
 *
 */
public final class NinePatch_Delegate {

    // ---- delegate manager ----
    private static final DelegateManager<NinePatch_Delegate> sManager =
            new DelegateManager<NinePatch_Delegate>(NinePatch_Delegate.class);

    // ---- delegate helper data ----
    /**
     * Cache map for {@link NinePatchChunk}.
     * When the chunks are created they are serialized into a byte[], and both are put
     * in the cache, using a {@link SoftReference} for the chunk. The default Java classes
     * for {@link NinePatch} and {@link NinePatchDrawable} only reference to the byte[] data, and
     * provide this for drawing.
     * Using the cache map allows us to not have to deserialize the byte[] back into a
     * {@link NinePatchChunk} every time a rendering is done.
     */
    private final static Map<byte[], SoftReference<NinePatchChunk>> sChunkCache =
        new HashMap<byte[], SoftReference<NinePatchChunk>>();

    // ---- delegate data ----
    private byte[] chunk;


    // ---- Public Helper methods ----

    /**
     * Serializes the given chunk.
     *
     * @return the serialized data for the chunk.
     */
    public static byte[] serialize(NinePatchChunk chunk) {
        // serialize the chunk to get a byte[]
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(chunk);
        } catch (IOException e) {
            Bridge.getLog().error(null, "Failed to serialize NinePatchChunk.", e, null /*data*/);
            return null;
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                }
            }
        }

        // get the array and add it to the cache
        byte[] array = baos.toByteArray();
        sChunkCache.put(array, new SoftReference<NinePatchChunk>(chunk));
        return array;
    }

    /**
     * Returns a {@link NinePatchChunk} object for the given serialized representation.
     *
     * If the chunk is present in the cache then the object from the cache is returned, otherwise
     * the array is deserialized into a {@link NinePatchChunk} object.
     *
     * @param array the serialized representation of the chunk.
     * @return the NinePatchChunk or null if deserialization failed.
     */
    public static NinePatchChunk getChunk(byte[] array) {
        SoftReference<NinePatchChunk> chunkRef = sChunkCache.get(array);
        NinePatchChunk chunk = chunkRef.get();
        if (chunk == null) {
            ByteArrayInputStream bais = new ByteArrayInputStream(array);
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(bais);
                chunk = (NinePatchChunk) ois.readObject();

                // put back the chunk in the cache
                if (chunk != null) {
                    sChunkCache.put(array, new SoftReference<NinePatchChunk>(chunk));
                }
            } catch (IOException e) {
                Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                        "Failed to deserialize NinePatchChunk content.", e, null /*data*/);
                return null;
            } catch (ClassNotFoundException e) {
                Bridge.getLog().error(LayoutLog.TAG_BROKEN,
                        "Failed to deserialize NinePatchChunk class.", e, null /*data*/);
                return null;
            } finally {
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        return chunk;
    }

    // ---- native methods ----

    @LayoutlibDelegate
    /*package*/ static boolean isNinePatchChunk(byte[] chunk) {
        NinePatchChunk chunkObject = getChunk(chunk);
        if (chunkObject != null) {
            return true;
        }

        return false;
    }

    @LayoutlibDelegate
    /*package*/ static long validateNinePatchChunk(long bitmap, byte[] chunk) {
        // the default JNI implementation only checks that the byte[] has the same
        // size as the C struct it represent. Since we cannot do the same check (serialization
        // will return different size depending on content), we do nothing.
        NinePatch_Delegate newDelegate = new NinePatch_Delegate();
        newDelegate.chunk = chunk;
        return sManager.addNewDelegate(newDelegate);
    }

    @LayoutlibDelegate
    /*package*/ static void nativeFinalize(long chunk) {
        sManager.removeJavaReferenceFor(chunk);
    }

    @LayoutlibDelegate
    /*package*/ static void nativeDraw(long canvas_instance, RectF loc, long bitmap_instance,
            long chunk, long paint_instance_or_null, int destDensity, int srcDensity) {
        draw(canvas_instance,
                (int) loc.left, (int) loc.top, (int) loc.right, (int) loc.bottom,
                bitmap_instance, chunk, paint_instance_or_null,
                destDensity, srcDensity);
    }

    @LayoutlibDelegate
    /*package*/ static void nativeDraw(long canvas_instance, Rect loc, long bitmap_instance,
            long chunk, long paint_instance_or_null, int destDensity, int srcDensity) {
        draw(canvas_instance,
                loc.left, loc.top, loc.right, loc.bottom,
                bitmap_instance, chunk, paint_instance_or_null,
                destDensity, srcDensity);
    }

    @LayoutlibDelegate
    /*package*/ static long nativeGetTransparentRegion(long bitmap, long chunk, Rect location) {
        return 0;
    }

    // ---- Private Helper methods ----

    private static void draw(long canvas_instance,
            final int left, final int top, final int right, final int bottom,
            long bitmap_instance, long chunk, long paint_instance_or_null,
            final int destDensity, final int srcDensity) {
        // get the delegate from the native int.
        final Bitmap_Delegate bitmap_delegate = Bitmap_Delegate.getDelegate(bitmap_instance);
        if (bitmap_delegate == null) {
            return;
        }

        byte[] c = null;
        NinePatch_Delegate delegate = sManager.getDelegate(chunk);
        if (delegate != null) {
            c = delegate.chunk;
        }
        if (c == null) {
            // not a 9-patch?
            BufferedImage image = bitmap_delegate.getImage();
            Canvas_Delegate.native_drawBitmap(null, canvas_instance, bitmap_instance,
                    0f, 0f, (float)image.getWidth(), (float)image.getHeight(),
                    (float)left, (float)top, (float)right, (float)bottom,
                    paint_instance_or_null, destDensity, srcDensity);
            return;
        }

        final NinePatchChunk chunkObject = getChunk(c);
        assert chunkObject != null;
        if (chunkObject == null) {
            return;
        }

        Canvas_Delegate canvas_delegate = Canvas_Delegate.getDelegate(canvas_instance);
        if (canvas_delegate == null) {
            return;
        }

        // this one can be null
        Paint_Delegate paint_delegate = Paint_Delegate.getDelegate(paint_instance_or_null);

        canvas_delegate.getSnapshot().draw(new GcSnapshot.Drawable() {
                @Override
                public void draw(Graphics2D graphics, Paint_Delegate paint) {
                    chunkObject.draw(bitmap_delegate.getImage(), graphics,
                            left, top, right - left, bottom - top, destDensity, srcDensity);
                }
            }, paint_delegate, true /*compositeOnly*/, false /*forceSrcMode*/);

     }
}
