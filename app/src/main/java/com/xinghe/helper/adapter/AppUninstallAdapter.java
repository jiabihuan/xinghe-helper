package com.xinghe.helper.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;
import com.xinghe.helper.model.InstalledApp;

import java.util.ArrayList;
import java.util.List;

public class AppUninstallAdapter extends RecyclerView.Adapter<AppUninstallAdapter.AppViewHolder> {

    private final List<InstalledApp> apps = new ArrayList<>();
    private final OnAppActionListener listener;

    public interface OnAppActionListener {
        void onAppClicked(InstalledApp app, View itemView);
    }

    public AppUninstallAdapter(OnAppActionListener listener) {
        this.listener = listener;
    }

    public void setApps(List<InstalledApp> items) {
        apps.clear();
        apps.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_uninstall, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final AppViewHolder holder, int position) {
        final InstalledApp app = apps.get(position);

        holder.ivIcon.setImageDrawable(app.getIcon());
        holder.tvName.setText(app.getAppName());

        holder.itemView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == 23 || keyCode == 66) {
                    listener.onAppClicked(app, holder.itemView);
                    return true;
                }
                return false;
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAppClicked(app, holder.itemView);
            }
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;

        AppViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
        }
    }
}
