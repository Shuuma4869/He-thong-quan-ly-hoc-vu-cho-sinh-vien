import { z } from "zod";

export const passwordSchema = z.string().min(12, "Mật khẩu cần ít nhất 12 ký tự.").max(64, "Mật khẩu tối đa 64 ký tự.")
  .refine((value) => new TextEncoder().encode(value).length <= 72, "Mật khẩu tối đa 72 byte UTF-8.");
export const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại."),
  newPassword: passwordSchema,
  confirmPassword: z.string().min(1, "Xác nhận mật khẩu mới."),
}).refine((value) => value.newPassword !== value.currentPassword, {
  path: ["newPassword"], message: "Mật khẩu mới phải khác mật khẩu hiện tại.",
}).refine((value) => value.newPassword === value.confirmPassword, {
  path: ["confirmPassword"], message: "Hai mật khẩu mới không khớp.",
});
export const accountSessionSchema = z.object({
  sessionRef: z.string().regex(/^[0-9a-f]{64}$/),
  current: z.boolean(),
  createdAt: z.iso.datetime({ offset: true }),
  lastAccessedAt: z.iso.datetime({ offset: true }),
  expiresAt: z.iso.datetime({ offset: true }),
});
export const accountSessionsSchema = z.array(accountSessionSchema);
export type PasswordChange = z.infer<typeof passwordChangeSchema>;
export type AccountSession = z.infer<typeof accountSessionSchema>;
export const loginSchema = z.object({
  email: z.email("Email không hợp lệ.").max(254),
  password: z.string().min(1, "Nhập mật khẩu.").max(64, "Mật khẩu tối đa 64 ký tự."),
});
export const registerSchema = loginSchema.extend({
  password: passwordSchema,
  displayName: z.string().trim().min(1, "Nhập tên hiển thị.").max(80, "Tên tối đa 80 ký tự."),
});
export const settingsUpdateSchema = z.object({
  notificationEmail: z.union([z.email("Email không hợp lệ.").max(254), z.literal(""), z.null()]),
  timezone: z.string().min(1, "Chọn múi giờ.").max(64),
  locale: z.enum(["vi-VN", "en-US"]),
  theme: z.enum(["LIGHT", "DARK", "SYSTEM"]),
  syncEmailAlertsEnabled: z.boolean(),
});
export const settingsSchema = settingsUpdateSchema.extend({ notificationEmailVerifiedAt: z.string().nullable() });
export const currentUserSchema = z.object({
  id: z.uuid(), email: z.email(), displayName: z.string().nullable(),
  role: z.enum(["STUDENT", "ADMIN"]), status: z.literal("ACTIVE"),
  createdAt: z.string(), settings: settingsSchema,
});
export type CurrentUser = z.infer<typeof currentUserSchema>;
export type Settings = z.infer<typeof settingsSchema>;
export type SettingsUpdate = z.infer<typeof settingsUpdateSchema>;
