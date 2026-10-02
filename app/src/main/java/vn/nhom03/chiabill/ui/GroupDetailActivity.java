package vn.nhom03.chiabill.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityGroupDetailBinding;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Category;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.user.InviteCode;
import vn.nhom03.chiabill.domain.user.PhoneNumber;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

public class GroupDetailActivity extends BaseActivity {

    private ActivityGroupDetailBinding b;
    private final RowAdapter bills = new RowAdapter();
    private String groupId;
    private AppSnapshot last;
    private boolean leaving;

    // Bộ lọc lịch sử (FR8). null / 0 = không lọc.
    private Category filterCategory;
    private String filterMember;
    private int filterPeriod; // 0 tất cả, 1 = 7 ngày, 2 = 30 ngày, 3 = tháng này
    private static final String[] PERIODS = {
        "Mọi lúc",
        "7 ngày qua",
        "30 ngày qua",
        "Tháng này",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        groupId = getIntent().getStringExtra(Nav.GROUP_ID);
        b = ActivityGroupDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        b.bills.setLayoutManager(new LinearLayoutManager(this));
        b.bills.setAdapter(bills);
        b.addBill.setOnClickListener(v ->
            startActivity(
                new Intent(this, BillEditActivity.class).putExtra(
                    Nav.GROUP_ID,
                    groupId
                )
            )
        );
        b.settle.setOnClickListener(v ->
            startActivity(
                new Intent(this, SettleActivity.class).putExtra(
                    Nav.GROUP_ID,
                    groupId
                )
            )
        );
        b.addMember.setOnClickListener(v -> askPhone());
        b.shareInvite.setOnClickListener(v -> shareInvite());
        b.leaveGroup.setOnClickListener(v -> confirmLeave());
        repo().snapshot().observe(this, this::render);

        // Sync data from server when entering the group
        repo().syncGroup(
            groupId,
            () -> {
                // Sync completed, Room LiveData will automatically trigger render()
            },
            err -> {
                // Optional: show a small warning that data is not synced
            }
        );
    }

