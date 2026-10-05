import type { SyncRun } from "./api";

export const runStatus: Record<SyncRun["status"], string> = {
  QUEUED: "Đang chờ", RUNNING: "Đang chạy", SUCCEEDED: "Thành công",
  PARTIAL: "Đồng bộ hoàn tất một phần", FAILED: "Thất bại",
};
export const stepStatus: Record<SyncRun["profileStepStatus"], string> = {
  PENDING: "Đang chờ", SUCCEEDED: "Thành công", FAILED: "Thất bại",
};

const failures: Record<string, string> = {
  CONNECTION_NOT_FOUND: "Chưa có kết nối học vụ để đồng bộ.",
  RECONNECTION_REQUIRED: "Phiên học vụ đã hết hạn; cần kết nối lại qua quy trình hiện có.",
  SOURCE_TIMEOUT: "Cổng học vụ phản hồi quá chậm.",
  SOURCE_UNAVAILABLE: "Cổng học vụ hiện không sẵn sàng.",
  SOURCE_SCHEMA_CHANGED: "Dữ liệu nguồn đã thay đổi cấu trúc; cần kiểm tra bộ đọc.",
  PROFILE_REFRESH_FAILED: "Chưa làm mới được hồ sơ.",
  CURRICULUM_REFRESH_FAILED: "Chưa làm mới được chương trình và danh mục.",
  LOCK_UNAVAILABLE: "Chưa lấy được quyền xử lý lượt đồng bộ; hệ thống sẽ thử lại nếu có lịch hẹn.",
  INTERNAL_ERROR: "Lượt đồng bộ gặp lỗi hệ thống. Vui lòng thử lại sau.",
};
export const failureMessage = (code: string | null) => code ? failures[code] ?? "Lượt đồng bộ gặp lỗi chưa được phân loại." : null;

export function localTime(value: string | null) {
  if (!value) return "Chưa có";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Không xác định" : new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium", timeStyle: "short",
  }).format(date);
}
