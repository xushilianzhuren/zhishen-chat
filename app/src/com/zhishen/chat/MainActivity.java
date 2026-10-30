package com.zhishen.chat;

import android.app.*;
import android.database.*;
import android.database.sqlite.*;
import android.provider.*;
import android.os.Environment;
import java.io.DataOutputStream;
import android.content.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.graphics.drawable.*;
import java.io.*;
import java.net.*;
import java.util.*;
import android.util.Base64;
import org.json.*;

public class MainActivity extends Activity {
    /** 服务端地址：可在「我的→服务器地址」设置（默认占位，构建时替换或运行时改） */
    static final String BASE_DEFAULT="http://10.0.2.2:8000"; // 本机模拟器调试默认
    /** 服务端地址：默认本机调试·「我的→服务器地址」可改并持久化 */
    String serverBase(){ String b=getSharedPreferences("zh",0).getString("server",null); return (b==null||b.isEmpty())?BASE_DEFAULT:b; }
    android.speech.tts.TextToSpeech tts; volatile boolean ttsOn=false; String lastReply="";
    int spinN=0; String statusBase=""; TextView statusV; Runnable spinR; static final String[] MOON={"◐","◓","◑","◒"};
    int C_BG=0xFFF7F3EC,C_ME=0xFFF3E1E2,C_AI=0xFFFFFFFF,C_PRI=0xFF8C1F28,C_TXT=0xFF26221C,C_SUB=0xFF8A8378,C_GOLD=0xFF9C7A3C,C_LINE=0xFFE7DFD2;
    float FS=1.0f; boolean NIGHT=false;
    void loadPrefs(){
        NIGHT=getSharedPreferences("zh",0).getBoolean("night",false);
        FS=getSharedPreferences("zh",0).getFloat("fs",1.0f);
        if(NIGHT){ C_BG=0xFF1C1A17; C_ME=0xFF3A2E2E; C_AI=0xFF2E2B26; C_PRI=0xFFB0413F; C_TXT=0xFFEDE7DD; C_SUB=0xFF9A948A; C_GOLD=0xFFC9A86A; C_LINE=0xFF3A362F; }
    }
    static CookieManager cm; String sid="",cs=""; 
    LinearLayout chatBox,root; ScrollView chatScroll; EditText input; TextView send; ListView sessList;
    FrameLayout flip; LinearLayout sessView,chatView,meView; LinearLayout tabs,top; TextView title; TextView fab; TextView tvUser,tvModel,tvVer; JSONObject[] MS=new JSONObject[0];
    MediaRecorder rec; File recFile; boolean recing=false; String pendingAudio;
    Handler ui; MainActivity act; android.net.Uri lastUri; android.view.GestureDetector gd;
    java.util.ArrayList<Uri> pendingUris=new java.util.ArrayList<>(); TextView pendingBar;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ttsInit();
        ui=new Handler(); act=this;
        try{ loadPrefs(); }catch(Throwable t){ FS=1.0f; NIGHT=false; }
        try{ android.content.res.Configuration _c=getResources().getConfiguration(); _c.fontScale=FS; applyOverrideConfiguration(_c); }catch(Throwable t2){}
        try{
            final Thread.UncaughtExceptionHandler old=Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((th,e)->{
                try{
                    String msg="崩溃: "+e.getClass().getSimpleName()+" "+(e.getStackTrace().length>0?e.getStackTrace()[0].toString():"");
                    android.util.Log.e("ZHISHEN","CRASH",e);
                    runOnUiThread(()->{ try{ android.widget.Toast.makeText(act,msg,android.widget.Toast.LENGTH_LONG).show(); }catch(Throwable t2){} });
                }catch(Throwable t3){}
                if(old!=null) old.uncaughtException(th,e);
            });
        }catch(Throwable t4){}
        if(cm==null){ cm=new CookieManager(); CookieHandler.setDefault(cm); } // v12.1:recreate不清登录凭证
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(C_BG);
        try{
        // 顶栏
        top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setBackgroundColor(C_PRI);
        top.setPadding(dp(16),dp(14),dp(16),dp(14)); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=new TextView(this); logo.setText("智神"); logo.setTextColor(0xFFFFFFFF); logo.setTextSize(17); logo.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams lp0=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); logo.setLayoutParams(lp0);
        TextView back=new TextView(this); back.setText("◀会话"); back.setTextColor(0xFFF3D9DB); back.setTextSize(14); back.setPadding(dp(12),dp(6),0,dp(6)); back.setOnClickListener(v->showSessions());
        TextView sch=new TextView(this); sch.setText("🔍"); sch.setTextColor(0xFFFFFFFF); sch.setTextSize(16); sch.setPadding(dp(10),0,dp(4),0);
        sch.setOnClickListener(v->doSearch());
        TextView copyAll=new TextView(this); copyAll.setText("📋"); copyAll.setTextColor(0xFFFFFFFF); copyAll.setTextSize(16); copyAll.setPadding(dp(12),0,dp(4),0);
        copyAll.setOnClickListener(v->copySession());
        TextView files=new TextView(this); files.setText("📦"); files.setTextColor(0xFFFFFFFF); files.setTextSize(16); files.setPadding(dp(16),0,0,0); files.setOnClickListener(v->showFiles());
        TextView spk=new TextView(this); spk.setText("🔊"); spk.setTextColor(ttsOn?0xFFFFD700:0xFFFFFFFF); spk.setTextSize(16); spk.setPadding(dp(12),0,0,0);
        spk.setOnClickListener(v->{ ttsOn=!ttsOn; spk.setTextColor(ttsOn?0xFFFFD700:0xFFFFFFFF); toast(ttsOn?"🔊朗读开":"🔇朗读关"); if(!ttsOn&&tts!=null)tts.stop(); });
        top.addView(logo); top.addView(back); top.addView(sch); top.addView(copyAll); top.addView(files); top.addView(spk);
        // 会话视图
        sessView=new LinearLayout(this); sessView.setOrientation(LinearLayout.VERTICAL);
        sessList=new ListView(this); sessList.setBackgroundColor(C_BG); sessList.setDivider(null);
        fab=new TextView(this); fab.setText("＋ 新对话"); fab.setTextColor(0xFFFFFFFF); fab.setTextSize(16); fab.setGravity(Gravity.CENTER);
        fab.setBackgroundColor(C_PRI); fab.setPadding(0,dp(14),0,dp(14)); fab.setTypeface(null,Typeface.BOLD);
        fab.setOnClickListener(v->newChat());
        sessView.addView(sessList,new LinearLayout.LayoutParams(-1,0,1)); sessView.addView(fab,new LinearLayout.LayoutParams(-1,-2));
        // 聊天视图
        chatView=new LinearLayout(this); chatView.setOrientation(LinearLayout.VERTICAL);
        title=new TextView(this); title.setTextColor(C_TXT); title.setTextSize(14); title.setLetterSpacing(0.08f); title.setTypeface(null,Typeface.BOLD); title.setGravity(Gravity.CENTER); title.setPadding(dp(16),dp(12),dp(16),dp(4));
        chatScroll=new ScrollView(this); chatScroll.setBackgroundColor(C_BG);
        chatBox=new LinearLayout(this); chatBox.setOrientation(LinearLayout.VERTICAL); chatBox.setPadding(dp(6),dp(4),dp(6),dp(4));
        chatScroll.addView(chatBox,new ViewGroup.LayoutParams(-1,-2));
        android.widget.FrameLayout scrollWrap=new android.widget.FrameLayout(this);
        scrollWrap.addView(chatScroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setBackgroundColor(NIGHT?C_AI:0xFFF7F3EC); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(10),dp(8),dp(10),dp(10));
        TextView emo=new TextView(this); emo.setText("😄"); emo.setTextSize(20); emo.setPadding(dp(8),0,dp(4),0); emo.setOnClickListener(v->showStickers());
        TextView mic=new TextView(this); mic.setText("🎤"); mic.setTextSize(20); mic.setPadding(dp(8),0,dp(8),0); mic.setOnClickListener(v->toggleRec());
        input=new EditText(this); input.setHint("说点什么…"); input.setTextSize(15); input.setBackground(round(NIGHT?0xFF26231E:0xFFEFE9DD,dp(22))); input.setPadding(dp(16),dp(10),dp(16),dp(10)); input.setTextColor(C_TXT); input.setHintTextColor(C_SUB); input.setMaxLines(4);
        LinearLayout.LayoutParams lpi=new LinearLayout.LayoutParams(0,-2,1); input.setLayoutParams(lpi);
        TextView plus=new TextView(this); plus.setText("📎"); plus.setTextSize(20); plus.setPadding(dp(10),0,dp(6),0); plus.setOnClickListener(v->pickFile());
        send=new TextView(this); send.setText("➤"); send.setTextSize(20); send.setTextColor(C_PRI); send.setPadding(dp(6),0,dp(8),0); send.setOnClickListener(v->sendText());
        bar.addView(emo); bar.addView(mic); bar.addView(input); bar.addView(plus); bar.addView(send);
        chatView.addView(scrollWrap,new LinearLayout.LayoutParams(-1,0,1));
        String[] NL={"▲","⇧","▼"};
        for(int ni=0;ni<3;ni++){
            final int fi=ni;
            TextView nb2=new TextView(this); nb2.setText(NL[ni]); nb2.setTextSize(14); nb2.setTextColor(C_PRI); nb2.setGravity(Gravity.CENTER);
            nb2.setBackground(round(0xE6FFFFFF,dp(18)));
            LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(dp(38),dp(38)); nlp.topMargin=dp(6);
            FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(dp(38),dp(38));
            flp.rightMargin=dp(10); flp.gravity=Gravity.END;
            nb2.setLayoutParams(nlp);
            android.widget.FrameLayout holder=new android.widget.FrameLayout(this);
            holder.addView(nb2);
            FrameLayout.LayoutParams hlp=new FrameLayout.LayoutParams(-2,-2);
            hlp.rightMargin=dp(10); hlp.bottomMargin=(3-ni)*(dp(38)+dp(6))+dp(90); hlp.gravity=Gravity.BOTTOM|Gravity.END;
            holder.setLayoutParams(hlp);
            scrollWrap.addView(holder);
            nb2.setOnClickListener(v->{
                if(fi==0){ chatScroll.post(()->chatScroll.smoothScrollTo(0,0)); }
                else if(fi==2){ chatScroll.post(()->chatScroll.smoothScrollTo(0,chatBox.getHeight())); }
                else { // 上一条:当前滚动位置上方最近的气泡
                    int cur=chatScroll.getScrollY();
                    int target=0;
                    for(int ci=0;ci<chatBox.getChildCount();ci++){
                        int top=chatBox.getChildAt(ci).getTop();
                        if(top<cur-40){ target=top; } else break;
                    }
                    final int t2=target;
                    chatScroll.post(()->chatScroll.smoothScrollTo(0,t2));
                }
            });
        }
        pendingBar=new TextView(this); pendingBar.setTextSize(13); pendingBar.setTextColor(C_PRI);
        pendingBar.setBackgroundColor(0xFFF6E3E4); pendingBar.setPadding(dp(14),dp(6),dp(14),dp(6)); pendingBar.setVisibility(View.GONE);
        pendingBar.setOnClickListener(v->{ pendingUris.clear(); pendingBar.setVisibility(View.GONE); toast("已清空待发图片"); });
        chatView.addView(pendingBar,new LinearLayout.LayoutParams(-1,-2));
        chatView.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        // 登录浮层
        LinearLayout login=new LinearLayout(this); login.setOrientation(LinearLayout.VERTICAL); login.setBackgroundColor(C_BG); login.setGravity(Gravity.CENTER); login.setId(99);
        TextView lt=new TextView(this); lt.setText("智神"); lt.setTextSize(22); lt.setTextColor(C_PRI); lt.setTypeface(null,Typeface.BOLD); lt.setGravity(Gravity.CENTER);
        EditText un=new EditText(this); un.setHint("用户名(注册后永久专属空间)"); un.setBackground(round(0xFFFFFFFF,dp(10))); un.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lun=new LinearLayout.LayoutParams(dp(260),-2); lun.topMargin=dp(20); un.setLayoutParams(lun);
        EditText pw=new EditText(this); pw.setHint("密码"); pw.setInputType(0x81); pw.setBackground(round(0xFFFFFFFF,dp(10))); pw.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lpw=new LinearLayout.LayoutParams(dp(260),-2); lpw.topMargin=dp(10); pw.setLayoutParams(lpw);
        LinearLayout brow=new LinearLayout(this); brow.setOrientation(LinearLayout.HORIZONTAL); brow.setGravity(Gravity.CENTER);
        Button go=new Button(this); go.setText("登录"); go.setBackgroundColor(C_PRI); go.setTextColor(0xFFFFFFFF);
        Button reg=new Button(this); reg.setText("注册"); reg.setBackgroundColor(0xFF6b4a2b); reg.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams bg1=new LinearLayout.LayoutParams(0,-2,1); bg1.topMargin=dp(14); bg1.rightMargin=dp(6); go.setLayoutParams(bg1);
        LinearLayout.LayoutParams bg2=new LinearLayout.LayoutParams(0,-2,1); bg2.topMargin=dp(14); bg2.leftMargin=dp(6); reg.setLayoutParams(bg2);
        brow.addView(go); brow.addView(reg);
        LinearLayout.LayoutParams lbrow=new LinearLayout.LayoutParams(dp(260),-2); lbrow.topMargin=dp(0); brow.setLayoutParams(lbrow);
        TextView msel=new TextView(this); msel.setText("模型: glm-5.3-flash (点切换)"); msel.setTextSize(13); msel.setTextColor(C_SUB); msel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lms=new LinearLayout.LayoutParams(dp(260),-2); lms.topMargin=dp(12); msel.setLayoutParams(lms);
        final String[] MDL={"glm-5.3-flash","glm-4.5-flash","glm-4-flash"}; final int[] mi={0};
        msel.setOnClickListener(v->{ mi[0]=(mi[0]+1)%3; msel.setText("模型: "+MDL[mi[0]]+" (点切换)");
            getSharedPreferences("zh",0).edit().putString("model",MDL[mi[0]]).apply(); });
        msel.setText("模型: "+getSharedPreferences("zh",0).getString("model",MDL[0])+" (点切换)");
        go.setOnClickListener(v->{ getSharedPreferences("zh",0).edit().putString("model",msel.getText().toString().replace("模型: ","").replace(" (点切换)","")).apply(); doLogin(un.getText().toString(),pw.getText().toString(),login,false); });
        reg.setOnClickListener(v->{ getSharedPreferences("zh",0).edit().putString("model",msel.getText().toString().replace("模型: ","").replace(" (点切换)","")).apply(); doLogin(un.getText().toString(),pw.getText().toString(),login,true); });
        login.addView(lt); login.addView(un); login.addView(pw); login.addView(brow); login.addView(msel);
        flip=new FrameLayout(this); flip.setBackgroundColor(C_BG); flip.addView(sessView,new FrameLayout.LayoutParams(-1,-1)); flip.addView(chatView,new FrameLayout.LayoutParams(-1,-1)); flip.addView(login,new FrameLayout.LayoutParams(-1,-1));
        // ═══ 我的视图 ═══
        meView=new LinearLayout(this); meView.setOrientation(LinearLayout.VERTICAL); meView.setBackgroundColor(C_BG); meView.setPadding(dp(20),dp(24),dp(20),dp(20));
        TextView mt=new TextView(this); mt.setText("我的"); mt.setTextSize(20); mt.setTextColor(C_TXT); mt.setTypeface(null,Typeface.BOLD);
        tvUser=new TextView(this); tvUser.setText("用户: 未登录"); tvUser.setTextSize(14); tvUser.setTextColor(C_SUB); tvUser.setPadding(0,dp(8),0,dp(16));
        meView.addView(mt); meView.addView(tvUser);
        String[][] MENU={{"🤖 模型选择","点击切换AI模型"},{"🔄 检查更新","获取最新版本"},{"🎨 外观","跟随系统"},{"🅰 字体大小","标准"},{"📇 账号信息","手机号/邮箱/微信"},{"📦 文件区","我的专属文件"}};
        android.widget.Switch swTheme=new android.widget.Switch(this); swTheme.setText("夜间模式"); swTheme.setChecked(NIGHT); swTheme.setPadding(0,dp(10),0,dp(10));
        swTheme.setOnCheckedChangeListener((b2,on)->{ getSharedPreferences("zh",0).edit().putBoolean("night",on).apply(); recreate(); });
        meView.addView(swTheme);
        meView.addView(mkMenu("🅰 字体大小",fsLabel(),v->{
            String[] F={"小","标准","大","特大"}; final float[] V={0.85f,1.0f,1.2f,1.4f};
            String cur=fsLabel();
            new android.app.AlertDialog.Builder(act).setTitle("字体大小").setItems(F,(d,w)->{
                getSharedPreferences("zh",0).edit().putFloat("fs",V[w]).apply(); recreate();
            }).show();
        }));
        meView.addView(mkMenu("🤖 模型选择",getSharedPreferences("zh",0).getString("model","glm-5.3-flash"),v->showModelPicker()));
        meView.addView(mkMenu("🔄 检查更新","获取最新版本",v->checkUpdate()));
        meView.addView(mkMenu("📇 账号信息","登录账号概览",v->toast("用户: "+getSharedPreferences("zh",0).getString("user","未登录"))));
        meView.addView(mkMenu("📦 文件区","我的专属文件",v->{ if(meView.getVisibility()==View.VISIBLE){showSessions();} }));
        meView.addView(mkMenu("💰 收款流水","小店收入审计·累计进账",v->showIncome()));
        meView.addView(mkMenu("📊 执行队列","VPS总线任务战况",v->showQueue()));
        meView.addView(mkMenu("🧠 白幼真状态卡","服务器/大脑/备份实况",v->showStatusCard()));
        meView.addView(mkMenu("🚀 插件发布","写好插件·一键发上市场",v->showPluginPublish()));
        meView.addView(mkMenu("📡 已发布","我发布的插件·星数与链接",v->showPublished()));
        TextView vtag=new TextView(this); vtag.setText("智神 v"+getVerName()+" · 智神科技"); vtag.setTextSize(12); vtag.setTextColor(C_SUB); vtag.setGravity(Gravity.CENTER); vtag.setPadding(0,dp(24),0,0);
        meView.addView(vtag);
        flip.addView(meView,new FrameLayout.LayoutParams(-1,-1)); meView.setVisibility(View.GONE);
        // ═══ 底部双tab ═══
        tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setBackgroundColor(0xFFFFFFFF);
        TextView tabChat=mkTab("💬 对话"); TextView tabMe=mkTab("👤 我的");
        tabs.addView(tabChat,new LinearLayout.LayoutParams(0,-2,1)); tabs.addView(tabMe,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(52)); tabs.setLayoutParams(tlp);
        tabChat.setOnClickListener(v->{ tabChat.setTextColor(C_PRI); tabMe.setTextColor(C_SUB); meView.setVisibility(View.GONE); sessView.setVisibility(View.VISIBLE); if(cs.length()>0&&chatView.getVisibility()==View.VISIBLE){} else showSessions(); });
        tabMe.setOnClickListener(v->{ tabMe.setTextColor(C_PRI); tabChat.setTextColor(C_SUB); sessView.setVisibility(View.GONE); chatView.setVisibility(View.GONE); meView.setVisibility(View.VISIBLE);
            tvUser.setText("用户: "+getSharedPreferences("zh",0).getString("user","未登录")); });
        root.addView(top,new LinearLayout.LayoutParams(-1,-2)); root.addView(flip,new LinearLayout.LayoutParams(-1,0,1)); root.addView(tabs);
        if(getSharedPreferences("zh",0).getString("user","").isEmpty()){ login.setVisibility(View.VISIBLE); sessView.setVisibility(View.GONE); tabs.setVisibility(View.GONE); top.setVisibility(View.GONE); fab.setVisibility(View.GONE); }
        else { login.setVisibility(View.GONE); tabs.setVisibility(View.VISIBLE); top.setVisibility(View.VISIBLE); fab.setVisibility(View.VISIBLE); loadSessions(); }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(C_BG));
        gd=new android.view.GestureDetector(this,new GestureHandler(this));
                }catch(Throwable buildErr){
            LinearLayout fb=new LinearLayout(this); fb.setOrientation(LinearLayout.VERTICAL); fb.setBackgroundColor(0xFFF7F3EC); fb.setPadding(30,60,30,30);
            TextView et=new TextView(this); et.setTextSize(14);
            java.io.StringWriter sw=new java.io.StringWriter(); buildErr.printStackTrace(new java.io.PrintWriter(sw));
            String ess=sw.toString(); if(ess.length()>1500) ess=ess.substring(0,1500);
            et.setText("智神启动遇到问题·把这个截图发给白幼真:\n\n"+ess);
            fb.addView(et); setContentView(fb); return;
        }
        setContentView(root); chatView.setVisibility(View.GONE);
        // v11.8:登录态只由用户手动登录建立·后台自动登录线程已根除(它会在用户打字时切视图=跳列表bug根因)
    }
    @Override public boolean dispatchTouchEvent(MotionEvent ev){ if(gd!=null)gd.onTouchEvent(ev); return super.dispatchTouchEvent(ev); }
    @Override public void onBackPressed(){
        if(chatView.getVisibility()==View.VISIBLE){ showSessions(); }
        else super.onBackPressed();
    }
    LinearLayout mkMenu(String t,String sub,View.OnClickListener ck){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setBackgroundColor(0xFFFFFFFF);
        row.setPadding(dp(14),dp(12),dp(14),dp(12)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(8); row.setLayoutParams(rp);
        TextView a=new TextView(this); a.setText(t); a.setTextSize(15); a.setTextColor(C_TXT);
        TextView b=new TextView(this); b.setText(sub); b.setTextSize(12); b.setTextColor(C_SUB);
        row.addView(a); row.addView(b); row.setOnClickListener(ck); return row;
    }
    TextView mkTab(String t){
        TextView x=new TextView(this); x.setText(t); x.setTextSize(13); x.setGravity(Gravity.CENTER);
        x.setPadding(0,dp(10),0,dp(10)); x.setTextColor(C_PRI); return x;
    }
    void showModelPicker(){
        final String[] M={"glm-5.3-flash","deepseek-chat","一问多模","glm-4.5-flash","glm-4-flash","qwen3.7-plus","kimi-k2.6","glm-5","minimax-m2.7"};
        final boolean[] OK={true,true,true,true,true,false,false,false,false};
        android.widget.ScrollView sc=new android.widget.ScrollView(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16),dp(10),dp(16),dp(16)); box.setBackgroundColor(NIGHT?0xFF26231F:0xFFFFFFFF);
        String cur=getSharedPreferences("zh",0).getString("model","glm-5.3-flash");
        for(int i=0;i<M.length;i++){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(14),dp(12),dp(14));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(6);
            row.setBackground(round(NIGHT?0xFF33302B:0xFFF5F1EA,dp(10))); row.setLayoutParams(rp);
            TextView nm=new TextView(this); nm.setText(M[i]); nm.setTextSize(15); nm.setTextColor(M[i].equals(cur)?C_PRI:C_TXT); nm.setTypeface(null,M[i].equals(cur)?Typeface.BOLD:Typeface.NORMAL);
            nm.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            TextView tag=new TextView(this); tag.setText(OK[i]?"官方":"即将开放"); tag.setTextSize(11);
            tag.setTextColor(OK[i]?0xFF2E7D32:0xFF9A948A); tag.setPadding(dp(8),dp(2),dp(8),dp(2));
            tag.setBackground(round(OK[i]?0xFFE7F2E7:0xFFF1EDE6,dp(8)));
            row.addView(nm); row.addView(tag);
            final int fi=i;
            row.setOnClickListener(v->{
                if(!OK[fi]){ toast(M[fi]+" 即将开放·敬请期待"); return; }
                getSharedPreferences("zh",0).edit().putString("model",M[fi]).apply();
                toast("已切换至 "+M[fi]);
                android.app.AlertDialog al=(android.app.AlertDialog)tag.getTag();
                if(pickDlg!=null&&pickDlg.isShowing()) pickDlg.dismiss();
            });
            box.addView(row);
        }
        android.app.AlertDialog.Builder ab=new android.app.AlertDialog.Builder(act);
        ab.setTitle("选择模型(智神)").setView(sc).setNegativeButton("关闭",null);
        pickDlg=ab.create(); pickDlg.show();
    }
    android.app.AlertDialog pickDlg;
    String fsLabel(){ float f=getSharedPreferences("zh",0).getFloat("fs",1.0f); return f<=0.9f?"小":f>=1.35f?"特大":f>=1.1f?"大":"标准"; }
    String getVerName(){ try{ return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }catch(Exception e){ return "?"; } }
    int getVerCode(){ try{ return getPackageManager().getPackageInfo(getPackageName(),0).versionCode; }catch(Exception e){ return 0; } }
    // ═══ v13 三面板 ═══
    void dlgText(String title,String s){
        new AlertDialog.Builder(act).setTitle(title).setMessage(s.length()>3500?s.substring(0,3500)+"…":s).setPositiveButton("关闭",null).show();
    }
    void showQueue(){
        net(()->{
            try{
                JSONObject j=new JSONObject(http("/api/queue",null));
                StringBuilder sb=new StringBuilder("总线任务总数: "+j.optInt("total")+"\n");
                JSONObject st=j.optJSONObject("stat");
                if(st!=null) sb.append("状态分布: ").append(st.toString()).append("\n\n");
                JSONArray ls=j.optJSONArray("lines");
                if(ls!=null) for(int i=0;i<ls.length();i++) sb.append(ls.optString(i)).append("\n");
                final String s=sb.toString();
                ui.post(()->dlgText("📊 执行队列·总线",s));
            }catch(Exception e){ ui.post(()->toast("队列加载失败·检查网络")); }
        });
    }
    void showStatusCard(){
        net(()->{
            try{
                JSONObject j=new JSONObject(http("/api/status",null));
                JSONArray ls=j.optJSONArray("lines");
                StringBuilder sb=new StringBuilder();
                if(ls!=null) for(int i=0;i<ls.length();i++) sb.append(ls.optString(i)).append("\n");
                final String s=sb.toString();
                ui.post(()->dlgText("🧠 白幼真状态卡",s));
            }catch(Exception e){ ui.post(()->toast("状态加载失败·检查网络")); }
        });
    }
    void showIncome(){
        net(()->{
            try{
                JSONObject j=new JSONObject(http("/api/income",null));
                JSONArray os=j.optJSONArray("orders");
                StringBuilder sb=new StringBuilder("累计收款: ¥"+j.optDouble("total",0)+"\n\n");
                if(os==null||os.length()==0) sb.append("暂无订单记录\n\n收款方式: 点下方按钮看收款码\n(钱进微信/支付宝即记录)");
                else for(int i=0;i<os.length();i++){
                    JSONObject o=os.optJSONObject(i);
                    if(o!=null) sb.append("· "+o.optString("date","")+" ¥"+o.optDouble("amount",0)+" "+o.optString("note","")+"\n");
                    else sb.append("· "+os.optString(i)+"\n");
                }
                final String s=sb.toString();
                ui.post(()->new AlertDialog.Builder(act).setTitle("💰 收款流水").setMessage(s)
                    .setPositiveButton("微信收款码",(d,w)->openUrl(serverBase()+"/api/qr?name=qr_wechat.png"))
                    .setNeutralButton("支付宝",(d,w)->openUrl(serverBase()+"/api/qr?name=qr_alipay.jpg"))
                    .setNegativeButton("关闭",null).show());
            }catch(Exception e){ ui.post(()->toast("收款数据加载失败·检查网络")); }
        });
    }
    void openUrl(String u){ try{ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(u))); }catch(Exception e){ toast("打开失败"); } }