    private void render(AppSnapshot s) {
        if (userMissing(s)) return;
        last = s;
        GroupEntity g = s.group(groupId);
        String me = me();
        if (g == null || !s.membersOf(groupId).contains(me)) {
            if (!leaving) toast("Bạn không ở trong nhóm này.");
            finish();
            return;
        }
        Ledger l = s.ledger();
        b.toolbar.setTitle(g.name);
        b.toolbar.setSubtitle(s.membersOf(groupId).size() + " thành viên");

        long net = l.owedTo(me, groupId) - l.owedBy(me, groupId);
        if (net > 0) {
            b.balanceLabel.setText("Bạn được nợ trong nhóm này");
            b.balance.setText(Money.format(net));
            b.balance.setTextColor(Ui.col(this, R.color.brand));
        } else if (net < 0) {
            b.balanceLabel.setText("Bạn đang nợ trong nhóm này");
            b.balance.setText(Money.format(-net));
            b.balance.setTextColor(Ui.col(this, R.color.owe));
        } else {
            b.balanceLabel.setText("Số dư của bạn");
            b.balance.setText("Sòng phẳng");
            b.balance.setTextColor(Ui.col(this, R.color.muted));
        }

        b.members.removeAllViews();
        for (String id : s.membersOf(groupId)) {
            UserEntity u = s.user(id);
            Chip c = new Chip(this);
            String label = s.name(id);
            if (id.equals(me)) label += " (bạn)";
            else if (u != null && u.guest) label += " (khách)";
            c.setText(label);
            c.setChipBackgroundColorResource(R.color.surface);
            c.setChipStrokeColorResource(R.color.line);
            c.setChipStrokeWidth(Ui.dp(this, 1));
            c.setChipIconVisible(false);
            boolean removable = me.equals(g.createdBy) && !id.equals(me);
            c.setClickable(removable);
            if (removable) c.setOnClickListener(v ->
                confirmRemove(id, s.name(id))
            );
            b.members.addView(c);
        }
        b.inviteCode.setText(
            g.inviteCode == null ? "—" : InviteCode.format(g.inviteCode)
        );
        b.shareInvite.setEnabled(g.inviteCode != null);

        buildFilters(s);
        List<Bill> all = new ArrayList<>(l.billsOfGroup(groupId));
        Collections.reverse(all); // mới nhất lên đầu
        List<Bill> list = new ArrayList<>();
        long shownTotal = 0;
        for (Bill bill : all) {
            if (matches(bill, l.splitOf(bill.getId()))) {
                list.add(bill);
                SplitResult sr = l.splitOf(bill.getId());
                if (sr != null) shownTotal += sr.getTotal();
            }
        }
        boolean filtered =
            filterCategory != null || filterMember != null || filterPeriod != 0;
        b.filterInfo.setText(
            filtered
                ? "Hiện " +
                      list.size() +
                      "/" +
                      all.size() +
                      " hoá đơn · tổng " +
                      Money.format(shownTotal)
                : all.size() + " hoá đơn · tổng " + Money.format(shownTotal)
        );
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Bill bill : list) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = s.name(bill.getPayerId());
            r.colorIndex = s.colorIndex(bill.getPayerId());
            r.title = bill.getTitle();
            r.subtitle =
                Ui.day(bill.getCreatedAt()) +
                " · " +
                bill.getCategory().label() +
                " · " +
                (bill.getPayerId().equals(me)
                    ? "Bạn"
                    : s.name(bill.getPayerId())) +
                " trả";
            r.amount = Money.format(l.splitOf(bill.getId()).getTotal());
            Debt mine = null;
            for (Debt d : l.debtsOfBill(bill.getId()))
                if (d.getDebtorId().equals(me)) mine = d;
            if (bill.getPayerId().equals(me)) {
                int open = 0;
                for (Debt d : l.debtsOfBill(bill.getId()))
                    if (d.getStatus().isOpen()) open++;
                r.pill = open == 0 ? "Đã thu đủ" : "Chờ " + open + " người trả";
                r.pillColors =
                    open == 0
                        ? new int[] {
                              Ui.col(this, R.color.ok_bg),
                              Ui.col(this, R.color.ok_fg),
                          }
                        : new int[] {
                              Ui.col(this, R.color.sand),
                              Ui.col(this, R.color.muted),
                          };
            } else if (mine != null) {
                r.pill =
                    "Phần bạn " +
                    Money.format(mine.getAmount()) +
                    " · " +
                    Ui.statusLabel(this, mine.getStatus()).toLowerCase();
                r.pillColors = Ui.statusColors(this, mine.getStatus());
            } else {
                r.pill = "Bạn không tham gia";
                r.pillColors = new int[] {
                    Ui.col(this, R.color.sand),
                    Ui.col(this, R.color.muted),
                };
            }
            r.onClick = () ->
                startActivity(
                    new Intent(this, BillDetailActivity.class).putExtra(
                        Nav.BILL_ID,
                        bill.getId()
                    )
                );
            rows.add(r);
        }
        bills.submit(rows);
        b.empty.setText(
            all.isEmpty()
                ? "Nhóm chưa có hoá đơn. Ai trả trước thì bấm “Thêm hoá đơn”."
                : "Không có hoá đơn nào khớp bộ lọc."
        );
        b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.settle.setEnabled(l.openDebtCount(groupId) > 0);
    }

    // ------------------------------------------------------------------ lọc lịch sử (FR8)

    private void buildFilters(AppSnapshot s) {
        b.filters.removeAllViews();
        filterChip(
            "Danh mục: " +
                (filterCategory == null ? "tất cả" : filterCategory.label()),
            filterCategory != null,
            () -> {
                String[] labels = new String[Category.values().length + 1];
                labels[0] = "Tất cả";
                for (int i = 0; i < Category.values().length; i++) labels[
                    i + 1
                ] = Category.values()[i].label();
                int cur =
                    filterCategory == null ? 0 : filterCategory.ordinal() + 1;
                choose(
                    "Lọc theo danh mục",
                    labels,
                    cur,
                    i ->
                        filterCategory =
                            i == 0 ? null : Category.values()[i - 1]
                );
            }
        );
        filterChip(
            "Người: " +
                (filterMember == null ? "tất cả" : s.name(filterMember)),
            filterMember != null,
            () -> {
                List<String> ids = s.allMembersOf(groupId);
                String[] labels = new String[ids.size() + 1];
                labels[0] = "Tất cả";
                int cur = 0;
                for (int i = 0; i < ids.size(); i++) {
                    labels[i + 1] =
                        s.name(ids.get(i)) +
                        (ids.get(i).equals(me()) ? " (bạn)" : "");
                    if (ids.get(i).equals(filterMember)) cur = i + 1;
                }
                choose(
                    "Hoá đơn có người này",
                    labels,
                    cur,
                    i -> filterMember = i == 0 ? null : ids.get(i - 1)
                );
            }
        );
        filterChip(
            "Thời gian: " + PERIODS[filterPeriod].toLowerCase(),
            filterPeriod != 0,
            () ->
                choose(
                    "Lọc theo thời gian",
                    PERIODS,
                    filterPeriod,
                    i -> filterPeriod = i
                )
        );
        if (
            filterCategory != null || filterMember != null || filterPeriod != 0
        ) {
            filterChip("Bỏ lọc", false, () -> {
                filterCategory = null;
                filterMember = null;
                filterPeriod = 0;
                if (last != null) render(last);
            });
        }
    }

    private void filterChip(String text, boolean active, Runnable onClick) {
        Chip c = (Chip) getLayoutInflater().inflate(
            R.layout.chip_filter,
            b.filters,
            false
        );
        c.setText(text);
        c.setCheckable(false);
        c.setChipBackgroundColorResource(
            active ? R.color.ok_bg : R.color.surface
        );
        c.setOnClickListener(v -> onClick.run());
        b.filters.addView(c);
    }

    private interface IntChoice {
        void pick(int i);
    }

    private void choose(
        String title,
        String[] labels,
        int current,
        IntChoice onPick
    ) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, current, (d, i) -> {
                onPick.pick(i);
                d.dismiss();
                if (last != null) render(last);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private boolean matches(Bill bill, SplitResult r) {
        if (
            filterCategory != null && bill.getCategory() != filterCategory
        ) return false;
        if (filterMember != null && !bill.getPayerId().equals(filterMember)) {
            ShareLine line = r == null ? null : r.lineOf(filterMember);
            if (line == null || line.getAmount() <= 0) return false;
        }
        if (filterPeriod != 0) {
            long now = System.currentTimeMillis();
            long from;
            if (filterPeriod == 1) from = now - 7L * 24 * 3600 * 1000;
            else if (filterPeriod == 2) from = now - 30L * 24 * 3600 * 1000;
            else {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                from = c.getTimeInMillis();
            }
            if (bill.getCreatedAt() < from) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ thành viên, mã mời, rời nhóm

    private void shareInvite() {
        if (last == null) return;
        GroupEntity g = last.group(groupId);
        if (g == null || g.inviteCode == null) return;
        String text =
            "Vào nhóm “" +
            g.name +
            "” trên ChiaBill: mở app → tab Nhóm → “Vào nhóm bằng mã” → nhập " +
            InviteCode.format(g.inviteCode);
        startActivity(
            Intent.createChooser(
                new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text),
                "Gửi mã mời"
            )
        );
    }

    private void confirmRemove(String userId, String name) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Xoá " + name + " khỏi nhóm?")
            .setMessage(
                "Chỉ xoá được khi mọi khoản của " +
                    name +
                    " trong nhóm đã xác nhận xong. " +
                    "Hoá đơn cũ vẫn giữ nguyên phần của " +
                    name +
                    "."
            )
            .setPositiveButton("Xoá khỏi nhóm", (d, w) ->
                repo().removeMember(
                    groupId,
                    userId,
                    me(),
                    () -> toast("Đã xoá " + name + " khỏi nhóm."),
                    this::toast
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void confirmLeave() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Rời nhóm?")
            .setMessage(
                "Bạn chỉ rời được khi không còn nợ ai và không còn ai nợ bạn trong nhóm. " +
                    "Hoá đơn cũ vẫn giữ nguyên. Muốn quay lại thì nhập mã mời."
            )
            .setPositiveButton("Rời nhóm", (d, w) -> {
                leaving = true; // snapshot có thể tới trước callback: đừng báo "không ở trong nhóm"
                repo().leaveGroup(
                    groupId,
                    me(),
                    () -> {
                        toast("Đã rời nhóm.");
                        finish();
                    },
                    err -> {
                        leaving = false;
                        toast(err);
                    }
                );
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /** Bước 1: nhập số điện thoại. */
    private void askPhone() {
        EditText input = new EditText(this);
        input.setHint("Số điện thoại, vd 0904 444 444");
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        FrameLayout box = new FrameLayout(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Thêm thành viên")
            .setMessage(getString(vn.nhom03.chiabill.R.string.demo_phones))
            .setView(box)
            .setPositiveButton("Tìm", (d, w) ->
                lookup(input.getText().toString())
            )
            .setNegativeButton(vn.nhom03.chiabill.R.string.cancel, null)
            .show();
    }

    /** Bước 2: tìm đúng số đó; thấy thì hỏi xác nhận rồi thêm. */
    private void lookup(String raw) {
        repo().findUserByPhone(
            raw,
            u -> {
                if (isFinishing() || isDestroyed()) return;
                if (u == null) {
                    toast("Chưa có tài khoản nào dùng số " + raw.trim() + ".");
                    return;
                }
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Thêm " + u.name + "?")
                    .setMessage(
                        u.name +
                            " · " +
                            PhoneNumber.masked(u.phone) +
                            "\n\nNgười này sẽ thấy các hoá đơn mới của nhóm. Hoá đơn cũ không đổi."
                    )
                    .setPositiveButton("Thêm vào nhóm", (d, w) ->
                        repo().addMember(
                            groupId,
                            u.id,
                            me(),
                            () -> toast("Đã thêm " + u.name + " vào nhóm."),
                            this::toast
                        )
                    )
                    .setNegativeButton(vn.nhom03.chiabill.R.string.cancel, null)
                    .show();
            },
            this::toast
        );
    }
}
