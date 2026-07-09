package com.xinghe.helper.adapter;

import android.view.KeyEvent;
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
            toggleExpanded(holder.getAdapterPosition(), holder);
        });

        holder.layoutItem.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == 23 || keyCode == 66) {
                toggleExpanded(pos, holder);
                return true;
            }
            return false;
        });

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppOpen(app);
            }
        });

        holder.btnUninstall.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppUninstall(app);
            }
        });

        holder.btnOpen.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                holder.btnUninstall.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_BACK) {
                collapsePanel(holder);
                return true;
            }
            return false;
        });

        holder.btnUninstall.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                holder.btnOpen.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                collapsePanel(holder);
                return true;
            }
            return false;
        });
    }

    private void toggleExpanded(int position, AppViewHolder holder) {
        if (expandedPosition == position) {
            collapsePanel(holder);
        } else {
            int oldPos = expandedPosition;
            expandedPosition = position;
            if (oldPos >= 0) {
                notifyItemChanged(oldPos);
            }
            updateActionPanel(holder, true);
            holder.btnOpen.requestFocus();
        }
    }

    private void collapsePanel(AppViewHolder holder) {
        if (expandedPosition == holder.getAdapterPosition()) {
            expandedPosition = -1;
            updateActionPanel(holder, false);
            holder.layoutItem.requestFocus();
        }
    }

    private void updateActionPanel(AppViewHolder holder, boolean visible) {
        holder.layoutActions.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
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
