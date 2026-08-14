package com.textbot.translator;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService extends Service {
    public interface FrameListener { void onFrame(String data); }
    private static volatile FrameListener listener;
    private static volatile ScreenCaptureService instance;

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private final AtomicBoolean requested = new AtomicBoolean(false);
    private int w, h, dpi;

    private WindowManager wm;
    private LinearLayout menu;
    private View hamburger;
    private boolean menuOpen = false;
    private boolean translating = false;

    public static void setFrameListener(FrameListener l) { listener = l; }
    public static boolean isRunning() { return instance != null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();

        Notification n = new Notification.Builder(this, "capture")
                .setContentTitle("SimplyTsL")
                .setContentText("화면 위 SimplyTsL 메뉴가 실행 중입니다.")
                .setSmallIcon(R.drawable.app_icon)
                .setOngoing(true)
                .build();

        // The service initially only hosts the overlay. MediaProjection
        // foreground mode is entered after Android grants the capture token.
        startForeground(1001, n);

        thread = new HandlerThread("SimplyTsLCapture");
        thread.start();
        handler = new Handler(thread.getLooper());

        if (Settings.canDrawOverlays(this)) showHamburger();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.textbot.START_CAPTURE".equals(intent.getAction())) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra("resultData", Intent.class)
                    : intent.getParcelableExtra("resultData");

            if (resultCode != -1 && data != null && projection == null) {
                MediaProjectionManager pm =
                        (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = pm.getMediaProjection(resultCode, data);
                if (Build.VERSION.SDK_INT >= 29) {
                    Notification n = new Notification.Builder(this, "capture")
                            .setContentTitle("SimplyTsL")
                            .setContentText("화면 번역 실행 중")
                            .setSmallIcon(R.drawable.app_icon)
                            .setOngoing(true)
                            .build();
                    startForeground(1001, n,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                }
                setupCapture();
                translating = true;
                requested.set(true);
            }
        }
        return START_NOT_STICKY;
    }

    private TextView menuButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(18, 12, 18, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(238, 25, 25, 25));
        bg.setCornerRadius(18);
        b.setBackground(bg);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private void showHamburger() {
        if (!Settings.canDrawOverlays(this) || wm != null) return;

        wm = (WindowManager)getSystemService(WINDOW_SERVICE);

        hamburger = menuButton("☰");
        hamburger.setTextSize(25);
        GradientDrawable circle = new GradientDrawable();
        circle.setColor(Color.argb(235, 25, 25, 25));
        circle.setShape(GradientDrawable.OVAL);
        hamburger.setBackground(circle);
        hamburger.setOnClickListener(v -> toggleMenu());

        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(
                dp(58), dp(58),
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        hp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        hp.x = dp(12);
        hp.y = 0;

        wm.addView(hamburger, hp);
    }

    private void toggleMenu() {
        if (menuOpen) {
            closeMenu();
            return;
        }

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER_HORIZONTAL);
        menu.setPadding(0, 0, 0, 8);

        TextView translate = menuButton("번역하기");
        TextView stop = menuButton("번역끄기");
        TextView settings = menuButton("설정");
        TextView exit = menuButton("종료");

        translate.setOnClickListener(v -> {
            closeMenu();
            startTranslation();
        });

        stop.setOnClickListener(v -> {
            stopTranslation();
            closeMenu();
        });

        settings.setOnClickListener(v -> {
            closeMenu();
            Intent i = new Intent(this, MainActivity.class);
            i.setAction("com.textbot.OPEN_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        exit.setOnClickListener(v -> stopSelf());

        menu.addView(translate, lp());
        menu.addView(stop, lp());
        menu.addView(settings, lp());
        menu.addView(exit, lp());

        WindowManager.LayoutParams mp = new WindowManager.LayoutParams(
                dp(150), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        mp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        mp.x = dp(12);
        mp.y = dp(145);

        wm.addView(menu, mp);
        menuOpen = true;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        p.setMargins(0, dp(3), 0, dp(3));
        return p;
    }

    private void closeMenu() {
        if (wm != null && menu != null) {
            try { wm.removeView(menu); } catch (Exception ignored) {}
            menu = null;
        }
        menuOpen = false;
    }

    private void startTranslation() {
        if (projection != null) {
            translating = true;
            requested.set(true);
            return;
        }

        // System screen-capture consent must be requested by an Activity.
        Intent i = new Intent(this, MainActivity.class);
        i.setAction("com.textbot.START_TRANSLATION");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void stopTranslation() {
        translating = false;
        requested.set(false);
        if (display != null) {
            display.release();
            display = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    private void setupCapture() {
        if (reader != null || projection == null) return;

        WindowManager x = (WindowManager)getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        x.getDefaultDisplay().getRealMetrics(dm);

        w = Math.min(dm.widthPixels, 1600);
        h = Math.min(dm.heightPixels, 1600);
        dpi = dm.densityDpi;

        reader = ImageReader.newInstance(
                w, h, PixelFormat.RGBA_8888, 2);

        reader.setOnImageAvailableListener(r -> {
            Image im = null;
            try {
                im = r.acquireLatestImage();
                if (im == null || !translating ||
                        !requested.compareAndSet(true, false)) return;

                String data = jpeg(im);
                FrameListener l = listener;
                if (l != null && data != null) l.onFrame(data);

                // Continue capturing automatically. The actual translation
                // layer in the WebView can consume each frame.
                if (translating) {
                    handler.postDelayed(() -> requested.set(true), 1200);
                }
            } finally {
                if (im != null) im.close();
            }
        }, handler);

        display = projection.createVirtualDisplay(
                "SimplyTsLScreen",
                w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, handler);
    }

    private String jpeg(Image im) {
        Image.Plane p = im.getPlanes()[0];
        ByteBuffer b = p.getBuffer();
        int stride = p.getPixelStride();
        int row = p.getRowStride();
        int pad = row - stride * w;

        Bitmap bm = Bitmap.createBitmap(
                w + pad / stride, h, Bitmap.Config.ARGB_8888);
        bm.copyPixelsFromBuffer(b);

        Bitmap crop = Bitmap.createBitmap(bm, 0, 0, w, h);
        if (crop != bm) bm.recycle();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        crop.compress(Bitmap.CompressFormat.JPEG, 78, out);
        crop.recycle();

        return "data:image/jpeg;base64," +
                android.util.Base64.encodeToString(
                        out.toByteArray(),
                        android.util.Base64.NO_WRAP);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager n =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            n.createNotificationChannel(new NotificationChannel(
                    "capture",
                    "SimplyTsL 화면 번역",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override public void onDestroy() {
        closeMenu();
        if (wm != null && hamburger != null) {
            try { wm.removeView(hamburger); } catch (Exception ignored) {}
        }
        stopTranslation();
        if (thread != null) thread.quitSafely();
        instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
