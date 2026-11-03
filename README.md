# DinkLab Android · Mock operations app

Ứng dụng Android Kotlin/Jetpack Compose quản lý lớp pickleball với dữ liệu demo lưu bằng Room. Không có backend, không gọi ngân hàng, không xử lý thanh toán thật.

## Chạy dự án

1. Mở thư mục dự án bằng Android Studio, chọn JDK 17.
2. Cài Android SDK Platform 35 và Build Tools 35.0.0; cấu hình `sdk.dir` trong file local.properties của máy.
3. Chạy Gradle Sync, chọn thiết bị Android 8.0/API 26 trở lên.
4. Run cấu hình `app`; màn hình đầu cho phép chọn tài khoản demo.

Các đường dẫn bên dưới là đường dẫn tuyệt đối trên máy triển khai; thay chúng khi chuyển máy.

```sh
cd /Users/ngttu/Workspace/drink_lab
export JAVA_HOME=/Users/ngttu/.dinklab-tools/jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
# Khi có thiết bị/emulator đang chạy:
./gradlew :app:connectedDebugAndroidTest
```

APK debug được tạo tại `/Users/ngttu/Workspace/drink_lab/app/build/outputs/apk/debug/app-debug.apk`.

## Tài khoản demo

Không có mật khẩu thật. Chọn nhân sự trên màn hình đăng nhập:

- **Minh Anh / Quang:** Admin, quản lý toàn bộ dữ liệu.
- **Linh / Tuấn / Hà:** Sale, khách được giao và công việc cá nhân.
- **Bảo Ngọc:** Marketing, nguồn khách và chiến dịch.
- **Hoàng Nam / Thu Trang / Đức Huy / Quỳnh Anh / Minh Quân:** Coach.
- **DinkLab:** chủ doanh nghiệp, xem tổng hợp.

Chuyển tài khoản trong Cài đặt → Đăng xuất. Quyền được kiểm tra tại repository nhưng không phải bảo mật production: dữ liệu vẫn nằm trên một thiết bị demo.

## Phân hệ

- CRM, lịch sử chăm sóc, việc cần làm, nhân sự, nguồn khách và chiến dịch.
- Khóa học mẫu, lớp, đăng ký học và chuyển lớp.
- Buổi học, lịch ngày/tuần, sinh lịch định kỳ và xem trước.
- Điểm danh, buổi hoãn/hủy, học bù.
- Coach, đơn giá, phân công qua lớp/buổi, tiền công.
- Cụm sân, sân, bảng giá và đặt sân.
- Khoản phải thu/phải trả, phiếu thu/chi/hoàn tiền, phân bổ và đảo phiếu.
- Dashboard, báo cáo, nhắc việc trong app, nhật ký và reset dữ liệu.

Danh sách có tìm kiếm, lọc trạng thái, lọc khoảng ngày, sắp xếp và bản ghi lưu trữ. Form có kiểm tra dữ liệu, cảnh báo bỏ thay đổi, trạng thái lưu và giữ form khi lỗi. Các picker tham chiếu có tìm kiếm.

## Dữ liệu demo

Seed một lần khi tạo database. Thay đổi được giữ khi đóng/mở app. Cài đặt Admin cho phép nạp lại bộ đầy đủ hoặc bộ rỗng (giữ 12 tài khoản đăng nhập).

Bộ đầy đủ gồm 60 khách, 12 lớp, 48 đăng ký, 100 buổi gốc và một buổi học bù, 4 cụm sân/10 sân, 5 coach, lịch sử chăm sóc và các chứng từ liên kết. Ngày được sinh tương đối với ngày reset. Dữ liệu không được nạp lại mỗi lần mở ứng dụng.

## Luồng thử nhanh

1. Chọn Admin → Khách hàng → thêm khách, sửa, tìm kiếm.
2. Tạo lớp mới, chọn khóa, coach/sân và thứ dạng `1,3` (thứ Hai/thứ Tư).
3. Chi tiết lớp → Thêm học viên, xác nhận đăng ký → tạo khoản phải thu học phí.
4. Chi tiết lớp → Xem trước & sinh lịch; kiểm tra trùng lịch được thực hiện trước khi lưu.
5. Chi tiết buổi → Đặt sân → xác nhận; hệ thống tạo khoản phải trả tiền sân.
6. Khởi tạo điểm danh; cập nhật từng học viên. Hoàn thành buổi qua form và nhập lý do → phát sinh công coach.
7. Mở khoản phải thu/trả → tạo phiếu, nhập số tiền và xác nhận → số dư thay đổi.
8. Phiếu đã xác nhận chỉ được đảo, không sửa/xóa trực tiếp.
9. Thử đổi sang Sale/Coach và kiểm tra phạm vi dữ liệu.

