package com.xinghe.helper.fragments;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xinghe.helper.R;

import java.util.ArrayList;
import java.util.List;

public class ManagerFragment extends Fragment {

    private RecyclerView rvApps;
    private TextView tvAppCount;
    private TextView tvEmpty;
    private AppAdapter adapter;
    private List<AppInfo> appList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manager, container, false);

        rvApps = view.findViewById(R.id.rv_apps);
        tvAppCount = view.findViewById(R.id.tv_app_count);
        tvEmpty = view.findViewById(R.id.tv_empty);

        rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AppAdapter();
        rvApps.setAdapter(adapter);

        loadApps();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadApps();
    }

    private void loadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = requireContext().getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                appList.clear();

                for (PackageInfo pi : packages) {
                    if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        AppInfo app = new AppInfo();
                        app.name = pi.applicationInfo.loadLabel(pm).toString();
                        app.packageName = pi.packageName;
                        app.versionName = pi.versionName;
                        app.icon = pi.applicationInfo.loadIcon(pm);
                        appList.add(app);
                    }
                }

                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvAppCount.setText("已安装: " + appList.size() + " 个应用");
                        if (appList.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvApps.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            rvApps.setVisibility(View.VISIBLE);
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }).start();
    }

    class AppInfo {
        String name;
        String packageName;
        String versionName;
        Drawable icon;
    }

    class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final AppInfo app = appList.get(position);
            holder.tvName.setText(app.name);
            holder.tvPackage.setText(app.packageName);
            holder.tvVersion.setText(app.versionName);
            holder.ivIcon.setImageDrawable(app.icon);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launchIntent = requireContext().getPackageManager()
                            .getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(requireContext(), "无法打开此应用", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return appList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvPackage;
            TextView tvVersion;

            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_icon);
                tvName = itemView.findViewById(R.id.tv_name);
                tvPackage = itemView.findViewById(R.id.tv_package);
                tvVersion = itemView.findViewById(R.id.tv_version);
            }
        }
    }
}
