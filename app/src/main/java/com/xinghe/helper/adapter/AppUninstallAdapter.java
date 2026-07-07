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
                    listener.onOpenApp(app);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    holder.btnOpen.requestFocus();
                    return true;
                }
                return false;
            }
        });

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

        holder.btnOpen.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    holder.btnUninstall.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    holder.itemView.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    holder.itemView.requestFocus();
                    return true;
                }
                return false;
            }
        });

        holder.btnUninstall.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == 23 || keyCode == 66) {
                    v.performClick();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    holder.btnOpen.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    holder.itemView.requestFocus();
                    return true;
                }
                return false;
            }
        });

        holder.btnOpen.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    holder.btnOpen.setTextColor(0xFFFFFFFF);
                } else {
                    holder.btnOpen.setTextColor(v.getResources().getColor(R.color.home_text_primary));
                }
            }
        });

        holder.btnUninstall.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    holder.btnUninstall.setTextColor(0xFFFFFFFF);
                } else {
                    holder.btnUninstall.setTextColor(v.getResources().getColor(R.color.home_text_primary));
                }
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
