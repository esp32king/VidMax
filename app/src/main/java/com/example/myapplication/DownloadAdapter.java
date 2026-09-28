package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.dl.DownloadItem;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.VH> {

    public interface RowCallback {
        void onAction(DownloadItem item);   // pause/resume/open/retry

        void onDelete(DownloadItem item);

        void onOpen(DownloadItem item);     // tap row
    }

    private final List<DownloadItem> data = new ArrayList<>();
    private final RowCallback cb;

    public DownloadAdapter(RowCallback cb) {
        this.cb = cb;
    }

    public void submit(List<DownloadItem> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final DownloadItem it = data.get(position);

        String title = it.title != null && !it.title.trim().isEmpty() ? it.title : it.url;
        h.title.setText(title);
        h.icon.setImageResource(it.audioOnly ? R.drawable.ic_music : R.drawable.ic_video);

        h.progress.setProgress(Math.round(it.percent));

        String meta;
        switch (it.state) {
            case DownloadItem.RUNNING:
                meta = it.qualityLabel + " · " + Math.round(it.percent) + "%"
                        + (it.speed != null && !it.speed.isEmpty() ? " · " + it.speed : "");
                h.action.setImageResource(R.drawable.ic_pause);
                h.progress.setVisibility(View.VISIBLE);
                break;
            case DownloadItem.PAUSED:
                meta = it.qualityLabel + " · Paused · " + Math.round(it.percent) + "%";
                h.action.setImageResource(R.drawable.ic_play);
                h.progress.setVisibility(View.VISIBLE);
                break;
            case DownloadItem.QUEUED:
                meta = it.qualityLabel + " · Queued";
                h.action.setImageResource(R.drawable.ic_pause);
                h.progress.setVisibility(View.VISIBLE);
                break;
            case DownloadItem.ERROR:
                String em = it.error != null ? it.error.trim() : "";
                if (em.length() > 70) em = em.substring(0, 70) + "…";
                meta = em.isEmpty() ? "Failed · tap retry" : "Failed · " + em;
                h.action.setImageResource(R.drawable.ic_retry);
                h.progress.setVisibility(View.GONE);
                break;
            case DownloadItem.DONE:
            default:
                meta = it.qualityLabel + " · " + FloatingService.human(it.totalBytes);
                h.action.setImageResource(R.drawable.ic_open);
                h.progress.setVisibility(View.GONE);
                break;
        }
        h.meta.setText(meta);

        h.action.setOnClickListener(v -> {
            if (it.state == DownloadItem.DONE) cb.onOpen(it);
            else cb.onAction(it);
        });
        h.delete.setOnClickListener(v -> cb.onDelete(it));
        h.itemView.setOnClickListener(v -> {
            if (it.state == DownloadItem.DONE) cb.onOpen(it);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon, action, delete;
        final TextView title, meta;
        final ProgressBar progress;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.rowIcon);
            action = v.findViewById(R.id.rowAction);
            delete = v.findViewById(R.id.rowDelete);
            title = v.findViewById(R.id.rowTitle);
            meta = v.findViewById(R.id.rowMeta);
            progress = v.findViewById(R.id.rowProgress);
        }
    }
}
