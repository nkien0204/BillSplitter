package vn.nhom03.chiabill.ui.tabs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.db.UserEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.FragmentDebtsBinding;
import vn.nhom03.chiabill.domain.model.Bill;
import vn.nhom03.chiabill.domain.model.Debt;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.ui.DebtDetailActivity;
import vn.nhom03.chiabill.ui.RowAdapter;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

public class DebtsFragment extends BaseFragment {
    private FragmentDebtsBinding b;
    private final RowAdapter owe = new RowAdapter();
    private final RowAdapter owed = new RowAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentDebtsBinding.inflate(inflater, container, false);
        b.oweList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.oweList.setAdapter(owe);
        b.owedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.owedList.setAdapter(owed);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo().snapshot().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void render(AppSnapshot s) {
        if (b == null || me() == null || s.user(me()) == null) return;
        String me = me();
        List<Debt> mine = new ArrayList<>();
        List<Debt> theirs = new ArrayList<>();
        for (Debt d : s.ledger().debts()) {
            if (d.getDebtorId().equals(me)) mine.add(d);
            else if (d.getCreditorId().equals(me)) theirs.add(d);
        }
        // Khoản còn mở lên trước, mới trước cũ.
        java.util.Comparator<Debt> order = (x, y) -> {
            int open = Boolean.compare(!x.getStatus().isOpen(), !y.getStatus().isOpen());
            if (open != 0) return open;
            Bill bx = s.ledger().bill(x.getBillId());
            Bill by = s.ledger().bill(y.getBillId());
            return Long.compare(by.getCreatedAt(), bx.getCreatedAt());
        };
        Collections.sort(mine, order);
        Collections.sort(theirs, order);
        owe.submit(rows(s, mine, true));
        owed.submit(rows(s, theirs, false));
        b.emptyOwe.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);
        b.emptyOwed.setVisibility(theirs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<RowAdapter.Row> rows(AppSnapshot s, List<Debt> debts, boolean iOwe) {
        List<RowAdapter.Row> out = new ArrayList<>();
        for (Debt d : debts) {
            String other = iOwe ? d.getCreditorId() : d.getDebtorId();
            UserEntity u = s.user(other);
            Bill bill = s.ledger().bill(d.getBillId());
            GroupEntity g = s.group(d.getGroupId());
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = s.name(other);
            r.colorIndex = s.colorIndex(other);
            r.title = iOwe ? "Trả " + s.name(other) : s.name(other) + (u != null && u.guest ? " (khách)" : "") + " nợ bạn";
            r.subtitle = bill.getTitle() + " · " + (g != null ? g.name : "");
            r.amount = Money.format(d.getAmount());
            r.amountColor = Ui.col(requireContext(), d.getStatus().isOpen() ? (iOwe ? R.color.owe : R.color.brand) : R.color.muted);
            r.pill = Ui.statusLabel(requireContext(), d.getStatus());
            r.pillColors = Ui.statusColors(requireContext(), d.getStatus());
            r.onClick = () -> startActivity(new Intent(requireContext(), DebtDetailActivity.class).putExtra(Nav.DEBT_KEY, d.getKey()));
            out.add(r);
        }
        return out;
    }
}
