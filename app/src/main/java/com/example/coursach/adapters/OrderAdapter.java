package com.example.coursach.adapters;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.models.Order;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {
    public interface OnClick         { void onClick(Order o); }
    public interface OnCancel        { void onCancel(Order o); }
    public interface OnDelete        { void onDelete(Order o); }
    public interface OnChangeStatus  { void onChangeStatus(Order o); }

    private final Context ctx;
    private List<Order> items;
    private final OnClick l;

    private boolean showManagerControls = false;
    private boolean showManagerDelete   = false;
    private OnChangeStatus statusListener;
    private OnDelete managerDeleteListener;

    private boolean showClientCancel = false;
    private OnCancel cancelListener;
    private OnDelete clientDeleteListener;

    public OrderAdapter(Context ctx, List<Order> items, OnClick l) {
        this.ctx = ctx; this.items = items; this.l = l;
    }

    public void setManagerMode(OnChangeStatus sl) {
        this.showManagerControls = true; this.statusListener = sl;
    }
    public void setManagerDeleteMode(OnDelete dl) {
        this.showManagerDelete = true; this.managerDeleteListener = dl;
    }
    public void setClientMode(OnCancel cl) {
        this.showClientCancel = true; this.cancelListener = cl;
    }
    public void setClientDeleteMode(OnDelete dl) {
        this.clientDeleteListener = dl;
    }

    public void updateData(List<Order> d) { this.items = d; notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_order, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Order o = items.get(pos);
        h.tvTitle.setText(o.getServiceTitle() != null ? o.getServiceTitle() : "Заказ");
        h.tvStatus.setText(o.getStatusLabel());
        h.tvAmount.setText(String.format("%.0f ₽", o.getTotalAmount()));
        h.tvDate.setText(o.getCreatedAt() != null
                ? o.getCreatedAt().substring(0, Math.min(10, o.getCreatedAt().length())) : "");
        String person = o.getBuyerName() != null ? o.getBuyerName()
                : (o.getSellerName() != null ? o.getSellerName() : "");
        h.tvPerson.setText(person);

        int color;
        switch (o.getStatus() != null ? o.getStatus() : "") {
            case "completed":  color = 0xFF2E7D32; break;
            case "in_progress":color = 0xFF1565C0; break;
            case "confirmed":  color = 0xFF00838F; break;
            case "cancelled":  color = 0xFFB71C1C; break;
            default:           color = 0xFF757575;
        }
        h.tvStatus.setTextColor(color);

        h.btnChat.setOnClickListener(v -> l.onClick(o));

        h.btnCancel.setVisibility(View.GONE);
        h.btnDelete.setVisibility(View.GONE);
        h.btnChangeStatus.setVisibility(View.GONE);

        if (showManagerControls && statusListener != null) {
            h.btnChangeStatus.setVisibility(View.VISIBLE);
            h.btnChangeStatus.setOnClickListener(v -> statusListener.onChangeStatus(o));
            if (showManagerDelete && managerDeleteListener != null) {
                h.itemView.setOnLongClickListener(v -> {
                    managerDeleteListener.onDelete(o); return true;
                });
            }
        } else if (showClientCancel) {
            String st = o.getStatus() != null ? o.getStatus() : "";
            boolean canCancel = st.equals("pending") || st.equals("confirmed");

            if (canCancel && cancelListener != null) {
                h.btnCancel.setVisibility(View.VISIBLE);
                h.btnCancel.setOnClickListener(v -> cancelListener.onCancel(o));
            }

            if (clientDeleteListener != null) {
                h.btnDelete.setVisibility(View.VISIBLE);
                h.btnDelete.setOnClickListener(v -> clientDeleteListener.onDelete(o));
            }
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStatus, tvAmount, tvDate, tvPerson;
        MaterialButton btnChat, btnCancel, btnDelete, btnChangeStatus;
        VH(@NonNull View v) {
            super(v);
            tvTitle        = v.findViewById(R.id.tvTitle);
            tvStatus       = v.findViewById(R.id.tvStatus);
            tvAmount       = v.findViewById(R.id.tvAmount);
            tvDate         = v.findViewById(R.id.tvDate);
            tvPerson       = v.findViewById(R.id.tvPerson);
            btnChat        = v.findViewById(R.id.btnChat);
            btnCancel      = v.findViewById(R.id.btnCancel);
            btnDelete      = v.findViewById(R.id.btnDelete);
            btnChangeStatus= v.findViewById(R.id.btnChangeStatus);
        }
    }
}
