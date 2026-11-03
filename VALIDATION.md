# Kết quả xác minh DinkApp Demo

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
- SHA-256 bản APK DinkApp hiện tại: `96d855642dd3fe3c86e17bdb909e0c9560e414c356faed2b85513809736e9cde`.
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

## Xác minh sau đổi tên repository/project

- Repository GitHub được đổi tên thành `ngttu06/dinklab`; giữ nguyên trạng thái public tại thời điểm đổi tên.
- Project Gradle: `dinklab`; nhãn app: `DinkLab`; application ID giữ nguyên `com.dinklab.app` để không đổi định danh ứng dụng.
- Thư mục workspace vẫn là `/Users/ngttu/Workspace/drink_lab`; các đường dẫn tài liệu tiếp tục trỏ đến vị trí thực tế.
- Chạy lại `:app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL**, 46 tác vụ (5 thực thi, 41 up-to-date). Unit test được Gradle xác nhận up-to-date, không chạy lại instrumentation/lint trong lần đổi tên.
- Kiểm tra APK bằng `aapt dump badging`: `application-label:'DinkLab'`.
- Log: `/Users/ngttu/.dinklab-tools/rename-build.log`.

## Đổi thương hiệu ứng dụng thành DinkApp

- Project Gradle, launcher label, tiêu đề, màn hình đăng nhập, theme và tên tài nguyên icon dùng **DinkApp**.
- Tên đơn vị mặc định, tài khoản chủ, cụm sân/sân mẫu và email Admin mẫu đã đổi theo thương hiệu mới.
- Có cập nhật một lần cho dữ liệu thương hiệu cũ đã lưu; giữ nguyên tên tùy chỉnh và giao dịch. Các chuỗi thương hiệu cũ trong mã migration và test là có chủ đích.
- Package/application ID `com.dinklab.app`, database `dinklab-demo.db`, repository `ngttu06/dinklab`, tài liệu yêu cầu gốc và các đường dẫn công cụ/workspace giữ nguyên để tương thích. Các phần trước của tài liệu này là lịch sử xác minh, không phải nhãn app hiện tại.
- Build APK, APK test và chạy lại unit test: **BUILD SUCCESSFUL; 14/14 unit test đạt**.
- Chạy trên Android 11/API 30: **8/8 instrumentation test đạt** (7 test repository, 1 test UI); bao gồm test nâng cấp thương hiệu giữ nguyên tên tùy chỉnh và dữ liệu tài chính.
- `aapt dump badging` xác nhận `application-label:'DinkApp'`; APK vẫn có kích thước 17.140.096 byte.
- Chưa chạy lại Lint cho lần đổi thương hiệu này; các kết quả Lint phía trên thuộc lần kiểm tra trước.
- Log: `/Users/ngttu/.dinklab-tools/dinkapp-build.log` và `/Users/ngttu/.dinklab-tools/dinkapp-connected.log`.