package vn.nhom03.chiabill.ui.bill;

import androidx.lifecycle.ViewModel;

/** Giữ bản nháp qua xoay màn hình. Không giữ tham chiếu View/Context. */
public class BillEditViewModel extends ViewModel {
    public BillDraft draft;
    /** Ảnh hoá đơn đang gửi lên máy chủ: chưa cho lưu cho tới khi xong. */
    public boolean uploading;
    /** Ảnh nhỏ của ảnh vừa chụp, để hiện lại sau khi xoay màn hình. */
    public android.graphics.Bitmap thumb;
}
