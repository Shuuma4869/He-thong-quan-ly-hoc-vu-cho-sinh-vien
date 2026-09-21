# Kết nối AMS với cổng QLĐT Phenikaa

> Phần khảo sát Phase 3 bên dưới được giữ nguyên theo thời điểm kiểm tra. Kết quả mới ngày 21/09/2026 nằm ở [Phase 4A: kiểm chứng HTTP và giao thức A/B](#phase-4a-kiểm-chứng-http-và-giao-thức-ab): Java đã đọc được lịch qua API nội bộ, nhưng chưa có chức năng nhập dữ liệu vào AMS.

Tài liệu này ghi lại những gì đã kiểm tra trên cổng QLĐT Phenikaa trong ngày 18–20/09/2026 và những việc cần làm rõ trước khi viết phần kết nối cho AMS. Người tiếp tục phát triển có thể đọc phần đầu để hiểu hướng xử lý, rồi tra đường dẫn API và tên trường ở các phụ lục.

Phạm vi hiện tại là khảo sát, chưa triển khai nhập dữ liệu vào AMS. `PhenikaaAcademicPortalClient` vẫn chưa có phần gọi HTTP; chương trình chưa tự đồng bộ hồ sơ, lịch hoặc điểm từ trường.

## 1. Những điều cần nắm trước

Cổng QLĐT có API cung cấp dữ liệu cho chính giao diện của nó. Đã quan sát được các yêu cầu lấy hồ sơ, lịch học, lịch thi, điểm và chương trình đào tạo sau khi chủ tài khoản tự đăng nhập. Vì vậy, hướng kết nối đáng xem xét là gọi các API này, thay vì đọc nội dung từng ô trên trang web.

Tuy nhiên, biết địa chỉ API mới chỉ là bước đầu:

- Yêu cầu có cả Bearer token và cookie. Chưa biết phiên dùng được bao lâu hoặc được gia hạn như thế nào.
- Dữ liệu gửi và nhận có một lớp chuyển đổi riêng của portal. Chưa có chương trình độc lập xử lý lớp này để gọi API ngoài trình duyệt.
- Một số mã lịch không đổi khi tải lại trang, nhưng chưa biết có giữ nguyên khi trường đổi giờ hoặc phòng hay không.
- Có kết nối Socket.IO và mã hỗ trợ Firebase, nhưng chưa xác nhận chúng thông báo thay đổi lịch học, lịch thi hoặc điểm.

Do đó, kết quả hiện tại đủ để xác định hướng và các điểm cần kiểm tra tiếp, chưa đủ để khẳng định bộ kết nối đã sẵn sàng chạy thực tế. Những chỗ ghi **chưa xác minh** là chưa có đủ thông tin, không có nghĩa portal không hỗ trợ.

## 2. Portal lấy dữ liệu như thế nào?

Khi mở một màn hình học vụ, trình duyệt gọi API, nhận dữ liệu rồi dùng mã JavaScript của portal để hiển thị lên giao diện. Endpoint là địa chỉ cụ thể nhận một yêu cầu, chẳng hạn yêu cầu lấy lịch học trong một khoảng ngày.

Các endpoint đã quan sát đều nằm trên `https://qldtbeta.phenikaa-uni.edu.vn`. Đây là **API nội bộ của portal**: giao diện của trường đang sử dụng chúng, nhưng chưa tìm thấy tài liệu chính thức hướng dẫn ứng dụng bên ngoài tích hợp. Không nên mặc định chúng có cam kết giữ nguyên đường dẫn hoặc cấu trúc dữ liệu.

### Dữ liệu không được gửi dưới dạng JSON thông thường

Các yêu cầu chính dùng phương thức POST, với phần thân dạng form và một trường tên `A`. Phản hồi là JSON, nhưng phần dữ liệu bên trong nằm ở `Data.B`, có kiểu chuỗi.

Mã frontend cho thấy luồng xử lý sau:

```text
Tham số cần tra cứu → AE(...) → trường A trong yêu cầu HTTP
Phản hồi HTTP      → Data.B  → AD(...) → dữ liệu để hiển thị
```

`AE` và `AD` là tên hàm trong mã portal. Đã thấy chúng được dùng để chuyển đổi dữ liệu, nhưng chưa phân tích đầy đủ cách hoạt động hoặc viết lại chúng cho AMS. Vì thế, không thể chỉ sao chép đường dẫn rồi gửi một JSON gồm mã sinh viên, ngày bắt đầu và ngày kết thúc để coi là đã kết nối được.

Phần tên trường nghiệp vụ trong phụ lục có hai nguồn: mã frontend và dữ liệu đã được portal xử lý trong bộ nhớ trình duyệt. Chúng không phải nội dung JSON đọc trực tiếp từ `Data.B` trên đường truyền. Sự khác biệt này quan trọng khi viết bộ đọc dữ liệu sau này.

### HTTP 200 chưa đủ để kết luận lấy dữ liệu thành công

Các API chính đều trả HTTP 200 trong lượt kiểm tra. Mã này cho biết yêu cầu đã được máy chủ xử lý ở mức HTTP; vẫn cần kiểm tra nội dung trả về để biết nghiệp vụ có thành công hay không.

Phản hồi quan sát được có các trường `Success`, `Message`, `Data`, `Pager` và `Id`. Lượt khảo sát chỉ giữ cấu trúc và kiểu dữ liệu, không lưu giá trị nghiệp vụ, nên không dùng HTTP 200 để suy ra tất cả phản hồi đều có `Success=true` hoặc đã trả đủ bản ghi.

Điều cần tránh là thấy một danh sách trống rồi xóa toàn bộ lịch cũ trong AMS. Danh sách đó có thể thật sự không có buổi học, nhưng cũng có thể thiếu trang dữ liệu, sai phạm vi tra cứu hoặc không đọc được phần dữ liệu bên trong. Bộ kết nối phải phân biệt được các trường hợp này trước khi đánh dấu một lịch đã bị hủy.

## 3. Đăng nhập và giữ phiên

### Đã biết gì về đăng nhập?

Trang gốc trả HTTP 200 rồi dùng JavaScript chuyển tới `/conggiangvien/login.aspx`. Trang đăng nhập có form nội bộ và liên kết Microsoft. Có dấu hiệu ASP.NET Web Forms trong mã trang, nhưng chưa xác định endpoint xử lý cách đăng nhập nội bộ.

Liên kết Microsoft có cấu hình OAuth 2.0 authorization code và OpenID Connect (OIDC). Hiểu ngắn gọn, đây là cách một ứng dụng chuyển người dùng sang hệ thống nhận diện như Microsoft, rồi nhận kết quả xác thực để tiếp tục đăng nhập. Thông tin quan sát được là `response_type=code`, `response_mode=form_post` và scope `openid profile`; chưa ghi nhận đầy đủ các bước trao đổi sau khi đăng nhập.

Cookie của Microsoft và `sso.phenikaa-uni.edu.vn` cũng xuất hiện. Điều này phù hợp với việc sử dụng đăng nhập một lần (SSO), nhưng chưa đủ để mô tả chính xác toàn bộ luồng xác thực của trường. Tài khoản được kiểm tra vào khu vực sinh viên thông qua `conggiangvien/index.aspx`; tên thư mục không có nghĩa tài khoản được cấp quyền giảng viên.

### Bearer token và cookie có vai trò gì?

Bearer token là một giá trị được gửi trong header `Authorization` để máy chủ xác thực yêu cầu. Cần bảo vệ giá trị này như thông tin đăng nhập, không đưa vào log hoặc tài liệu. Cookie là dữ liệu trình duyệt lưu và tự gửi theo phạm vi tên miền, đường dẫn cùng các quy tắc bảo mật của nó.

Trong các yêu cầu học vụ đã quan sát, trình duyệt gửi cả Bearer token lẫn cookie. Chưa thử bỏ một trong hai rồi gọi lại API, nên chưa thể nói máy chủ bắt buộc cả hai hay chỉ dùng một phần. Cũng chưa xác định cookie nào là yếu tố quyết định phiên đăng nhập.

Đã kiểm tra lần lượt: mở lịch học, tải lại trang, chuyển sang màn hình khác rồi quay về lịch học. Các yêu cầu lịch vẫn trả HTTP 200 và không cần đăng nhập lại. Token trước và sau các thao tác này giống nhau; phép so sánh diễn ra trong bộ nhớ, chỉ ghi kết quả đúng/sai, không giữ token hoặc mã băm của token.

Kết quả đó chỉ cho biết phiên tiếp tục dùng được trong lượt kiểm tra. Nó chưa trả lời được phiên hết hạn sau bao lâu, có thể sử dụng từ máy chủ AMS hay không, hoặc có được tự gia hạn hay không.

### Những gì chưa xác minh về gia hạn và đăng xuất

Không thấy yêu cầu gia hạn token hoặc đăng nhập lại âm thầm trong khoảng quan sát sau login. Liên kết Microsoft đã đọc cũng không có scope `offline_access`. Cả hai điều này đều chưa đủ để kết luận trường không có cơ chế gia hạn: việc đó có thể diễn ra ở thời điểm khác hoặc phía máy chủ.

Khi bấm đăng xuất, trình duyệt chuyển tới `login.microsoftonline.com/common/oauth2/v2.0/logout`. Chưa xác nhận đã quay lại trang login hoặc phiên cũ bị vô hiệu hóa phía server. Không gọi lại API bằng phiên cũ để kiểm tra việc này.

Với AMS, hướng xử lý dự kiến là không lưu mật khẩu Phenikaa. Nếu phiên được phép sử dụng hết hạn, ngừng đồng bộ và yêu cầu người dùng kết nối lại. Việc tự gia hạn chỉ được triển khai sau khi hiểu và xác nhận cách sử dụng hợp lệ của cơ chế nguồn.

### Cách đọc các thuộc tính cookie

Tài liệu chỉ giữ metadata của cookie, tức là tên và thuộc tính, không giữ giá trị:

- `HttpOnly`: JavaScript trong trang không đọc được cookie qua `document.cookie`; trình duyệt vẫn có thể gửi cookie theo yêu cầu HTTP.
- `Secure`: cookie chỉ được gửi qua kết nối HTTPS. Thiếu cờ này không có nghĩa đã xảy ra rò rỉ, nhưng là điểm cần lưu ý về cấu hình.
- `SameSite`: giới hạn khi nào cookie được gửi trong các tình huống liên quan nhiều website. `Lax` hạn chế hơn `None`; đây không phải cơ chế thay thế hoàn toàn bảo vệ CSRF.
- Cookie dạng session không có thời điểm hết hạn cố định trong metadata đã đọc. Không được dùng thông tin đó để suy ra thời hạn phiên phía máy chủ, hoặc cam kết cứ đóng trình duyệt là phiên sẽ mất.

| Cookie | Tên miền và đường dẫn | Thời hạn trong trình duyệt | HttpOnly | Secure | SameSite |
| --- | --- | --- | --- | --- | --- |
| `__AntiXsrfToken` | `qldtbeta.phenikaa-uni.edu.vn`, `/` | Session | Có | Không | Lax |
| Một cookie có tên động, đã ẩn tên | `qldtbeta.phenikaa-uni.edu.vn`, `/` | Session | Không | Không | Lax |
| `MSISAuth`, `MSISSignOut`, `MSISAuthenticated`, `MSISLoopDetectionCookie` | `sso.phenikaa-uni.edu.vn`, `/adfs` | Session | Có | Có | None |

Phía Microsoft còn thấy `ESTSAUTH` dạng session và `ESTSAUTHPERSISTENT` có thời điểm hết hạn. Cả hai có HttpOnly, Secure và SameSite=None. Không dùng thời hạn cookie Microsoft để kết luận thời hạn phiên QLĐT.

Các cờ của cookie nguồn chỉ là kết quả quan sát, không phải cấu hình để sao chép sang AMS. Cookie `AMS_SESSION` và bảo vệ CSRF của AMS phải được giữ nguyên. CSRF là tình huống một website khác khiến trình duyệt gửi thao tác ngoài ý muốn bằng phiên đang đăng nhập; đây là lý do không thể chỉ dựa vào việc người dùng đã có cookie hợp lệ.

## 4. Kết quả theo từng nhóm dữ liệu

### Hồ sơ sinh viên

Đã mở được hồ sơ cá nhân và quan sát yêu cầu lấy thông tin hồ sơ. Mã frontend cho thấy yêu cầu sử dụng người dùng đang đăng nhập. Chưa xác nhận đầy đủ kiểu dữ liệu và trường nào có thể thiếu trong phần hồ sơ đã xử lý.

Khi nhập vào AMS, hồ sơ phải thuộc tài khoản AMS đang kết nối. Không để client gửi một mã người học tùy ý để truy cập hồ sơ khác, và không lấy thêm thông tin cá nhân chỉ vì portal có trả về.

### Lịch học

Đã có dữ liệu lịch trên giao diện và trong bộ nhớ sau xử lý của portal. Các trường quan trọng gồm mã lịch, mã lớp, ngày học, giờ/phút bắt đầu và kết thúc, tiết học, phòng và giảng viên. Một số trường có giá trị `null`, tức là trong mẫu đó không có giá trị; không được tự thay bằng 0 hoặc chuỗi rỗng nếu làm thay đổi ý nghĩa.

Giao diện hiển thị ngày dạng DD/MM/YYYY và múi giờ GMT+7. Khi chuyển sang kiểu thời gian của AMS, cần xác minh cách đọc chuỗi ngày và quy tắc giờ, rồi mới chuyển sang thời điểm chuẩn. Nếu đọc nhầm ngày/tháng hoặc coi giờ địa phương là UTC, lịch có thể bị lệch dù API vẫn trả dữ liệu bình thường.

Còn có yêu cầu riêng cho lớp chưa có lịch chi tiết. Danh sách này rỗng trong lượt quan sát, nên chưa biết cấu trúc từng bản ghi. Không được tự tạo giờ học cho lớp chưa được công bố lịch.

### Lịch thi

Đã xác định yêu cầu lấy danh sách học kỳ và yêu cầu lấy lịch thi. Sau khi chọn một học kỳ có sẵn trên giao diện rồi bấm xem, API trả 200 nhưng hai bảng thi không có hàng, cũng không có thông báo rỗng rõ ràng.

Vì vậy, chưa thể khẳng định học kỳ đó không có kỳ thi, hoặc xác nhận cấu trúc một bản ghi từ API lịch thi riêng. Tuy nhiên, lịch cá nhân có bản ghi được phân loại là thi; các bản ghi này đã được dùng để so sánh mã qua lần tải lại trang. Hai nguồn này cần phân biệt để tránh báo rằng API thi riêng đã được kiểm chứng đầy đủ.

### Điểm và kết quả học tập

Đã mở được bảng điểm và xác định yêu cầu lấy kết quả học tập, cùng một số yêu cầu phụ lấy chương trình, thời gian đào tạo và kết quả tích lũy. Mã frontend có các trường điểm, điểm quy đổi, đánh giá, lần học và lần thi.

Chưa xác nhận đủ kiểu dữ liệu và ý nghĩa của từng trường để tính GPA. Điểm chưa có không phải điểm 0; lần thi không phải lần học; có điểm hệ 4 cũng chưa đủ để kết luận môn đó được tính vào GPA. Các quyết định này cần quy chế rõ ràng, không suy từ màu hoặc nhãn trên giao diện.

### Chương trình đào tạo

Đã có dữ liệu môn trong chương trình, tín chỉ, một số thuộc tính môn và thời gian học dự kiến. Portal còn gọi các yêu cầu riêng cho nhóm bắt buộc, nhóm tự chọn và phân bổ theo môn. Mở một màn hình có thể tạo nhiều yêu cầu, không chỉ một lần gọi API.

Mã frontend có cả tín chỉ học tập và tín chỉ dùng tính học phí. AMS phải dùng đúng loại theo mục đích. Cũng đã thấy tên trường về số tín chỉ và số môn phải hoàn thành trong một nhóm; cần xác minh rõ ý nghĩa trước khi bổ sung ràng buộc vào database.

Phần quan hệ môn mới được xem từ mã frontend, chưa quan sát yêu cầu thực tế trả quan hệ tiên quyết. Có cột thông tin quan hệ trong dữ liệu chương trình, nhưng chưa đủ để chuyển thành các điều kiện học trước hoặc học song hành.

## 5. Làm sao nhận ra vẫn là cùng một buổi học?

Đây là vấn đề quan trọng với chức năng phát hiện thay đổi lịch. AMS cần biết một buổi học vừa đổi phòng vẫn là buổi cũ, không phải buổi cũ bị xóa rồi xuất hiện một buổi mới.

Ví dụ giả định: một buổi chuyển từ phòng A sang phòng B. Nếu dùng ngày + giờ + phòng làm mã định danh, mã sẽ đổi theo phòng. Khi đó hệ thống dễ tạo hai sự kiện thay vì cập nhật sự kiện cũ. Mã định danh cần tách khỏi các thông tin có thể thay đổi này.

### Kết quả so sánh qua lần tải lại trang

Các bản ghi được đối chiếu trong bộ nhớ để tìm lại cùng buổi sau reload. Chỉ ghi tên trường, trường có tồn tại hay không và giá trị có giữ nguyên hay không; không ghi giá trị mã.

| Dữ liệu và trường | Kết quả trong mẫu đã so sánh | Ý nghĩa hiện tại |
| --- | --- | --- |
| Buổi học: `ID` | Thay đổi | Không dùng làm mã ổn định cho AMS |
| Buổi học: `IDLICHHOC` | Giữ nguyên | Có thể xem xét làm mã nguồn, cần kiểm tra thêm phạm vi |
| Lớp: `IDLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_ID` | Giữ nguyên; hai giá trị bằng nhau trong mẫu | Chưa khẳng định hai trường luôn có cùng ý nghĩa |
| Bản ghi thi trong lịch cá nhân: `ID` | Thay đổi | Không dùng làm mã ổn định |
| Bản ghi thi trong lịch cá nhân: `IDLICHHOC` | Giữ nguyên | Có thể xem xét, chưa biết phạm vi theo ca hoặc lần thi |
| Bản ghi thi: hai trường mã lớp kể trên | Không có trong mẫu | Chưa đủ thông tin để nối với lần học tương ứng |

**Giữ nguyên khi tải lại trang không đồng nghĩa giữ nguyên khi trường đổi lịch.** Chưa có phép kiểm tra với thay đổi phòng/giờ thật, và không tự sửa portal để tạo tình huống đó. Cũng chưa biết một `IDLICHHOC` đại diện một buổi riêng hay một lịch lặp nhiều tuần. Nếu mã được dùng chung cho nhiều buổi, dùng riêng nó làm khóa sẽ khiến các buổi ghi đè lên nhau.

Mã môn `DAOTAO_HOCPHAN_ID` đã xuất hiện trong dữ liệu chương trình, nhưng chưa so qua reload. Mã học kỳ và khóa của lần học cũng chưa được kiểm chứng độ ổn định. Không nên coi mọi trường có chữ ID đều dùng được ngay làm khóa lưu trữ.

### Hướng lưu mã nguồn trong AMS

AMS tiếp tục dùng UUID nội bộ cho mỗi đối tượng. UUID là mã định danh do AMS quản lý, không phụ thuộc mã sinh viên hay mã lịch của trường. Đề xuất là giữ mã của trường trong một bảng đối chiếu riêng, để biết mã nguồn nào ứng với bản ghi AMS nào. Đây mới là ý tưởng thiết kế, chưa có bảng mới trong database.

Bảng đối chiếu cần biết dữ liệu thuộc hồ sơ nào, đến từ nguồn và tài khoản nào, là loại đối tượng gì, và mã đó chỉ duy nhất trong phạm vi nào. Không chỉ lưu mỗi chuỗi ID, vì cùng một chuỗi có thể được dùng ở nhiều loại dữ liệu hoặc tài khoản.

Khi kết nối lại, credential có thể thay nhưng vẫn là cùng tài khoản trường. Cần xác minh điều đó để dùng lại các bản ghi AMS cũ, tránh nhập lại thành dữ liệu trùng. Credential ở đây là thông tin dùng để xác thực, như token hoặc cookie phiên, không phải mã định danh hồ sơ.

Thiết kế Phase 2 vẫn được giữ:

- Buổi học: `(profile_id, section_id, occurrence_key)`.
- Lịch thi: `(profile_id, student_course_id, occurrence_key)`.

`occurrence_key` là khóa phân biệt từng buổi hoặc từng lần thi trong phạm vi tương ứng. Cách tạo khóa từ dữ liệu Phenikaa chưa được chốt. Nếu nguồn không có mã đủ ổn định, cần bước đối soát; trường hợp chưa ghép chắc chắn thì giữ trạng thái chưa xác định, không tự đoán rồi phát thông báo thay đổi sai.

## 6. Dữ liệu nguồn có phù hợp model AMS hiện tại không?

Nhìn chung, cách tách môn học, lớp mở, lần học, buổi học và kỳ thi của Phase 2 vẫn phù hợp. Các khái niệm này khác nhau: một môn có thể mở nhiều lớp, một lớp có nhiều buổi, còn sinh viên có thể học lại cùng môn ở một lần học khác.

Các tên trường của Phenikaa phải được xử lý ở phần kết nối, không đưa thẳng vào model dùng chung của AMS. Khi đổi nguồn dữ liệu, phần còn lại của ứng dụng vẫn cần hiểu cùng các khái niệm học vụ. Chi tiết model hiện tại xem tại [tài liệu database](database-model.md).

| Model AMS | Dữ liệu nguồn có thể sử dụng | Việc cần làm rõ trước khi nhập |
| --- | --- | --- |
| `StudentProfile` — hồ sơ học vụ | Hồ sơ cá nhân, chương trình, khóa học | Chỉ lấy trường cần thiết; gắn đúng user AMS và tài khoản trường |
| `Semester` — học kỳ | Mã thời gian đào tạo, năm học, học kỳ, đợt | Phân biệt kỳ với đợt; không mặc định một năm chỉ có hai kỳ |
| `Course` — môn học | Mã môn, tên môn, tín chỉ học tập | Dùng đúng loại tín chỉ; lưu số thập phân chính xác thay vì số thực làm tròn tùy ý |
| `ClassSection` — lớp mở | Mã và tên lớp học phần | Xác nhận lớp thuộc môn và học kỳ nào; phạm vi duy nhất của mã lớp |
| `StudentCourse` — một lần học | Lần học, môn, kỳ, tín chỉ, có thể có lớp | Không dùng lần thi thay cho lần học, không ghi đè mất lần học lại |
| `AcademicResult` — kết quả của lần học | Điểm số, điểm chữ/quy đổi, đánh giá | Xác nhận trạng thái đạt/chưa đạt/đang học và cách tính tín chỉ đạt |
| `ClassSession` — một buổi học | Mã lịch, ngày, giờ, phòng, giảng viên | Khóa ổn định, ngày giờ và các trường chưa công bố |
| `Exam` — lịch thi | Bản ghi thi và thông tin ca/lần thi | Cách nối với lần học; mẫu từ lịch cá nhân chưa có mã lớp |
| `Curriculum` — chương trình đào tạo | Mã tổ chức chương trình, mã/tên chương trình | Xác nhận khóa áp dụng và phiên bản; không suy hai loại mã là một |
| `CurriculumGroup` — nhóm môn | Nhóm bắt buộc/tự chọn, yêu cầu tín chỉ/số môn | Kiểm tra điều kiện hoàn thành nhóm có cần cả số môn hay không |
| `CurriculumCourse` — môn trong chương trình | Môn, tín chỉ, thuộc tính và kỳ dự kiến | Phân biệt bắt buộc với tự chọn; không coi giá trị thiếu là không bắt buộc |
| `CoursePrerequisite` — điều kiện học trước | Tên loại quan hệ, toán tử và mức điều kiện trong mã frontend | Chưa có dữ liệu quan hệ được kiểm chứng đủ để chuyển thành quy tắc |
| `GradingPolicy` — chính sách điểm | Một số trường về thang điểm/cách tính trong mã frontend | Cần quy chế và phiên bản áp dụng, không suy từ một bảng điểm |

### Những điểm có thể cần mở rộng model

Chưa có thay đổi database trong khảo sát này. Các điểm sau cần được xác minh trước khi quyết định sửa model:

**Yêu cầu số môn trong nhóm.** `CurriculumGroup` hiện lưu số tín chỉ tối thiểu. Nếu quy định yêu cầu đồng thời đủ tín chỉ và đủ số môn, chỉ một trường tín chỉ sẽ không diễn tả hết điều kiện. Tên trường `SOHOCPHANQUYDINH` gợi ý khả năng này, nhưng vẫn cần kiểm tra ý nghĩa thực tế.

**Điều kiện tiên quyết phức tạp hơn.** Model hiện phân biệt học trước và học song hành; nhiều điều kiện được hiểu là phải thỏa tất cả. Nếu chương trình cho phép “đã học môn A hoặc môn B”, đó là điều kiện OR, khác với “phải học cả A và B”. Môn tương đương hoặc yêu cầu điểm tối thiểu cũng chưa được model hiện tại hỗ trợ. Không nên biến mọi quan hệ của nguồn thành một cặp môn tiên quyết rồi bỏ mất các điều kiện đi kèm.

**Lần học và lần thi.** Một lần học có thể có nhiều lần thi. `AcademicResult` hiện giữ kết quả hiện tại của lần học, không phải lịch sử mọi lần thi. Nếu chức năng sau này cần lịch sử đó, phải thiết kế rõ thay vì ghi đè các điểm vào cùng một bản ghi.

**Liên kết lịch thi với lần học.** `Exam` đang gắn với `StudentCourse`. Mẫu thi trong lịch cá nhân chưa đủ thông tin để xác định liên kết này. Không tạo một lần học giả chỉ để thỏa khóa ngoại; cần thêm dữ liệu hoặc giữ bản ghi ở bước chờ đối soát.

**Quy chế tính điểm.** Chưa xác định nguồn quy chế và phiên bản dùng cho từng chương trình/khóa. Cùng một bảng điểm chưa chắc đủ để suy ra cách chọn điểm học lại, môn được tính GPA hoặc ngưỡng xếp loại. AMS không được tự điền các ngưỡng này rồi trình bày như quy định của trường.

## 7. Có thể nhận thay đổi lịch ngay lập tức không?

Push là cách nguồn chủ động gửi thông tin khi có thay đổi. Polling là cách AMS tự hỏi lại nguồn theo chu kỳ. Muốn gọi là cập nhật tức thời dựa trên push, cần biết nguồn thật sự phát sự kiện học vụ và AMS được phép nhận các sự kiện đó.

### Những gì đã thấy

Có kết nối WebSocket tới `wss://api-apis.com/socket.io/`, với các tên tham số `EIO`, `sid`, `transport`. WebSocket cho phép duy trì kết nối để hai bên gửi dữ liệu mà không phải tạo yêu cầu HTTP mới cho từng tin. Socket.IO là thư viện hỗ trợ kiểu trao đổi này. Có frame gửi/nhận, nhưng không đọc hoặc lưu nội dung riêng tư của chúng.

Mã portal có phần `nodeChat`, nên kết nối đó có thể phục vụ chat hoặc thông báo chung. Chưa có bằng chứng cho thấy nó phát sự kiện đổi lịch hoặc đổi điểm.

Portal cũng tải thư viện Firebase messaging và mã xử lý thông báo. Chưa cấp quyền nhận thông báo, lấy push token hoặc kiểm tra một thông báo học vụ thật. Có mã hỗ trợ không đồng nghĩa chức năng đã hoạt động cho tài khoản hoặc cho trường hợp AMS cần.

Không thấy phản hồi SSE trong lượt quan sát. SSE là một cách khác để máy chủ gửi dữ liệu một chiều về trình duyệt. Webhook — nguồn gọi ngược tới một địa chỉ của AMS — và cơ chế chờ yêu cầu lâu để nhận tin mới cũng chưa được xác minh. Không kết luận chúng không tồn tại chỉ vì chưa thấy.

### Kết quả quan sát khi để trang lịch mở

Trong **143 giây**, không thấy API lịch học được gọi lặp. Có yêu cầu tải mẫu HTML của lịch lặp khoảng **20 giây**, nhưng tải lại phần giao diện không đồng nghĩa tải lại dữ liệu lịch. Mã portal còn có bộ hẹn giờ kiểm tra phiên bản cấu hình mỗi 300000 ms, tức 5 phút; đây cũng không phải chu kỳ cập nhật học vụ.

Khoảng quan sát này chỉ đủ để nói chưa thấy polling API lịch trong hơn hai phút đó, không đủ chứng minh portal không có polling dài hơn hoặc push.

### Phương án dự kiến cho AMS

Nếu chưa có push học vụ sử dụng được, AMS sẽ cần polling. Có thể gọi đây là cập nhật định kỳ hoặc gần thời gian thực; dữ liệu vẫn có độ trễ tối đa theo chu kỳ và thời gian xử lý, không nên hứa là cập nhật ngay lập tức.

Mốc ban đầu để thảo luận, chưa phải lịch chạy đã triển khai hoặc giới hạn do trường công bố:

| Nhóm dữ liệu | Chu kỳ dự kiến | Lý do đề xuất |
| --- | --- | --- |
| Lịch học, lịch thi | 30–60 phút | Thay đổi có ảnh hưởng trực tiếp tới việc đi học/thi, cần kiểm tra thường xuyên hơn |
| Điểm | 12–24 giờ | Chưa có yêu cầu theo dõi từng phút; giảm số yêu cầu không cần thiết |
| Hồ sơ, chương trình | Hàng tuần hoặc khi người dùng yêu cầu | Không cần tải lại toàn bộ trong mỗi lần kiểm tra lịch |

Các mốc này cần điều chỉnh theo chính sách cho phép và chi phí gọi API thực tế. Nhất là màn hình chương trình có nhiều yêu cầu theo từng môn, không nên ghép nó vào mỗi lần đồng bộ lịch.

Khi triển khai cần tránh nhiều tác vụ chạy cùng lúc cho một tài khoản, lệch nhẹ thời điểm chạy giữa các tài khoản để không dồn tải, và giãn thời gian thử lại khi nguồn lỗi. Hai kỹ thuật sau thường gọi là jitter và backoff. Nếu nguồn yêu cầu chờ qua `Retry-After`, phải tôn trọng; nếu phiên hết hạn, dừng đồng bộ thay vì tiếp tục gọi lỗi liên tục.

## 8. Hướng triển khai phần kết nối

Ưu tiên đầu tiên vẫn là API chính thức nếu trường cung cấp và cho phép sử dụng. Với những gì đã quan sát, hướng tiếp theo đáng thử là một HTTP client gọi API nội bộ và chuyển phản hồi thành dữ liệu có kiểu rõ ràng trong Java. Cách này chỉ được chọn sau khi kiểm chứng được việc xác thực, lớp chuyển đổi `A/B` và cách sử dụng phù hợp.

Chưa có bản thử gọi API độc lập ngoài trình duyệt. Vì thế, đây là hướng đề xuất, chưa phải cách kết nối đã chứng minh hoạt động từ đầu đến cuối. Nếu API không dùng được theo cách phù hợp, mới cân nhắc tự động hóa trình duyệt; đọc HTML là lựa chọn sau cùng khi thật sự cần. Không chọn trình duyệt làm giải pháp production chỉ vì nó thuận tiện cho khảo sát.

### Có cần đổi AcademicPortalClient không?

Hiện tại phương thức là:

```java
AcademicSnapshot fetchSnapshot(StudentConnectionId connectionId);
```

Nó yêu cầu lấy một gói dữ liệu học vụ chung. Tuy nhiên, các API nguồn không có cùng phạm vi: lịch theo khoảng ngày, thi theo kỳ/môn, điểm theo chương trình, chương trình đào tạo lại cần nhiều yêu cầu phụ. Một nhóm có thể đọc được trong khi nhóm khác chưa đọc được.

Đề xuất là tách việc lấy dữ liệu theo từng nhóm: hồ sơ, lịch học, lịch thi, kết quả học tập và chương trình. Mỗi nhóm cần cho biết đã lấy lúc nào, trong phạm vi nào, đầy đủ hay chỉ một phần. Ví dụ, đọc được lịch của một tuần không có nghĩa đã có toàn bộ lịch của học kỳ. Sau đó mới tổng hợp thành dữ liệu dùng trong AMS nếu cần.

Đây là cách chia theo chức năng lấy dữ liệu, đôi khi gọi là capability-oriented interface. Chưa cần tạo nhiều lớp/interface chỉ để theo tên gọi này; chỉ tách khi có luồng xử lý và lỗi riêng cần quản lý.

`AcademicSnapshot` hiện có môn, kỳ, lớp, lần học kèm điểm cơ bản, buổi học và thi; chưa có hồ sơ, chương trình/điều kiện môn, chính sách điểm và trạng thái đầy đủ riêng cho từng nhóm. Contract và code hiện tại vẫn giữ nguyên trong Phase 3, chờ chốt cách đọc nguồn trước khi sửa.

### Việc cần làm trước và trong Phase 4

1. **Kiểm chứng một luồng gọi API độc lập.** Xác nhận cách truy cập được phép, phiên hợp lệ và lớp chuyển đổi `A/B`. Nếu chưa làm được, báo rõ điểm dừng; không trả dữ liệu giả để coi như kết nối xong.
2. **Làm rõ dữ liệu còn thiếu.** Ưu tiên bản ghi từ API thi riêng, chi tiết hồ sơ/điểm, phân trang, kỳ/đợt và phạm vi mã lịch. Với độ ổn định khi lịch thay đổi, cần thay đổi tự nhiên hoặc tài liệu nguồn, không sửa dữ liệu trường để thử.
3. **Chốt cách nối dữ liệu vào AMS.** Xác định mã nguồn, quyền sở hữu hồ sơ và các model cần mở rộng. Phiên đăng nhập phải lưu tách biệt nếu được phép, mã hóa và không xuất hiện trong DTO nghiệp vụ hoặc log. DTO là đối tượng chuyển dữ liệu giữa các phần của ứng dụng, không phải nơi chứa credential.
4. **Kiểm thử phần chuyển đổi trước khi lưu database.** Dùng dữ liệu tổng hợp và máy chủ HTTP giả lập để kiểm tra dữ liệu hợp lệ, rỗng, thiếu một phần, hết phiên và sai cấu trúc. “Giả lập” chỉ phục vụ test, không được trình bày thành một integration thật.
5. **Triển khai nhập dữ liệu trong phạm vi được duyệt.** Phải bảo đảm nhập lại cùng dữ liệu không tạo bản ghi trùng; tính chất này thường gọi là idempotency. Kiểm tra liên kết cha/con, cách xử lý lỗi giữa chừng và phạm vi dữ liệu trước khi cập nhật lịch cũ.

Chưa bắt đầu các bước triển khai trên. Tác vụ đồng bộ định kỳ, phát hiện thay đổi, Google Calendar và email cũng không tự động nằm trong phạm vi chỉ vì được nhắc đến ở thiết kế mục tiêu.

## 9. Các câu hỏi chưa có câu trả lời chắc chắn

| Câu hỏi | Vì sao cần trả lời? | Giới hạn của lượt kiểm tra |
| --- | --- | --- |
| AMS gọi API bằng HTTP client độc lập được không? | Quyết định cách triển khai connector | Chưa xử lý đầy đủ `A/B`, chưa có bản thử ngoài browser |
| Phiên hết hạn/gia hạn thế nào? | Tránh đồng bộ bằng phiên hết hạn; thiết kế kết nối lại | Phiên vẫn dùng được trong lượt ngắn, không thấy gia hạn |
| Đăng xuất đã hủy phiên phía server chưa? | Biết cách thu hồi quyền truy cập | Chỉ thấy chuyển tới trang logout Microsoft |
| `IDLICHHOC` đại diện một buổi hay lịch lặp? | Tránh ghi đè nhiều buổi lên một bản ghi | Chỉ so reload, chưa xác minh phạm vi mã và thay đổi tự nhiên |
| Làm sao nối một lịch thi với đúng lần học? | Model Exam cần StudentCourse tương ứng | Mẫu thi trong lịch cá nhân thiếu mã lớp/khóa lần học đã xác minh |
| Đã lấy đủ danh sách chưa? | Không đánh dấu hủy dựa trên dữ liệu thiếu | Chưa kiểm chứng giới hạn trang, tổng số trang và lỗi nghiệp vụ |
| Điều kiện môn và quy chế điểm được diễn giải ra sao? | Không tính GPA hoặc điều kiện tốt nghiệp sai | Một số tên trường mới thấy trong mã frontend, chưa có quy chế được xác nhận |
| Có push học vụ và quyền tích hợp dài hạn không? | Quyết định cách cập nhật và vận hành | Có hạ tầng thông báo, chưa chứng minh sự kiện cần thiết hoặc chính sách sử dụng |

Các nhận xét chỉ dựa trên một tài khoản và một khoảng thời gian quan sát. Chưa có cơ sở để khái quát cho mọi khóa, chương trình hoặc trạng thái tài khoản. API nội bộ cũng có thể thay đổi mà không báo trước, nên bộ đọc dữ liệu sau này phải phát hiện cấu trúc không còn phù hợp và báo lỗi rõ ràng.

## 10. Cách kiểm tra và giới hạn bảo mật

Lượt đầu kiểm tra giao diện và mã frontend mà trang sử dụng. Lượt sau dùng Chrome với profile tạm riêng ngoài repository; chủ tài khoản tự đăng nhập, rồi mới bật phần theo dõi yêu cầu bằng Playwright/CDP. CDP là giao thức điều khiển và kiểm tra trình duyệt, ở đây chỉ kết nối tới Chrome tạm trên máy local, không dùng profile cá nhân.

Không tự điền mật khẩu, vượt CAPTCHA/MFA, dò endpoint, đổi mã sinh viên hoặc thử tài khoản khác. Chỉ mở các màn hình đọc dữ liệu; không lưu hồ sơ, đăng ký môn hoặc điểm danh.

Script theo dõi loại bỏ dữ liệu nhạy cảm trong bộ nhớ trước khi xuất kết quả. Nó chỉ giữ tên trường, kiểu, đường dẫn không có giá trị query riêng tư và kết quả so sánh. Không tạo HAR (file lưu phiên trao đổi HTTP), bản sao cookie/token, ảnh chụp riêng tư hoặc phản hồi nguyên bản. Đã đóng Chrome nghiên cứu và xóa profile/script tạm sau khảo sát.

Các phép kiểm tra đã thực hiện:

- Mở đủ năm nhóm màn hình và ghi nhận yêu cầu thực tế, không chỉ dựa trên tên API trong mã nguồn.
- Tải lại/chuyển màn hình để kiểm tra phiên và so sánh token trong bộ nhớ.
- So các mã của cùng bản ghi lịch trước/sau tải lại; không giữ giá trị mã.
- Quan sát kết nối WebSocket và để trang lịch mở 143 giây để kiểm tra yêu cầu lặp.
- Kiểm tra script loại bỏ token/cookie value và dữ liệu trong query bằng ví dụ tổng hợp trước khi dùng.

Không quan sát thấy HTTP lỗi từ 400 trở lên của API portal trong lượt này, nhưng chưa kiểm tra hết phiên, học kỳ không hợp lệ hoặc giới hạn tần suất. Không cố tạo lỗi bằng cách gửi yêu cầu bất thường. Có lúc biến `main_doc` còn trỏ tới module trước, nên không dùng cấu trúc đó làm bằng chứng cho module mới; đây là lý do chưa xác nhận đầy đủ dữ liệu hồ sơ và điểm sau xử lý.

Không có code hoặc migration mới trong Phase 3. Kiểm thử tự động của AMS không gọi Phenikaa thật; adapter hiện vẫn chưa triển khai HTTP. Việc CI của code AMS đạt không thay thế việc kiểm chứng bộ kết nối với nguồn. Bản tài liệu chỉnh sửa này dùng lại kết quả đã có, không thực hiện thêm lượt truy cập tài khoản.

## Phụ lục A. Đường dẫn API và cấu trúc trao đổi

Phần này dành cho lúc tra cứu hoặc viết phần kết nối. Host chung là `https://qldtbeta.phenikaa-uni.edu.vn`; ghép host với đường dẫn trong bảng để có địa chỉ đầy đủ. Những chuỗi dài ở cuối đường dẫn là tên action đang được portal sử dụng, không phải token đăng nhập.

### A.1. Các yêu cầu chính

| Nhóm | Phương thức và đường dẫn | Tham số từ mã frontend và kết quả trên giao diện |
| --- | --- | --- |
| Hồ sơ | POST `/sinhvienapi3/api/SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP` | `LayThongTinChiTietHoSo`, `strId` từ tài khoản hiện tại; đã mở hồ sơ, không lưu thay đổi |
| Lịch học | POST `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv` | `LayDSLichCaNhan`, `strQLSV_NguoiHoc_Id`, `strNgayBatDau`, `strNgayKetThuc`; có lịch trên giao diện và trong bộ nhớ sau xử lý |
| Lịch thi | POST `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpFSkoHgokCS4gIikVKSgP` | `LayDSLichThi_KeHoachThi`, người học, `strDaoTao_ThoiGianDaoTao_Id`, `strDaoTao_HocPhan_Id`; mã có nhánh thay action qua `strHam` |
| Điểm | POST `/sinhvienapi3/api/SV_ThongTin_MH/CiQ1EDQgCS4iFSAxAiAPKSAv` | `KetQuaHocTapCaNhan`, người học, `strDaoTao_ChuongTrinh_Id`, thông tin chức năng/người dùng; có bảng điểm trên giao diện |
| Chương trình | POST `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCS4iESkgLx4CFQPP` | Chương trình, bộ lọc, `pageIndex/pageSize`; có danh sách môn và kiểu dữ liệu trong bộ nhớ sau xử lý |

Tên tham số trong cột cuối được đọc từ mã frontend. Trên đường truyền, chúng đã được bọc vào trường `A`, không xuất hiện dưới dạng các trường JSON riêng. Các endpoint trong bảng đều đã có yêu cầu thực tế; chi tiết tham số không đồng nghĩa đã kiểm chứng mọi lựa chọn lọc.

### A.2. Các yêu cầu phụ

Tất cả đường dẫn dưới đây đã được quan sát với POST và HTTP 200. Không cần gọi toàn bộ chúng trong mọi lần đồng bộ; phải chọn theo dữ liệu đang cần.

| Nhóm | Đường dẫn | Vai trò |
| --- | --- | --- |
| Profile | `/sinhvienapi3/api/SV_KeHoach_MH/DSA4BRIKJAkuICIpDykgMQkuEi4P` | Kế hoạch tự nhập hồ sơ |
| Timetable | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4FQoDDS4xCikuLyYCLg0oIikCKSgVKCQ1` | Lớp chưa có lịch chi tiết; lọc theo người học/ngày |
| Exam | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikVKSgP` | Danh sách thời gian/học kỳ |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4FSkuLyYVKC8CKTQuLyYVMygvKQkuIgPP` | Thông tin chương trình |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikJLiIP` | Thời gian đào tạo |
| Grades | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4CiQ1EDQgFSgiKQ00OBUpJC4KKS4o` | Kết quả tích lũy theo khối |
| Curriculum | `/dangkyhocapi3/api/DKH_Chung_MH/DSA4BRICKTQuLyYVMygvKQPP` | Danh sách chương trình |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKBU0AikuLx4FLi8P` | Khối tự chọn |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKAMgNQM0LiIP` | Khối bắt buộc |
| Curriculum | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCS4iESkgLx4CFR4RAwPP` | Phân bổ theo môn; giao diện tự phát nhiều request |

Có thêm GET `/cmsapi/api/CMS_DanhMucThuocTinh/LayDanhSachDuLieuTheoBangDM` để lấy dữ liệu danh mục. Yêu cầu trả 200 JSON, có Bearer và cookie, không có body. Tên query parameter đã ghi nhận: `_`, `dTrangThai`, `strChucNang_Id`, `strMaBangDanhMuc`, `strNguoiThucHien_Id`, `strTieuChiSapXep`. Không giữ giá trị của các tham số này.

Action quan hệ môn `KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eEDQgLwkkCS4iESkgLwPP` mới thấy trong mã frontend, **chưa quan sát yêu cầu thực tế**. Tham số khai báo có tổ chức chương trình, học phần, loại quan hệ và phân trang. Không xếp nó cùng nhóm endpoint đã kiểm chứng ở trên.

### A.3. Những chi tiết cần biết khi gọi API

| Thành phần | Đã quan sát |
| --- | --- |
| Request chính | POST, không có query parameter; body chỉ thấy field `A` |
| Content-Type gửi | `application/x-www-form-urlencoded; charset=UTF-8` — dữ liệu form, không phải JSON body trực tiếp |
| Header liên quan | `accept`, `authorization`, `content-type`, `cookie`, `origin`, `referer` |
| Xác thực | Có Bearer và cookie; chưa thử tập header tối thiểu cần thiết |
| Response | HTTP 200, `application/json` |
| Cấu trúc phản hồi | `Data: { B: string }`, `Message: string`, `Success: boolean`, `Pager: null`, `Id: null` trong mẫu chính |

Đây là mô tả kiểu dữ liệu, không phải một phản hồi mẫu thật. `Pager` ở lớp ngoài chưa đủ để biết dữ liệu trong `B` được phân trang ra sao. Phân trang là việc chia một danh sách dài thành nhiều phần; nếu chỉ đọc phần đầu thì AMS có thể bỏ sót bản ghi.

Mã chương trình đào tạo có `pageIndex/pageSize`, có chỗ đặt số lượng mỗi trang rất lớn. Chưa kiểm tra giới hạn máy chủ và không thử tải hàng loạt để tìm giới hạn. Không sao chép giá trị đó thành mặc định của AMS.

### A.4. Vị trí mã frontend đã đối chiếu

Các module được trang tham chiếu nằm dưới `/conggiangvien/ApisCongSinhVien/modules/`:

- Hồ sơ: `profile/script/tunhaphoso.js`.
- Lịch học: `thoikhoabieu/script/lichgiang.js`.
- Lịch thi: `thoikhoabieu/script/lichthi.js`.
- Điểm: `hoctap/script/diemhoc.js`.
- Chương trình: `hoctap/script/chuongtrinhhoc.js`.

Mẫu HTML lịch lặp trong phép quan sát nằm ở `/congsinhvien/ApisCongSinhVien/modules/thoikhoabieu/html/lichhoc.html`. Đây là đường dẫn đã thấy thực tế, khác tiền tố với các module kể trên; không tự sửa đường dẫn cho giống nhau.

## Phụ lục B. Tên trường dữ liệu để đối chiếu khi viết mapper

Mapper là phần chuyển dữ liệu của nguồn sang model AMS, chẳng hạn chuyển tên trường tiếng Việt của portal thành `startsAt`, `room` hoặc `credits`. Tên và kiểu bên dưới giúp bắt đầu công việc đó, chưa phải hợp đồng dữ liệu được trường cam kết.

### B.1. Lịch cá nhân sau xử lý

Đã kiểm tra mảng `main_doc.LichGiang.dtLichHoc` trong bộ nhớ trình duyệt. Chỉ xem tên trường và kiểu, không lưu giá trị riêng tư.

| Tên trường | Kiểu quan sát |
| --- | --- |
| `ID`, `IDLICHHOC`, `IDLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_ID` | Chuỗi trong mẫu buổi học; bản ghi thi có thể thiếu hai trường mã lớp |
| `TENHOCPHAN`, `TENLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_TEN`, `PHANLOAI` | Chuỗi |
| `NGAYHOC`, `THU`, `THUHOC`, `BUOIHOC` | Chuỗi; giao diện hiển thị ngày DD/MM/YYYY, GMT+7; dạng dữ liệu bên trong B trên đường truyền chưa xác nhận |
| `SOTIET`, `TIETBATDAU`, `TIETKETTHUC`, `GIOBATDAU`, `PHUTBATDAU`, `GIOKETTHUC`, `PHUTKETTHUC` | Số |
| `IDPHONGHOC`, `PHONGHOC_TEN`, `TENPHONGHOC`, `PHONGHOC_MA`, `GIANGVIEN` | Chuỗi trong mẫu |
| `BAIHOC`, `NGAYBATDAU`, `NGAYKETTHUC`, `CATHI` | `null` trong mẫu buổi học, chưa biết ở các trường hợp khác |

Mảng `dtTKBKhongLichChiTiet` rỗng ở lần kiểm tra nên chưa có cấu trúc phần tử. Các trường đang `null` trong mẫu không có nghĩa luôn `null` ở mọi tài khoản hoặc mọi loại lịch. Không suy kiểu ngày trong API chỉ từ cách giao diện hiển thị.

### B.2. Môn trong chương trình sau xử lý

Đã kiểm tra mảng `main_doc.ChuongTrinh.dtHocPhan_ChuongTrinh`.

| Tên trường | Kiểu quan sát |
| --- | --- |
| `ID`, `DAOTAO_TOCHUCCHUONGTRINH_ID`, `DAOTAO_CHUONGTRINH_MA/TEN`, `DAOTAO_HOCPHAN_ID/MA/TEN` | Chuỗi; dấu `/` viết gọn các trường cùng tiền tố, ví dụ `_MA/TEN` là hai trường `_MA` và `_TEN` |
| `HOCTRINHAPDUNGHOCTAP`, `HOCTRINHAPDUNGTINHHOCPHI`, `LAMONTINHDIEMTHEOCHUONGTRINH` | Số |
| `DAOTAO_THOIGIAN_KEHOACH_ID`, `DAOTAO_THOIGIAN_KEHOACH`, `DAOTAO_THOIGIAN_KEHOACH_NAM` | Chuỗi |
| Các trường ID, nhãn, năm của thời gian thực tế | `null` hoặc chuỗi |
| Các trường kỳ/đợt theo kế hoạch và thực tế | `null` trong mẫu; ý nghĩa chưa chốt |
| `THUOCTINHHOCPHAN_ID/TEN/MA`, `KHOIKIENTHUC` | Chuỗi |
| `THONGTINQUANHEHOCPHAN` | `null` hoặc chuỗi; chưa chuyển thành các quan hệ điều kiện học |

### B.3. Những trường còn chủ yếu dựa trên mã frontend

Các tên dưới đây hữu ích để tìm đúng chỗ xử lý, nhưng cần dữ liệu được kiểm chứng hoặc quy chế nguồn trước khi dùng để quyết định nghiệp vụ.

| Nhóm AMS | Tên trường tìm thấy | Cần chú ý |
| --- | --- | --- |
| Hồ sơ | `QLSV_NGUOIHOC_MASO`, nhãn chương trình/khóa | Chưa xác nhận đủ kiểu và các trường có thể thiếu; không dùng mã sinh viên làm khóa chính trong database AMS |
| Học kỳ | `DAOTAO_THOIGIANDAOTAO_ID`, `NAMHOC`, `HOCKY`, `DOTHOC` | Chưa chốt quan hệ giữa học kỳ và đợt học |
| Lớp mở | `MA_LOPHOCPHAN` | Đã có một số ID lớp trong lịch, nhưng cách ghép đủ môn/kỳ/lớp còn cần kiểm tra |
| Lần học/lần thi | `LANHOC`, `LANTHI`, `CATHI` | Không dùng thay thế lẫn nhau; `CATHI` có thể null trong bản ghi buổi học |
| Kết quả | `DIEM`, `DIEMQUYDOI`, `DIEMQUYDOI_TEN`, `DANHGIA_TEN`, `KETQUA` | Chưa xác nhận đầy đủ thang điểm, trạng thái và cách tính tín chỉ đạt |
| Nhóm môn | `SOTINCHIQUYDINH`, `SOHOCPHANQUYDINH` | Cần biết điều kiện yêu cầu tín chỉ, số môn hay cả hai |
| Quan hệ môn | `LOAIQUANHE_TEN`, `TOANTU_TEN`, `GIATRIDIEUKIEN`, `MUCDIEUKIEN_TEN` | Chưa xác định cách áp dụng loại quan hệ, toán tử và mức điều kiện |
| Chính sách điểm | `THANGDIEM_MA`, `THUOCTINHLANTINH` | Chưa có quy chế/phiên bản nguồn được xác nhận |

Riêng `LAMONTINHDIEMTHEOCHUONGTRINH` đã thấy có kiểu số trong dữ liệu chương trình sau xử lý. Điều đó chưa đủ để tự đặt `includedInGpa=true`: còn cần biết ý nghĩa giá trị và chính sách áp dụng. Tương tự, tên giống nhau giữa hai API chưa bảo đảm chúng dùng cùng phạm vi hoặc cùng loại ID.

## Phase 4A: kiểm chứng HTTP và giao thức A/B

### Kết quả và phạm vi

Ngày 21/09/2026, đã dùng HTTP client Java đọc lịch cá nhân từ API nội bộ Phenikaa bằng phiên do chủ tài khoản tự đăng nhập. Java tự tạo request, đọc phản hồi, giải mã `Data.B` và chuyển từng bản ghi sang model có kiểu. Không lấy nội dung lịch từ DOM, tức là không đọc các ô đang hiển thị trên trang web để thay cho việc gọi API.

Đây là nền kết nối, chưa phải chức năng đồng bộ hoàn chỉnh. Chưa có màn hình kết nối tài khoản Phenikaa trong AMS, chưa lưu phiên hoặc lịch vào database, chưa chạy định kỳ và chưa phát hiện thay đổi. Hồ sơ, API thi riêng, điểm và chương trình đào tạo chưa được triển khai trong phase này.

Các mức xác nhận dùng trong phần này:

- **VERIFIED — đã kiểm chứng:** có lần chạy thực tế hoặc test cụ thể; giới hạn của lần kiểm tra được ghi kèm.
- **OBSERVED — đã quan sát:** thấy trong mã hoặc mẫu phản hồi, chưa xem là quy tắc cho mọi tài khoản.
- **UNKNOWN — chưa rõ:** thiếu bằng chứng để kết luận. Viết xong code không làm một điểm chưa rõ trở thành đã kiểm chứng.
- **PROPOSED — đề xuất:** phương án cho bước sau, chưa phải hành vi đang có trong ứng dụng.

### AE và AD thực sự làm gì?

**VERIFIED:** hai hàm dùng XOR trên từng đơn vị ký tự UTF-16, kết hợp UTF-8 và Base64. Không thấy bước nén hoặc AES trong hai hàm này.

```text
AE: chuỗi JSON → XOR với khóa lặp → byte UTF-8 → Base64
AD: Base64 → byte UTF-8 → chuỗi → XOR với khóa lặp → chuỗi JSON
```

XOR là phép toán trên các bit; dùng lại cùng khóa sẽ đảo được phép biến đổi. “Khóa lặp” nghĩa là khi đọc hết khóa thì quay về ký tự đầu tiên. UTF-16 quan trọng vì JavaScript xử lý chuỗi theo đơn vị 16 bit: một emoji có thể chiếm hai đơn vị, không chỉ một. Java dùng cùng cách tính trong `PhenikaaPayloadCodec`, thay vì XOR trực tiếp các byte UTF-8.

Base64 chỉ đổi cách biểu diễn dữ liệu thành chuỗi. Toàn bộ lớp A/B này là cách làm khó đọc dữ liệu trực tiếp, **không phải lớp mã hóa đủ để bảo vệ credential**. Vẫn cần HTTPS, bảo vệ bộ nhớ và không ghi payload vào log.

**OBSERVED:** khóa gửi request lấy từ phần sau dấu `/` trong `action`. Với lịch cá nhân, đó là `DSA4BRINKCIpAiAPKSAv`, một phần đường dẫn công khai, không phải token. Khóa đọc phản hồi là tham số `iM`; mã portal lấy từ `edu.system.iM` rồi đưa vào request. **UNKNOWN:** chưa xác định đầy đủ nơi tạo giá trị `iM`, thời hạn và quan hệ của nó với phiên. Vì vậy, AMS nhận giá trị từ ngữ cảnh phiên đang có, không chép một giá trị thật vào source và không tự sinh giá trị thay thế.

Codec được đối chiếu trong bộ nhớ với chính AE/AD của portal bằng dữ liệu giả, gồm tiếng Việt, emoji, mảng và `null`. Test chỉ lưu những vector giả này; không lưu chuỗi A/B từ tài khoản thật. Bộ đọc từ chối Base64 không hợp lệ, byte UTF-8 lỗi và dữ liệu vượt giới hạn. Nếu giải mã ra chuỗi không phải JSON hợp lệ, client báo `DECODE_ERROR`, không cố sửa chuỗi để đọc tiếp.

### HTTP client và phiên được tách như thế nào?

`PhenikaaHttpTransport` dùng HTTP client của Java 21. Constructor dùng trong ứng dụng chỉ cho phép host HTTPS cố định của portal; không có tham số URL do người dùng nhập. Constructor dành cho test chỉ mở thêm địa chỉ loopback `127.0.0.1`, để test offline không gọi ra Internet.

Transport nhận giới hạn thời gian kết nối, thời gian nhận toàn bộ phản hồi và kích thước tối đa qua constructor. Lượt kiểm chứng dùng lần lượt 8 giây, 15 giây và 2 MiB. Đây là cấu hình của lượt thử, không phải SLA của portal. Giới hạn kích thước được kiểm tra ngay trong lúc nhận dữ liệu, kể cả khi máy chủ không khai báo trước độ dài; giới hạn thời gian vẫn áp dụng khi đã nhận header nhưng phần thân bị treo.

Client không tự đi theo redirect và không chuyển credential sang địa chỉ khác. Nó không tự retry khi lỗi xác thực. Khoảng lịch mỗi lần gọi giới hạn từ 1 đến 31 ngày, tính cả hai đầu; chưa có thuật toán chia khoảng dài thành nhiều lần đọc.

`PhenikaaSession` giữ Bearer, cookie tùy chọn, khóa đọc phản hồi và ngữ cảnh người học/chức năng riêng với dữ liệu lịch. Nó chỉ sống trong bộ nhớ, không phải entity, không được trả từ controller và không nằm trong snapshot. `close()` xóa các mảng ký tự mà đối tượng sở hữu. Cần hiểu giới hạn này: Java và thư viện HTTP vẫn có thể tạo bản sao chuỗi trong bộ nhớ; thao tác đó không bảo đảm xóa mọi bản sao khỏi JVM.

Các lớp không ghi request/response vào log. `toString()` của session, envelope và model lịch chỉ trả nhãn đã che nội dung; exception chỉ chứa mã lỗi, không gắn lỗi gốc có thể mang payload. Transport từ chối khởi tạo nếu bật thuộc tính `jdk.httpclient.HttpClient.log`. Khi vận hành vẫn không được bật wire logging, dump heap hoặc công cụ chụp request cho phiên thật chỉ để chẩn đoán: chúng có thể đọc dữ liệu ngoài cơ chế che log của những lớp này.

Chưa có cơ chế cấp một phiên Phenikaa cho user AMS. Mã người học hiện được lấy từ request của chính tài khoản đã đăng nhập, không phải từ tham số API công khai. **PROPOSED:** trước khi mở chức năng kết nối cho người dùng, cần ràng buộc tài khoản nguồn với user AMS phía server; không cho người dùng gửi mã người học tùy ý hoặc tái sử dụng phiên của người khác.

### Lượt kiểm chứng độc lập và vật liệu xác thực cần thiết

API duy nhất dùng để kiểm chứng Java là:

```text
POST /sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
Body: một trường A
```

Chủ tài khoản tự đăng nhập trong profile trình duyệt tạm ngoài repository. Công cụ local lấy đúng thông tin phiên của request lịch và bàn giao một lần cho Java qua loopback. Dữ liệu bàn giao được mã hóa và kiểm tra chữ ký; không truyền credential trên dòng lệnh, không ghi session ra file và không lưu raw response. Công cụ này chỉ phục vụ kiểm chứng, không phải endpoint hoặc dịch vụ mới của AMS. Sau lượt thử đã đóng trình duyệt, xóa profile/helper tạm và giải phóng phiên trong bộ nhớ. Việc xóa profile local không có nghĩa token đã bị thu hồi tại máy chủ Phenikaa.

**VERIFIED trong mẫu tài khoản và khoảng ngày đã thử:**

| Cách gọi | Kết quả |
| --- | --- |
| Bearer + cookie + Origin/Referer như request nguồn | Request thành công, nghiệp vụ thành công, giải mã và mapping thành công; có bản ghi |
| Bỏ cookie, giữ các thành phần còn lại | Vẫn thành công đủ các bước trên |
| Bỏ Bearer, giữ cookie và các thành phần còn lại | Bị từ chối xác thực; client trả `SESSION_EXPIRED`, yêu cầu kết nối lại |

Từ đó, Bearer là **required** và cookie là **optional trong lượt đọc lịch đã thử**. Không suy rộng kết luận này sang đăng nhập, SSO hoặc API khác. Origin/Referer là **unknown** vì chưa thử bỏ riêng. Lượt Java không gửi header anti-CSRF riêng và vẫn thành công; bỏ toàn bộ cookie cũng thành công trong mẫu này. Điều đó không chứng minh các thao tác khác không cần CSRF, và không thay đổi bảo vệ CSRF của AMS.

Đây là gọi server-to-server: CORS của trình duyệt không quyết định Java có đọc được phản hồi hay không. Không có bước bỏ qua CAPTCHA, MFA, chứng chỉ TLS hoặc quyền truy cập.

Lượt đầu đọc đủ phản hồi nhưng mapper từ chối kiểu số. Kiểm tra cấu trúc trong bộ nhớ cho thấy portal gửi giờ/phút dưới dạng số thập phân có giá trị nguyên. Mapper đã sửa để nhận cả `7` và `7.0` — ví dụ giả định — nhưng từ chối `7.5`, chuỗi `"7"` và giá trị ngoài khoảng hợp lệ. Sau sửa, lượt chạy qua toàn bộ client thành công. Không thay test để bỏ qua lỗi production.

### Phản hồi và lỗi được phân biệt ra sao?

**VERIFIED:** phản hồi lịch thành công có `Success=true`, `Data.B` là chuỗi; sau AD, nội dung là một mảng JSON. Envelope có kiểu chỉ giữ thông tin cần đọc. `Message`, `Pager` và `Id` không được sao chép vào lỗi hoặc log; chưa dùng chúng để khẳng định độ đầy đủ.

| Tình huống | Kết quả client |
| --- | --- |
| HTTP 2xx, `Success=true`, B hợp lệ và mapping được | Trả `ScheduleObservation` |
| HTTP 200 nhưng `Success=false` | `BUSINESS_FAILURE` |
| HTTP 401 hoặc redirect đúng trang login cùng origin | `SESSION_EXPIRED`; `reconnectionRequired=true` |
| HTTP lỗi khác, kể cả 403, hoặc redirect khác | `HTTP_ERROR`; không tự chuyển sang host mới |
| Thiếu/sai kiểu envelope, sai cấu trúc dữ liệu, ngày/giờ không hợp lệ | `UNEXPECTED_SCHEMA` |
| Base64/UTF-8 lỗi hoặc chuỗi sau AD không phải JSON hợp lệ | `DECODE_ERROR` |
| Không thiết lập/duy trì được kết nối | `NETWORK_ERROR` |
| Quá thời gian hoặc kích thước đã cấu hình | `TIMEOUT` hoặc `RESPONSE_TOO_LARGE` |

Không gọi mọi lỗi nghiệp vụ hoặc giải mã là “hết phiên”, vì làm vậy sẽ che lỗi protocol. **UNKNOWN:** chưa đợi token tự hết hạn, chưa kiểm chứng refresh hoặc dấu hiệu riêng của expiry trong HTTP 200. Test offline chứng minh nhánh xử lý 401/redirect hoạt động; nó không chứng minh mọi phiên hết hạn của portal đều trả đúng hai dạng đó. Tương tự, sai khóa XOR không có cơ chế kiểm tra toàn vẹn riêng: lỗi cú pháp hoặc schema giúp phát hiện nhiều trường hợp, không phải bằng chứng mật mã rằng khóa chắc chắn đúng.

### DTO, mapper và thời gian

`PhenikaaScheduleItem` nằm trong `academic.infrastructure.phenikaa`. Tên trường riêng của nguồn được đọc tại đây, không đưa vào entity học vụ. Kết quả chuẩn hóa là `ScheduleObservation`, gồm khoảng ngày đã yêu cầu, múi giờ và danh sách phần tử bất biến.

| Trường nguồn được đọc | Trường chuẩn hóa | Giới hạn ý nghĩa |
| --- | --- | --- |
| `IDLICHHOC` | `identity.scheduleId` | Mã ứng viên, chưa phải khóa lưu trữ |
| `IDLOPHOCPHAN` | `identity.sectionId` | Giữ riêng với mã đăng ký |
| `DANGKY_LOPHOCPHAN_ID` | `identity.enrollmentSectionId` | Không mặc định bằng mã lớp |
| `TENHOCPHAN` | `courseName` | Chưa suy ra mã môn hoặc Course UUID |
| `NGAYHOC` | `date` | Đọc chặt `dd/MM/uuuu`, không dựa system locale |
| Các trường giờ/phút bắt đầu, kết thúc | `startsAt`, `endsAt` | `LocalTime`, không coi là UTC |
| `TENPHONGHOC`, `GIANGVIEN` | `room`, `lecturer` | Có thể thiếu; không tự điền dữ liệu |
| `PHANLOAI` | `kind` | `LICHHOC` → CLASS, `LICHTHI` → EXAM, loại khác → UNKNOWN |

**OBSERVED:** định dạng ngày trong dữ liệu đã giải mã là DD/MM/YYYY; giao diện nguồn hiển thị GMT+7. Model giữ ngày/giờ địa phương với `Asia/Ho_Chi_Minh` rõ ràng, chưa biến thành timestamp UTC hoặc suy lịch lặp. Ngày không hợp lệ hay ngoài khoảng đã yêu cầu bị từ chối. Một cặp giờ/phút có thể cùng thiếu; nếu chỉ có một nửa thì báo lỗi. Nếu có cả thời điểm bắt đầu và kết thúc, kết thúc phải sau bắt đầu trong cùng ngày. Lịch qua nửa đêm chưa được hỗ trợ, không tự cộng một ngày.

Đọc được một item mang loại EXAM trong lịch cá nhân không có nghĩa đã triển khai API thi riêng. Chưa tạo entity `Exam`, `StudentCourse` hoặc `ClassSession` từ dữ liệu này. Một lần gọi thiếu hoặc sai một item sẽ thất bại cả lần đọc, không âm thầm bỏ item đó rồi báo đủ lịch.

### Identity, độ đầy đủ và contract hiện tại

Không có bằng chứng mới rằng `IDLICHHOC` giữ nguyên khi đổi phòng/giờ, duy nhất giữa các tài khoản hoặc đại diện đúng một buổi. Kết quả qua reload của Phase 3 vẫn chỉ là mã ứng viên. `CandidateIdentity.scope()` luôn trả `UNVERIFIED`; không dùng `ID` biến động, thời gian, phòng hoặc hash các trường này làm khóa.

`ScheduleObservation.completeness()` luôn trả `UNKNOWN`, kể cả danh sách có bản ghi hoặc rỗng. Điều này buộc phần nhập dữ liệu sau này phải xác minh phạm vi/phân trang trước khi đánh dấu buổi cũ đã bị hủy. Không tạo bảng source mapping khi phạm vi ID chưa rõ; đề xuất đối chiếu AMS UUID với nguồn/tài khoản/loại đối tượng vẫn chờ review ở bước sau.

`AcademicPortalClient.fetchSnapshot(connectionId)` chưa đổi contract và vẫn báo chưa hỗ trợ. Adapter thêm thao tác thực tế `fetchSchedule(session, from, through)`, không trả snapshot rỗng để giả lập hoàn thành. Chưa đưa tham số phiên riêng của Phenikaa lên interface dùng chung khi AMS chưa có connection store hoặc application service quản lý ownership. Khi có use case nhập lịch thật, mới chốt contract theo từng khả năng và cách tra phiên qua connection ID.

### Test, giới hạn và cách tiếp tục

Các test mới chạy offline với HTTP server trên loopback, chỉ dùng dữ liệu giả. Chúng kiểm tra vector codec, envelope, HTTP 200 nhưng nghiệp vụ thất bại, dữ liệu hỏng, timeout trước header và giữa phần thân, response quá lớn, redirect không chuyển credential, 401, lỗi mạng, che thông tin chẩn đoán, mapping ngày/giờ và giới hạn identity/độ đầy đủ. Không cần tài khoản Phenikaa trong CI và không thêm secret cho workflow.

Lượt local `mvnw.cmd test` chạy 60 test, không lỗi hoặc bỏ qua. `mvnw.cmd verify` đã được chạy nhưng thất bại ở 44 integration test PostgreSQL/Redis vì Testcontainers báo không tìm thấy Docker khả dụng. Đây là hạn chế của môi trường kiểm tra hiện tại, không được ghi thành toàn bộ verify đã pass; không sửa hoặc tắt integration test để né lỗi. Kết quả CI của commit bàn giao cần được kiểm tra riêng trên GitHub.

Database, migration, frontend và workflow giữ nguyên. Các điểm còn cần quyết định trước bước tiếp theo là cách kết nối lại phiên cho user AMS, ràng buộc tài khoản nguồn, phạm vi source ID và tiêu chí một lần đọc được coi là đầy đủ. Thời hạn/refresh của phiên, nguồn tạo `iM`, yêu cầu Origin/Referer và schema API thi riêng vẫn chưa rõ.

Phase 4A dừng ở nền HTTP đã kiểm chứng. Không tự bắt đầu import, scheduler, change detection, Google Calendar hoặc email; không merge vào `main`.
