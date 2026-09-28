package vn.nhom03.chiabill.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.databinding.ItemRowBinding;
import vn.nhom03.chiabill.util.Ui;

/** Một adapter cho mọi danh sách dạng thẻ: avatar, tiêu đề, dòng phụ, số tiền, nhãn. */
public final class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public static final class Row {
        public String avatarName;       // null = ẩn avatar
        public int colorIndex;
        public String title = "";
        public String subtitle;
        public boolean subtitleMultiline;
        public String amount;
        public Integer amountColor;
        public String pill;
        public int[] pillColors;
        public Runnable onClick;
    }

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        ItemRowBinding b = h.b;
        if (r.avatarName != null) {
            b.avatar.setVisibility(View.VISIBLE);
            Ui.avatar(b.avatar, r.avatarName, r.colorIndex);
        } else {
            b.avatar.setVisibility(View.GONE);
        }
        b.title.setText(r.title);
        b.subtitle.setVisibility(r.subtitle == null ? View.GONE : View.VISIBLE);
        b.subtitle.setText(r.subtitle);
        b.subtitle.setMaxLines(r.subtitleMultiline ? 6 : 2);
        b.amount.setVisibility(r.amount == null ? View.GONE : View.VISIBLE);
        b.amount.setText(r.amount);
        b.amount.setTextColor(r.amountColor != null ? r.amountColor : Ui.col(b.amount.getContext(), vn.nhom03.chiabill.R.color.ink));
        if (r.pill != null && r.pillColors != null) {
            b.pill.setVisibility(View.VISIBLE);
            Ui.pill(b.pill, r.pill, r.pillColors);
        } else {
            b.pill.setVisibility(View.GONE);
        }
        // setOnClickListener (kể cả null) luôn bật clickable, nên đặt clickable SAU nó.
        b.getRoot().setOnClickListener(r.onClick == null ? null : v -> r.onClick.run());
        b.getRoot().setClickable(r.onClick != null);
        b.getRoot().setFocusable(r.onClick != null);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public static final class VH extends RecyclerView.ViewHolder {
        final ItemRowBinding b;

        VH(ItemRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
