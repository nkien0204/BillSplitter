package vn.nhom03.chiabill.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.BillEntity;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.data.repo.BillInput;
import vn.nhom03.chiabill.databinding.ActivityBillEditBinding;
import vn.nhom03.chiabill.databinding.ItemBillItemBinding;
import vn.nhom03.chiabill.databinding.ItemPreviewRowBinding;
import vn.nhom03.chiabill.databinding.ItemShareRowBinding;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Category;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.domain.model.ShareLine;
import vn.nhom03.chiabill.domain.model.SplitMode;
import vn.nhom03.chiabill.domain.model.SplitResult;
import vn.nhom03.chiabill.domain.split.SplitEngine;
import vn.nhom03.chiabill.ui.bill.BillDraft;
import vn.nhom03.chiabill.ui.bill.BillEditViewModel;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

/**
 * Tạo / sửa hoá đơn. Bảng xem trước tính lại bằng SplitEngine sau mỗi thao tác,
 * nên người trả thấy phần từng người trước khi lưu. Không cho lưu khi còn món chưa gán, tổng bằng 0,
 * hoặc chia theo % mà tổng chưa đúng 100%.
 *
 * Hai danh sách thành viên: {@code members} là người hiện tại (hiện thành chip), {@code order} là mọi người
 * từng ở nhóm (thứ tự chia). Sửa hoá đơn cũ có người đã rời thì người đó vẫn hiện để phần của họ không mất.
 */
public class BillEditActivity extends BaseActivity {
    private static final long MAX_AMOUNT = 999_999_999L;

