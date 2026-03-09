package com.example.coursach.adapters;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.models.ChatMessage;
import java.util.List;
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
    private static final int ME = 1, OTHER = 2;
    private final Context ctx; private List<ChatMessage> items; private final String myId;
    public ChatAdapter(Context ctx, List<ChatMessage> items, String myId) { this.ctx = ctx; this.items = items; this.myId = myId; }
    public void updateData(List<ChatMessage> d) { this.items = d; notifyDataSetChanged(); }
    @Override public int getItemViewType(int pos) { return items.get(pos).getSenderId().equals(myId) ? ME : OTHER; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int type) {
        int lay = type == ME ? R.layout.item_chat_me : R.layout.item_chat_other;
        return new VH(LayoutInflater.from(ctx).inflate(lay, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        ChatMessage m = items.get(pos);
        h.tvContent.setText(m.getContent());
        if (h.tvSender != null) h.tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "");
        if (h.tvTime != null && m.getCreatedAt() != null && m.getCreatedAt().length() >= 16)
            h.tvTime.setText(m.getCreatedAt().substring(11, 16));
        if (h.tvAvatar != null) {
            String name = m.getSenderName() != null ? m.getSenderName().trim() : "?";
            String initials = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
            if (name.contains(" ")) initials = (String.valueOf(name.charAt(0)) + name.charAt(name.indexOf(' ') + 1)).toUpperCase();
            h.tvAvatar.setText(initials);
        }
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView tvContent, tvSender, tvTime, tvAvatar;
        VH(@NonNull View v) {
            super(v); tvContent = v.findViewById(R.id.tvContent);
            tvSender = v.findViewById(R.id.tvSender); tvTime = v.findViewById(R.id.tvTime);
            tvAvatar = v.findViewById(R.id.tvAvatar);
        }
    }
}
