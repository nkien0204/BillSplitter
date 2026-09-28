package vn.nhom03.chiabill.ui.tabs;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import vn.nhom03.chiabill.ChiaBillApp;
import vn.nhom03.chiabill.R;
import vn.nhom03.chiabill.data.db.GroupEntity;
import vn.nhom03.chiabill.data.repo.AppSnapshot;
import vn.nhom03.chiabill.databinding.FragmentGroupsBinding;
import vn.nhom03.chiabill.domain.ledger.Ledger;
import vn.nhom03.chiabill.domain.model.Money;
import vn.nhom03.chiabill.ui.CreateGroupActivity;
import vn.nhom03.chiabill.ui.GroupDetailActivity;
import vn.nhom03.chiabill.ui.RowAdapter;
import vn.nhom03.chiabill.util.Nav;
import vn.nhom03.chiabill.util.Ui;

public class GroupsFragment extends BaseFragment {
    private FragmentGroupsBinding b;
    private final RowAdapter adapter = new RowAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentGroupsBinding.inflate(inflater, container, false);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.createGroup.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreateGroupActivity.class)));
        b.joinGroup.setOnClickListener(v -> askInviteCode());
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
        Ledger l = s.ledger();
        b.hello.setText("Chào " + s.name(me) + ", số dư của bạn ở mọi nhóm");
        b.owedToMe.setText(Money.format(l.owedTo(me, null)));
        b.iOwe.setText(Money.format(l.owedBy(me, null)));

        List<RowAdapter.Row> rows = new ArrayList<>();
        for (GroupEntity g : s.groupsOf(me)) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.avatarName = g.name;
            r.colorIndex = Math.abs(g.id.hashCode());
            r.title = g.name;
            r.subtitle = s.membersOf(g.id).size() + " thành viên · " + l.billsOfGroup(g.id).size() + " hoá đơn";
            long net = l.owedTo(me, g.id) - l.owedBy(me, g.id);
            if (net > 0) {
                r.amount = Money.format(net);
                r.amountColor = Ui.col(requireContext(), R.color.brand);
                r.pill = "Bạn được nợ";
                r.pillColors = new int[]{Ui.col(requireContext(), R.color.ok_bg), Ui.col(requireContext(), R.color.ok_fg)};
            } else if (net < 0) {
                r.amount = Money.format(-net);
                r.amountColor = Ui.col(requireContext(), R.color.owe);
                r.pill = "Bạn đang nợ";
                r.pillColors = new int[]{Ui.col(requireContext(), R.color.owe_bg), Ui.col(requireContext(), R.color.owe_fg)};
            } else {
                r.pill = "Sòng phẳng";
                r.pillColors = new int[]{Ui.col(requireContext(), R.color.sand), Ui.col(requireContext(), R.color.muted)};
            }
            r.onClick = () -> startActivity(new Intent(requireContext(), GroupDetailActivity.class).putExtra(Nav.GROUP_ID, g.id));
            rows.add(r);
        }
        adapter.submit(rows);
        b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** FR3: nhập mã mời 6 ký tự, vào nhóm rồi mở luôn nhóm đó. */
    private void askInviteCode() {
        EditText input = new EditText(requireContext());
        input.setHint("vd DAL-AT7");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout box = new FrameLayout(requireContext());
        int pad = Ui.dp(requireContext(), 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Vào nhóm bằng mã mời")
                .setMessage("Người trong nhóm gửi mã cho bạn. Demo: DAL-AT7 (Đà Lạt), LAB-234 (phòng lab).")
                .setView(box)
                .setPositiveButton("Vào nhóm", (d, w) -> {
                    ChiaBillApp a = app(); // callback chạy sau, fragment có thể đã detach
                    String me = me();
                    if (me == null) return;
                    a.repository().joinByInviteCode(input.getText().toString(), me, groupId -> {
                        android.app.Activity act = getActivity();
                        if (act == null || act.isFinishing()) return;
                        act.startActivity(new Intent(act, GroupDetailActivity.class).putExtra(Nav.GROUP_ID, groupId));
                    }, err -> android.widget.Toast.makeText(a, err, android.widget.Toast.LENGTH_LONG).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