    private ActivityBillEditBinding b;
    private BillEditViewModel vm;
    private AppSnapshot snap;
    private List<String> members;
    private List<String> order;
    private boolean built;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireUser()) return;
        b = ActivityBillEditBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setupToolbar(b.toolbar);
        vm = new ViewModelProvider(this).get(BillEditViewModel.class);
        repo().snapshot().observe(this, this::onSnapshot);
    }

    private void onSnapshot(AppSnapshot s) {
        if (userMissing(s)) return;
        snap = s;
        if (built) {
            renderPreview();
            return;
        }
        String groupId = getIntent().getStringExtra(Nav.GROUP_ID);
        String billId = getIntent().getStringExtra(Nav.BILL_ID);
        if (vm.draft == null) {
            if (billId != null) {
                Bill bill = s.ledger().bill(billId);
                BillEntity be = s.billEntity(billId);
                if (bill == null || be == null) { finish(); return; }
                if (!me().equals(be.createdBy) && !me().equals(be.payerId)) {
                    toast("Chỉ người tạo hoặc người trả được sửa hoá đơn này.");
                    finish();
                    return;
                }
                vm.draft = BillDraft.from(bill);
            } else {
                vm.draft = BillDraft.create(groupId, me(), s.membersOf(groupId));
            }
        }
        GroupEntity g = s.group(vm.draft.groupId);
        if (g == null || !s.membersOf(g.id).contains(me())) { finish(); return; }
        order = s.allMembersOf(g.id);
        members = new ArrayList<>();
        List<String> referenced = referencedIds(vm.draft);
        for (String id : order) if (s.membersOf(g.id).contains(id) || referenced.contains(id)) members.add(id);
        b.toolbar.setTitle(vm.draft.id == null ? "Hoá đơn mới" : "Sửa hoá đơn");
        b.toolbar.setSubtitle(g.name);
        buildForm();
        built = true;
        renderPreview();
    }

    // ------------------------------------------------------------------ dựng form (một lần)

    private void buildForm() {
        BillDraft d = vm.draft;
        b.title.setText(d.title);
        b.title.addTextChangedListener(new SimpleWatcher(t -> d.title = t));

        for (Category cat : Category.values()) {
            Chip c = chip(b.categories, cat.label(), true);
            c.setChecked(cat == d.category);
            c.setOnCheckedChangeListener((v, checked) -> { if (checked) d.category = cat; });
        }
        showDate();
        b.date.setOnClickListener(v -> pickDate());

        for (String id : members) {
            Chip c = chip(b.payers, s(id), true);
            c.setChecked(id.equals(d.payerId));
            c.setOnCheckedChangeListener((v, checked) -> {
                if (checked) { d.payerId = id; renderPreview(); }
            });
        }

        b.mode.check(modeButton(d.mode));
        b.mode.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            d.mode = id == R.id.modeItems ? SplitMode.ITEMIZED
                    : id == R.id.modeCustom ? SplitMode.CUSTOM
                    : id == R.id.modePercent ? SplitMode.PERCENT : SplitMode.EQUAL;
            if (d.mode == SplitMode.PERCENT && d.percent.isEmpty()) prefillPercent();
            if (d.mode == SplitMode.CUSTOM && d.rounding != 1) {
                // Số tiền tự nhập phải giữ nguyên từng đồng: bỏ làm tròn 1.000đ
                d.rounding = 1;
                b.rounding.check(R.id.round1);
            }
            showModeSection();
            renderPreview();
        });
        showModeSection();

        bindMoney(b.subtotal, d.subtotal, v -> d.subtotal = v);
        for (String id : members) {
            Chip c = chip(b.participants, s(id), true);
            c.setChecked(d.participants.contains(id));
            c.setOnCheckedChangeListener((v, checked) -> {
                if (checked) d.participants.add(id); else d.participants.remove(id);
                renderPreview();
            });
        }

        rebuildItems();
        b.addItem.setOnClickListener(v -> {
            d.items.add(new BillDraft.Item());
            rebuildItems();
            renderPreview();
        });

        bindPercent(b.vat, d.vat, new int[]{R.id.vat0, R.id.vat8, R.id.vat10}, new int[]{0, 8, 10}, v -> d.vat = v);
        bindPercent(b.service, d.service, new int[]{R.id.svc0, R.id.svc5, R.id.svc10}, new int[]{0, 5, 10}, v -> d.service = v);
        bindPercent(b.rounding, (int) d.rounding, new int[]{R.id.round1, R.id.round1000}, new int[]{1, 1000}, v -> d.rounding = v);

        b.save.setOnClickListener(v -> save());
    }

    private static int modeButton(SplitMode m) {
        switch (m) {
            case ITEMIZED: return R.id.modeItems;
            case CUSTOM: return R.id.modeCustom;
            case PERCENT: return R.id.modePercent;
            default: return R.id.modeEqual;
        }
    }

    private void showModeSection() {
        SplitMode m = vm.draft.mode;
        b.subtotalBox.setVisibility(m == SplitMode.EQUAL || m == SplitMode.PERCENT ? View.VISIBLE : View.GONE);
        b.equalSection.setVisibility(m == SplitMode.EQUAL ? View.VISIBLE : View.GONE);
        b.itemsSection.setVisibility(m == SplitMode.ITEMIZED ? View.VISIBLE : View.GONE);
        boolean shares = m == SplitMode.CUSTOM || m == SplitMode.PERCENT;
        b.sharesSection.setVisibility(shares ? View.VISIBLE : View.GONE);
        if (shares) rebuildShares();
        switch (m) {
            case ITEMIZED: b.modeHint.setText("Mỗi món chia đều cho những người dùng món đó."); break;
            case CUSTOM: b.modeHint.setText("Nhập số tiền của từng người. Tạm tính = tổng các số này; VAT và phí chia theo tỉ lệ."); break;
            case PERCENT: b.modeHint.setText("Nhập phần trăm của từng người, tổng phải đúng 100%. Để trống = 0%."); break;
            default: b.modeHint.setText("Tạm tính chia đều cho những người được chọn."); break;
        }
    }

    /** Chia % đều cho thành viên hiện tại làm điểm xuất phát; phần dư phần vạn cho người đầu. */
    private void prefillPercent() {
        List<String> ids = new ArrayList<>();
        for (String id : members) if (snap.membersOf(vm.draft.groupId).contains(id)) ids.add(id);
        if (ids.isEmpty()) return;
        long each = SplitEngine.PERCENT_TOTAL / ids.size();
        long rest = SplitEngine.PERCENT_TOTAL - each * ids.size();
        for (int i = 0; i < ids.size(); i++) vm.draft.percent.put(ids.get(i), each + (i == 0 ? rest : 0));
    }

    /** Dòng nhập cho từng người ở chế độ Số tiền / Theo %. Dựng lại mỗi lần đổi chế độ. */
    private void rebuildShares() {
        b.sharesContainer.removeAllViews();
        boolean percent = vm.draft.mode == SplitMode.PERCENT;
        Map<String, Long> map = percent ? vm.draft.percent : vm.draft.custom;
        for (String id : members) {
            ItemShareRowBinding rb = ItemShareRowBinding.inflate(getLayoutInflater(), b.sharesContainer, false);
            rb.getRoot().setSaveFromParentEnabled(false); // các dòng dùng chung id, xem ghi chú ở rebuildItems
            rb.shareName.setText(s(id));
            Long cur = map.get(id);
            if (percent) {
                rb.shareBox.setSuffixText("%");
                rb.shareValue.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                rb.shareValue.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
                // setKeyListener đổi inputType theo listener: đặt lại để bàn phím số có dấu thập phân
                rb.shareValue.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                rb.shareValue.setText(cur == null || cur == 0 ? "" : SplitEngine.formatPercent(cur).replace("%", ""));
                rb.shareValue.addTextChangedListener(new SimpleWatcher(t -> {
                    long bp = SplitEngine.parsePercent(t);
                    rb.shareBox.setError(bp < 0 ? "Sai" : null);
                    if (bp > 0) map.put(id, bp); else map.remove(id);
                    renderPreview();
                }));
            } else {
                bindMoney(rb.shareValue, cur == null ? 0 : cur, v -> {
                    if (v > 0) map.put(id, v); else map.remove(id);
                });
            }
            b.sharesContainer.addView(rb.getRoot());
        }
    }

    private void showDate() {
        b.date.setText("Ngày " + new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT)
                .format(new java.util.Date(vm.draft.date)));
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(vm.draft.date);
        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(vm.draft.date);
            picked.set(y, m, day);
            vm.draft.date = Math.min(picked.getTimeInMillis(), System.currentTimeMillis());
            showDate();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }

    /** Mọi người được nhắc tới trong bản nháp (người trả, người tham gia, người dùng món, người có phần). */
    private static List<String> referencedIds(BillDraft d) {
        List<String> out = new ArrayList<>();
        if (d.payerId != null) out.add(d.payerId);
        out.addAll(d.participants);
        for (BillDraft.Item it : d.items) out.addAll(it.who);
        out.addAll(d.custom.keySet());
        out.addAll(d.percent.keySet());
        return out;
    }

    private void rebuildItems() {
        b.itemsContainer.removeAllViews();
        LayoutInflater inf = getLayoutInflater();
        for (BillDraft.Item it : vm.draft.items) {
            ItemBillItemBinding ib = ItemBillItemBinding.inflate(inf, b.itemsContainer, false);
            // Các dòng món dùng chung id (itemName, itemPrice…): không cho hệ thống lưu/khôi phục state theo id,
            // nếu không khi xoay màn hình mọi dòng sẽ nhận text của dòng cuối và ghi đè bản nháp.
            ib.getRoot().setSaveFromParentEnabled(false);
            ib.itemName.setText(it.name);
            ib.itemName.addTextChangedListener(new SimpleWatcher(t -> it.name = t));
            bindMoney(ib.itemPrice, it.price, v -> it.price = v);
            ib.itemRemove.setOnClickListener(v -> {
                vm.draft.items.remove(it);
                if (vm.draft.items.isEmpty()) vm.draft.items.add(new BillDraft.Item());
                rebuildItems();
                renderPreview();
            });
            for (String id : members) {
                Chip c = chip(ib.itemWho, s(id), true);
                c.setChecked(it.who.contains(id));
                c.setOnCheckedChangeListener((v, checked) -> {
                    if (checked) it.who.add(id); else it.who.remove(id);
                    renderPreview();
                });
            }
            ib.getRoot().setTag(ib);
            b.itemsContainer.addView(ib.getRoot());
        }
    }

    // ------------------------------------------------------------------ xem trước

    private void renderPreview() {
        if (vm.draft == null || members == null) return;
        BillDraft d = vm.draft;
        vn.nhom03.chiabill.domain.model.BillSpec spec = d.toSpec();
        SplitResult r = SplitEngine.split(spec, order);
        String invalid = SplitEngine.check(spec, order);

        b.previewTotal.setText(Money.format(r.getTotal()));
        StringBuilder calc = new StringBuilder("Tạm tính " + Money.format(r.getSubtotal()));
        if (r.getVat() > 0) calc.append(" · VAT ").append(Money.format(r.getVat()));
        if (r.getService() > 0) calc.append(" · phí ").append(Money.format(r.getService()));
        b.previewCalc.setText(calc);

        b.previewRows.removeAllViews();
        for (ShareLine l : r.getLines()) {
            ItemPreviewRowBinding pr = ItemPreviewRowBinding.inflate(getLayoutInflater(), b.previewRows, true);
            pr.name.setText(s(l.getMemberId()) + (l.getMemberId().equals(d.payerId) ? " · người trả" : ""));
            pr.amount.setText(Money.format(l.getAmount()));
            long adj = l.getRoundingAdj();
            pr.adj.setText(adj == 0 ? "" : (adj > 0 ? "+" : "") + Money.dots(adj));
        }

        // cảnh báo từng món chưa gán
        for (int i = 0; i < b.itemsContainer.getChildCount() && i < d.items.size(); i++) {
            ItemBillItemBinding ib = (ItemBillItemBinding) b.itemsContainer.getChildAt(i).getTag();
            BillDraft.Item it = d.items.get(i);
            ib.itemWarn.setVisibility(it.price > 0 && it.who.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (d.mode == SplitMode.CUSTOM) {
            b.sharesSum.setText("Tạm tính " + Money.format(r.getSubtotal()));
            b.sharesSum.setTextColor(Ui.col(this, R.color.muted));
        } else if (d.mode == SplitMode.PERCENT) {
            long sum = 0;
            for (String id : order) { Long v = d.percent.get(id); if (v != null) sum += v; }
            b.sharesSum.setText("Tổng " + SplitEngine.formatPercent(sum) + " / 100%");
            b.sharesSum.setTextColor(Ui.col(this, sum == SplitEngine.PERCENT_TOTAL ? R.color.brand : R.color.owe));
        }

        if (invalid != null) {
            b.previewCheck.setText(invalid);
            b.previewCheck.setTextColor(Ui.col(this, R.color.owe_on_dark));
        } else if (!r.getUnassignedItems().isEmpty()) {
            b.previewCheck.setText("Còn món chưa gán: " + String.join(", ", r.getUnassignedItems()));
            b.previewCheck.setTextColor(Ui.col(this, R.color.owe_on_dark));
        } else if (r.getTotal() <= 0) {
            b.previewCheck.setText("Nhập số tiền để xem phần từng người");
            b.previewCheck.setTextColor(Ui.col(this, R.color.owe_on_dark));
        } else {
            b.previewCheck.setText("✓ Tổng các phần " + Money.format(r.sumOfShares()) + " khớp tổng hoá đơn");
            b.previewCheck.setTextColor(Ui.col(this, R.color.brand_on_dark));
        }
        b.save.setEnabled(invalid == null && r.isSavable());
    }

    private void save() {
        BillDraft d = vm.draft;
        b.save.setEnabled(false);
        repo().saveBill(new BillInput(d.id, d.groupId, d.title, d.toSpec(), d.category, d.date), me(), id -> {
            toast(d.id == null ? "Đã lưu và báo cho những người cần trả." : "Đã cập nhật hoá đơn.");
            if (d.id == null) startActivity(new Intent(this, BillDetailActivity.class).putExtra(Nav.BILL_ID, id));
            finish();
        }, err -> {
            toast(err);
            b.save.setEnabled(true);
        });
    }

    // ------------------------------------------------------------------ tiện ích

    private String s(String id) {
        String n = snap.name(id);
        return id.equals(me()) ? n + " (bạn)" : n;
    }

    private Chip chip(ChipGroup parent, String text, boolean checkable) {
        Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, parent, false);
        c.setText(text);
        c.setCheckable(checkable);
        c.setId(View.generateViewId());
        parent.addView(c);
        return c;
    }

    private interface LongSink { void set(long v); }
    private interface IntSink { void set(int v); }
    private interface TextSink { void set(String t); }

    /** Ô tiền: chỉ nhận số, tự chèn dấu chấm hàng nghìn, cập nhật bản nháp và bảng xem trước. */
    private void bindMoney(EditText field, long initial, LongSink sink) {
        field.setText(initial > 0 ? Money.dots(initial) : "");
        field.addTextChangedListener(new TextWatcher() {
            private boolean self;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (self) return;
                long v = Money.parseDigits(e.toString(), MAX_AMOUNT);
                String pretty = v > 0 ? Money.dots(v) : "";
                if (!pretty.equals(e.toString())) {
                    self = true;
                    field.setText(pretty);
                    field.setSelection(pretty.length());
                    self = false;
                }
                sink.set(v);
                renderPreview();
            }
        });
    }

    private void bindPercent(MaterialButtonToggleGroup group, int current, int[] ids, int[] values, IntSink sink) {
        for (int i = 0; i < ids.length; i++) if (values[i] == current) group.check(ids[i]);
        if (group.getCheckedButtonId() == View.NO_ID) group.check(ids[0]);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) sink.set(values[i]);
            renderPreview();
        });
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final TextSink sink;
        SimpleWatcher(TextSink sink) { this.sink = sink; }
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
        @Override public void afterTextChanged(Editable e) { sink.set(e.toString()); }
    }
}
