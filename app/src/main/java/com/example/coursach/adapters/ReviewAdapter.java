package com.example.coursach.adapters;
import android.content.Context;
import android.view.*;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.models.Review;
import java.util.List;
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {
    private final Context ctx;
    private List<Review> items;
    public ReviewAdapter(Context ctx, List<Review> items) { this.ctx = ctx; this.items = items; }
    public void updateData(List<Review> d) { this.items = d; notifyDataSetChanged(); }
    public List<Review> getItems() { return items; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_review, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Review r = items.get(pos);
        h.tvAuthor.setText(r.getAuthorName() != null ? r.getAuthorName() : "Клиент");
        if (h.ratingBar != null) h.ratingBar.setRating(r.getRating());
        h.tvComment.setText(r.getComment() != null ? r.getComment() : "");
        if (h.tvDate != null && r.getCreatedAt() != null && r.getCreatedAt().length() >= 10)
            h.tvDate.setText(r.getCreatedAt().substring(0, 10));
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvComment, tvDate;
        RatingBar ratingBar;
        VH(@NonNull View v) {
            super(v);
            tvAuthor = v.findViewById(R.id.tvAuthor); tvComment = v.findViewById(R.id.tvComment);
            tvDate = v.findViewById(R.id.tvDate); ratingBar = v.findViewById(R.id.ratingBar);
        }
    }
}
