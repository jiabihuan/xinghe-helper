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
    private int expandedPosition = -1;
    private final OnAppActionListener listener;

    public interface OnAppActionListener {
        void onOpenApp(InstalledApp app);
        void onUninstallApp(InstalledApp app);
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
        final boolean expanded = position == expandedPosition;

        holder.ivIcon.setImageDrawable(app.getIcon());
        holder.tvName.setText(app.getAppName());
        holder.layoutActions.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (expanded) {
            holder.btnOpen.post(new Runnable() {
                @Override
                public void run() {
                    holder.btnOpen.requestFocus();
                }
            });
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActions(holder.getAdapterPosition());
            }
        });

        holder.itemView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0) return false;
                if (keyCode == 23 || keyCode == 66) {
                    showActions(holder.getAdapterPosition());
                    return true;
                }
                return false;
            }
        });

        holder.layoutActions.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0) return false;
                if (keyCode == 4 || keyCode == 111) {
                    hideActions(holder);
                    return true;
                }
                return false;
            }
        });

        View.OnKeyListener actionKeyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0) return false;
                if (keyCode == 4 || keyCode == 111) {
                    hideActions(holder);
                    return true;
                }
                return false;
            }
        };

        holder.btnOpen.setOnKeyListener(actionKeyListener);
        holder.btnUninstall.setOnKeyListener(actionKeyListener);

        holder.btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpenApp(app);
            }
        });

        holder.btnUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onUninstallApp(app);
            }
        });

        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus && !holder.btnOpen.isFocused() && !holder.btnUninstall.isFocused()) {
                    hideActions(holder);
                }
            }
        };

        holder.btnOpen.setOnFocusChangeListener(focusChangeListener);
        holder.btnUninstall.setOnFocusChangeListener(focusChangeListener);
    }

    private void showActions(int position) {
        if (position == -1) return;
        int oldPosition = expandedPosition;
        expandedPosition = position;
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition);
        }
        notifyItemChanged(position);
    }

    private void hideActions(AppViewHolder holder) {
        int position = holder.getAdapterPosition();
        if (position == -1) return;
        expandedPosition = -1;
        holder.layoutActions.setVisibility(View.GONE);
        holder.itemView.requestFocus();
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        LinearLayout layoutActions;
        TextView btnOpen;
        TextView btnUninstall;

        AppViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnOpen = itemView.findViewById(R.id.btnOpen);
            btnUninstall = itemView.findViewById(R.id.btnUninstall);
        }
    }
}
