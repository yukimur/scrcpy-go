package com.genymobile.scrcpy;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.RemoteException;
import android.view.IRotationWatcher;
import android.view.InputEvent;

import com.genymobile.scrcpy.wrappers.ServiceManager;

public final class Device {

    public interface RotationListener {
        void onRotationChanged(int rotation);
    }

    private final ServiceManager serviceManager = new ServiceManager();

    private ScreenInfo screenInfo;
    private RotationListener rotationListener;

    public Device(Options options) {
        screenInfo = computeScreenInfo();
        registerRotationWatcher(new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) throws RemoteException {
                synchronized (Device.this) {
                    screenInfo = screenInfo.withRotation(rotation);

                    // notify
                    if (rotationListener != null) {
                        rotationListener.onRotationChanged(rotation);
                    }
                }
            }
        });
    }

    public synchronized ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    private ScreenInfo computeScreenInfo() {
        DisplayInfo displayInfo = serviceManager.getDisplayManager().getDisplayInfo();
        boolean rotated = (displayInfo.getRotation() & 1) != 0;
        Size deviceSize = displayInfo.getSize();
        Rect contentRect = new Rect(0, 0, deviceSize.getWidth(), deviceSize.getHeight());
//        if (crop != null) {
//            if (rotated) {
//                // the crop (provided by the user) is expressed in the natural orientation
//                crop = flipRect(crop);
//            }
//            if (!contentRect.intersect(crop)) {
//                // intersect() changes contentRect so that it is intersected with crop
//                Ln.w("Crop rectangle (" + formatCrop(crop) + ") does not intersect device screen (" + formatCrop(deviceSize.toRect()) + ")");
//                contentRect = new Rect(); // empty
//            }
//        }

        Size videoSize = computeVideoSize(contentRect.width(), contentRect.height());
        return new ScreenInfo(contentRect, videoSize, rotated);
    }

//    private static String formatCrop(Rect rect) {
//        return rect.width() + ":" + rect.height() + ":" + rect.left + ":" + rect.top;
//    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Size computeVideoSize(int w, int h) {
        // Compute the video size and the padding of the content inside this video.
        // Principle:
        // - scale down the great side of the screen to maxSize (if necessary);
        // - scale down the other side so that the aspect ratio is preserved;
        // - round this value to the nearest multiple of 8 (H.264 only accepts multiples of 8)
        StringBuilder sb = new StringBuilder(String.format("computeVideoSize() (%d, %d) => ", w, h));
        w &= ~7; // in case it's not a multiple of 8
        h &= ~7;
        sb.append(String.format("(%d, %d)", w, h));
        System.err.println(sb.toString());
//        if (maxSize > 0) {
//            if (BuildConfig.DEBUG && maxSize % 8 != 0) {
//                throw new AssertionError("Max size must be a multiple of 8");
//            }
//            boolean portrait = h > w;
//            int major = portrait ? h : w;
//            int minor = portrait ? w : h;
//            if (major > maxSize) {
//                int minorExact = minor * maxSize / major;
//                // +4 to round the value to the nearest multiple of 8
//                minor = (minorExact + 4) & ~7;
//                major = maxSize;
//            }
//            w = portrait ? minor : major;
//            h = portrait ? major : minor;
//        }

//        if (correctedValue != null) {
//            w += correctedValue.x;
//            h += correctedValue.y;
//            System.err.printf("computeVideoSize() 修正之后的值 (%d, %d)\n", w, h);
//        } else {
//            System.err.printf("computeVideoSize() 没有修正值\n");
//        }
        return new Size(w, h);
    }

    public Point getPhysicalPoint(Point point, Size screenSize) {
        // it hides the field on purpose, to read it with a lock
//        @SuppressWarnings("checkstyle:HiddenField")
        ScreenInfo screenInfo = getScreenInfo(); // read with synchronization
//        Size videoSize = screenInfo.getVideoSize();
//        if (!videoSize.equals(screenSize) &&
//                (videoSize.getWidth() != screenSize.getHeight() || videoSize.getHeight() != screenSize.getWidth())) {
//            // The client sends a click relative to a video with wrong dimensions,
//            // the device may have been rotated since the event was generated, so ignore the event
//            System.err.println("videoSize:" + videoSize + " screenSize:" + screenSize);
//            return null;
//        }
        Rect contentRect = screenInfo.getContentRect();
        if (contentRect.width() == screenSize.getWidth() &&
                contentRect.height() == screenSize.getHeight() &&
                contentRect.left == 0 &&
                contentRect.top == 0) {
            return point;
        }
        point.x = contentRect.left + point.x * contentRect.width() / screenSize.getWidth();
        point.y = contentRect.top + point.y * contentRect.height() / screenSize.getHeight();
        return point;
    }

    public static String getDeviceName() {
        return Build.MODEL;
    }

    public boolean injectInputEvent(InputEvent inputEvent, int mode) {
        return serviceManager.getInputManager().injectInputEvent(inputEvent, mode);
    }

    public boolean isScreenOn() {
        return serviceManager.getPowerManager().isScreenOn();
    }

    public void registerRotationWatcher(IRotationWatcher rotationWatcher) {
        serviceManager.getWindowManager().registerRotationWatcher(rotationWatcher);
    }

    public synchronized void setRotationListener(RotationListener rotationListener) {
        this.rotationListener = rotationListener;
    }

    static Rect flipRect(Rect crop) {
        return new Rect(crop.top, crop.left, crop.bottom, crop.right);
    }
}
