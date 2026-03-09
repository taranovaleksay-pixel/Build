package com.example.coursach.fragments;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.coursach.R;
import com.example.coursach.activities.ServiceDetailActivity;
import com.example.coursach.adapters.ServiceAdapter;
import com.example.coursach.models.Service;
import com.example.coursach.network.SupabaseClient;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.Response;
public class CatalogFragment extends Fragment {
    private RecyclerView rv; private ServiceAdapter adapter; private ProgressBar pb;
    private SwipeRefreshLayout srl; private EditText etSearch; private ChipGroup chipGroup; private TextView tvEmpty;
    private List<Service> allServices=new ArrayList<>(); private String selCat="";
    private final ExecutorService exec=Executors.newSingleThreadExecutor(); private SupabaseClient client;
    private static final String[] CATS={"Все","Фундамент","Кровля","Отделка","Электрика","Сантехника","Ремонт","Демонтаж","Прочее"};
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf,@Nullable ViewGroup c,@Nullable Bundle s){
        return inf.inflate(R.layout.fragment_catalog,c,false);}
    @Override public void onViewCreated(@NonNull View v,@Nullable Bundle s){
        super.onViewCreated(v,s);
        client=SupabaseClient.getInstance(requireContext());
        rv=v.findViewById(R.id.recyclerView);pb=v.findViewById(R.id.progressBar);
        srl=v.findViewById(R.id.swipeRefresh);etSearch=v.findViewById(R.id.etSearch);
        chipGroup=v.findViewById(R.id.chipGroup);tvEmpty=v.findViewById(R.id.tvEmpty);
        adapter=new ServiceAdapter(requireContext(),new ArrayList<>(),svc->{
            Intent i=new Intent(requireContext(),ServiceDetailActivity.class);
            i.putExtra("service_id",svc.getId());startActivity(i);});
        rv.setLayoutManager(new LinearLayoutManager(requireContext())); rv.setAdapter(adapter);
        srl.setOnRefreshListener(this::load);
        for(String cat:CATS){Chip chip=new Chip(requireContext());chip.setText(cat);chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColorResource(R.color.chip_bg_selector);
            chip.setTextColor(requireContext().getResources().getColorStateList(R.color.chip_text_selector));
            chipGroup.addView(chip);}
        ((Chip)chipGroup.getChildAt(0)).setChecked(true);
        chipGroup.setOnCheckedStateChangeListener((g,ids)->{
            if(!ids.isEmpty()){Chip c2=g.findViewById(ids.get(0));
                if(c2!=null){selCat=c2.getText().toString().equals("Все")?"":c2.getText().toString();filter();}}});
        etSearch.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s2,int a,int b2,int c2){}
            public void onTextChanged(CharSequence s2,int a,int b2,int c2){filter();}
            public void afterTextChanged(Editable s2){}});
        load();
    }
    public void load(){
        if(pb==null)return; pb.setVisibility(View.VISIBLE);
        exec.execute(()->{try{
            Response r=client.get("services","select=*&order=created_at.desc");
            String body=r.body().string();boolean ok=r.isSuccessful();r.close();
            List<Service> list=new ArrayList<>();
            if(ok){JSONArray arr=new JSONArray(body);
                for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);
                    Service sv=new Service();sv.setId(o.optString("id"));sv.setSellerId(o.optString("seller_id"));
                    sv.setTitle(o.optString("title"));sv.setDescription(o.optString("description"));
                    sv.setCategory(o.optString("category"));sv.setPrice(o.optDouble("price",0));list.add(sv);}}
            ui(()->{allServices=list;pb.setVisibility(View.GONE);srl.setRefreshing(false);filter();});
        }catch(Exception e){ui(()->{pb.setVisibility(View.GONE);srl.setRefreshing(false);
            if(isAdded())Toast.makeText(requireContext(),"Нет соединения",Toast.LENGTH_SHORT).show();});}});
    }
    private void filter(){
        if(adapter==null)return;
        String q=etSearch.getText().toString().trim().toLowerCase();
        List<Service> f=new ArrayList<>();
        for(Service sv:allServices){
            boolean mc=selCat.isEmpty()||selCat.equalsIgnoreCase(sv.getCategory());
            boolean mq=q.isEmpty()||sv.getTitle().toLowerCase().contains(q)||(sv.getDescription()!=null&&sv.getDescription().toLowerCase().contains(q));
            if(mc&&mq)f.add(sv);}
        tvEmpty.setVisibility(f.isEmpty()?View.VISIBLE:View.GONE);adapter.updateData(f);
    }
    private void ui(Runnable r){if(isAdded()&&getActivity()!=null)requireActivity().runOnUiThread(r);}
    @Override public void onDestroyView(){super.onDestroyView();exec.shutdown();}
}
