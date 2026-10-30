package com.zhishen.chat;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

public class StkAdapter extends BaseAdapter {
    Activity act;
    StkAdapter(Activity a){ act=a; }
    public int getCount(){ return 81; }
    public Object getItem(int p){ return p; }
    public long getItemId(int p){ return p; }
    public View getView(int p, View cv, ViewGroup pg){
        ImageView iv=(cv instanceof ImageView)?(ImageView)cv:new ImageView(act);
        int rid=act.getResources().getIdentifier("stk"+String.format("%02d",p),"drawable",act.getPackageName());
        if(rid>0){ iv.setImageResource(rid); iv.setAdjustViewBounds(true);
            float d=act.getResources().getDisplayMetrics().density;
            iv.setPadding((int)(4*d),(int)(4*d),(int)(4*d),(int)(4*d)); }
        return iv;
    }
}