// ===== v3.7 插件发布/已发布（GitHub REST直连·手机端独立发布） =====
    /** 已发布插件列表的 GitHub 账号（可改成你自己的） */
    static final String GH_USER="your-github-username";
    String ghToken(){ return getSharedPreferences("zh",0).getString("gh_token",""); }
    /** GitHub REST通用调用：method=GET/POST/PUT，body=JSON或null。返回响应文本或null。 */
    String gh(String url,String method,String body,String token){
        try{ HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(60000);
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestProperty("Accept","application/vnd.github+json");
            c.setRequestProperty("User-Agent","zhishen-app");
            if(body!=null){ c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json");
                OutputStream o=c.getOutputStream(); o.write(body.getBytes("UTF-8")); o.close(); }
            BufferedReader rd=new BufferedReader(new InputStreamReader(c.getResponseCode()<300?c.getInputStream():c.getErrorStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder(); String l; while((l=rd.readLine())!=null)sb.append(l); rd.close();
            return sb.toString();
        }catch(Exception e){ return null; } }
    String b64(String s){ return android.util.Base64.encodeToString(s.getBytes(),android.util.Base64.NO_WRAP); }
    /** 建仓+推最小插件五件（package.json/index.js/cordis.patch.yml/README/locale） */
    boolean ghPublish(String token,String name,String desc,String indexJs){
        String repoApi="https://api.github.com/user/repos";
        String cr=gh(repoApi,"POST","{\"name\":\""+name+"\",\"description\":\""+desc.replace("\"","'")+"\",\"private\":false,\"has_wiki\":false,\"has_issues\":false,\"auto_init\":false}",token);
        if(cr==null) return false;
        if(cr.contains("already_exists")){ /* 已有仓·直接更新文件 */ }
        else if(!cr.contains("\"full_name\"")) return false;
        String base="https://api.github.com/repos/"+GH_USER+"/contents/";
        String patch="- insert:\n    - id: "+name.replace("dsh-","")+"\n      name: '"+name+"'\n      config: { enabled: true }\n";
        String pkg="{\"name\":\""+name+"\",\"version\":\"1.0.0\",\"private\":false,\"type\":\"module\",\"description\":\""+desc.replace("\"","'")+"\",\"license\":\"MIT\",\"main\":\"./index.js\",\"dsh\":{\"bundle\":{\"patch\":\"./cordis.patch.yml\"}}}";
        String readme="# "+name+"\n\n"+desc+"\n\nInstall:\n```\ndsh plugin add github:"+GH_USER+"/"+name+"\n```\nMIT License.\n";
        String pid=name.replace("dsh-","");
        String localeEn="{\"meta\":{\"title\":\""+pid+"\",\"description\":\""+desc.replace("\"","'")+"\"}}";
        String localeZh="{\"meta\":{\"title\":\""+name+"\",\"description\":\""+desc.replace("\"","'")+"\"}}";
        String[][] files={{"package.json",pkg},{"index.js",indexJs},{"cordis.patch.yml",patch},{"README.md",readme},
                          {"locale/en.json",localeEn},{"locale/zh.json",localeZh}};
        for(String[] f:files){
            String put="{\"message\":\"publish "+name+"\",\"content\":\""+b64(f[1])+"\"}";
            String r=gh(base+f[0],"PUT",put,token);
            if(r==null||(!r.contains("content")&&!r.contains("message"))) return false;
        }
        // 打话题（市场按dsh-plugin话题抓取）
        gh("https://api.github.com/repos/"+GH_USER+"/"+name+"/topics","PUT",
           "{\"names\":[\"dsh\",\"dsh-plugin\",\"deepseek-harness\",\"ai-agent\",\"agent\",\"plugin\"]}",token);
        return true;
    }
    void showPluginPublish(){
        android.widget.ScrollView sc=new android.widget.ScrollView(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(10),dp(20),dp(10));
        EditText etName=new EditText(this); etName.setHint("插件名·如 dsh-my-plugin"); etName.setTextSize(14);
        EditText etDesc=new EditText(this); etDesc.setHint("一句话描述（中英皆可）"); etDesc.setTextSize(14);
        EditText etCode=new EditText(this); etCode.setHint("index.js 全文粘贴\n（export const name=… + export default function plugin(ctx){…}）");
        etCode.setTextSize(12); etCode.setMinHeight(dp(220)); etCode.setGravity(Gravity.TOP);
        etCode.setBackground(round(NIGHT?0xFF2A2A2A:0xFFFFFFFF,dp(8)));
        EditText etTok=new EditText(this); etTok.setHint("GitHub令牌（首次填·记住后不再问）"); etTok.setTextSize(12);
        String saved=ghToken(); if(!saved.isEmpty()){ etTok.setHint("令牌已记住（留空即用）"); }
        box.addView(etName); box.addView(etDesc); box.addView(etCode); box.addView(etTok);
        sc.addView(box);
        new AlertDialog.Builder(act).setTitle("🚀 插件发布·直上市场")
            .setView(sc)
            .setPositiveButton("发布",(d,w)->{
                String name=etName.getText().toString().trim();
                String desc=etDesc.getText().toString().trim();
                String code=etCode.getText().toString();
                String tokIn=etTok.getText().toString().trim();
                final String tok=tokIn.isEmpty()?saved:tokIn;
                if(!name.matches("dsh-[a-z0-9-]+")){ toast("插件名须 dsh- 开头·小写字母数字连字符"); return; }
                if(desc.isEmpty()||code.trim().isEmpty()||tok.isEmpty()){ toast("四项都要填（令牌首次必填）"); return; }
                getSharedPreferences("zh",0).edit().putString("gh_token",tok).apply();
                toast("发布中…约10秒");
                net(()->{
                    boolean ok=ghPublish(tok,name,desc,code);
                    ui.post(()->{
                        if(ok){ toast("✅ 发布成功·明日市场可搜");
                            new AlertDialog.Builder(act).setTitle("✅ 已发布")
                                .setMessage(name+" 已上 GitHub 公开仓\n· 立即可看: github.com/"+GH_USER+"/"+name+"\n· 市场(GitHub Actions 每日抓取)明日可搜\n· 别人安装: dsh plugin add github:"+GH_USER+"/"+name)
                                .setPositiveButton("看已发布栏",(dd,ww)->showPublished())
                                .setNegativeButton("关闭",null).show();
                        } else toast("发布失败·查令牌/网络/仓是否同名");
                    });
                });
            })
            .setNegativeButton("取消",null).show();
    }
    void showPublished(){
        net(()->{
            String r=gh("https://api.github.com/users/"+GH_USER+"/repos?per_page=100&sort=pushed","GET",null,"");
            StringBuilder sb=new StringBuilder();
            int n=0;
            try{
                org.json.JSONArray arr=new org.json.JSONArray(r==null?"[]":r);
                for(int i=0;i<arr.length();i++){
                    org.json.JSONObject o=arr.getJSONObject(i);
                    String nm=o.optString("name","");
                    if(!nm.startsWith("dsh-")) continue;
                    n++;
                    sb.append("📦 ").append(nm)
                      .append("  ★").append(o.optInt("stargazers_count",0))
                      .append(o.optBoolean("private")?" [私有]":"")
                      .append("\n   ").append(o.optString("description","")).append("\n\n");
                }
            }catch(Exception e){}
            final int cnt=n;
            if(cnt==0) sb.append("还没有已发布插件\n去「🚀 插件发布」发第一件");
            else sb.append("共 ").append(cnt).append(" 件 · 市场按 dsh-plugin 话题每日抓取·发布次日可搜");
            final String s=sb.toString();
            ui.post(()->new AlertDialog.Builder(act).setTitle("📡 已发布（"+cnt+"）").setMessage(s)
                .setPositiveButton("打开主页",(dd,ww)->openUrl("https://github.com/"+GH_USER+"?tab=repositories&q=dsh-"))
                .setNegativeButton("关闭",null).show());
        });
    }

    void checkUpdate(){
        net(()->{
            try{
                String r=http("/api/version",null);
                org.json.JSONObject j=new org.json.JSONObject(r);
                int code=j.optInt("code",0); final String url=j.optString("url",""); final String name=j.optString("name","");
                ui.post(()->{
                    if(code>getVerCode()){
                        new android.app.AlertDialog.Builder(act)
                            .setTitle("发现新版本 "+name)
                            .setMessage("当前 v"+getVerName()+" → 最新 "+name+"\n点击确定下载安装")
                            .setPositiveButton("下载更新",(d,w)->{
                                try{ startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url))); }
                                catch(Exception e){ toast("打开下载失败"); }
                            }).setNegativeButton("以后再说",null).show();
                    } else toast("已是最新版本 v"+getVerName());
                });
            }catch(Exception e){ ui.post(()->toast("检查更新失败·检查网络")); }
        });
    }
    void doLogin(String un,String pw,View lv,boolean isReg){
        if(un==null||un.trim().length()<2||pw==null||pw.length()<4){ toast("用户名≥2位·密码≥4位"); return; }
        final String ep="u="+enc(un.trim())+"&p="+enc(pw);
        net(()->{
            String r=http(isReg?"/register":"/login",ep);
            if((r==null||!r.contains("ok"))&&!isReg){ toast("登录失败·没账号就点注册"); return; }
            if(r==null||!r.contains("ok")){ toast("注册失败·用户名可能已存在"); return; }
            sid=getSid(); getSharedPreferences("zh",0).edit().putString("user",un.trim()).apply(); ui.post(()->{ lv.setVisibility(View.GONE); tabs.setVisibility(View.VISIBLE); top.setVisibility(View.VISIBLE); fab.setVisibility(View.VISIBLE); loadSessions(); });
        });
    }
    boolean who(){ String r=http("/api/who",null); try{return new JSONObject(r).optBoolean("ok");}catch(Exception e){return false;} }
    String getSid(){ try{for(HttpCookie c:cm.getCookieStore().getCookies())if(c.getName().equals("sid"))return c.getValue();}catch(Exception e){} return ""; }
    void loadSessions(){
        net(()->{
            String r=http("/api/list",""); if(r==null)return;
            try{ JSONArray arr=new JSONObject(r).getJSONArray("sessions");
                ArrayList<String> items=new ArrayList<>(); final ArrayList<String> ids=new ArrayList<>();
                for(int i=0;i<arr.length();i++){ JSONObject s=arr.getJSONObject(i); ids.add(s.optString("id",""));
                    items.add("💬 "+s.optString("title","新对话")+"\n"+new Date(s.optLong("ts",0)*1000).toLocaleString()); }
                ui.post(()->{ sessList.setAdapter(new SessAdapter(act,items));
                    sessList.setOnItemClickListener((a,v,p,l)->openChat(ids.get(p)));
                    sessList.setOnItemLongClickListener((a,v,p,l)->{
                        final String sid0=ids.get(p);
                        new AlertDialog.Builder(act).setTitle("会话操作")
                          .setItems(new String[]{"✏️ 重命名","📌 置顶/取消置顶","📤 导出记录"},(dg,w)->{
                            if(w==0){
                                final EditText in=new EditText(act); in.setText("");
                                new AlertDialog.Builder(act).setTitle("新会话名").setView(in)
                                  .setPositiveButton("确定",(d2,w2)->net(()->{
                                    http("/api/rename","id="+enc(sid0)+"&t="+enc(in.getText().toString()));
                                    ui.post(()->{toast("已改名");loadSessions();});
                                  })).setNegativeButton("取消",null).show();
                            } else if(w==1) {
                                net(()->{ http("/api/pin","id="+enc(sid0)+"&v=1");
                                  ui.post(()->{toast("已切换置顶");loadSessions();}); });
                            } else {
                                net(()->{ String rx=http("/api/export","id="+enc(sid0));
                                  try{
                                    android.content.ContentValues cv=new android.content.ContentValues();
                                    cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"会话导出_"+sid0+".txt");
                                    cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,"text/plain");
                                    android.net.Uri uri=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
                                    OutputStream os=getContentResolver().openOutputStream(uri);
                                    os.write(rx.getBytes("UTF-8")); os.close();
                                    ui.post(()->toast("✅导出到下载目录·会话导出_"+sid0+".txt"));
                                  }catch(Exception e){ ui.post(()->toast("导出失败")); } });
                            }
                          }).show();
                        return true; }); });
            }catch(Exception e){
                if(getSharedPreferences("zh",0).getString("user","").isEmpty()){
                    ui.post(()->{ sessView.setVisibility(View.GONE); if(tabs!=null)tabs.setVisibility(View.GONE); if(top!=null)top.setVisibility(View.GONE); if(fab!=null)fab.setVisibility(View.GONE);
                        flip.getChildAt(2).setVisibility(View.VISIBLE); toast("请先登录"); });
                } else { // v14: 凭证失效自动弹登录页(不再死循环报错)
                    ui.post(()->{ getSharedPreferences("zh",0).edit().putString("user","").apply();
                        sessView.setVisibility(View.GONE); if(tabs!=null)tabs.setVisibility(View.GONE); if(top!=null)top.setVisibility(View.GONE); if(fab!=null)fab.setVisibility(View.GONE);
                        flip.getChildAt(2).setVisibility(View.VISIBLE); toast("登录已失效·请重新登录"); });
                }
            }
        });
    }
    void openChat(String id){ cs=id; net(()->{
        String r=http("/api/get","id="+enc(cs)); if(r==null)return;
        try{ JSONArray arr=new JSONObject(r).getJSONArray("msgs");
            ArrayList<JSONObject> list=new ArrayList<>(); for(int i=Math.max(0,arr.length()-200);i<arr.length();i++)list.add(arr.getJSONObject(i));
            ui.post(()->{ flip.getChildAt(0).setVisibility(View.GONE); chatView.setVisibility(View.VISIBLE); render(list); }); }
        catch(Exception e){}
    });}
    void showSessions(){ chatView.setVisibility(View.GONE); flip.getChildAt(0).setVisibility(View.VISIBLE); loadSessions(); }
    void newChat(){ String id="s"+System.currentTimeMillis(); openChat(id); }
    void render(ArrayList<JSONObject> list){ render(list,null); }
    void render(ArrayList<JSONObject> list,String hl){
        chatBox.removeAllViews();
        for(JSONObject m:list) smartBubble(m.optString("c","ai"),m.optString("m"),hl);
        ui.post(()->{
            if(hl!=null){ scrollToText(hl); } else chatScroll.fullScroll(View.FOCUS_DOWN); }); }
    boolean isCn(String l){ int cn=0; for(char ch:l.toCharArray()) if(Character.toString(ch).matches("[\\u4e00-\\u9fff]"))cn++;
        return l.length()>0 && cn*100/l.length()>=15; }
    void smartBubble(String who,String text,String hl){
        StringBuilder body=new StringBuilder(),det=new StringBuilder();
        for(String l:text.split("\n")){
            boolean tech=l.startsWith("/CMD")||l.startsWith("⚙")||l.startsWith("↳")||l.startsWith("===")||(l.length()>0&&!isCn(l));
            if(tech)det.append(l).append("\n"); else body.append(l).append("\n"); }
        String bt=body.toString().trim(),dt=det.toString().trim();
        if(dt.length()==0){ addBubble(who,bt); if(hl!=null)markHl(bt,hl); return; }
        Object[] parts=addBubbleV2(who,bt);
        TextView hdr=(TextView)parts[1],detc=(TextView)parts[2];
        detc.setText(dt); hdr.setVisibility(View.VISIBLE);
        hdr.setText("⚙️ 执行细节（"+dt.split("\n").length+"行）▼");
        if(hl!=null&&bt.contains(hl))markHl(bt,hl);
    }
    void markHl(String bt,String hl){}
    void scrollToText(String kw){
        chatScroll.post(()->{
            for(int i=0;i<chatBox.getChildCount();i++){
                View row=chatBox.getChildAt(i);
                java.util.ArrayList<TextView> tvs=new java.util.ArrayList<>();
                collect(row,tvs);
                for(TextView t:tvs){ String tx=t.getText().toString();
                    if(tx.contains(kw)){ t.setBackgroundColor(0xFFFFE58A);
                        int top=0; View p=row; while(p!=chatBox){ top+=p==null?0:p.getTop(); View pp=(View)p.getParent(); if(pp==chatBox)break; p=pp; }
                        chatScroll.smoothScrollTo(0,Math.max(0,top-dp(60))); return; } } }
        }); }
    void collect(View v,java.util.ArrayList<TextView> out){
        if(v instanceof TextView)out.add((TextView)v);
        if(v instanceof ViewGroup){ ViewGroup g=(ViewGroup)v; for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out); } }
    Object[] addBubbleV2(String who,String text){
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        TextView hdr=new TextView(this); hdr.setText(""); hdr.setTextSize(12); hdr.setTextColor(C_SUB);
        hdr.setPadding(dp(8),dp(2),dp(8),dp(2)); hdr.setVisibility(View.GONE);
        TextView det=new TextView(this); det.setTextSize(12); det.setTextColor(C_SUB);
        det.setBackgroundColor(NIGHT?0xFF26231E:0xFFF0EAE0); det.setPadding(dp(10),dp(6),dp(10),dp(6)); det.setVisibility(View.GONE);
        TextView body=new TextView(this); body.setText(text); body.setTextSize(15); body.setTextColor(C_TXT);
        body.setLineSpacing(dpf(2.5f),1f);
        body.setBackground(round(who.equals("me")?C_ME:C_AI,dp(16))); body.setPadding(dp(14),dp(10),dp(14),dp(10));
        body.setTextIsSelectable(true);
        body.setOnLongClickListener(v->{
            android.content.ClipboardManager c2=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            c2.setPrimaryClip(android.content.ClipData.newPlainText("msg",body.getText().toString()));
            Toast.makeText(act,"✅本条已复制",Toast.LENGTH_SHORT).show(); return true; });
        hdr.setOnClickListener(v->{ det.setVisibility(det.getVisibility()==View.GONE?View.VISIBLE:View.GONE); });
        boolean me=who.equals("me");
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.gravity=me?Gravity.END:Gravity.START; lp.width=me?-2:-1; lp.leftMargin=me?dp(48):dp(4); lp.rightMargin=me?dp(4):dp(4);
        wrap.addView(hdr,new LinearLayout.LayoutParams(-2,-2));
        wrap.addView(det,new LinearLayout.LayoutParams(-1,-2));
        wrap.addView(body,lp);
        LinearLayout.LayoutParams lw=new LinearLayout.LayoutParams(-1,-2); lw.topMargin=dp(6);
        chatBox.addView(wrap,lw);
        ui.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
        TextView status=new TextView(this); status.setText(""); status.setTextSize(12); status.setTextColor(C_SUB);
        status.setTypeface(null,Typeface.ITALIC); status.setPadding(dp(14),dp(2),dp(8),dp(2));
        status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        status.setMarqueeRepeatLimit(-1); status.setVisibility(View.GONE);
        wrap.addView(status,new LinearLayout.LayoutParams(-1,-2));
        return new Object[]{body,hdr,det,status};
    }
    TextView addBubble(String who,String text){
        boolean me=who.equals("me");
        TextView t=new TextView(this); t.setText(text); t.setTextSize(15); t.setTextColor(C_TXT);
        t.setLineSpacing(dpf(2.5f),1f); t.setBackground(me?round(C_ME,dp(16)):round(C_AI,dp(16))); t.setPadding(dp(14),dp(10),dp(14),dp(10));
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.topMargin=dp(6);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        lp.gravity=me?Gravity.END:Gravity.START; lp.width=me?-2:-1; lp.leftMargin=me?dp(48):dp(4); lp.rightMargin=me?dp(4):dp(4);
        t.setOnLongClickListener(v->{
            android.content.ClipboardManager c2=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            c2.setPrimaryClip(android.content.ClipData.newPlainText("msg",t.getText().toString()));
            Toast.makeText(act,"✅本条已复制",Toast.LENGTH_SHORT).show(); return true; });
        row.addView(t,lp); chatBox.addView(row,new LinearLayout.LayoutParams(-1,-2));
        ui.post(()->chatScroll.fullScroll(View.FOCUS_DOWN)); return t; }
    void sendText(){
        String msg=input.getText().toString().trim(); if(msg.isEmpty()||cs.isEmpty())return;
        input.setText(""); addBubble("me",msg);
        if(!pendingUris.isEmpty()){
            final java.util.ArrayList<Uri> toSend=new java.util.ArrayList<>(pendingUris);
            pendingUris.clear(); pendingBar.setVisibility(View.GONE);
            addBubble("me","[📎附 "+toSend.size()+"张图]");
            for(Uri u:toSend)uploadOne(u);
        }
        final Object[] parts=addBubbleV2("ai","…"); lastBot=(TextView)parts[0];
        final TextView bot=(TextView)parts[0],hdr=(TextView)parts[1],det=(TextView)parts[2];
        final TextView status=(TextView)parts[3];
        final long t0=android.os.SystemClock.elapsedRealtime();
        net(()->{
            try{
                final String r=http("/api/chat_bg","id="+enc(cs)+"&msg="+enc(msg)+"&model="+enc(getSharedPreferences("zh",0).getString("model","glm-5.3-flash")));
                final String tid=new JSONObject(r).optString("tid","");
                final String tid2=new JSONObject(r).optString("tid2","");
                ui.post(()->send.setText("⏹"));
                StringBuilder shown=new StringBuilder(); int lastIdx=0;
                while(true){
                    Thread.sleep(900);
                    String tr=http("/api/task?tid="+enc(tid),null);
                    JSONObject t=new JSONObject(tr);
                    JSONArray steps=t.optJSONArray("steps"); if(steps==null) steps=new JSONArray();
                    for(int i=lastIdx;i<steps.length();i++){
                        JSONObject st=steps.getJSONObject(i);
                        final String cx=st.optString("cmd",""); final String th=st.optString("thought","");
                        final String stepTxt=st.has("cmd")?("⚙ 正在执行 "+cx):("💭 "+th.split("\n")[0]);
                        ui.post(()->{ statusBase=stepTxt; statusV=status; status.setText(MOON[0]+" "+stepTxt); status.setVisibility(View.VISIBLE); status.setSelected(true); startSpin(); });
                        if(st.has("cmd")){
                            final String line="⚙️ "+cx+"\n↳ "+st.optString("out","").substring(0,Math.min(300,st.optString("out","").length()))+"\n\n";
                            ui.post(()->det.append(line));
                            shown.append("\n"); }
                        else if(st.has("thought")){
                            final String tl="💭 "+th+"\n\n";
                            ui.post(()->det.append(tl));
                            for(String ln:th.split("\n")){ if(isCn(ln)){ shown.append("\n").append(ln.length()>80?ln.substring(0,80)+"…":ln); break; } } }
                        final String hdrtxt="已思考（用时"+(android.os.SystemClock.elapsedRealtime()-t0)/1000+"秒·"+steps.length()+"步）▼";
                        ui.post(()->{ hdr.setVisibility(View.VISIBLE); hdr.setText(hdrtxt); });
                        uiSet(bot,shown+"\n（他在干活…可切出去，回来收结果）");
                    }
                    lastIdx=steps.length();
                    if(t.getBoolean("done")){
                        final String ff=t.optString("full",""); final int ns=steps.length();
                        ui.post(()->{ hdr.setText("已思考（用时"+(android.os.SystemClock.elapsedRealtime()-t0)/1000+"秒·"+ns+"步）▼"); if(spinR!=null)ui.removeCallbacks(spinR); status.setVisibility(View.GONE); });
                        uiSet(bot,ff);
                        if(!tid2.isEmpty()){ // v15一问多模:第二底座对照
                            ui.post(()->uiSet(bot,ff+"\n\n━━━ 一问多模·DeepSeek出刀中… ━━━"));
                            while(true){
                                Thread.sleep(1200);
                                String tr2=http("/api/task?tid="+enc(tid2),null);
                                JSONObject t2=new JSONObject(tr2);
                                if(t2.optBoolean("done")){ final String f2=t2.optString("full","");
                                    ui.post(()->uiSet(bot,ff+"\n\n━━━ 一问多模·DeepSeek ━━━\n"+f2)); break; }
                            }
                        }
                        break; }
                }
            }catch(Exception e){ uiSet(bot,"⚠ "+e.getMessage()); }
            ui.post(()->{ send.setText("➤"); speakOut(bot.getText().toString()); });
        });
    }
    void streamChat(String msg,TextView bot,TextView hdr,TextView det,long t0){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(serverBase()+"/api/chat_stream").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(300000);
            c.setRequestProperty("Cookie","sid="+sid);
            OutputStream o=c.getOutputStream();
            o.write(("id="+enc(cs)+"&msg="+enc(msg)).getBytes("UTF-8")); o.close();
            if(c.getResponseCode()!=200){ uiSet(bot,"⚠ 连接失败"+c.getResponseCode()); return; }
            BufferedReader rd=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder(); String ln;
            while((ln=rd.readLine())!=null){ if(!ln.startsWith("data: "))continue;
                if(ln.length()<7)continue; JSONObject j=new JSONObject(ln.substring(6));
                if(j.has("chunk")){ sb.append(j.optString("chunk","")); uiSet(bot,sb.toString()); }
                else if(j.has("status")){ sb.append("\n⚡").append(j.optString("status","")); uiSet(bot,sb.toString()); }
                else if(j.has("exec")){ String cx=j.getJSONObject("exec").optString("cmd","");
                    det.append("⚙️ "+cx+"\n"); ui.post(()->{ hdr.setVisibility(View.VISIBLE); hdr.setText("已思考（用时"+(android.os.SystemClock.elapsedRealtime()-t0)/1000+"秒）▼"); }); }
                else if(j.has("exec_result")){ String oo=j.getJSONObject("exec_result").optString("out","");
                    det.append("↳ "+oo.substring(0,Math.min(150,oo.length()))+"\n"); }
            }
            rd.close();
            final long el=(android.os.SystemClock.elapsedRealtime()-t0)/1000;
            ui.post(()->{ if(hdr.getVisibility()==View.VISIBLE)hdr.setText("已思考（用时"+el+"秒）▼"); else if(el>2){hdr.setVisibility(View.VISIBLE);hdr.setText("用时"+el+"秒");} });
            try{ String r2=http("/api/get","id="+enc(cs));
                JSONArray a2=new JSONObject(r2).getJSONArray("msgs");
                if(a2.length()>0){ String fa=a2.getJSONObject(a2.length()-1).optString("m","");
                    if(fa.length()>0) uiSet(bot,fa); } }catch(Exception ee){}
        }catch(Exception e){
            // 自愈:流断了拉全量历史,若最后一条比当前显示长则补全
            try{ Thread.sleep(1500);
                String r=http("/api/get","id="+enc(cs));
                JSONArray arr=new JSONObject(r).getJSONArray("msgs");
                if(arr.length()>0){ String last=arr.getJSONObject(arr.length()-1).optString("m","");
                    String cur=bot.getText().toString();
                    if(last.length()>cur.length()) uiSet(bot,last.replaceFirst("^[\\s\\S]*?(?=[\\S])","").length()>0&&cur.equals("…")?last:last); }
            }catch(Exception e2){}
        }
    }
    void startSpin(){ if(spinR!=null)ui.removeCallbacks(spinR);
        spinR=()->{ if(statusV==null||statusV.getVisibility()!=View.VISIBLE)return;
            spinN=(spinN+1)%MOON.length; statusV.setText(MOON[spinN]+" "+statusBase); ui.postDelayed(spinR,200); };
        ui.postDelayed(spinR,200); }
    void uiSet(final TextView t,final String s){ ui.post(()->{ t.setText(s); chatScroll.fullScroll(View.FOCUS_DOWN); }); }
    TextView lastBot;
    void speakOut(String s){ try{ if(tts==null||!ttsOn)return;
        StringBuilder b=new StringBuilder(); for(String ln:s.split("\n")){ if(!ln.startsWith("⚙")&&!ln.startsWith("↳")&&!ln.startsWith("💭")) b.append(ln); }
        String txt=b.toString().replace("━","").trim(); if(txt.length()>600)txt=txt.substring(0,600);
        if(txt.length()>0) tts.speak(txt,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"zhishen"); }catch(Exception e){} }
    void ttsInit(){ try{ tts=new android.speech.tts.TextToSpeech(this,st->{ if(st==android.speech.tts.TextToSpeech.SUCCESS) tts.setLanguage(java.util.Locale.CHINA); }); }catch(Exception e){} }
    void toggleRec(){
        if(!recing){
            try{ recFile=new File(getCacheDir(),"v"+System.currentTimeMillis()+".m4a");
                rec=new MediaRecorder(); rec.setAudioSource(MediaRecorder.AudioSource.MIC);
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                rec.setOutputFile(recFile.getAbsolutePath()); rec.prepare(); rec.start(); recing=true; toast("🎙 说吧…再点结束"); }
            catch(Exception e){ toast("麦克风不可用"); }
        }else{
            try{ rec.stop(); }catch(Exception e){}
            recing=false; rec.release(); rec=null; sendVoice(recFile);
        }
    }
    void sendVoice(final File f){
        addBubble("me","🎤[语音发送中…]");
        net(()->{
            String r=multipart("/api/voice","id="+cs,"a",f);
            try{ JSONObject j=new JSONObject(r);
                if(j.has("err")){ ui.post(()->{try{addBubble("ai","⚠"+j.getString("err"));}catch(Exception e){}}); return; }
                final String txt=j.optString("text"),rep=j.optString("reply");
                String au=j.optString("audio");
                ui.post(()->{ try{ addBubble("me","🎤[语音] "+txt); addBubble("ai",rep+" 🔊"); }catch(Exception e){} });
                if(au!=null&&au.length()>1000){ byte[] mp3=Base64.decode(au,Base64.DEFAULT);
                    File pf=new File(getCacheDir(),"tts.mp3"); FileOutputStream fo=new FileOutputStream(pf); fo.write(mp3); fo.close();
                    MediaPlayer mp=new MediaPlayer(); mp.setDataSource(pf.getAbsolutePath()); mp.prepare(); mp.start(); }
            }catch(Exception e){ ui.post(()->addBubble("ai","⚠语音异常")); }
        });
    }
    void pickFile(){
        new AlertDialog.Builder(this).setTitle("发送什么?")
          .setItems(new String[]{"🖼️ 图片(可多选10张)","🎬 视频","📄 任意文件"},(dg,w)->{
            Intent i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType(w==0?"image/*":(w==1?"video/*":"*/*"));
            if(w==0){ i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); }
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i,"选择"),w==0?21:7);
          }).show();
    }
    @Override protected void onActivityResult(int rq,int rc,Intent d){
        super.onActivityResult(rq,rc,d);
        if(rc!=RESULT_OK||d==null)return;
        if(rq==21){
            java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();
            if(d.getClipData()!=null){ for(int i=0;i<Math.min(10,d.getClipData().getItemCount());i++)uris.add(d.getClipData().getItemAt(i).getUri()); }
            else if(d.getData()!=null)uris.add(d.getData());
            pendingUris.addAll(uris);
            pendingBar.setVisibility(View.VISIBLE);
            pendingBar.setText("🖼️ "+pendingUris.size()+"张图待发·可继续打字,点➤一起发(点此取消)");
            return;
        }
        if(rq!=7)return;
        Uri u=d.getData(); uploadOne(u);
    }
    void uploadOne(Uri u){
        try{
            String name=queryName(u);
            File tmp=new File(getCacheDir(),"up_"+name);
            InputStream is=getContentResolver().openInputStream(u); FileOutputStream fo=new FileOutputStream(tmp);
            byte[] buf=new byte[65536]; int n; long size=0;
            while((n=is.read(buf))>0){ fo.write(buf,0,n); size+=n; } is.close(); fo.close();
            final File ff=tmp; final String fn=name;
            addBubble("me","[发送中: "+fn+"]");
            net(()->{
                String r;
                if(ff.length()>3*1024*1024){ r=chunkUpload(ff,fn); } else { r=multipart("/api/upload","id="+cs,"f",ff); }
                try{ JSONObject j=new JSONObject(r);
                    ui.post(()->{ try{ addBubble("me","["+fn+" "+(ff.length()/1024)+"KB·已存VPS]"); addBubble("ai",j.optString("reply","已收")); }catch(Exception e){} });
                }catch(Exception e){ ui.post(()->addBubble("ai","⚠上传异常")); }
            });
        }catch(Exception e){ toast("读取文件失败"); }
    }
    String chunkUpload(File f,String name){
        try{
            long len=f.length(); int CH=3*1024*1024; int total=(int)((len+CH-1)/CH);
            for(int i=0;i<total;i++){
                long st=(long)i*CH, en=Math.min(len,st+CH);
                File part=new File(getCacheDir(),"part"+i);
                InputStream in=new FileInputStream(f); in.skip(st); FileOutputStream po=new FileOutputStream(part);
                byte[] b=new byte[65536]; long left=en-st; int n;
                while(left>0&&(n=in.read(b,0,(int)Math.min(b.length,left)))>0){ po.write(b,0,n); left-=n; }
                in.close(); po.close();
                String r=uploadWithProgress("/api/upchunk","id="+cs+"&fname="+enc(name)+"&idx="+i,part,null);
                part.delete();
            }
            return uploadWithProgress("/api/updone","id="+cs+"&fname="+enc(name)+"&total="+total,null,null);
        }catch(Exception e){ return "{\"reply\":\"⚠分块失败\"}"; }
    }
    String uploadWithProgress(String path,String fields,File f,Runnable prog){
        try{
            String B="----zk"; HttpURLConnection c=(HttpURLConnection)new URL(serverBase()+path).openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(300000);
            c.setRequestProperty("Cookie","sid="+sid); c.setRequestProperty("Content-Type","multipart/form-data; boundary="+B);
            OutputStream o=new DataOutputStream(c.getOutputStream());
            for(String kv:fields.split("(?=&[a-z]+=)")){ int eq=kv.indexOf('&'); String pair=eq==0?kv.substring(1):kv;
                String[] p=pair.split("=",2); o.write(("--"+B+"\r\nContent-Disposition: form-data; name=\""+p[0]+"\"\r\n\r\n"+(p.length>1?p[1]:"")+"\r\n").getBytes("UTF-8")); }
            if(f!=null){ o.write(("--"+B+"\r\nContent-Disposition: form-data; name=\"f\"; filename=\"p\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes());
                FileInputStream fi=new FileInputStream(f); byte[] b=new byte[65536]; int n; while((n=fi.read(b))>0)o.write(b,0,n); fi.close(); o.write("\r\n".getBytes()); }
            o.write(("--"+B+"--\r\n").getBytes()); o.close();
            BufferedReader rd=new BufferedReader(new InputStreamReader(c.getResponseCode()==200?c.getInputStream():c.getErrorStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder(); String l; while((l=rd.readLine())!=null)sb.append(l); rd.close(); return sb.toString();
        }catch(Exception e){ return "{\"reply\":\"⚠"+e.getMessage()+"\"}"; }
    }
    String multipart(String path,String fields,String fkey,File f){ return multipart(path,fields,fkey,f,f.getName()); }
    String multipart(String path,String fields,String fkey,File f,String fname){
        try{
            String B="----mm"; HttpURLConnection c=(HttpURLConnection)new URL(serverBase()+path).openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(300000);
            c.setRequestProperty("Cookie","sid="+sid); c.setRequestProperty("Content-Type","multipart/form-data; boundary="+B);
            OutputStream o=new DataOutputStream(c.getOutputStream());
            String[] kv=fields.split("&");
            for(String k:kv){ String[] p=k.split("=",2); o.write(("--"+B+"\r\nContent-Disposition: form-data; name=\""+p[0]+"\"\r\n\r\n"+(p.length>1?p[1]:"")+"\r\n").getBytes("UTF-8")); }
            o.write(("--"+B+"\r\nContent-Disposition: form-data; name=\""+fkey+"\"; filename=\""+fname+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes());
            FileInputStream fi=new FileInputStream(f); byte[] b=new byte[65536]; int n; while((n=fi.read(b))>0)o.write(b,0,n); fi.close();
            o.write(("\r\n--"+B+"--\r\n").getBytes()); o.close();
            BufferedReader rd=new BufferedReader(new InputStreamReader(c.getResponseCode()==200?c.getInputStream():c.getErrorStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder(); String l; while((l=rd.readLine())!=null)sb.append(l); rd.close(); return sb.toString();
        }catch(Exception e){ return "{\"reply\":\"⚠"+e.getMessage()+"\"}"; }
    }
    void showFiles(){
        net(()->{
            String r=http("/api/files","");
            try{ JSONArray arr=new JSONObject(r).getJSONArray("files");
                final ArrayList<String> names=new ArrayList<>(); final ArrayList<String> paths=new ArrayList<>();
                for(int i=0;i<arr.length();i++){ JSONObject f=arr.getJSONObject(i); names.add("⬇️ "+f.optString("name","")+" ("+Math.max(1,f.getLong("size")/1024)+"KB)"); paths.add(f.getString("name")); }
                ui.post(()->{
                    if(names.isEmpty()){ toast("暂无文件——让他cp到/opt/zhishen/share/"); return; }
                    new AlertDialog.Builder(this).setTitle("📦 我的文件").setItems(names.toArray(new String[0]),(dg,w)->download(paths.get(w))).show();
                });
            }catch(Exception e){ toast("文件列表异常"); }
        });
    }
    void doSearch(){
        final EditText in=new EditText(this); in.setHint("搜关键词…");
        new AlertDialog.Builder(this).setTitle("🔍 搜索聊天记录").setView(in)
          .setPositiveButton("搜",(d,w)->{ String q=in.getText().toString().trim(); if(q.isEmpty())return;
            net(()->{
                String r=http("/api/search","q="+enc(q)); ArrayList<String> items=new ArrayList<>(); final ArrayList<String[]> refs=new ArrayList<>();
                try{ JSONArray hits=new JSONObject(r).getJSONArray("hits");
                    for(int i=0;i<hits.length();i++){ JSONObject h=hits.optJSONObject(i); if(h==null)continue;
                        items.add("[" + h.optString("title","") + "]\n…" + h.optString("snip","") + "…");
                        refs.add(new String[]{h.optString("sid"),q}); } }catch(Exception e){}
                ui.post(()->{
                    if(items.isEmpty()){ toast("没搜到「"+q+"」"); return; }
                    new AlertDialog.Builder(act).setTitle("找到"+items.size()+"条").setItems(items.toArray(new String[0]),(d2,w2)->{
                        String[] rf=refs.get(w2); cs=rf[0];
                        net(()->{ String hr=http("/api/get","id="+enc(cs));
                            try{ JSONArray arr=new JSONObject(hr).getJSONArray("msgs");
                                ArrayList<JSONObject> list=new ArrayList<>(); for(int i=Math.max(0,arr.length()-200);i<arr.length();i++)list.add(arr.getJSONObject(i));
                                ui.post(()->{ flip.getChildAt(0).setVisibility(View.GONE); chatView.setVisibility(View.VISIBLE); render(list,rf[1]); });
                            }catch(Exception e){} });
                    }).show();
                });
            });
          }).setNegativeButton("取消",null).show();
    }
    void copySession(){
        if(cs.isEmpty()){ toast("先进入一个会话"); return; }
        net(()->{
            try{ String r=http("/api/get","id="+enc(cs));
                JSONArray arr=new JSONObject(r).getJSONArray("msgs");
                StringBuilder sb=new StringBuilder();
                for(int i=0;i<arr.length();i++){ JSONObject m=arr.getJSONObject(i);
                    sb.append(m.optString("c").equals("me")?"我":"白幼真").append(": ").append(m.optString("m")).append("\n\n"); }
                android.content.ClipboardManager cm2=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                cm2.setPrimaryClip(android.content.ClipData.newPlainText("chat",sb.toString()));
                ui.post(()->toast("✅全会话已复制("+arr.length()+"条)·去粘贴"));
            }catch(Exception e){ ui.post(()->toast("复制失败")); }
        });
    }
    void download(final String f){
        toast("开始下载:"+new File(f).getName());
        new Thread(()->{
            try{
                String name=new File(f).getName();
                URL u=new URL(serverBase()+"/api/dl?f="+enc(f));
                final HttpURLConnection c=(HttpURLConnection)u.openConnection();
                c.setRequestProperty("Cookie","sid="+sid); c.setConnectTimeout(10000); c.setReadTimeout(300000);
                InputStream in=c.getInputStream();
                OutputStream out; long t=0;
                if(Build.VERSION.SDK_INT>=29){
                    android.content.ContentValues cv=new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,name);
                    cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/octet-stream");
                    Uri uri=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv); lastUri=uri;
                    out=getContentResolver().openOutputStream(uri);
                }else{
                    out=new FileOutputStream(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),name));
                }
                byte[] b=new byte[65536]; int n;
                while((n=in.read(b))>0){ out.write(b,0,n); t+=n; }
                out.close(); in.close();
                final long tt=t;
                ui.post(()->{
                    Toast.makeText(act,"✅已下载:"+new File(f).getName()+"("+(tt/1024)+"KB)·正在打开",Toast.LENGTH_SHORT).show();
                    try{
                        if(Build.VERSION.SDK_INT>=29 && lastUri!=null){
                            Intent it=new Intent(Intent.ACTION_VIEW);
                            String nm=new File(f).getName().toLowerCase();
                            it.setDataAndType(lastUri, nm.endsWith(".apk")?"application/vnd.android.package-archive":(nm.endsWith(".md")||nm.endsWith(".txt")?"text/plain":"*/*"));
                            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(it);
                        } else { Toast.makeText(act,"已下载·去「文件管理→Download」",Toast.LENGTH_LONG).show(); }
                    }catch(Exception e2){ Toast.makeText(act,"已下载·去「文件管理→Download」",Toast.LENGTH_LONG).show(); }
                });
            }catch(final Exception e){ ui.post(()->Toast.makeText(act,"下载失败:"+e.getMessage(),Toast.LENGTH_LONG).show()); }
        }).start();
    }
    String queryName(Uri u){ Cursor c=getContentResolver().query(u,null,null,null,null);
        try{ if(c!=null&&c.moveToFirst()){ int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0)return c.getString(i); } }finally{ if(c!=null)c.close(); }
        return "file"; }
    String http(String path,String post){
        try{ HttpURLConnection c=(HttpURLConnection)new URL(serverBase()+path).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(60000); c.setRequestProperty("Cookie","sid="+sid);
            if(post!=null){ c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                OutputStream o=c.getOutputStream(); o.write(post.getBytes("UTF-8")); o.close(); }
            BufferedReader rd=new BufferedReader(new InputStreamReader(c.getResponseCode()==200?c.getInputStream():c.getErrorStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder(); String l; while((l=rd.readLine())!=null)sb.append(l); rd.close(); return sb.toString();
        }catch(Exception e){ return null; } }
    void net(final Runnable r){ new Thread(()->{ try{ r.run(); }catch(Exception e){} }).start(); }
    void toast(String s){ ui.post(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show()); }
    String enc(String s){ try{ return URLEncoder.encode(s,"UTF-8"); }catch(Exception e){ return s; } }
    Drawable round(int color,float rad){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(rad); return g; }
    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }
    float dpf(float v){ return v*getResources().getDisplayMetrics().density; }

    @Override protected void onDestroy(){ try{ if(tts!=null){tts.stop();tts.shutdown();} }catch(Exception e){} super.onDestroy(); }

// ===== v18表情面板 =====
    void showStickers(){
        try{
            android.widget.GridView gv=new android.widget.GridView(this);
            gv.setNumColumns(5); gv.setBackgroundColor(NIGHT?C_AI:0xFFFFFFFF);
            gv.setAdapter(new StkAdapter(act));
            gv.setOnItemClickListener((av,v,pid,id2)->{ dlg.dismiss(); sendSticker(pid); });
            dlg=new android.app.AlertDialog.Builder(this).setTitle("白幼真表情包").setView(gv).setNegativeButton("收起",null).create();
            dlg.show();
        }catch(Exception e){ toast("表情面板异常"); }
    }
    android.app.AlertDialog dlg;
    void sendSticker(int p){
        try{
            int rid=getResources().getIdentifier("stk"+String.format("%02d",p),"drawable",getPackageName());
            if(rid<=0)return;
            android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeResource(getResources(),rid);
            if(bm==null)return;
            File dir=new File(getCacheDir(),"stk"); dir.mkdirs();
            final File ff=new File(dir,"stk"+p+".png");
            FileOutputStream fo=new FileOutputStream(ff); bm.compress(android.graphics.Bitmap.CompressFormat.PNG,100,fo); fo.close();
            addStickerBubble(rid,true);
            net(()->{
                try{
                    String r=multipart("/api/upload","id="+cs,"f",ff);
                    JSONObject j=new JSONObject(r);
                    ui.post(()->{ try{ addBubble("ai",j.optString("reply","收到表情")); }catch(Exception e2){} });
                }catch(Exception e3){ ui.post(()->addBubble("ai","⚠表情上传异常")); }
            });
        }catch(Exception e){ toast("表情发送失败"); }
    }
    void addStickerBubble(int rid,boolean me){
        try{
            android.widget.ImageView iv=new android.widget.ImageView(this);
            iv.setImageResource(rid); iv.setAdjustViewBounds(true); iv.setMaxWidth(dp(96));
            iv.setBackground(round(me?C_ME:C_AI,dp(16))); iv.setPadding(dp(8),dp(8),dp(8),dp(8));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.topMargin=dp(6);
            lp.gravity=me?Gravity.END:Gravity.START;
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(iv,lp); chatBox.addView(row,new LinearLayout.LayoutParams(-1,-2));
            ui.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
        }catch(Exception e){}
    }
}
