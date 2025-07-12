package com.harish.dndscheduler;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ClassSlotAdapter extends RecyclerView.Adapter<ClassSlotAdapter.SlotViewHolder> {

    private final List<ClassTimeSlot> classSlots;

    public ClassSlotAdapter(List<ClassTimeSlot> classSlots) {
        this.classSlots = classSlots;
    }

    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        ClassTimeSlot slot = classSlots.get(position);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String timeRange = timeFormat.format(new Date(slot.getStartMillis())) + " - " +
                timeFormat.format(new Date(slot.getEndMillis()));

        holder.tvTime.setText(timeRange);
        holder.tvSubject.setText(slot.getSubject());
    }

    @Override
    public int getItemCount() {
        return classSlots.size();
    }

    static class SlotViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvSubject;

        public SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_class_time);
            tvSubject = itemView.findViewById(R.id.tv_class_subject);
        }
    }
}
