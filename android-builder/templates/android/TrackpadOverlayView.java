package __PACKAGE__;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Optional Android-only pointer and magnifier layer. It does not modify the editor's egui widgets.
 * When trackpad mode is off and loupe is off, touches outside the floating handle pass through.
 */
final class TrackpadOverlayView extends View {
    private static final String TAG = "KinoraTrackpad";
    private static final String PREFS = "com.kinora.effect_trackpad";
    private static final long LONG_PRESS_MS = 430L;
    private static final long TAP_MS = 35L;
    private static final int G_NONE = 0;
    private static final int G_HANDLE = 1;
    private static final int G_TRACKPAD = 2;
    private static final int G_DIRECT_LOUPE = 3;
    private static final int G_MULTI = 4;
    private static final int G_LEFT_BUTTON = 5;
    private static final int G_RIGHT_BUTTON = 6;
    private static final int G_SETTINGS = 7;
    private static final String[] SETTING_LABELS = {"Trackpad", "Loupe", "Auto loupe", "Speed", "Magnification", "Cursor size", "Opacity", "Diagnostic report", "Close"};

    private final Activity activity;
    private final View nativeSurface;
    private final SurfaceView pixelSurface;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Choreographer choreographer = Choreographer.getInstance();
    private final float density;
    private final int touchSlop;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF toggleRect = new RectF();
    private final RectF clickRect = new RectF();
    private final RectF rightRect = new RectF();
    private final RectF settingsRect = new RectF();
    private final RectF panelRect = new RectF();
    private final RectF loupeDest = new RectF();
    private final Path loupeClip = new Path();
    private final Rect sourceRect = new Rect();
    private final RectF[] settingRows = new RectF[9];
    private final float[] overlaySurfaceBounds = new float[4];
    private final float[] surfacePoint = new float[2];
    private final int[] overlayLocation = new int[2];
    private final int[] surfaceLocation = new int[2];
    private final MotionEvent.PointerProperties[] mousePointerProperties = {new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] mousePointerCoords = {new MotionEvent.PointerCoords()};
    private final Choreographer.FrameCallback loupeFrame = this::onLoupeFrame;

    private boolean trackpadEnabled;
    private boolean loupeEnabled;
    private boolean autoLoupe;
    private boolean settingsOpen;
    private boolean disposed;
    private boolean handleMoved;
    private boolean handleLongPressed;
    private boolean gestureMoved;
    private boolean dragActive;
    private boolean directLoupeActive;
    private boolean pixelCopyPending;
    private boolean loupeHasFrame;
    private boolean warningLogged;
    private boolean fingerForwarded;
    private int gesture = G_NONE;
    private int settingsSlider = -1;
    private int settingsRowDown = -1;
    private int mouseButtons;
    private int captureGeneration;
    private long mouseDownTime;
    private long lastCaptureAt;
    private long gestureDownAt;
    private float handleDownX;
    private float handleDownY;
    private float lastFingerX;
    private float lastFingerY;
    private float fingerDownX;
    private float fingerDownY;
    private float cursorX = Float.NaN;
    private float cursorY = Float.NaN;
    private float lensSourceX;
    private float lensSourceY;
    private float toggleX = Float.NaN;
    private float toggleY = Float.NaN;
    private float speed;
    private float magnification;
    private float cursorSizeDp;
    private float overlayOpacity;
    private static final float LOUPE_DIAMETER_DP = 148f;
    private Bitmap loupeBitmap;
    private MotionEvent savedDownEvent;
    private MotionEvent lastDirectEvent;
    private Runnable pendingLongPress;

    TrackpadOverlayView(Activity activity, View nativeSurface) {
        super(activity);
        this.activity = activity;
        this.nativeSurface = nativeSurface;
        this.pixelSurface = nativeSurface instanceof SurfaceView ? (SurfaceView) nativeSurface : null;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.density = getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        this.trackpadEnabled = prefs.getBoolean("trackpad_enabled", false);
        this.loupeEnabled = prefs.getBoolean("loupe_enabled", false);
        this.autoLoupe = prefs.getBoolean("auto_loupe", true);
        this.speed = clamp(prefs.getFloat("pointer_speed", 1.35f), 0.35f, 3.0f);
        this.magnification = clamp(prefs.getFloat("magnification", 2.5f), 2.0f, 4.0f);
        this.cursorSizeDp = clamp(prefs.getFloat("cursor_size_dp", 22f), 12f, 40f);
        this.overlayOpacity = clamp(prefs.getFloat("cursor_opacity", 0.92f), 0.2f, 1.0f);
        if (prefs.contains("toggle_x_fraction")) {
            toggleX = prefs.getFloat("toggle_x_fraction", 0.88f) * Math.max(1, getResources().getDisplayMetrics().widthPixels);
            toggleY = prefs.getFloat("toggle_y_fraction", 0.08f) * Math.max(1, getResources().getDisplayMetrics().heightPixels);
        }
        for (int i = 0; i < settingRows.length; i++) settingRows[i] = new RectF();
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (pixelSurface == null) Log.w(TAG, "Native surface is not a SurfaceView; loupe capture will be unavailable");
    }

