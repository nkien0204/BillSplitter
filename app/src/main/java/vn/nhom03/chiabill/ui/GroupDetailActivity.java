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
import java.util.Collections;
import java.util.List;
import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.ActivityGroupDetailBinding;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.Money;
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
        b.deleteGroup.setOnClickListener(v -> confirmDelete());
        repo().snapshot().observe(this, this::render);

        // Lấy dữ liệu mới nhất từ máy chủ khi mở nhóm; Room LiveData tự vẽ lại.
        repo().syncAll(null, null);
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
        b.deleteGroup.setVisibility(
            me.equals(g.createdBy) ? View.VISIBLE : View.GONE
        );
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

        // v0.4.0: bỏ bộ lọc lịch sử cho gọn; nhóm bạn bè ít hoá đơn nên đọc danh sách là đủ.
        List<Bill> list = new ArrayList<>(l.billsOfGroup(groupId));
        Collections.reverse(list); // mới nhất lên đầu
        long shownTotal = 0;
        for (Bill bill : list) {
            SplitResult sr = l.splitOf(bill.getId());
            if (sr != null) shownTotal += sr.getTotal();
        }
        b.filterInfo.setText(list.size() + " hoá đơn · tổng " + Money.format(shownTotal));
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
        b.empty.setText("Nhóm chưa có hoá đơn. Ai trả trước thì bấm “Thêm hoá đơn”.");
        b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.settle.setEnabled(l.openDebtCount(groupId) > 0);
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

    private void confirmDelete() {
        String name = last != null && last.group(groupId) != null
            ? last.group(groupId).name
            : "";
        new MaterialAlertDialogBuilder(this)
            .setTitle("Xoá nhóm “" + name + "”?")
            .setMessage(
                "Toàn bộ hoá đơn và lịch sử của nhóm sẽ bị xoá vĩnh viễn, với mọi thành viên. " +
                    "Chỉ xoá được khi mọi khoản trong nhóm đã xác nhận xong. Không thể hoàn tác."
            )
            .setPositiveButton("Xoá nhóm", (d, w) -> {
                leaving = true; // snapshot có thể tới trước callback: đừng báo "không ở trong nhóm"
                repo().deleteGroup(
                    groupId,
                    me(),
                    () -> {
                        toast("Đã xoá nhóm.");
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