## Quy ước nghiệp vụ

- Tiền VND là số nguyên `Long`; không nhập dấu phân cách vào form.
- Ngày nhập `yyyy-MM-dd`; thời gian nhập `yyyy-MM-ddTHH:mm`, hiển thị `dd/MM/yyyy`.
- Trạng thái nghĩa vụ và trạng thái thanh toán tách biệt; số dư tính từ phiếu xác nhận.
- Cọc sân là thanh toán một phần cùng khoản phải trả, không phải chi phí bổ sung.
- Một phiếu có thể phân bổ nhiều khoản bằng trường `ID:số tiền;ID:số tiền`. ID có trên màn hình chi tiết; nếu bỏ trống dùng khoản được chọn.
- Đảo phiếu loại tác động của phiếu khỏi số dư/báo cáo, giữ chứng từ và lý do.
- Hoàn tiền liên kết phiếu thu gốc, mở lại số phải thu. Nếu miễn học phí, Admin phải điều chỉnh khoản phải thu riêng.
- Rút/chuyển lớp không tự hoàn/miễn học phí. Chuyển lớp tạo đăng ký nháp mới và giữ tài chính cũ.
- Buổi hoàn thành khóa giờ/sân/coach/đơn giá. Điều chỉnh điểm danh đã chốt cần lý do.
- Buổi bù dùng định danh buổi gốc để không tính tiến độ hai lần.
- Sân đã có thanh toán không đổi lịch tự động; phải xử lý giao dịch/thỏa thuận trước.
- Xóa danh mục có liên kết chuyển thành lưu trữ; chứng từ xác nhận phải hủy/đảo theo luồng riêng.

## Giới hạn hiện tại

- Đây là mock app cục bộ, không đồng bộ nhiều máy, không có xác thực thật.
- UI CRUD dùng form theo schema thống nhất; chưa phải wizard tạo lớp nhiều bước.
- Có đổi lịch hàng loạt các buổi tương lai chưa hoàn thành; không áp dụng cho đặt sân đã thanh toán, không sửa buổi đã hoàn thành.
- Bảng giá sân có CRUD; chọn giá ưu đãi/khung giờ phải nhập rõ trên đặt sân, chưa tự tra toàn bộ bảng giá.
- Phân bổ nhiều khoản dùng text ID, chưa có allocation picker nhiều dòng.
- Chưa có upload ảnh/chứng từ, export PDF/Excel, push, SMS, Zalo, OTP, cloud backup.
- Nhắc việc được tạo khi mở màn hình, có đọc/ẩn và bật/tắt chung; chưa có công tắc riêng từng nhóm.
- Tên đơn vị lưu trong cài đặt, thương hiệu giao diện vẫn là DinkLab.
- Báo cáo công nợ là số dư hiện tại lọc theo hạn, không phải snapshot lịch sử. Báo cáo lớp là học phí phân bổ theo buổi hoàn thành trừ chi phí trực tiếp; chưa có sổ kế toán hoặc lợi nhuận ròng.
- Hoàn tiền nhiều nghĩa vụ dùng phân bổ tỷ lệ và chặn số tiền không chia chính xác tới đơn vị VND; bản vận hành thật cần giao diện phân bổ hoàn tiền chi tiết.

## Kiến trúc

ViewModel → Repository → Room. Model/schema nghiệp vụ dùng chung cho các phân hệ; Room lưu record với payload JSON và index theo loại. Đây là lựa chọn dành cho bản mock linh hoạt, **không phải schema quan hệ production**. Kiểm tra tham chiếu và thao tác liên quan được đặt trong transaction tại repository. Khi thêm backend cần chuẩn hóa bảng, migration và quyền server-side.

Unit tests kiểm tra lịch, tiền, quyền, tiến độ và tính hợp lệ seed. Instrumentation tests kiểm tra seed idempotent, CRUD Room, đảo phiếu và quyền ghi. Kết quả chạy thực tế được ghi trong `/Users/ngttu/Workspace/drink_lab/VALIDATION.md`.