    private void onLoupeFrame(long frameTimeNanos) {
        if (!disposed && loupeVisible()) {
            long now = SystemClock.uptimeMillis();
            if (now - lastCaptureAt >= 16L) requestLoupeCapture(lensSourceX, lensSourceY);
            invalidate();
            choreographer.postFrameCallback(loupeFrame);
        }
    }

    void onHostFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            cancelOverlayLongPress();
            if (dragActive) endMouseDrag();
            if (gesture == G_DIRECT_LOUPE && fingerForwarded && lastDirectEvent != null && !directLoupeActive) {
                dispatchTouchCopy(lastDirectEvent, true);
            }
            hideManualLoupe();
            stopLoupeFrames();
        } else if (loupeVisible()) {
            startLoupeFrames();
            requestLoupeCapture(lensSourceX, lensSourceY);
        }
        invalidate();
    }

    void dispose() {
        disposed = true;
        cancelOverlayLongPress();
        stopLoupeFrames();
        if (dragActive) endMouseDrag();
        recycleEvent(savedDownEvent);
        recycleEvent(lastDirectEvent);
        savedDownEvent = null;
        lastDirectEvent = null;
        if (loupeBitmap != null) {
            if (!pixelCopyPending) loupeBitmap.recycle();
            loupeBitmap = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (trackpadEnabled) {
            ensureCursorInitialized();
            sendMouseMove(cursorX, cursorY, false);
            updateAutoLoupe();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelOverlayLongPress();
        stopLoupeFrames();
        if (dragActive) endMouseDrag();
        super.onDetachedFromWindow();
    }

    private void ensureBounds() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float margin = dp(12f);
        float size = dp(48f);
        if (Float.isNaN(toggleX) || Float.isNaN(toggleY)) {
            toggleX = getWidth() - size - margin;
            toggleY = margin * 2f;
        }
        toggleX = clamp(toggleX, margin, Math.max(margin, getWidth() - size - margin));
        toggleY = clamp(toggleY, margin, Math.max(margin, getHeight() - size - margin));
        toggleRect.set(toggleX, toggleY, toggleX + size, toggleY + size);
        clickRect.setEmpty();
        rightRect.setEmpty();
        settingsRect.setEmpty();
        if (trackpadEnabled) {
            float gap = dp(6f);
            float height = dp(40f);
            float clickWidth = dp(68f);
            float rightWidth = dp(68f);
            float settingsWidth = dp(52f);
            float total = clickWidth + rightWidth + settingsWidth + gap * 2f;
            boolean placeRight = toggleX + size + gap + total <= getWidth() - margin;
            float x = placeRight ? toggleX + size + gap : Math.max(margin, toggleX - gap - total);
            float y = toggleY + (size - height) * 0.5f;
            clickRect.set(x, y, x + clickWidth, y + height);
            x += clickWidth + gap;
            rightRect.set(x, y, x + rightWidth, y + height);
            x += rightWidth + gap;
            settingsRect.set(x, y, x + settingsWidth, y + height);
        }
        if (settingsOpen) {
            float width = Math.min(dp(290f), Math.max(dp(220f), getWidth() - margin * 2f));
            float rowHeight = dp(31f);
            float height = dp(40f) + rowHeight * settingRows.length;
            float left = clamp(toggleX, margin, Math.max(margin, getWidth() - width - margin));
            float top = toggleY + size + margin;
            if (top + height > getHeight() - margin) top = Math.max(margin, toggleY - height - margin);
            if (top + height > getHeight() - margin) top = Math.max(margin, getHeight() - height - margin);
            panelRect.set(left, top, left + width, Math.min(getHeight() - margin, top + height));
            for (int i = 0; i < settingRows.length; i++) {
                float rowTop = top + dp(40f) + i * rowHeight;
                settingRows[i].set(left + dp(8f), rowTop, left + width - dp(8f), rowTop + rowHeight);
            }
        } else {
            panelRect.setEmpty();
            for (RectF row : settingRows) row.setEmpty();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            if (Float.isNaN(cursorX) || Float.isNaN(cursorY)) {
                cursorX = w * 0.5f;
                cursorY = h * 0.5f;
            }
            ensureBounds();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ensureBounds();
        if (loupeVisible()) drawLoupe(canvas);
        if (trackpadEnabled && !Float.isNaN(cursorX)) drawCursor(canvas);
        drawControls(canvas);
        if (settingsOpen) drawSettings(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (disposed) return false;
        ensureBounds();
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return beginGesture(event);
            case MotionEvent.ACTION_POINTER_DOWN:
                return onPointerDown(event);
            case MotionEvent.ACTION_MOVE:
                return onMove(event);
            case MotionEvent.ACTION_POINTER_UP:
                return onPointerUp(event);
            case MotionEvent.ACTION_UP: {
                boolean handled = gesture != G_NONE || trackpadEnabled || loupeEnabled || settingsOpen;
                finishGesture(event, false);
                return handled;
            }
            case MotionEvent.ACTION_CANCEL:
                finishGesture(event, true);
                return true;
            default:
                return gesture != G_NONE;
        }
    }

    private boolean beginGesture(MotionEvent event) {
        float x = event.getX(0);
        float y = event.getY(0);
        gestureDownAt = SystemClock.uptimeMillis();
        handleDownX = x;
        handleDownY = y;
        lastFingerX = x;
        lastFingerY = y;
        fingerDownX = x;
        fingerDownY = y;
        gestureMoved = false;
        handleMoved = false;
        handleLongPressed = false;
        directLoupeActive = false;
        settingsSlider = -1;
        settingsRowDown = -1;
        if (settingsOpen && panelRect.contains(x, y)) {
            gesture = G_SETTINGS;
            settingsRowDown = settingRowAt(x, y);
            if (settingsRowDown >= 3 && settingsRowDown <= 6) {
                settingsSlider = settingsRowDown;
                updateSettingFromTouch(event);
            }
            invalidate();
            return true;
        }
        if (toggleRect.contains(x, y)) {
            gesture = G_HANDLE;
            scheduleHandleLongPress();
            return true;
        }
        if (trackpadEnabled && clickRect.contains(x, y)) {
            gesture = G_LEFT_BUTTON;
            scheduleButtonDrag();
            return true;
        }
        if (trackpadEnabled && rightRect.contains(x, y)) {
            gesture = G_RIGHT_BUTTON;
            return true;
        }
        if (trackpadEnabled && settingsRect.contains(x, y)) {
            gesture = G_SETTINGS;
            settingsOpen = !settingsOpen;
            invalidate();
            return true;
        }
        if (settingsOpen) {
            settingsOpen = false;
            ensureBounds();
        }
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                || event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            gesture = G_NONE;
            return false;
        }
        if (trackpadEnabled) {
            gesture = G_TRACKPAD;
            saveSavedDownEvent(event);
            scheduleTrackpadLongPress();
            return true;
        }
        if (loupeEnabled && nativeSurface != null) {
            gesture = G_DIRECT_LOUPE;
            fingerForwarded = true;
            saveSavedDownEvent(event);
            saveLastDirectEvent(event);
            dispatchTouchCopy(event, false);
            scheduleDirectLoupeLongPress();
            return true;
        }
        gesture = G_NONE;
        return false;
    }

    private boolean onPointerDown(MotionEvent event) {
        cancelOverlayLongPress();
        if (gesture == G_TRACKPAD) {
            if (dragActive) endMouseDrag();
            if (savedDownEvent != null) dispatchTouchCopy(savedDownEvent, false);
            dispatchTouchCopy(event, false);
            gesture = G_MULTI;
            fingerForwarded = true;
            invalidate();
            return true;
        }
        if (gesture == G_DIRECT_LOUPE) {
            if (directLoupeActive) {
                if (lastDirectEvent != null) dispatchTouchCopy(lastDirectEvent, true);
                directLoupeActive = false;
                if (savedDownEvent != null) dispatchTouchCopy(savedDownEvent, false);
            }
            dispatchTouchCopy(event, false);
            saveLastDirectEvent(event);
            gesture = G_MULTI;
            fingerForwarded = true;
            return true;
        }
        if (gesture == G_MULTI) {
            dispatchTouchCopy(event, false);
            return true;
        }
        return gesture != G_NONE;
    }

    private boolean onPointerUp(MotionEvent event) {
        if (gesture == G_MULTI) {
            dispatchTouchCopy(event, false);
            return true;
        }
        return gesture != G_NONE;
    }

    private boolean onMove(MotionEvent event) {
        float x = event.getX(0);
        float y = event.getY(0);
        switch (gesture) {
            case G_HANDLE: {
                float dx = x - handleDownX;
                float dy = y - handleDownY;
                if (!handleMoved && Math.hypot(dx, dy) > touchSlop) {
                    handleMoved = true;
                    cancelOverlayLongPress();
                }
                if (handleMoved) {
                    toggleX += x - lastFingerX;
                    toggleY += y - lastFingerY;
                    ensureBounds();
                    invalidate();
                }
                break;
            }
            case G_TRACKPAD:
                if (!gestureMoved && Math.hypot(x - fingerDownX, y - fingerDownY) > touchSlop) {
                    gestureMoved = true;
                    if (!dragActive) cancelOverlayLongPress();
                }
                moveCursorBy(x - lastFingerX, y - lastFingerY);
                if (dragActive) sendMouseMove(cursorX, cursorY, true);
                updateAutoLoupe();
                invalidate();
                break;
            case G_LEFT_BUTTON:
                if (dragActive) {
                    if (!gestureMoved && Math.hypot(x - fingerDownX, y - fingerDownY) > touchSlop) gestureMoved = true;
                    moveCursorBy(x - lastFingerX, y - lastFingerY);
                    sendMouseMove(cursorX, cursorY, true);
                    updateAutoLoupe();
                }
                break;
            case G_DIRECT_LOUPE:
                if (directLoupeActive) {
                    lensSourceX = x;
                    lensSourceY = y;
                    requestLoupeCapture(x, y);
                    invalidate();
                } else {
                    if (!gestureMoved && Math.hypot(x - fingerDownX, y - fingerDownY) > touchSlop) {
                        gestureMoved = true;
                        cancelOverlayLongPress();
                    }
                    dispatchTouchCopy(event, false);
                    saveLastDirectEvent(event);
                }
                break;
            case G_MULTI:
                dispatchTouchCopy(event, false);
                break;
            case G_SETTINGS:
                if (settingsSlider >= 3 && settingsSlider <= 6) updateSettingFromTouch(event);
                break;
            default:
                return false;
        }
        lastFingerX = x;
        lastFingerY = y;
        return true;
    }

    private void finishGesture(MotionEvent event, boolean cancelled) {
        cancelOverlayLongPress();
        int finishing = gesture;
        if (finishing == G_HANDLE) {
            if (handleMoved) saveTogglePosition();
            else if (!handleLongPressed && !cancelled) setTrackpadEnabled(!trackpadEnabled);
        } else if (finishing == G_TRACKPAD) {
            if (dragActive) endMouseDrag();
            else if (!cancelled && !gestureMoved) clickAtCursor(false);
        } else if (finishing == G_LEFT_BUTTON) {
            if (dragActive) endMouseDrag();
            else if (!cancelled) clickAtCursor(false);
        } else if (finishing == G_RIGHT_BUTTON) {
            if (!cancelled) clickAtCursor(true);
        } else if (finishing == G_DIRECT_LOUPE) {
            if (directLoupeActive) {
                if (!cancelled) dispatchTouchClick(event.getX(0), event.getY(0));
                hideManualLoupe();
            } else if (fingerForwarded) {
                dispatchTouchCopy(event, cancelled);
            }
        } else if (finishing == G_MULTI) {
            dispatchTouchCopy(event, cancelled);
        } else if (finishing == G_SETTINGS && !cancelled) {
            int rowUp = settingRowAt(event.getX(0), event.getY(0));
            if ((settingsSlider < 3 || settingsSlider >= 7) && rowUp == settingsRowDown) activateSettingRow(rowUp);
        }
        gesture = G_NONE;
        gestureMoved = false;
        handleMoved = false;
        handleLongPressed = false;
        directLoupeActive = false;
        fingerForwarded = false;
        settingsSlider = -1;
        settingsRowDown = -1;
        recycleEvent(savedDownEvent);
        recycleEvent(lastDirectEvent);
        savedDownEvent = null;
        lastDirectEvent = null;
        invalidate();
    }

    private void scheduleHandleLongPress() {
        cancelOverlayLongPress();
        pendingLongPress = () -> {
            if (gesture == G_HANDLE && !handleMoved) {
                handleLongPressed = true;
                settingsOpen = !settingsOpen;
                ensureBounds();
                invalidate();
            }
        };
        mainHandler.postDelayed(pendingLongPress, LONG_PRESS_MS);
    }

    private void scheduleTrackpadLongPress() {
        cancelOverlayLongPress();
        pendingLongPress = () -> {
            if (gesture == G_TRACKPAD && !gestureMoved && trackpadEnabled) {
                beginMouseDrag(cursorX, cursorY);
                invalidate();
            }
        };
        mainHandler.postDelayed(pendingLongPress, LONG_PRESS_MS);
    }

    private void scheduleButtonDrag() {
        cancelOverlayLongPress();
        pendingLongPress = () -> {
            if (gesture == G_LEFT_BUTTON && trackpadEnabled) {
                beginMouseDrag(cursorX, cursorY);
                invalidate();
            }
        };
        mainHandler.postDelayed(pendingLongPress, LONG_PRESS_MS);
    }

    private void scheduleDirectLoupeLongPress() {
        cancelOverlayLongPress();
        pendingLongPress = () -> {
            if (gesture == G_DIRECT_LOUPE && !gestureMoved && loupeEnabled && lastDirectEvent != null) {
                dispatchTouchCopy(lastDirectEvent, true);
                directLoupeActive = true;
                lensSourceX = lastDirectEvent.getX(0);
                lensSourceY = lastDirectEvent.getY(0);
                startLoupeFrames();
                requestLoupeCapture(lensSourceX, lensSourceY);
                invalidate();
            }
        };
        mainHandler.postDelayed(pendingLongPress, LONG_PRESS_MS);
    }

    private void cancelOverlayLongPress() {
        if (pendingLongPress != null) {
            mainHandler.removeCallbacks(pendingLongPress);
            pendingLongPress = null;
        }
    }

    private void setTrackpadEnabled(boolean enabled) {
        if (trackpadEnabled == enabled) return;
        trackpadEnabled = enabled;
        prefs.edit().putBoolean("trackpad_enabled", enabled).apply();
        if (!enabled) {
            if (dragActive) endMouseDrag();
            stopLoupeFrames();
        } else {
            ensureCursorInitialized();
            sendMouseMove(cursorX, cursorY, false);
            updateAutoLoupe();
        }
        ensureBounds();
        invalidate();
        Log.i(TAG, "trackpad enabled=" + enabled);
    }

    private void setLoupeEnabled(boolean enabled) {
        loupeEnabled = enabled;
        prefs.edit().putBoolean("loupe_enabled", enabled).apply();
        if (!enabled) {
            hideManualLoupe();
            stopLoupeFrames();
        } else if (trackpadEnabled && autoLoupe) {
            ensureCursorInitialized();
            lensSourceX = cursorX;
            lensSourceY = cursorY;
            startLoupeFrames();
            requestLoupeCapture(cursorX, cursorY);
        }
        invalidate();
    }

    private void moveCursorBy(float dx, float dy) {
        ensureCursorInitialized();
        cursorX += dx * speed;
        cursorY += dy * speed;
        float[] bounds = surfaceBoundsInOverlay();
        cursorX = clamp(cursorX, bounds[0], bounds[2]);
        cursorY = clamp(cursorY, bounds[1], bounds[3]);
        if (!dragActive) sendMouseMove(cursorX, cursorY, false);
    }

    private void ensureCursorInitialized() {
        if (Float.isNaN(cursorX) || Float.isNaN(cursorY)) {
            float[] bounds = surfaceBoundsInOverlay();
            cursorX = (bounds[0] + bounds[2]) * 0.5f;
            cursorY = (bounds[1] + bounds[3]) * 0.5f;
        }
    }

    private float[] surfaceBoundsInOverlay() {
        float left = 0f, top = 0f, right = Math.max(1, getWidth()), bottom = Math.max(1, getHeight());
        if (nativeSurface != null && nativeSurface.getWidth() > 0 && nativeSurface.getHeight() > 0) {
            getLocationOnScreen(overlayLocation);
            nativeSurface.getLocationOnScreen(surfaceLocation);
            left = surfaceLocation[0] - overlayLocation[0];
            top = surfaceLocation[1] - overlayLocation[1];
            right = left + nativeSurface.getWidth();
            bottom = top + nativeSurface.getHeight();
        }
        overlaySurfaceBounds[0] = left;
        overlaySurfaceBounds[1] = top;
        overlaySurfaceBounds[2] = right;
        overlaySurfaceBounds[3] = bottom;
        return overlaySurfaceBounds;
    }

    private float[] toSurface(float overlayX, float overlayY) {
        if (nativeSurface == null) {
            surfacePoint[0] = overlayX;
            surfacePoint[1] = overlayY;
            return surfacePoint;
        }
        getLocationOnScreen(overlayLocation);
        nativeSurface.getLocationOnScreen(surfaceLocation);
        surfacePoint[0] = overlayX + overlayLocation[0] - surfaceLocation[0];
        surfacePoint[1] = overlayY + overlayLocation[1] - surfaceLocation[1];
        return surfacePoint;
    }

    private void dispatchTouchCopy(MotionEvent event, boolean asCancel) {
        if (nativeSurface == null) return;
        MotionEvent copy = MotionEvent.obtain(event);
        getLocationOnScreen(overlayLocation);
        nativeSurface.getLocationOnScreen(surfaceLocation);
        copy.offsetLocation(overlayLocation[0] - surfaceLocation[0], overlayLocation[1] - surfaceLocation[1]);
        if (asCancel) copy.setAction(MotionEvent.ACTION_CANCEL);
        nativeSurface.dispatchTouchEvent(copy);
        copy.recycle();
    }

    private void dispatchTouchClick(float x, float y) {
        if (nativeSurface == null) return;
        float[] point = toSurface(x, y);
        long down = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        nativeSurface.dispatchTouchEvent(downEvent);
        MotionEvent upEvent = MotionEvent.obtain(down, down + TAP_MS, MotionEvent.ACTION_UP, point[0], point[1], 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        nativeSurface.dispatchTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();
    }

    private MotionEvent obtainMouseEvent(long downTime, long eventTime, int action, float x, float y, int buttonState) {
        MotionEvent.PointerProperties properties = mousePointerProperties[0];
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coordinates = mousePointerCoords[0];
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = 1f;
        coordinates.size = 1f;
        return MotionEvent.obtain(
                downTime, eventTime, action, 1, mousePointerProperties, mousePointerCoords,
                0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
    }

    private void sendMouseMove(float x, float y, boolean held) {
        if (nativeSurface == null) return;
        float[] point = toSurface(x, y);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = obtainMouseEvent(
                dragActive ? mouseDownTime : now, now, MotionEvent.ACTION_MOVE,
                point[0], point[1], held ? MotionEvent.BUTTON_PRIMARY : mouseButtons);
        nativeSurface.dispatchTouchEvent(event);
        event.recycle();
    }

    private void sendMouseButton(int button, boolean pressed, float x, float y) {
        if (nativeSurface == null) return;
        float[] point = toSurface(x, y);
        long now = SystemClock.uptimeMillis();
        int action;
        if (button == MotionEvent.BUTTON_PRIMARY) {
            action = pressed ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
            if (pressed) mouseDownTime = now;
        } else {
            action = pressed ? MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE;
            if (pressed && mouseDownTime == 0L) mouseDownTime = now;
        }
        int eventButtonState = mouseButtons | button;
        if (pressed) mouseButtons |= button;
        else mouseButtons &= ~button;
        MotionEvent event = obtainMouseEvent(
                mouseDownTime == 0L ? now : mouseDownTime, now, action,
                point[0], point[1], eventButtonState);
        if (button == MotionEvent.BUTTON_PRIMARY) nativeSurface.dispatchTouchEvent(event);
        else nativeSurface.dispatchGenericMotionEvent(event);
        event.recycle();
        if (!pressed && mouseButtons == 0) mouseDownTime = 0L;
    }

    private void beginMouseDrag(float x, float y) {
        if (dragActive) return;
        ensureCursorInitialized();
        sendMouseMove(x, y, false);
        sendMouseButton(MotionEvent.BUTTON_PRIMARY, true, x, y);
        dragActive = true;
        Log.d(TAG, "pointer drag started");
    }

    private void endMouseDrag() {
        if (!dragActive) return;
        sendMouseButton(MotionEvent.BUTTON_PRIMARY, false, cursorX, cursorY);
        dragActive = false;
        Log.d(TAG, "pointer drag ended");
    }

    private void clickAtCursor(boolean rightClick) {
        ensureCursorInitialized();
        int button = rightClick ? MotionEvent.BUTTON_SECONDARY : MotionEvent.BUTTON_PRIMARY;
        sendMouseMove(cursorX, cursorY, false);
        sendMouseButton(button, true, cursorX, cursorY);
        sendMouseButton(button, false, cursorX, cursorY);
    }

    private void updateAutoLoupe() {
        if (trackpadEnabled && loupeEnabled && autoLoupe) {
            lensSourceX = cursorX;
            lensSourceY = cursorY;
            startLoupeFrames();
            requestLoupeCapture(cursorX, cursorY);
        }
    }

    private boolean loupeVisible() {
        return loupeEnabled && ((trackpadEnabled && autoLoupe) || directLoupeActive) && !disposed;
    }

    private void startLoupeFrames() {
        if (disposed || !loupeVisible()) return;
        choreographer.removeFrameCallback(loupeFrame);
        choreographer.postFrameCallback(loupeFrame);
    }

    private void stopLoupeFrames() { choreographer.removeFrameCallback(loupeFrame); }

    private void hideManualLoupe() {
        directLoupeActive = false;
        captureGeneration++;
        loupeHasFrame = false;
        stopLoupeFrames();
    }

    private void requestLoupeCapture(float x, float y) {
        if (!loupeVisible() || pixelCopyPending || pixelSurface == null || !pixelSurface.getHolder().getSurface().isValid()) return;
        int surfaceWidth = pixelSurface.getWidth();
        int surfaceHeight = pixelSurface.getHeight();
        if (surfaceWidth < 2 || surfaceHeight < 2) return;
        float[] point = toSurface(x, y);
        int side = Math.max(24, Math.round(dp(LOUPE_DIAMETER_DP) / magnification));
        side = Math.min(side, Math.min(surfaceWidth, surfaceHeight));
        if (side < 2) return;
        ensureLoupeBitmap(side);
        int left = Math.round(point[0] - side * 0.5f);
        int top = Math.round(point[1] - side * 0.5f);
        left = Math.max(0, Math.min(surfaceWidth - side, left));
        top = Math.max(0, Math.min(surfaceHeight - side, top));
        sourceRect.set(left, top, left + side, top + side);
        pixelCopyPending = true;
        lastCaptureAt = SystemClock.uptimeMillis();
        int generation = captureGeneration;
        try {
            PixelCopy.request(pixelSurface, sourceRect, loupeBitmap, result -> {
                pixelCopyPending = false;
                if (disposed || generation != captureGeneration) return;
                if (result == PixelCopy.SUCCESS) {
                    loupeHasFrame = true;
                    warningLogged = false;
                } else if (!warningLogged) {
                    warningLogged = true;
                    Log.w(TAG, "PixelCopy loupe capture unavailable, result=" + result);
                }
                invalidate();
            }, mainHandler);
        } catch (Throwable error) {
            pixelCopyPending = false;
            if (!warningLogged) {
                warningLogged = true;
                Log.w(TAG, "PixelCopy request failed", error);
            }
        }
    }

    private void ensureLoupeBitmap(int side) {
        if (loupeBitmap != null && loupeBitmap.getWidth() == side && !loupeBitmap.isRecycled()) return;
        if (loupeBitmap != null) loupeBitmap.recycle();
        loupeBitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        loupeHasFrame = false;
    }

    private void drawLoupe(Canvas canvas) {
        float diameter = dp(LOUPE_DIAMETER_DP);
        float margin = dp(8f);
        float cx = lensSourceX;
        float cy = lensSourceY;
        float lx = cx + diameter * 0.82f;
        float ly = cy - diameter * 0.82f;
        if (cx > getWidth() * 0.62f) lx = cx - diameter * 0.82f;
        if (ly - diameter * 0.5f < margin) ly = cy + diameter * 0.82f;
        lx = clamp(lx, margin + diameter * 0.5f, getWidth() - margin - diameter * 0.5f);
        ly = clamp(ly, margin + diameter * 0.5f, getHeight() - margin - diameter * 0.5f);
        float radius = diameter * 0.5f;
        int save = canvas.save();
        loupeClip.reset();
        loupeClip.addCircle(lx, ly, radius, Path.Direction.CW);
        canvas.clipPath(loupeClip);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(31, 39, 49));
        canvas.drawCircle(lx, ly, radius, paint);
        if (loupeHasFrame && loupeBitmap != null && !loupeBitmap.isRecycled()) {
            loupeDest.set(lx - radius, ly - radius, lx + radius, ly + radius);
            canvas.drawBitmap(loupeBitmap, null, loupeDest, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2f));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(lx, ly, radius * 0.45f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(save);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3f));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(lx, ly, radius, paint);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(0xFF111820);
        canvas.drawCircle(lx, ly, radius - dp(2f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, dp(4f), paint);
    }

    private void drawCursor(Canvas canvas) {
        float radius = dp(cursorSizeDp * 0.5f);
        int alpha = Math.round(255f * overlayOpacity);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 15, 20, 25));
        canvas.drawCircle(cursorX, cursorY, radius + dp(3f), paint);
        paint.setColor(Color.argb(alpha, 255, 255, 255));
        canvas.drawCircle(cursorX, cursorY, radius, paint);
        paint.setColor(Color.argb(255, 30, 144, 255));
        canvas.drawCircle(cursorX, cursorY, Math.max(dp(2f), radius * 0.28f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(alpha, 8, 18, 28));
        canvas.drawCircle(cursorX, cursorY, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawControls(Canvas canvas) {
        drawButton(canvas, toggleRect, trackpadEnabled ? "TP ON" : "TP", trackpadEnabled);
        if (trackpadEnabled) {
            drawButton(canvas, clickRect, "Click", false);
            drawButton(canvas, rightRect, "Right", false);
            drawButton(canvas, settingsRect, "Set", false);
        }
    }

    private void drawButton(Canvas canvas, RectF bounds, String label, boolean active) {
        if (bounds.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? 0xEE1478B8 : 0xDD18232E);
        canvas.drawRoundRect(bounds, dp(12f), dp(12f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(active ? Color.WHITE : 0xDDE8EEF5);
        canvas.drawRoundRect(bounds, dp(12f), dp(12f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(12f));
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = bounds.centerY() - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(label, bounds.centerX(), baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSettings(Canvas canvas) {
        if (panelRect.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF018202A);
        canvas.drawRoundRect(panelRect, dp(12f), dp(12f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(0xFF8C9AA8);
        canvas.drawRoundRect(panelRect, dp(12f), dp(12f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(14f));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Trackpad / Loupe", panelRect.left + dp(12f), panelRect.top + dp(25f), paint);
        for (int i = 0; i < settingRows.length; i++) {
            RectF row = settingRows[i];
            float cy = row.centerY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE8EDF2);
            paint.setTextSize(dp(12f));
            canvas.drawText(SETTING_LABELS[i], row.left + dp(4f), cy + dp(4f), paint);
            if (i <= 2) {
                boolean value = i == 0 ? trackpadEnabled : i == 1 ? loupeEnabled : autoLoupe;
                float width = dp(34f), height = dp(18f);
                float sx = row.right - width - dp(6f);
                tempRect.set(sx, cy - height * 0.5f, sx + width, cy + height * 0.5f);
                paint.setColor(value ? 0xFF168AD0 : 0xFF56616D);
                canvas.drawRoundRect(tempRect, height * 0.5f, height * 0.5f, paint);
                paint.setColor(Color.WHITE);
                float knob = dp(12f);
                canvas.drawCircle(value ? sx + width - knob * 0.65f : sx + knob * 0.65f, cy, knob * 0.45f, paint);
            } else if (i >= 3 && i <= 6) {
                float sliderLeft = row.left + dp(111f);
                float sliderRight = row.right - dp(32f);
                float value = settingValue(i);
                paint.setColor(0xFF617080);
                paint.setStrokeWidth(dp(3f));
                canvas.drawLine(sliderLeft, cy, sliderRight, cy, paint);
                paint.setColor(0xFF25A3ED);
                canvas.drawLine(sliderLeft, cy, sliderLeft + (sliderRight - sliderLeft) * value, cy, paint);
                canvas.drawCircle(sliderLeft + (sliderRight - sliderLeft) * value, cy, dp(6f), paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(dp(10f));
                canvas.drawText(settingValueLabel(i), sliderRight + dp(5f), cy + dp(4f), paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private final RectF tempRect = new RectF();

    private int settingRowAt(float x, float y) {
        for (int i = 0; i < settingRows.length; i++) if (settingRows[i].contains(x, y)) return i;
        return -1;
    }

    private void activateSettingRow(int row) {
        if (row == 0) setTrackpadEnabled(!trackpadEnabled);
        else if (row == 1) setLoupeEnabled(!loupeEnabled);
        else if (row == 2) {
            autoLoupe = !autoLoupe;
            prefs.edit().putBoolean("auto_loupe", autoLoupe).apply();
            if (trackpadEnabled && loupeEnabled && autoLoupe) updateAutoLoupe();
            else if (!autoLoupe) stopLoupeFrames();
        } else if (row == 7 || row == 8) {
            settingsOpen = false;
        }
        ensureBounds();
        invalidate();
    }

    private void updateSettingFromTouch(MotionEvent event) {
        int row = settingsSlider;
        if (row >= 3 && row <= 6) {
            float left = panelRect.left + dp(119f);
            float right = panelRect.right - dp(43f);
            float t = clamp((event.getX(0) - left) / Math.max(dp(1f), right - left), 0f, 1f);
            if (row == 3) speed = 0.35f + t * 2.65f;
            else if (row == 4) magnification = 2f + t * 2f;
            else if (row == 5) cursorSizeDp = 12f + t * 28f;
            else overlayOpacity = 0.2f + t * 0.8f;
            String key = row == 3 ? "pointer_speed" : row == 4 ? "magnification" : row == 5 ? "cursor_size_dp" : "cursor_opacity";
            float value = row == 3 ? speed : row == 4 ? magnification : row == 5 ? cursorSizeDp : overlayOpacity;
            prefs.edit().putFloat(key, value).apply();
            if (row == 4 && loupeBitmap != null && !pixelCopyPending) {
                loupeBitmap.recycle();
                loupeBitmap = null;
                loupeHasFrame = false;
            }
            invalidate();
        }
    }

    private float settingValue(int row) {
        if (row == 3) return (speed - 0.35f) / 2.65f;
        if (row == 4) return (magnification - 2f) / 2f;
        if (row == 5) return (cursorSizeDp - 12f) / 28f;
        return (overlayOpacity - 0.2f) / 0.8f;
    }

    private String settingValueLabel(int row) {
        if (row == 3) return String.format(java.util.Locale.US, "%.1fx", speed);
        if (row == 4) return String.format(java.util.Locale.US, "%.1fx", magnification);
        if (row == 5) return String.format(java.util.Locale.US, "%.0f", cursorSizeDp);
        return String.format(java.util.Locale.US, "%.0f%%", overlayOpacity * 100f);
    }

    private void saveTogglePosition() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        prefs.edit().putFloat("toggle_x_fraction", toggleX / getWidth()).putFloat("toggle_y_fraction", toggleY / getHeight()).apply();
    }

    private void saveSavedDownEvent(MotionEvent event) {
        recycleEvent(savedDownEvent);
        savedDownEvent = MotionEvent.obtain(event);
    }

    private void saveLastDirectEvent(MotionEvent event) {
        recycleEvent(lastDirectEvent);
        lastDirectEvent = MotionEvent.obtain(event);
    }

    private static void recycleEvent(MotionEvent event) { if (event != null) event.recycle(); }
    private float dp(float value) { return value * density; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
