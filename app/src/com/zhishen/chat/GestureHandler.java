package com.zhishen.chat;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class GestureHandler extends GestureDetector.SimpleOnGestureListener {
    private final MainActivity act;
    GestureHandler(MainActivity a){ act=a; }
    @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy){
        if(e1==null||e2==null) return false;
        float dx=e2.getX()-e1.getX(), dy=e2.getY()-e1.getY();
        int d80=(int)(80*act.getResources().getDisplayMetrics().density);
        if(Math.abs(dx)>d80 && Math.abs(dx)>Math.abs(dy)*1.5f && Math.abs(vx)>700){
            if(dx<0 && act.chatView.getVisibility()!=android.view.View.VISIBLE){ if(!act.cs.isEmpty())act.openChat(act.cs); else act.newChat(); return true; }
            if(dx>0 && act.chatView.getVisibility()==android.view.View.VISIBLE){ act.showSessions(); return true; }
        }
        return false;
    }
}
