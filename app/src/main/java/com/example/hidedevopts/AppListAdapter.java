package com.example.hidedevopts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

public class AppListAdapter extends BaseAdapter {

    private final Context context;
    private final List<AppInfo> apps;
    private final Set<String> selectedPackages;
    private final MainActivity activity;

    public AppListAdapter(Context context, List<AppInfo> apps,
                          Set<String> selectedPackages, MainActivity activity) {
        this.context = context;
        this.apps = apps;
        this.selectedPackages = selectedPackages;
        this.activity = activity;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.app_icon);
            holder.name = convertView.findViewById(R.id.app_name);
            holder.pkg = convertView.findViewById(R.id.app_package);
            holder.checkBox = convertView.findViewById(R.id.app_checkbox);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppInfo info = apps.get(position);
        holder.icon.setImageDrawable(info.icon);
        holder.name.setText(info.appName);
        holder.pkg.setText(info.packageName);

        // CheckBox 只负责显示状态，点击事件由整个 item 处理，避免复用错乱
        holder.checkBox.setClickable(false);
        holder.checkBox.setChecked(selectedPackages.contains(info.packageName));

        convertView.setOnClickListener(v -> {
            boolean newState = !holder.checkBox.isChecked();
            holder.checkBox.setChecked(newState);
            activity.onPackageChecked(info.packageName, newState);
        });

        return convertView;
    }

    static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView pkg;
        CheckBox checkBox;
    }
}