package com.xinghe.helper.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;
import com.xinghe.helper.model.InstalledApp;

import java.util.ArrayList;
import java.util.List;

public class AppUninstallAdapter extends RecyclerView.Adapter<AppUninstallAdapter.AppViewHolder> {

    private final List<InstalledApp> apps = new ArrayList<>();
    private final OnAppActionListener listener;
    private int expandedPosition = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface OnAppActionListener {
        void onAppOpen(InstalledApp app);
        void onAppUninstall(InstalledApp app);
    }

    public AppUninstallAdapter(OnAppActionListener listener) {
        this.listener = listener;
    }

    public void setApps(List<InstalledApp> items) {
        apps.clear();
        apps.addAll(items);
        expandedPosition = -1;
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

        boolean isExpanded = position == expandedPosition;
        updateActionPanel(holder, isExpanded);

        holder.layoutItem.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            if (expandedPosition == pos) {
                expandedPosition = -1;
                notifyItemChanged(pos);
            } else {
                int oldPos = expandedPosition;
                expandedPosition = pos;
                if (oldPos >= 0) {
                    notifyItemChanged(oldPos);
                }
                notifyItemChanged(pos);

                handler.postDelayed(() -> {
                    holder.btnOpen.requestFocus();
                }, 150);
            }
        });

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppOpen(app);
            }
        });

        holder.btnOpen.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                if (expandedPosition == holder.getAdapterPosition()) {
                    expandedPosition = -1;
                    notifyItemChanged(holder.getAdapterPosition());
                }
            }
        });

        holder.btnUninstall.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppUninstall(app);
            }
        });

        holder.btnUninstall.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                if (expandedPosition == holder.getAdapterPosition()) {
                    expandedPosition = -1;
                    notifyItemChanged(holder.getAdapterPosition());
                }
            }
        });
    }

    private void updateActionPanel(AppViewHolder holder, boolean visible) {
        holder.layoutActions.setVisibility(visible ? View.VISIBLE : View.GONE);

        holder.btnOpen.setFocusable(visible);
        holder.btnOpen.setFocusableInTouchMode(visible);
        holder.btnUninstall.setFocusable(visible);
        holder.btnUninstall.setFocusableInTouchMode(visible);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutItem;
        LinearLayout layoutActions;
        ImageView ivIcon;
        TextView tvName;
        TextView btnOpen;
        TextView btnUninstall;

        AppViewHolder(View itemView) {
            super(itemView);
            layoutItem = itemView.findViewById(R.id.layoutItem);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            btnOpen = itemView.findViewById(R.id.btnOpen);
            btnUninstall = itemView.findViewById(R.id.btnUninstall);
        }
    }
}
