package com.zhishen.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

public class SessAdapter extends ArrayAdapter<String> {
    SessAdapter(Context c, List<String> items) { super(c, android.R.layout.simple_list_item_1, items); }
    @Override public View getView(int p, View cv, ViewGroup pg) {
        TextView t = (TextView) super.getView(p, cv, pg);
        boolean night = getContext().getSharedPreferences("zh", 0).getBoolean("night", false);
        t.setTextColor(night ? 0xFFEDE7DD : 0xFF26221C);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        int d = (int)(getContext().getResources().getDisplayMetrics().density);
        t.setPadding(20*d, 15*d, 16*d, 15*d);
        return t;
    }
}
