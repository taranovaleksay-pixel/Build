package com.example.coursach.adapters;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.models.Service;
import java.util.List;
public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.VH> {
    public interface OnClick{void onClick(Service s);}
    private final Context ctx; private List<Service> items; private final OnClick l;
    public ServiceAdapter(Context ctx,List<Service> items,OnClick l){this.ctx=ctx;this.items=items;this.l=l;}
    public void updateData(List<Service> d){this.items=d;notifyDataSetChanged();}
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int t){return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_service,p,false));}
    @Override public void onBindViewHolder(@NonNull VH h,int pos){
        Service s=items.get(pos);
        h.tvTitle.setText(s.getTitle());
        h.tvCategory.setText(s.getCategory()!=null?s.getCategory():"—");
        h.tvPrice.setText(String.format("от %.0f ₽",s.getPrice()));
        h.tvDesc.setText(s.getDescription()!=null&&!s.getDescription().isEmpty()?s.getDescription():"Описание не указано");
        if(s.getReviewCount()>0){h.tvRating.setText(String.format("★ %.1f (%d)",s.getRating(),s.getReviewCount()));h.tvRating.setVisibility(View.VISIBLE);}
        else h.tvRating.setVisibility(View.GONE);
        h.itemView.setOnClickListener(v->l.onClick(s));}
    @Override public int getItemCount(){return items.size();}
    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle,tvCategory,tvPrice,tvDesc,tvRating;
        VH(@NonNull View v){super(v);tvTitle=v.findViewById(R.id.tvTitle);tvCategory=v.findViewById(R.id.tvCategory);
            tvPrice=v.findViewById(R.id.tvPrice);tvDesc=v.findViewById(R.id.tvDescription);tvRating=v.findViewById(R.id.tvRating);}
    }
}
