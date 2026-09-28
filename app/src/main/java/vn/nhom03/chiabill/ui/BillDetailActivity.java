package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityBillDetailBinding;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.BillSpec;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

public class BillDetailActivity extends BaseActivity {
    private ActivityBillDetailBinding b;
    private final RowAdapter shares = new RowAdapter();
    private String billId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        billId = getIntent().getStringExtra(Nav.BILL_ID);
        b = ActivityBillDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        b.shares.setLayoutManager(new LinearLayoutManager(this));
        b.shares.setAdapter(shares);
        repo().snapshot().observe(this, this::render);
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        Bill bill = s.ledger().bill(billId);
        BillEntity be = s.billEntity(billId);
        if (bill == null || be == null) {
            finish();
            return;
        }
        String me = me();
        GroupEntity g = s.group(bill.getGroupId());
        BillSpec spec = bill.getSpec();
        SplitResult r = s.ledger().splitOf(billId);

        b.toolbar.setTitle(bill.getTitle());
        b.toolbar.setSubtitle((g != null ? g.name : "") + " · " + Ui.day(bill.getCreatedAt()) + " · " + bill.getCategory().label());
        Ui.avatar(b.payerAvatar, s.name(bill.getPayerId()), s.colorIndex(bill.getPayerId()));
        b.payerLine.setText((bill.getPayerId().equals(me) ? "Bạn" : s.name(bill.getPayerId())) + " đã trả");
        b.total.setText(Money.format(r.getTotal()));

        StringBuilder calc = new StringBuilder();
        calc.append("Tạm tính ").append(Money.format(r.getSubtotal()));
        switch (spec.getMode()) {
            case ITEMIZED: calc.append(" · chia theo ").append(spec.getItems().size()).append(" món"); break;
            case CUSTOM: calc.append(" · mỗi người một số tiền"); break;
            case PERCENT: calc.append(" · chia theo phần trăm"); break;
            default: calc.append(" · chia đều ").append(spec.getParticipants().size()).append(" người"); break;
        }
        if (r.getVat() > 0) calc.append("\nVAT ").append(spec.getVatPercent()).append("%: ").append(Money.format(r.getVat()));
        if (r.getService() > 0) calc.append("\nPhí phục vụ ").append(spec.getServicePercent()).append("%: ").append(Money.format(r.getService()));
        calc.append(spec.getRounding() > 1 ? "\nNgười nợ làm tròn tới 1.000đ, phần lẻ dồn cho người trả" : "\nLàm tròn tới 1đ");
        b.calc.setText(calc);

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (ShareLine l : r.getLines()) {
            RowAdapter.Row row = new RowAdapter.Row();
            String id = l.getMemberId();
            row.avatarName = s.name(id);
            row.colorIndex = s.colorIndex(id);
            row.title = s.name(id) + (id.equals(me) ? " (bạn)" : "");
            StringBuilder sub = new StringBuilder();
            if (spec.getMode() == SplitMode.PERCENT && spec.getShares().containsKey(id)) {
                sub.append(vn.nhom03.chiabill.domain.split.SplitEngine.formatPercent(spec.getShares().get(id))).append(" · ");
            }
            sub.append("gốc ").append(Money.dots(l.getBase()));
            if (l.getFee() != 0) sub.append(" · phí ").append(Money.dots(l.getFee()));
            if (l.getRoundingAdj() != 0) sub.append(" · làm tròn ").append(l.getRoundingAdj() > 0 ? "+" : "").append(Money.dots(l.getRoundingAdj()));
            row.subtitle = sub.toString();
            row.amount = Money.format(l.getAmount());
            if (id.equals(bill.getPayerId())) {
                row.pill = "Người trả";
                row.pillColors = new int[]{Ui.col(this, R.color.sand), Ui.col(this, R.color.muted)};
            } else {
                Debt d = s.ledger().debt(Debt.keyOf(billId, id));
                if (d != null) {
                    row.pill = Ui.statusLabel(this, d.getStatus());
                    row.pillColors = Ui.statusColors(this, d.getStatus());
                    row.onClick = () -> startActivity(new Intent(this, DebtDetailActivity.class).putExtra(Nav.DEBT_KEY, d.getKey()));
                }
            }
            rows.add(row);
        }
        shares.submit(rows);
        b.check.setText((r.isBalanced() ? "✓ " : "✗ ") + "Tổng các phần " + Money.format(r.sumOfShares())
                + (r.isBalanced() ? " = tổng hoá đơn" : " ≠ " + Money.format(r.getTotal())));

        boolean owner = me.equals(be.createdBy) || me.equals(be.payerId);
        b.ownerActions.setVisibility(owner ? View.VISIBLE : View.GONE);
        b.editNote.setText(owner ? "Sửa số tiền sẽ đưa các khoản chưa xác nhận về “Chưa trả” và báo lại cho người nợ."
                : "Chỉ " + s.name(be.payerId) + " (người trả) sửa được hoá đơn này.");
        b.edit.setOnClickListener(v -> startActivity(new Intent(this, BillEditActivity.class)
                .putExtra(Nav.GROUP_ID, bill.getGroupId()).putExtra(Nav.BILL_ID, billId)));
        b.delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Xoá hoá đơn?")
                .setMessage("Mọi khoản nợ của hoá đơn này sẽ biến mất.")
                .setPositiveButton("Xoá", (dlg, w) -> repo().deleteBill(billId, me, this::finish, this::toast))
                .setNegativeButton("Huỷ", null)
                .show());
    }
}
