# Kết quả xác minh DinkLab Demo

Ngày ghi nhận: **03/11/2026**.

## Môi trường đã chạy

- macOS, kiến trúc x86_64.
- Temurin JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1.
- Kotlin 2.0.21, Android SDK Platform 35.
- Emulator `DinkLab_API30`, Android 11 / API 30, x86_64, chạy headless.
- Công cụ được cài riêng tại `/Users/ngttu/.dinklab-tools`; không thay Java mặc định của máy.
- Đường dẫn SDK riêng được khai báo trong `/Users/ngttu/Workspace/drink_lab/local.properties` và không đưa vào source control.

## Lệnh xác minh đã chạy

```sh
cd /Users/ngttu/Workspace/drink_lab
export JAVA_HOME=/Users/ngttu/.dinklab-tools/jdk/Contents/Home
/Users/ngttu/.dinklab-tools/gradle-8.11.1/bin/gradle \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:connectedDebugAndroidTest \
  :app:lintDebug \
  --console=plain
```

Kết quả lần cuối: **BUILD SUCCESSFUL**, 84 tác vụ, 36 thực thi và 48 up-to-date.

Gradle Wrapper đã được tạo trong dự án. Lệnh kiểm thử trên sử dụng trực tiếp bản Gradle cài riêng; chưa kiểm tra riêng việc tải distribution qua wrapper trên máy sạch.

## Kiểm thử

| Nhóm | Số bài | Đạt | Lỗi | Bỏ qua |
|---|---:|---:|---:|---:|
| Unit test nghiệp vụ | 14 | 14 | 0 | 0 |
| Instrumentation: Room/repository | 6 | 6 | 0 | 0 |
| Instrumentation: giao diện Compose | 1 | 1 | 0 | 0 |

### Unit test

- Khoảng giờ giao nhau và hai lịch nối tiếp.
- Sinh đúng 10 buổi thứ Hai/thứ Tư.
- Thanh toán từng phần, đảo phiếu và công nợ quá hạn.
- Cọc sân và thanh toán phần còn lại không cộng chi phí trùng.
- Phân bổ không trùng mục tiêu.
- Hoàn tiền mở lại số phải thu.
- Học bù không tăng gấp đôi tiến độ.
- Chính sách nghỉ và số buổi đã sử dụng.
- Seed có ID duy nhất và tham chiếu hợp lệ.
- Seed vượt qua kiểm tra nghiệp vụ, không có xung đột lịch.
- Phạm vi quyền xem và quyền ghi.
- Bộ rỗng giữ tài khoản đăng nhập.
- Chặn giảm giá vượt học phí.
- Chặn thu vượt công nợ.

### Room/repository trên emulator

- Seed không chạy trùng; thêm, sửa, xóa danh mục được phản ánh trong database.
- Phiếu xác nhận không sửa trực tiếp nhưng đảo được.
- Reset bộ rỗng không bị tự seed lại khi khởi tạo.
- Sale không tạo được chứng từ tài chính.
- Đổi lịch hàng loạt giữ nguyên buổi đã hoàn thành.
- Hoàn thành một buổi nhiều lần không tạo công coach trùng.

### Giao diện trên emulator

Đã chạy luồng: chọn Admin → dashboard → Khách hàng → tìm “Nguyễn Minh” → mở hồ sơ → kiểm tra thao tác đăng ký học.

Lần chạy đầu UI test timeout vì tài khoản cần tìm không xuất hiện trong vùng đầu của danh sách lazy. Sau khi ưu tiên tài khoản Admin ở đầu danh sách đăng nhập và tăng thời gian chờ khởi tạo, chạy lại toàn bộ 7 instrumentation test đạt.

## Android Lint

- **0 lỗi**, **13 cảnh báo**.
- 11 cảnh báo có dependency mới hơn; dự án đang khóa các phiên bản đã build/test cùng nhau.
- 1 cảnh báo `DataExtractionRules`: `android:dataExtractionRules` chỉ áp dụng từ Android 12; Lint đề nghị bổ sung `android:fullBackupContent` cho các API cũ. Manifest hiện đặt `android:allowBackup="false"`; cấu hình Android 12+ chủ đích loại dữ liệu demo khỏi backup/transfer.
- 1 cảnh báo `KaptUsageInsteadOfKsp`: Room hỗ trợ KSP, hiện dự án dùng KAPT.
- Build cũng có cảnh báo KAPT dùng chế độ ngôn ngữ 1.9 và tùy chọn processor không được sử dụng ở một số source set test; không chặn build/test.

## Sản phẩm và báo cáo

- APK: `/Users/ngttu/Workspace/drink_lab/app/build/outputs/apk/debug/app-debug.apk`.
- Kích thước APK tại thời điểm kiểm tra: **17.140.096 byte**.
- SHA-256: `2277b770fe1cb541265d26dbaf8373e7e51efa3d2ea1b9b98da3b696f82f6836`.
- Unit test report: `/Users/ngttu/Workspace/drink_lab/app/build/reports/tests/testDebugUnitTest/index.html`.
- Instrumentation report: `/Users/ngttu/Workspace/drink_lab/app/build/reports/androidTests/connected/debug/index.html`.
- Lint report: `/Users/ngttu/Workspace/drink_lab/app/build/reports/lint-results-debug.html`.
- Log build gốc: `/Users/ngttu/.dinklab-tools/final-build.log`.

## Phạm vi chưa xác minh / chưa hoàn thiện

- Chưa thử trên điện thoại thật, API 26 hoặc API 35, tablet, màn hình nhỏ và font lớn.
- UI smoke test không thay thế nghiệm thu thủ công mọi form và mọi vai trò.
- Chưa có test tự động cho từng tổ hợp hoàn tiền, điều chỉnh, hủy, lưu trữ và đổi lịch.
- Chưa có kiểm thử release signing, phát hành Play Store, hiệu năng quy mô lớn hoặc migration database.
- Mô hình lưu record JSON trong Room là thiết kế mock linh hoạt, không phải schema production có foreign key cho từng quan hệ.
- Danh sách các giới hạn chức năng và hướng dẫn thử nằm trong `/Users/ngttu/Workspace/drink_lab/README.md`.

**Kết luận:** bản demo đã build và chạy kiểm thử được; không coi kết quả này là xác nhận đầy đủ cho vận hành tài chính thật hoặc bảo mật production.