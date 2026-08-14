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
import android.util.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.*;
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
    private int w,h,dpi;
    private WindowManager wm;
    private View overlay;
    private LinearLayout root, resultPanel, resultList;
    private TextView status, fold;
    private boolean collapsed = false;
    private long lastRequestAt = 0;
    private static final long MIN_CAPTURE_INTERVAL_MS = 1200;

    public static void setFrameListener(FrameListener l){ listener=l; }
    public static boolean isRunning(){ return instance!=null; }
    public static void requestFrame(){
        ScreenCaptureService s=instance;
        if(s!=null){
            long now=System.currentTimeMillis();
            if(now-s.lastRequestAt>=MIN_CAPTURE_INTERVAL_MS){
                s.lastRequestAt=now; s.requested.set(true);
            }
        }
    }
    public static void showTranslations(String json){ if(instance!=null) instance.updateResults(json); }
    public static void showError(String message){ if(instance!=null) instance.updateError(message); }

    @Override public void onCreate(){
        super.onCreate(); instance=this; createChannel();
        Notification n=new Notification.Builder(this,"capture")
            .setContentTitle("SimplyTsL")
            .setContentText("화면 번역이 실행 중입니다.")
            .setSmallIcon(R.drawable.app_icon).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(1001,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1001,n);
        thread=new HandlerThread("SimplyTsLCapture"); thread.start(); handler=new Handler(thread.getLooper()); showOverlay();
    }

    @Override public int onStartCommand(Intent in,int flags,int id){
        if(in==null)return START_NOT_STICKY;
        int rc=in.getIntExtra("resultCode",-1);
        Intent data=Build.VERSION.SDK_INT>=33?in.getParcelableExtra("resultData",Intent.class):in.getParcelableExtra("resultData");
        if(rc!=-1&&data!=null&&projection==null){
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=m.getMediaProjection(rc,data); setup();
        }
        return START_NOT_STICKY;
    }

    private TextView tv(String text,float size,int color){
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setPadding(8,6,8,6); return t;
    }

    private void showOverlay(){
        if(!Settings.canDrawOverlays(this))return;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,6,10,8);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.argb(242,24,24,24)); bg.setCornerRadius(24); root.setBackground(bg);

        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=tv("SimplyTsL",15,Color.WHITE); title.setTypeface(null,1);
        status=tv("실시간 번역 준비",11,Color.LTGRAY); status.setGravity(Gravity.CENTER_VERTICAL);
        fold=new TextView(this); fold.setText("⌃"); fold.setTextColor(Color.WHITE); fold.setTextSize(20); fold.setGravity(Gravity.CENTER); fold.setPadding(10,2,10,2);
        Button close=new Button(this); close.setText("×"); close.setTextSize(18); close.setAllCaps(false);
        header.addView(title,new LinearLayout.LayoutParams(78,-2)); header.addView(status,new LinearLayout.LayoutParams(0,-2,1)); header.addView(fold,new LinearLayout.LayoutParams(45,45)); header.addView(close,new LinearLayout.LayoutParams(50,45));
        root.addView(header);

        resultPanel=new LinearLayout(this); resultPanel.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); scroll.addView(resultList);
        resultPanel.addView(scroll,new LinearLayout.LayoutParams(-1,220)); root.addView(resultPanel);

        TextView hint=tv("다른 브라우저 위에 표시됩니다 · 자동 번역",10,Color.LTGRAY); hint.setGravity(Gravity.CENTER); root.addView(hint);

        fold.setOnClickListener(v->{ collapsed=!collapsed; resultPanel.setVisibility(collapsed?View.GONE:View.VISIBLE); hint.setVisibility(collapsed?View.GONE:View.VISIBLE); fold.setText(collapsed?"⌄":"⌃"); });
        close.setOnClickListener(v->stopSelf());

        final WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-2,
            Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; lp.x=0; lp.y=110;
        final float[] down={0,0}; final int[] start={0,0};
        View.OnTouchListener drag=(v,e)->{ if(e.getAction()==MotionEvent.ACTION_DOWN){down[0]=e.getRawX();down[1]=e.getRawY();start[0]=lp.x;start[1]=lp.y;return true;} if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=start[0]+(int)(e.getRawX()-down[0]);lp.y=start[1]+(int)(e.getRawY()-down[1]);wm.updateViewLayout(overlay,lp);return true;} return false;};
        title.setOnTouchListener(drag); status.setOnTouchListener(drag);
        overlay=root; wm.addView(overlay,lp);
    }

    private void updateResults(String json){
        new Handler(Looper.getMainLooper()).post(()->{
            try{
                resultList.removeAllViews(); JSONArray a=new JSONArray(json);
                if(a.length()==0){ resultList.addView(tv("번역할 텍스트가 없습니다.",12,Color.LTGRAY)); }
                for(int i=0;i<a.length();i++){
                    JSONObject o=a.getJSONObject(i); String original=o.optString("text",""); String tr=o.optString("translation","");
                    LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(8,5,8,5);
                    TextView orig=tv(original,10,Color.LTGRAY); TextView trans=tv(tr,15,Color.WHITE); trans.setTypeface(null,1);
                    row.addView(orig); row.addView(trans);
                    GradientDrawable rg=new GradientDrawable(); rg.setColor(Color.argb(145,255,255,255)); rg.setCornerRadius(12); row.setBackground(rg);
                    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,3,0,3); resultList.addView(row,rp);
                }
                status.setText("번역 완료 · "+a.length()+"개");
            }catch(Exception e){ updateError("번역 결과 오류"); }
            // Continue polling after each completed translation.
            new Handler(Looper.getMainLooper()).postDelayed(ScreenCaptureService::requestFrame, 300);
        });
    }
    private void updateError(String message){ new Handler(Looper.getMainLooper()).post(()->{if(status!=null)status.setText(message); new Handler().postDelayed(ScreenCaptureService::requestFrame,1500);}); }

    private void setup(){
        WindowManager x=(WindowManager)getSystemService(WINDOW_SERVICE); DisplayMetrics dm=new DisplayMetrics(); x.getDefaultDisplay().getRealMetrics(dm);
        w=Math.min(dm.widthPixels,1600); h=Math.min(dm.heightPixels,1600); dpi=dm.densityDpi;
        reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(r->{ Image im=null; try{ im=r.acquireLatestImage(); if(im==null||!requested.compareAndSet(true,false))return; String data=jpeg(im); FrameListener l=listener; if(l!=null)l.onFrame(data); }finally{if(im!=null)im.close();}},handler);
        display=projection.createVirtualDisplay("SimplyTsL",w,h,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        requestFrame();
    }
    private String jpeg(Image im){ Image.Plane p=im.getPlanes()[0]; ByteBuffer b=p.getBuffer(); int stride=p.getPixelStride(),row=p.getRowStride(),pad=row-stride*w; Bitmap bm=Bitmap.createBitmap(w+pad/stride,h,Bitmap.Config.ARGB_8888); bm.copyPixelsFromBuffer(b); Bitmap crop=Bitmap.createBitmap(bm,0,0,w,h); if(crop!=bm)bm.recycle(); ByteArrayOutputStream o=new ByteArrayOutputStream(); crop.compress(Bitmap.CompressFormat.JPEG,78,o); crop.recycle(); return "data:image/jpeg;base64,"+Base64.encodeToString(o.toByteArray(),Base64.NO_WRAP); }
    private void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); n.createNotificationChannel(new NotificationChannel("capture","SimplyTsL 화면 번역",NotificationManager.IMPORTANCE_LOW)); } }
    @Override public void onDestroy(){ if(wm!=null&&overlay!=null)try{wm.removeView(overlay);}catch(Exception e){} if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(thread!=null)thread.quitSafely();instance=null;super.onDestroy(); }
    @Override public IBinder onBind(Intent i){return null;}
}
