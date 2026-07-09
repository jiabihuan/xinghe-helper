package com.xinghe.helper.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
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
    private Runnable collapseRunnable;

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
                updateActionPanel(holder, false);
                expandedPosition = -1;
            } else {
                int oldPos = expandedPosition;
                expandedPosition = pos;
                
                if (oldPos >= 0) {
                    View oldView = findViewByPosition(findRecyclerView(v), oldPos);
                    if (oldView != null) {
                        LinearLayout oldActions = oldView.findViewById(R.id.layoutActions);
                        if (oldActions != null) {
                            oldActions.setVisibility(View.INVISIBLE);
                        }
                    }
                }
                
                updateActionPanel(holder, true);

                handler.postDelayed(() -> {
                    if (expandedPosition == pos) {
                        holder.btnOpen.requestFocus();
                    }
                }, 50);
            }
        });

        holder.layoutItem.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            RecyclerView recyclerView = findRecyclerView(v);
            if (recyclerView == null) return false;

            GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
            if (lm == null) return false;
            int span = lm.getSpanCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (expandedPosition != pos) {
                    v.performClick();
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (pos < getItemCount() - 1 && (pos + 1) % span != 0) {
                    moveFocusTo(recyclerView, lm, pos + 1);
                    return true;
                }
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (pos > 0 && pos % span != 0) {
                    moveFocusTo(recyclerView, lm, pos - 1);
                    return true;
                }
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (pos >= span) {
                    moveFocusTo(recyclerView, lm, pos - span);
                    return true;
                }
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int belowPos = pos + span;
                if (belowPos < getItemCount()) {
                    moveFocusTo(recyclerView, lm, belowPos);
                    return true;
                }
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (expandedPosition == pos) {
                    expandedPosition = -1;
                    notifyItemChanged(pos);
                    holder.layoutItem.requestFocus();
                    return true;
                }
            }

            return false;
        });

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppOpen(app);
            }
        });

        holder.btnOpen.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getAdapterPosition();

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                v.performClick();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                holder.btnUninstall.requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                collapseActions(pos);
                holder.layoutItem.requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                collapseActions(pos);
                holder.layoutItem.requestFocus();
                return true;
            }

            return false;
        });

        holder.btnOpen.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && expandedPosition == holder.getAdapterPosition()) {
                scheduleCollapse(holder.getAdapterPosition());
            }
        });

        holder.btnUninstall.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppUninstall(app);
            }
        });

        holder.btnUninstall.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = holder.getAdapterPosition();

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                v.performClick();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                holder.btnOpen.requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                collapseActions(pos);
                holder.layoutItem.requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                collapseActions(pos);
                holder.layoutItem.requestFocus();
                return true;
            }

            return false;
        });

        holder.btnUninstall.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && expandedPosition == holder.getAdapterPosition()) {
                scheduleCollapse(holder.getAdapterPosition());
            }
        });
    }

    private void scheduleCollapse(int position) {
        if (collapseRunnable != null) {
            handler.removeCallbacks(collapseRunnable);
        }
        collapseRunnable = () -> {
            if (expandedPosition == position) {
                expandedPosition = -1;
                notifyItemChanged(position);
            }
        };
        handler.postDelayed(collapseRunnable, 200);
    }

    private void collapseActions(int position) {
        if (collapseRunnable != null) {
            handler.removeCallbacks(collapseRunnable);
            collapseRunnable = null;
        }
        if (expandedPosition == position) {
            expandedPosition = -1;
            notifyItemChanged(position);
        }
    }

    private RecyclerView findRecyclerView(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof RecyclerView) {
                return (RecyclerView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private View findViewByPosition(RecyclerView recyclerView, int position) {
        if (recyclerView == null) return null;
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm != null) {
            return lm.findViewByPosition(position);
        }
        return null;
    }

    private void moveFocusTo(RecyclerView recyclerView, GridLayoutManager lm, int targetPos) {
        View targetView = lm.findViewByPosition(targetPos);
        if (targetView != null) {
            View focusTarget = targetView.findViewById(R.id.layoutItem);
            if (focusTarget != null) {
                focusTarget.requestFocus();
            } else {
                targetView.requestFocus();
            }
        } else {
            recyclerView.scrollToPosition(targetPos);
            recyclerView.post(() -> {
                View v = lm.findViewByPosition(targetPos);
                if (v != null) {
                    View ft = v.findViewById(R.id.layoutItem);
                    if (ft != null) ft.requestFocus();
                    else v.requestFocus();
                }
            });
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

    public int getExpandedPosition() {
        return expandedPosition;
    }

    public void collapseCurrent() {
        if (expandedPosition >= 0) {
            int pos = expandedPosition;
            expandedPosition = -1;
            notifyItemChanged(pos);
        }
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