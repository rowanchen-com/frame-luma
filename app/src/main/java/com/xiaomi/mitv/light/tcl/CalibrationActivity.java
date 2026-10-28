package com.xiaomi.mitv.light.tcl;
import android.app.Activity;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.xiaomi.mitv.light.tcl.layout.*;
import com.xiaomi.mitv.light.core.EdgeLayout;
import org.json.*;

/** Private remote-friendly calibration, explicit save and expiring visual locators. */
public final class CalibrationActivity extends Activity {
 private LinearLayout body;
 private TextView status;
 private int[] counts;
 private int head,tail,offset,samples,depth,unit=1,zone;
 private boolean reverse;
 private int totalUnits;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable cancelTest=new Runnable(){public void run(){clearTest();}};
 @Override public void onCreate(Bundle state){super.onCreate(state);AppCore.initialize(this);
  LayoutOptions l=AppCore.layout();totalUnits=l.units;counts=l.counts.clone();head=l.offHead;tail=l.offTail;offset=l.offset;reverse=l.reverse;samples=l.samples;depth=l.depth;
  ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(36,24,36,36);body.setBackgroundColor(Color.rgb(13,22,37));scroll.addView(body);setContentView(scroll);
  label("四边九区高级校准",25);
  label("正面看：底部中央 → 左下 → 左上 → 顶部 → 右侧 → 底部中央。每段约10cm，是协议控制单元。",17);
  label("逐段定位可核对起点与转角。默认布局是初始分配，实际贴装尚待确认。",16);
  status=label("修改后可测试，保存后记住；返回会结束定位并恢复随影。",16);
  final TextView loc=adjust("定位段",1,totalUnits,unit,new Setter(){public void set(int v){unit=v;}});
  button("点亮所选一段（20秒）",new Runnable(){public void run(){if(testReady()){AppCore.locate(unit,-1,20000);Client.refresh(CalibrationActivity.this);status.setText("白色定位第 "+unit+" / "+totalUnits+" 段，其余熄灭；20秒后恢复随影");arm();}}});
  button("下一段并点亮",new Runnable(){public void run(){unit=unit%totalUnits+1;loc.setTag(unit);loc.setText("定位段："+unit);if(testReady()){AppCore.locate(unit,-1,20000);Client.refresh(CalibrationActivity.this);status.setText("正在定位第 "+unit+" 段");arm();}}});
  button("结束定位，恢复随影",new Runnable(){public void run(){clearTest();}});
  label("九区长度：起点从底部中央先往左，合计应与控制单元总数一致",20);
  for(int i=0;i<9;i++){final int z=i;adjust((i+1)+" "+EdgeLayout.LABELS[i],0,totalUnits,counts[i],new Setter(){public void set(int v){counts[z]=v;total();}});}
  adjust("首部关闭段数",0,totalUnits,head,new Setter(){public void set(int v){head=v;total();}});
  adjust("尾部关闭段数",0,totalUnits,tail,new Setter(){public void set(int v){tail=v;total();}});
  adjust("整体偏移（段）",0,totalUnits-1,offset,new Setter(){public void set(int v){offset=v;}});
  final Button direction=button("方向："+(reverse?"反向":"底部中央先往左"),null);
  direction.setOnClickListener(new View.OnClickListener(){public void onClick(View v){reverse=!reverse;direction.setText("方向："+(reverse?"反向":"底部中央先往左"));}});
  final Button accuracy=button("每区取色："+samples+"点",null);
  accuracy.setOnClickListener(new View.OnClickListener(){public void onClick(View v){samples=samples==1?3:1;accuracy.setText("每区取色："+samples+"点");}});
  label("1点响应快；3点在每区分散采样后平均，TCL上会更慢。原生4K坐标，不照搬小米的乘2缩放。",16);
  adjust("边缘取色深度（%）",5,30,depth,new Setter(){public void set(int v){depth=v;}});
  button("保存并开启九区随影",new Runnable(){public void run(){try{
   AppCore.setLayout(options().json());AppCore.setEnabled(true);Client.flow(CalibrationActivity.this,true);Client.refresh(CalibrationActivity.this);
   status.setText("已保存九区配置，随影开启。仍需逐区对照，不能仅凭保存确认准确。");
  }catch(Exception e){status.setText(e.getMessage());}}});
  adjust("测试区号",1,9,1,new Setter(){public void set(int v){zone=v-1;}});
  button("保存当前长度并点亮所选区（20秒）",new Runnable(){public void run(){try{options();if(testReady()){
   AppCore.setLayout(options().json());AppCore.locate(0,zone,20000);Client.refresh(CalibrationActivity.this);status.setText("白色定位区域："+EdgeLayout.LABELS[zone]);arm();
  }}catch(Exception e){status.setText(e.getMessage());}}});
  button("恢复默认布局",new Runnable(){public void run(){AppCore.resetLayout();Client.refresh(CalibrationActivity.this);finish();}});
 }
 private LayoutOptions options()throws Exception{
  JSONObject j=new JSONObject();j.put("mode","four_edge9");j.put("unit_count",totalUnits);JSONArray a=new JSONArray();for(int n:counts)a.put(n);j.put("counts",a);
  j.put("off_head",head);j.put("off_tail",tail);j.put("offset_units",offset);j.put("reverse",reverse);j.put("samples_per_zone",samples);j.put("depth_percent",depth);return new LayoutOptions(j);
 }
 private boolean testReady(){if(!AppCore.enabled()||!AppCore.power()){status.setText("请先保存并开启随影，再做定位测试");return false;}return true;}
 private void total(){int n=head+tail;for(int c:counts)n+=c;status.setText("九区＋首尾关闭段合计："+n+" / "+totalUnits+"；保存前必须一致");}
 private void arm(){handler.removeCallbacks(cancelTest);handler.postDelayed(cancelTest,20000);}
 private void clearTest(){handler.removeCallbacks(cancelTest);AppCore.clearLocator();Client.refresh(this);if(status!=null)status.setText("定位结束，恢复当前随影模式");}
 private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(4,8,4,8);body.addView(t);return t;}
 private interface Setter{void set(int value);}
 private TextView adjust(final String name,final int min,final int max,int initial,final Setter setter){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);
  final TextView text=new TextView(this);text.setTag(initial);text.setTextColor(Color.WHITE);text.setTextSize(18);text.setText(name+"："+initial);row.addView(text,new LinearLayout.LayoutParams(0,56,1));
  for(final int delta:new int[]{-1,1}){Button b=new Button(this);b.setText(delta<0?"−":"＋");b.setPadding(0,0,0,0);b.setTextSize(18);b.setGravity(Gravity.CENTER);b.setContentDescription(name+(delta<0?"减少":"增加"));row.addView(b,new LinearLayout.LayoutParams(88,56));b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){int value=Math.max(min,Math.min(max,((Integer)text.getTag())+delta));text.setTag(value);setter.set(value);text.setText(name+"："+value);}});}
  body.addView(row);return text;
 }
 private Button button(String text,final Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setPadding(0,0,0,0);b.setGravity(Gravity.CENTER);body.addView(b,new LinearLayout.LayoutParams(-1,64));if(action!=null)b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});return b;}
 @Override protected void onPause(){clearTest();super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
