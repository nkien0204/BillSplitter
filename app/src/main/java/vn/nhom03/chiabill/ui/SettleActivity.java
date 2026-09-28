package vn.nhom03.chiabill.ui;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivitySettleBinding;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.model.Transfer;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

/** Đề xuất tất toán: gộp mọi khoản chưa trả trong nhóm thành ít lần chuyển nhất (tham lam). */
public class SettleActivity extends BaseActivity {
    private ActivitySettleBinding b;
    private final RowAdapter nets = new RowAdapter();
    private final RowAdapter transfers = new RowAdapter();
    private String groupId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        groupId = getIntent().getStringExtra(Nav.GROUP_ID);
        b = ActivitySettleBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        b.nets.setLayoutManager(new LinearLayoutManager(this));
        b.nets.setAdapter(nets);
        b.transfers.setLayoutManager(new LinearLayoutManager(this));
        b.transfers.setAdapter(transfers);
        repo().snapshot().observe(this, this::render);
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        GroupEntity g = s.group(groupId);
        if (g == null) { finish(); return; }
        b.toolbar.setSubtitle(g.name);
        Ledger l = s.ledger();
        List<Transfer> plan = l.settlement(groupId);
        int open = l.openDebtCount(groupId);
        b.headline.setText(open == 0 ? "Không còn khoản nào" : open + " khoản → " + plan.size() + " lần chuyển");

        List<RowAdapter.Row> netRows = new ArrayList<>();
        for (Map.Entry<String, Long> e : l.netBalances(groupId).entrySet()) {
            // Người đã rời nhóm mà không còn khoản nào thì không cần hiện
            if (e.getValue() == 0 && !s.membersOf(groupId).contains(e.getKey())) continue;
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = s.name(e.getKey());
            r.colorIndex = s.colorIndex(e.getKey());
            r.title = s.name(e.getKey()) + (e.getKey().equals(me()) ? " (bạn)" : "");
            long v = e.getValue();
            r.subtitle = v > 0 ? "Được nhận lại" : v < 0 ? "Cần trả thêm" : "Sòng phẳng";
            r.amount = Money.formatSigned(v);
            r.amountColor = Ui.col(this, v > 0 ? R.color.brand : v < 0 ? R.color.owe : R.color.muted);
            netRows.add(r);
        }
        nets.submit(netRows);

        List<RowAdapter.Row> tRows = new ArrayList<>();
        for (Transfer t : plan) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = s.name(t.getFromId());
            r.colorIndex = s.colorIndex(t.getFromId());
            r.title = s.name(t.getFromId()) + " → " + s.name(t.getToId());
            r.amount = Money.format(t.getAmount());
            tRows.add(r);
        }
        transfers.submit(tRows);
        b.none.setVisibility(tRows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
