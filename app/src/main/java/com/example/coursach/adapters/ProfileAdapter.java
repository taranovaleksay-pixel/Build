package com.example.coursach.adapters;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.coursach.R;
import com.example.coursach.models.Profile;
import java.util.List;
public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.VH> {
    public interface OnClick{void onClick(Profile p);}
    private final Context ctx; private List<Profile> items; private final OnClick l;
    public ProfileAdapter(Context ctx,List<Profile> items,OnClick l){this.ctx=ctx;this.items=items;this.l=l;}
    public void updateData(List<Profile> d){this.items=d;notifyDataSetChanged();}
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int t){return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_profile,p,false));}
    @Override public void onBindViewHolder(@NonNull VH h,int pos){
        Profile p=items.get(pos);
        h.tvName.setText(p.getFullName().isEmpty()?"Без имени":p.getFullName());
        h.tvEmail.setText(p.getEmail()!=null?p.getEmail():"");
        h.tvRole.setText(p.getRoleLabel());
        h.tvStatus.setText(p.isBlocked()?"🔒 Заблокирован":"✅ Активен");
        h.tvStatus.setTextColor(p.isBlocked()?0xFFB71C1C:0xFF2E7D32);
        h.itemView.setOnClickListener(v->l.onClick(p));}
    @Override public int getItemCount(){return items.size();}
    static class VH extends RecyclerView.ViewHolder{
        TextView tvName,tvEmail,tvRole,tvStatus;
        VH(@NonNull View v){super(v);tvName=v.findViewById(R.id.tvName);tvEmail=v.findViewById(R.id.tvEmail);
            tvRole=v.findViewById(R.id.tvRole);tvStatus=v.findViewById(R.id.tvStatus);}
    }
}
