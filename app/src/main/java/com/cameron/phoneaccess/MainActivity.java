package com.cameron.phoneaccess;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Daily unlock history. Launching this screen never inserts a history entry. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,31,43), CARD = Color.rgb(27,45,59);
    private static final int INK = Color.rgb(239,245,248), MUTED = Color.rgb(177,197,209);
    private static final int MINT = Color.rgb(154,228,208);
    private static final String CHANGED = "com.cameron.phoneaccess.HISTORY_CHANGED";
    private LocalDate selectedDay = LocalDate.now();
    private LocalDate latestAvailableDay = LocalDate.now();
    private TextView status, count, empty;
    private Button date, next, battery;
    private EventAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    private final Runnable updateStatus = this::refresh;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            try { selectedDay = LocalDate.parse(state.getString("day")); }
            catch (Exception ignored) { selectedDay = LocalDate.now(); }
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        LinearLayout root = column();
        boolean scrollLayout = getResources().getConfiguration().screenHeightDp < 650
                || getResources().getConfiguration().fontScale > 1.2f;
        root.setBackgroundColor(BG);
        root.setPadding(dp(20),dp(20),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(dp(20)+i.left,dp(20)+i.top,dp(20)+i.right,dp(12)+i.bottom);
            } else {
                v.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(20)+insets.getSystemWindowInsetTop(),
                        dp(20)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        TextView eyebrow = text("YOUR DAILY LOG",11,MINT,true);
        eyebrow.setLetterSpacing(0.14f);
        root.addView(eyebrow);
        TextView title = text("Phone Access History",27,INK,true);
        title.setPadding(0,dp(7),0,dp(7)); root.addView(title);
        TextView subtitle = text("Every unlock, one timestamp.",14,MUTED,false);
        root.addView(subtitle);

        LinearLayout serviceCard = column();
        serviceCard.setPadding(dp(14),dp(12),dp(14),dp(12));
        serviceCard.setBackground(shape(CARD,14));
        LinearLayout.LayoutParams cardParams = matchWrap(); cardParams.topMargin=dp(18);
        root.addView(serviceCard,cardParams);
        status=text("Starting unlock logging…",14,MINT,true); serviceCard.addView(status);
        TextView privacy=text("Saved only on this phone. No account or internet.",12,MUTED,false);
        privacy.setPadding(0,dp(5),0,0); serviceCard.addView(privacy);

        LinearLayout controls=row();
        LinearLayout.LayoutParams controlsParams=matchWrap(); controlsParams.topMargin=dp(16);
        root.addView(controls,controlsParams);
        Button previous=button("‹"); previous.setTextSize(28);
        previous.setContentDescription("Previous day");
        previous.setOnClickListener(v->{selectedDay=selectedDay.minusDays(1);refresh();});
        controls.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(52)));
        date=button(""); date.setTextSize(15); date.setOnClickListener(v->pickDate());
        controls.addView(date,new LinearLayout.LayoutParams(0,dp(52),1));
        next=button("›"); next.setTextSize(28); next.setContentDescription("Next day");
        next.setOnClickListener(v->{selectedDay=selectedDay.plusDays(1);refresh();});
        controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(52)));

        LinearLayout summary=row(); summary.setGravity(Gravity.CENTER_VERTICAL);
        count=text("0 unlocks",25,INK,true);
        summary.addView(count,new LinearLayout.LayoutParams(0,dp(56),1));
        count.setGravity(Gravity.CENTER_VERTICAL);
        Button today=button("Today"); today.setOnClickListener(v->{selectedDay=LocalDate.now();refresh();});
        summary.addView(today,new LinearLayout.LayoutParams(dp(78),dp(48)));
        root.addView(summary);
        TextView listHeading=text("UNLOCK TIMES · NEWEST FIRST",10,MUTED,true);
        listHeading.setLetterSpacing(0.08f); listHeading.setPadding(0,dp(6),0,dp(10));
        root.addView(listHeading);

        android.widget.FrameLayout content=new android.widget.FrameLayout(this);
        root.addView(content,scrollLayout ? new LinearLayout.LayoutParams(-1,dp(280))
                : new LinearLayout.LayoutParams(-1,0,1));
        ListView list=new ListView(this); list.setDividerHeight(dp(8));
        if(scrollLayout) list.setOnTouchListener((v,event)->{
            boolean finished=event.getActionMasked()==android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL;
            v.getParent().requestDisallowInterceptTouchEvent(!finished);return false;
        });
        list.setDivider(new android.graphics.drawable.ColorDrawable(BG));
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        adapter=new EventAdapter(); list.setAdapter(adapter);
        content.addView(list,new android.widget.FrameLayout.LayoutParams(-1,-1));
        empty=text("",15,MUTED,false); empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18),dp(16),dp(18),dp(16));
        content.addView(empty,new android.widget.FrameLayout.LayoutParams(-1,-1));
        list.setEmptyView(empty);

        LinearLayout footer=row();
        battery=button("Battery settings"); battery.setTextSize(12);
        battery.setOnClickListener(v->openBatterySettings());
        footer.addView(battery,new LinearLayout.LayoutParams(0,dp(48),1));
        Button help=button("How it works"); help.setTextSize(12); help.setOnClickListener(v->showHelp());
        footer.addView(help,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(footer);
        if(scrollLayout){
            android.widget.ScrollView scroll=new android.widget.ScrollView(this);
            scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
            scroll.addView(root,new android.widget.ScrollView.LayoutParams(-1,-2));setContentView(scroll);
        }else setContentView(root);
        root.requestApplyInsets();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT>=33) registerReceiver(updates,new IntentFilter(CHANGED),Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(updates,new IntentFilter(CHANGED));
            receiverRegistered=true;
        }
        UnlockService.start(this);
        refresh(); handler.postDelayed(updateStatus,600);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                && !getPreferences(0).getBoolean("notificationAsked",false)) {
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        }
    }
    @Override protected void onPause() {
        handler.removeCallbacks(updateStatus);
        if(receiverRegistered){unregisterReceiver(updates);receiverRegistered=false;}
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("day",selectedDay.toString());super.onSaveInstanceState(out);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants); refresh();
    }

    private void refresh() {
        if(adapter==null)return;
        String label=selectedDay.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy",Locale.getDefault()));
        date.setText(label); date.setContentDescription("Choose date. "+label);
        try {
            HistoryStore store=HistoryStore.get(this);
            latestAvailableDay=LocalDate.now();
            List<HistoryStore.Day> recordedDays=store.days();
            if(!recordedDays.isEmpty()){
                LocalDate latestRecorded=LocalDate.parse(recordedDays.get(0).date);
                if(latestRecorded.isAfter(latestAvailableDay))latestAvailableDay=latestRecorded;
            }
            adapter.events=store.eventsForDay(selectedDay.toString());
            adapter.notifyDataSetChanged();
            int n=adapter.events.size();count.setText(n+" "+(n==1?"unlock":"unlocks"));
            empty.setText(selectedDay.equals(LocalDate.now())?
                    "No unlocks recorded today.\n\nLock and unlock your phone to begin your history.":
                    "No unlocks recorded on this day.");
        }catch(Exception e){
            adapter.events=new ArrayList<>();adapter.notifyDataSetChanged();count.setText("History unavailable");
            empty.setText("History could not be loaded. Reopen the app to try again.");
        }
        next.setEnabled(selectedDay.isBefore(latestAvailableDay));next.setAlpha(next.isEnabled()?1f:0.3f);
        if(UnlockService.lastError!=null) {status.setText(UnlockService.lastError);status.setTextColor(Color.rgb(255,199,140));}
        else {status.setText(UnlockService.running?"●  Unlock logging is active":"Starting unlock logging…");status.setTextColor(MINT);}
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        boolean unrestricted=pm.isIgnoringBatteryOptimizations(getPackageName());
        battery.setText(unrestricted?"Battery: unrestricted":"Battery settings");
    }
    private void pickDate(){
        DatePickerDialog picker=new DatePickerDialog(this,(view,year,month,day)->{
            selectedDay=LocalDate.of(year,month+1,day);refresh();
        },selectedDay.getYear(),selectedDay.getMonthValue()-1,selectedDay.getDayOfMonth());
        picker.getDatePicker().setMaxDate(latestAvailableDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1);picker.show();
    }
    private void openBatterySettings(){
        new AlertDialog.Builder(this).setTitle("Keep logging in the background")
            .setMessage("Choose Phone Access History and allow unrestricted battery use. On some phones, also allow auto-start and remove the app from sleeping-app lists.\n\nAndroid may otherwise stop the logger and miss unlocks.")
            .setPositiveButton("Open settings",(dialog,which)->{
                try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
                catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            }).setNegativeButton("Later",null).show();
    }
    private void showHelp(){
        new AlertDialog.Builder(this).setTitle("Phone Access History")
            .setMessage("Opening this app starts logging if it has stopped. You can then leave the app or swipe it from Recents. The ongoing notification shows that logging is active.\n\nEach Android unlock signal adds one timestamp. Waking the screen while it stays locked does not count. Opening this app does not count.\n\nSelect a date to browse its history. Times and days use the time zone where each unlock was recorded. Logging begins after your first launch; earlier unlocks cannot be recovered.\n\nThe logger attempts to resume after reboot. Force Stop, Android’s Stop control, and manufacturer battery restrictions can interrupt it. Reopen the app to resume; missed unlocks cannot be reconstructed.\n\nAll history stays in private storage on this phone. No internet permission or cloud backup. Uninstalling the app or clearing its storage deletes its history.\n\nVersion 1.0.0 • Android 8.0 or later")
            .setPositiveButton("Got it",null).show();
    }

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String value){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(MINT);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);
        android.util.TypedValue attr=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,attr,true);
        b.setBackgroundResource(attr.resourceId);return b;
    }
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}

    private final class EventAdapter extends BaseAdapter {
        List<HistoryStore.Event> events=new ArrayList<>();
        @Override public int getCount(){return events.size();}
        @Override public Object getItem(int position){return events.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public boolean isEnabled(int position){return false;}
        @Override public View getView(int position,View convert,ViewGroup parent){
            LinearLayout item;
            if(convert instanceof LinearLayout)item=(LinearLayout)convert;
            else {
                item=row();item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(14),dp(12),dp(14),dp(12));
                item.setBackground(shape(CARD,12));
                TextView time=text("",20,INK,true); item.addView(time,new LinearLayout.LayoutParams(0,-2,1));
                TextView zone=text("",11,MUTED,false); zone.setGravity(Gravity.RIGHT);item.addView(zone);
            }
            HistoryStore.Event event=events.get(position);
            ZoneId zone;try{zone=ZoneId.of(event.zoneId);}catch(Exception e){zone=ZoneId.systemDefault();}
            DateTimeFormatter format=DateTimeFormatter.ofPattern(android.text.format.DateFormat.is24HourFormat(MainActivity.this)?"HH:mm:ss":"h:mm:ss a",Locale.getDefault()).withZone(zone);
            ((TextView)item.getChildAt(0)).setText(format.format(Instant.ofEpochMilli(event.timestamp)));
            ((TextView)item.getChildAt(1)).setText(DateTimeFormatter.ofPattern("z · xxx",Locale.getDefault()).withZone(zone).format(Instant.ofEpochMilli(event.timestamp)));
            return item;
        }
    }
}
