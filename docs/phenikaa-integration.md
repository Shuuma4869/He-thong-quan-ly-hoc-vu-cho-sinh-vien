# Kết nối AMS với cổng QLĐT Phenikaa

> Các phần Phase 3–5C được giữ theo thời điểm kiểm tra. [Kết luận Phase 5D](#phase-5d-kết-luận-nghiên-cứu-ngữ-nghĩa-kết-quả-học-tập) xác định rõ phần điểm có thể đọc và phần chưa đủ cơ sở để lưu. Chương trình, môn và nhóm đã được nhập trong phạm vi Phase 5B; lần học, kết quả, lịch và thi chưa được nhập. `IDLICHHOC` không phải khóa duy nhất cho từng buổi. Chưa có giao diện kết nối tài khoản Phenikaa trong AMS.

Tài liệu này ghi lại các lượt kiểm tra cổng QLĐT Phenikaa từ 18 đến 30/09/2026 và những việc cần làm rõ trước khi mở thêm khả năng kết nối cho AMS. Người tiếp tục phát triển có thể đọc phần đầu để hiểu hướng xử lý, rồi tra đường dẫn API và tên trường ở các phần sau.

Phạm vi của phần Phase 3 bên dưới là khảo sát. Khi đó adapter chưa gọi HTTP hoặc nhập dữ liệu; không dùng nhận định lịch sử này để thay cho kết quả Phase 4A/4B ở cuối tài liệu.

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

## Phase 4B: kết nối mã hóa và nhập hồ sơ

### Phạm vi đang có

Phase này thêm nơi lưu kết nối Phenikaa của từng tài khoản AMS và luồng nhập hồ sơ. Phần gọi HTTP nằm trong `PhenikaaHttpClient`; `PhenikaaAcademicPortalClient` phụ trách tra kết nối đúng chủ sở hữu, giải mã phiên trong thời gian gọi nguồn và ghi trạng thái lần truy cập. Tên lớp đổi để tách rõ hai trách nhiệm này, không phải tạo thêm một client gọi nguồn song song.

Chưa có màn hình kết nối, API nhận token hoặc tác vụ chạy định kỳ. Việc cấp phiên hiện chỉ có đường gọi nội bộ dành cho công cụ kiểm chứng local được kiểm soát. Người dùng không được yêu cầu mở DevTools để sao chép Bearer/cookie. Cách đăng nhập/kết nối lại dành cho sản phẩm vẫn cần thiết kế riêng.

Lịch dừng ở **SCHEDULE_PERSISTENCE = BLOCKED_PARTIAL**. Đọc được một buổi học chưa có nghĩa đã xác định được môn và học kỳ của buổi đó. Những khoảng trống này được liệt kê bên dưới; không tạo môn hoặc học kỳ giả để vượt qua ràng buộc database.

### Kết nối thuộc về ai?

Mỗi tài khoản AMS có tối đa một hàng `phenikaa_connection`, kể cả sau khi ngắt kết nối. Kết nối lại cập nhật chính hàng này, giữ nguyên UUID. Đây là quy tắc chặt hơn “một kết nối đang hoạt động”: nó đơn giản hóa việc tìm kết nối hiện tại và giữ ràng buộc nguồn khi đăng nhập lại.

`user_id` là khóa ngoại tới `app_user`. UUID của kết nối và UUID của hồ sơ đều do AMS tạo; không dùng mã sinh viên hoặc ID của trường làm khóa chính. API đọc trạng thái lấy user từ principal của Spring Security, tức là danh tính đã đăng nhập trong session AMS. Tham số `userId` do browser gửi không được dùng để chọn tài khoản.

Trước khi đọc hoặc thay đổi kết nối, adapter kiểm tra user còn `ACTIVE` và khóa hàng tài khoản trong transaction. “Khóa hàng” ở đây là khóa ghi của PostgreSQL: hai lượt nhập đồng thời của cùng user phải lần lượt xử lý, kể cả khi hồ sơ chưa tồn tại. Unique constraint trên `student_profile.user_id` vẫn là lớp bảo vệ cuối cùng, không chỉ dựa vào kiểm tra trong Java.

Khóa được giữ cả trong lúc gọi HTTP, tối đa theo timeout đã cấu hình. Đây là lựa chọn đơn giản cho luồng nhập thủ công hiện tại; một lượt nguồn chậm có thể khiến lượt nhập khác của cùng user phải chờ. Khi có worker hoặc tải lớn hơn cần đánh giá lại, không coi cách này là thiết kế hàng đợi hoàn chỉnh.

Ngoài phiên mã hóa, kết nối giữ `encrypted_subject`: ID người học được mã hóa riêng, chỉ dùng so sánh khi kết nối lại. Ngắt kết nối xóa phiên dùng để gọi nguồn nhưng giữ phần ràng buộc này. Nhờ vậy, đăng nhập một tài khoản trường khác không âm thầm ghi đè hồ sơ học vụ đã có. Đổi sang tài khoản nguồn khác hiện bị từ chối bằng `SOURCE_ACCOUNT_MISMATCH`; chưa có quy trình chuyển hoặc xóa hồ sơ để thực hiện việc đó.

### Phiên được bảo vệ như thế nào?

Phiên được mã hóa bằng **AES-256-GCM** qua JCA/JCE của Java, không dùng giao thức XOR A/B làm lớp bảo vệ lưu trữ. AES bảo vệ nội dung; GCM thêm kiểm tra toàn vẹn để phát hiện dữ liệu bị sửa hoặc giải mã bằng sai khóa.

Mỗi lần mã hóa có nonce ngẫu nhiên 12 byte và authentication tag 16 byte. Nonce không phải mật khẩu và được lưu cùng ciphertext. Dùng nonce mới giúp hai lần mã hóa cùng nội dung tạo ra kết quả khác nhau. Không dùng IV cố định.

AAD là phần ngữ cảnh được xác thực cùng ciphertext nhưng không cần giữ bí mật. AMS đưa mục đích (`session` hoặc `subject`), phiên bản format, phiên bản khóa, UUID kết nối và UUID user vào AAD. Ví dụ, chép cột mã hóa từ kết nối của người A sang người B sẽ làm kiểm tra toàn vẹn thất bại, không tạo một phiên hợp lệ của B.

Payload phiên có phiên bản riêng, ở Phase 4B là `1`, lưu theo định dạng nhị phân có độ dài từng chuỗi. Nội dung gồm Bearer, cookie tùy chọn, khóa phản hồi, ID người học và **hai mã chức năng riêng cho hồ sơ/lịch**. Format không được expose qua API. Mã chức năng riêng là cần thiết vì lần khảo sát mới thấy hai request dùng giá trị khác nhau, dù người học và khóa phản hồi giống nhau. Phase 4C mở rộng payload nhưng vẫn đọc được phiên bản này; xem phần tương thích bên dưới.

Sau giải mã, dữ liệu chỉ sống trong phạm vi một lần truy cập và được đóng trong `try-with-resources`. Các mảng byte/ký tự do code sở hữu được xóa best-effort. Cần hiểu đúng giới hạn: Java, JSON parser và HTTP client có thể tạo bản sao chuỗi; không thể bảo đảm xóa mọi bản sao khỏi heap. Không bật wire logging, SQL bind logging hoặc heap dump khi xử lý phiên thật.

`toString()` của phiên, cipher, entity kết nối và dữ liệu hồ sơ đều che nội dung. Lỗi nguồn chỉ được chuyển thành mã cố định; không kèm message gốc, response body hoặc ID người học. Lỗi toàn vẹn trả `SESSION_INTEGRITY_FAILURE`, không giả định là token hết hạn.

### Cấu hình khóa và bật tính năng

Mặc định `AMS_PHENIKAA_ENABLED=false`. Khi chưa bật, backend vẫn có schema nhưng không tạo client nhập hồ sơ hoặc endpoint trạng thái Phenikaa. Đây không phải trường hợp trả hồ sơ rỗng để giả lập nguồn đã kết nối.

Để bật tính năng, cấu hình bên ngoài source:

| Biến | Ý nghĩa |
| --- | --- |
| `AMS_PHENIKAA_ENABLED` | Bật bằng `true` khi đã có khóa riêng và quy trình cấp phiên được kiểm soát |
| `AMS_PHENIKAA_SESSION_KEY` | Khóa ngẫu nhiên 32 byte được biểu diễn Base64; không phải chuỗi mật khẩu tự đặt |
| `AMS_PHENIKAA_KEY_VERSION` | Số nguyên dương, mặc định `1`; nhận diện khóa đang dùng |
| `AMS_PHENIKAA_CONNECT_TIMEOUT` | Giới hạn kết nối, mặc định `8s` |
| `AMS_PHENIKAA_RESPONSE_TIMEOUT` | Giới hạn toàn bộ phản hồi, mặc định `15s` |
| `AMS_PHENIKAA_MAX_RESPONSE_BYTES` | Giới hạn phản hồi, mặc định 2 MiB |

Nếu bật mà thiếu/sai khóa, startup thất bại với thông báo cố định, không in giá trị cấu hình. Không có khóa production mặc định; `.env.example` chỉ để placeholder rỗng. Test sinh khóa riêng, không đọc khóa production.

Hiện chỉ hỗ trợ một phiên bản khóa đang hoạt động, chưa có key ring hoặc công cụ xoay khóa. **Không chỉ đổi khóa trong environment rồi khởi động lại:** dữ liệu cũ sẽ không giải mã được. Việc xoay khóa phải giải mã bằng khóa cũ và mã hóa lại bằng khóa mới trong quy trình riêng có kiểm thử. Mất khóa cũng làm mất khả năng đọc phiên/ràng buộc nguồn đã lưu; bản sao database không thay thế được việc bảo vệ khóa.

### Trạng thái và xử lý lỗi

| Tình huống | Trạng thái sau đó | Dữ liệu được giữ lại |
| --- | --- | --- |
| Cấp phiên nội bộ và kiểm tra hồ sơ thành công | `CONNECTED` | Phiên mã hóa, ràng buộc nguồn và thời điểm xác thực |
| Nguồn trả dấu hiệu hết xác thực đã nhận diện (`SESSION_EXPIRED`) | `RECONNECTION_REQUIRED` | Hồ sơ, học vụ cũ và ciphertext; không dùng lại phiên cho tới khi kết nối lại |
| Timeout, lỗi mạng, HTTP/nghiệp vụ/schema/giải mã | Giữ trạng thái hiện tại | Hồ sơ cũ, phiên mã hóa; cập nhật thời điểm và mã lỗi |
| Chủ tài khoản ngắt kết nối qua thao tác nội bộ | `DISCONNECTED` | Hồ sơ/học vụ và ràng buộc nguồn; xóa `encrypted_session` |

`session_expires_at` hiện là `NULL`: chưa xác minh được expiry đáng tin cậy, không tự đặt thời hạn theo phỏng đoán. `last_failed_access_at`/`last_failure_code` ghi lần lỗi gần nhất và vẫn được giữ khi lượt sau thành công; cần so sánh với `last_successful_access_at` để hiểu thứ tự, không coi mã lỗi cũ là kết quả hiện tại.

Nếu đã ngắt kết nối, thử cấp lại một phiên lỗi vẫn giữ `DISCONNECTED`. Không chuyển sang trạng thái có phiên khi ciphertext đã bị xóa; chỉ lần cấp phiên được kiểm tra thành công mới kích hoạt lại kết nối.

Luồng nhập đọc và kiểm tra **toàn bộ hồ sơ trước khi sửa dữ liệu học vụ**. Lỗi nguồn được phép lưu metadata thất bại của kết nối mà không ghi hồ sơ. Lỗi database khi thực sự ghi hồ sơ làm rollback cả transaction, gồm metadata thành công vừa cập nhật. Vì vậy, `noRollbackFor` chỉ áp dụng cho loại lỗi nguồn an toàn đã định nghĩa, không áp dụng cho mọi exception.

### Request hồ sơ và dữ liệu được nhập

**OBSERVED trong request thật và mã `tunhaphoso.js`:**

```text
POST /sinhvienapi3/api/SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
Body: A=<nội dung tạo theo giao thức Phase 4A>
```

JSON trước khi tạo A gồm `action`, `func=pkg_hosohocvien.LayThongTinChiTietHoSo`, `iM`, `strId`, `strChucNang_Id`, `strNguoiThucHien_Id`. ID đích và ID người thực hiện đều lấy từ phiên nguồn được cấp nội bộ; không nhận ID tùy ý từ browser AMS.

Trong mẫu đọc ngày 21/09/2026, HTTP thành công, `Success=true`, giải mã được mảng có đúng một hồ sơ và `ID` khớp `strId` đã yêu cầu. Mapper yêu cầu đúng một phần tử và kiểm tra ID trước khi lấy dữ liệu. Nếu nguồn thay đổi cấu trúc, thiếu ID hoặc trả người học khác, cả lượt đọc bị từ chối.

| Trường nguồn | Cách dùng trong AMS | Giới hạn |
| --- | --- | --- |
| `ID` | Kiểm tra đúng người học của phiên | Không đưa lên API trạng thái hoặc dùng làm UUID AMS |
| `MASO` | `StudentProfile.studentNumber` | Bắt buộc là chuỗi hợp lệ, tối đa 80 ký tự |
| `NGANH` | `StudentProfile.programName` | Tên ngành hiển thị; chưa phải identity hoặc phiên bản curriculum |
| `KHOADAOTAO`, `MANGANH`, `LOP` | Chưa nhập | Đã thấy tên trường, nhưng chưa chốt mapping phù hợp với domain hiện tại |
| `DAOTAO_TOCHUCCHUONGTRINH_ID` | Chưa nhập | `null` trong mẫu; không tạo curriculum thay thế |

Không nhập họ tên, ngày sinh, số giấy tờ, điện thoại hoặc địa chỉ chỉ vì response có sẵn. Không lưu raw profile response. Tên trường, cohort, curriculum và grading policy đã có trong AMS được giữ nguyên vì nguồn hiện chưa xác nhận đủ để cập nhật chúng.

Khi `NGANH` thiếu hoặc `null`, hồ sơ mới để trống và hồ sơ cũ giữ giá trị đã có. Chuỗi trắng, sai kiểu hoặc vượt độ dài là lỗi schema, không coi là yêu cầu xóa. Nhập lần hai tìm theo user, cập nhật cùng UUID. Đây là tính idempotent: thực hiện lại cùng việc không sinh thêm hồ sơ trùng.

### Contract cho application và API trạng thái

`AcademicPortalClient` bỏ `fetchSnapshot` chưa triển khai, thay bằng các khả năng thực sự có: tìm kết nối hiện tại, `fetchProfile` và `fetchSchedule`. Application truyền UUID của user đã xác thực và `StudentConnectionId`, không cầm `PhenikaaSession`. Adapter luôn kiểm tra lại cặp user/kết nối trước khi giải mã.

`ProfileImportService.importCurrentProfile` chỉ nhận user hiện tại do server xác định; không nhận mã sinh viên hoặc người học để chọn đối tượng đích. Chưa expose thao tác import/cấp phiên thành API công khai. Luồng kết nối qua giao diện sẽ cần thiết kế riêng, không tái sử dụng helper research như một endpoint production.

Khi bật tính năng, `GET /api/me/connections/phenikaa` chỉ trả trạng thái, các thời điểm truy cập, mã lỗi an toàn và cờ cần kết nối lại. User chưa có kết nối nhận `DISCONNECTED` với thời điểm trống. Không trả UUID nguồn, Bearer, cookie, khóa phản hồi hoặc ciphertext. Spring Security/session/CSRF hiện tại giữ nguyên; không có API CRUD cho các bảng mới.

### Vì sao lịch chưa được lưu?

Đã đọc toàn bộ tên trường của mẫu lịch giải mã và đối chiếu mã `lichgiang.js`. Ngoài các trường Phase 4A đã dùng, mẫu có các nhãn lớp, giảng viên, phòng và tiết học. Các trường này hữu ích để hiển thị nhưng chưa tạo được liên kết đáng tin cậy sang `Course` và `Semester`.

| Quan hệ cần có | Bằng chứng hiện tại | Kết luận |
| --- | --- | --- |
| Lớp → môn | Có `TENHOCPHAN`, các ID/nhãn lớp; không có mã/ID môn và tín chỉ rõ ràng trong item đã quan sát | Chưa xác minh được mapping tối thiểu để tạo `Course`; không dùng tên môn hoặc hash tên làm mã |
| Lớp → học kỳ | Chưa có năm học, mã kỳ/đợt hoặc thời gian đào tạo có thể giải thích trong item | Không suy học kỳ từ ngày buổi học |
| Mã lớp nguồn → `ClassSection` | Có `IDLOPHOCPHAN` và `DANGKY_LOPHOCPHAN_ID` riêng | Giữ cả hai; chưa mặc định cùng ý nghĩa hoặc duy nhất trong mọi phạm vi |
| Buổi nguồn → `ClassSession` | Có ứng viên `IDLICHHOC` | Giữ mức quan sát qua reload; ổn định khi đổi giờ/phòng vẫn **UNKNOWN** |

Các tên được quan sát thêm gồm `DANGKY_LOPHOCPHAN_TEN`, `TENLOPHOCPHAN`, `GIANGVIEN_ID`, `IDPHONGHOC`, `PHONGHOC_TEN`, `PHONGHOC_MA`, `SOTIET`, `TIETBATDAU`, `TIETKETTHUC`, `BUOIHOC`. Không dùng việc tên trường xuất hiện để tự suy nghĩa hoặc scope của ID.

Chưa xác minh được request hỗ trợ nhỏ nhất để nối lớp → môn → kỳ. Phần này cần nghiên cứu tiếp trong phạm vi được duyệt; không gọi/import toàn bộ curriculum để lấp chỗ trống. Việc “không thấy trong mẫu” cũng không chứng minh mọi endpoint của portal đều thiếu thông tin đó.

Vì chưa qua gate, không tạo source mapping table, không insert `Course`, `Semester`, `ClassSection` hoặc `ClassSession`. Kết quả lịch vẫn là `ScheduleObservation` với `completeness=UNKNOWN`. Response rỗng không xóa lịch, không đánh dấu hủy, không phát `CLASS_REMOVED`. Không dùng `ID` của giao diện hoặc ngày/giờ/phòng làm identity.

### Những điều vẫn chưa được kết luận

- Cookie chỉ được chứng minh có thể bỏ trong mẫu lịch của Phase 4A. Lượt hồ sơ giữ cookie; chưa thử bỏ riêng nên không suy rộng sang hồ sơ hoặc API thi.
- Mẫu hồ sơ/lịch dùng cùng `iM`; nơi tạo, lifecycle và refresh vẫn **UNKNOWN**. Hai capability giữ mã chức năng riêng.
- Chưa chờ hết hạn tự nhiên hoặc kiểm chứng refresh. Chỉ các dấu hiệu 401/redirect login đã nhận diện mới dẫn tới yêu cầu kết nối lại.
- Chưa có quy trình đổi tài khoản trường, xoay khóa, production connect UX hoặc lịch chạy đồng bộ.
- Không triển khai API thi riêng, điểm, curriculum đầy đủ, change detection, Google Calendar hoặc email trong Phase 4B.

### Kiểm thử và lượt kiểm chứng local

**VERIFIED ngày 21/09/2026:** Java chạy độc lập với DOM, dùng chính client và application service của backend để đọc hồ sơ thật. Phiên do chủ tài khoản tự đăng nhập được bàn giao một lần qua loopback, mã hóa AES-GCM và kiểm tra chữ ký; khóa bàn giao chỉ tồn tại tạm. Helper nằm ngoài repository, không có endpoint bàn giao trong source ứng dụng.

Lượt thử dùng tài khoản AMS giả trong PostgreSQL Testcontainers riêng, không ghi vào database đang dùng của dự án. Kết quả chỉ xuất boolean/số lượng:

| Bước | Kết quả |
| --- | --- |
| Java gọi HTTP, nghiệp vụ nguồn thành công, giải mã và mapping hồ sơ | Thành công |
| Lưu kết nối với phiên mã hóa | Thành công; expiry vẫn NULL |
| Import vào hồ sơ thuộc user của kết nối | Thành công, 1 hồ sơ |
| Import lần hai | Vẫn 1 hồ sơ, giữ UUID |
| Đóng database/Redis tạm | Đã đóng; không giữ hồ sơ thật làm fixture |

Lần chuẩn bị helper đầu tiên dừng do classloader của chế độ chạy Java source không truy cập được lớp cấu hình test; chưa tới bước gọi nguồn. Helper được biên dịch trước khi chạy lại. Kết quả thành công ở bảng trên là lượt sau, không suy từ lượt Node kiểm tra cấu trúc ban đầu.

Lệnh local `mvnw.cmd -o verify` sau khi hoàn thiện đạt **85 unit/HTTP test và 62 integration test**, không lỗi hoặc bỏ qua. Các ca mới kiểm tra mã hóa/toàn vẹn/AAD/khóa sai, cấu hình khóa, HTTP hồ sơ, ownership, constraint, migrate V5 → V6, nhập lặp/đồng thời, ngắt/kết nối lại, schema/hết phiên và rollback khi database lỗi. Schema sạch cũng khởi động thành công với `ddl-auto=validate`. Ca kết nối lại thất bại sau khi đã ngắt cũng được bổ sung để kiểm tra trạng thái không mâu thuẫn với việc phiên đã bị xóa.

Hai lỗi ở lượt test trung gian đã được xử lý: mock hết phiên phải được thay bằng `doReturn` để không ném lỗi ngay lúc thiết lập lại; context kiểm thử cấu hình phải dùng bộ chuyển đổi kiểu của Spring Boot để đọc timeout dạng `8s`. Không tắt check hoặc đổi hành vi production để né test.

CI chỉ dùng HTTP loopback/mocks và fixture tổng hợp, không gọi Phenikaa thật, không cần credential của trường. Helper, browser profile và dữ liệu container của lượt live đã được dọn. Đã đối chiếu 68 file code/docs/test report với các giá trị phiên đang có trong bộ nhớ: không có file khớp; sau đó đã giải phóng phiên nghiên cứu. Đây là kiểm tra các giá trị đã biết, không phải cam kết rằng một công cụ quét có thể chứng minh tuyệt đối không còn dữ liệu nhạy cảm ở mọi nơi trên máy.

Phần Connection + Profile đã qua kiểm chứng local; trạng thái CI phải đối chiếu với đúng commit bàn giao trên GitHub. Schedule persistence vẫn **BLOCKED_PARTIAL** theo gate nêu trên, không bị đổi thành PASS chỉ vì test hồ sơ đạt.

## Phase 4C: lịch học và lịch thi

### Kết quả và giới hạn

Phase 4C bổ sung bộ đọc lịch thi cá nhân qua API riêng, không lấy các mục `LICHTHI` trong thời khóa biểu để thay thế. Lượt Java ngày 24/09/2026 đã gọi nguồn thật, giải mã và chuẩn hóa được 6 lịch thi; số lượng khớp với trình duyệt. Phần hồ sơ của 4B được giữ nguyên.

**Phase 4 tổng thể vẫn là PARTIAL.** Lý do chính không còn là “chưa tìm được API”, mà là chưa xác định được cách nối một bản ghi nguồn với đúng đối tượng trong AMS:

| Phần việc | Trạng thái | Phạm vi kết luận |
| --- | --- | --- |
| Nhập hồ sơ | PASS từ 4B | Kiểm thử hồi quy tiếp tục đạt |
| Đọc lịch học | PASS | Đọc theo khoảng ngày; chưa khẳng định đầy đủ |
| Lưu lịch học | BLOCKED_PARTIAL | Chưa có học kỳ đã xác minh và khóa riêng cho từng buổi |
| Đọc lịch thi cá nhân | PASS trong schema đã quan sát | Có mẫu thật và kiểm chứng Java; kế hoạch thi chung chưa có mẫu |
| Lưu lịch thi | BLOCKED_PARTIAL | Chưa nối được đến một lần học thật của `StudentCourse` |

`VERIFIED` trong phần này nghĩa là đã kiểm tra trực tiếp hành vi được nói tới. `OBSERVED` là điều thấy trong mẫu hoặc mã frontend, chưa đủ để suy rộng. `UNKNOWN` là chưa biết; `BLOCKED` là thiếu bằng chứng bắt buộc để làm bước tiếp theo. Không có thiết kế `PROPOSED` nào được tính như tính năng đã chạy.

### Lịch học: đã tìm được đường nối đến môn

Nguồn hỗ trợ nhỏ được tìm thấy là màn hình **Tra cứu kết quả đăng ký**, không phải import toàn bộ điểm hoặc chương trình học. Frontend gọi danh sách kỳ, kế hoạch đăng ký của tài khoản, rồi kết quả đăng ký theo bộ lọc đó. Chỉ mở phần tra cứu và lịch chi tiết của lớp đã đăng ký; không bấm xác nhận đăng ký, đổi lớp, điểm danh hoặc sửa dữ liệu trường.

Trong mẫu, 4 mục lịch của một tuần nối được theo ID lớp tới 3 hàng kết quả đăng ký. Các hàng đăng ký có `DAOTAO_HOCPHAN_ID`, `DAOTAO_HOCPHAN_MA`, `DAOTAO_HOCPHAN_TEN`. Đây là bằng chứng tốt hơn ghép bằng tên môn: tên có thể trùng hoặc đổi, còn phép nối này dùng ID nguồn. Lịch chi tiết một lớp cũng có `IDHOCPHAN` khớp ID môn trong kết quả đăng ký.

Tuy nhiên, chưa có tín chỉ riêng của từng môn từ nguồn hỗ trợ này. `SOTINCHIDADANGKY` được frontend dùng làm tổng tín chỉ đã đăng ký, không được lấy làm `Course.credits` cho từng hàng. Chưa tạo `Course` hoặc thêm mapper lưu môn khi thiếu dữ liệu bắt buộc. Không dùng tên môn, mã băm của tên hay mã buổi học để thay mã môn.

### Vì sao chưa map được học kỳ?

Danh sách kỳ có `ID` và nhãn `THOIGIAN` dạng năm–năm–số. Nhãn này chưa giải thích đầy đủ quan hệ giữa năm học, học kỳ và đợt đào tạo.

Quan trọng hơn, trong hai phản hồi kết quả đăng ký đã kiểm tra, bộ lọc kỳ có giá trị nhưng `DAOTAO_THOIGIANDAOTAO_ID` trong các hàng **không bằng ID kỳ đã chọn**. ID trên hàng cũng không xuất hiện trong danh sách kỳ đăng ký hoặc danh sách kỳ thi đã lấy. Đây là bằng chứng cho thấy không thể đơn giản chép mã bộ lọc vào bản ghi rồi coi các khái niệm đó tương đương. Chưa kết luận đây là quan hệ cha–con, một đợt học riêng hay cách xử lý bộ lọc của nguồn.

Vì vậy, `Semester.Identifier(academicYearStart, termCode)` chưa được tạo. Không tách nhãn hiển thị rồi tự gọi phần cuối là học kỳ; cũng không suy kỳ từ tháng diễn ra lịch. `ExamPeriod` mới chỉ giữ mã và nhãn của **phạm vi tra cứu nguồn**, không phải học kỳ AMS.

### ID lớp và ID từng buổi: phát hiện làm thay đổi hướng lưu

| Trường | Phạm vi quan sát được | Độ tin cậy và giới hạn |
| --- | --- | --- |
| `IDLOPHOCPHAN` | Frontend dùng khi hỏi lịch/điểm danh theo lớp; khớp mã lớp trong mẫu đăng ký | OBSERVED; chưa có cam kết scope toàn hệ thống |
| `DANGKY_LOPHOCPHAN_ID` | Xuất hiện trong lịch, kết quả đăng ký và request lịch chi tiết lớp | VERIFIED phép nối trong mẫu; chưa coi là ID của một lần học |
| Hai ID lớp ở trên | Bằng nhau trong mẫu lịch đã đọc | Không gộp hai trường hoặc suy rằng mọi phản hồi đều như vậy |
| `IDLICHHOC` | Một giá trị xuất hiện ở nhiều ngày trong khoảng 28 ngày | VERIFIED không phải khóa duy nhất cho một buổi trong mẫu này |
| `ID` của thời khóa biểu | Đã thay đổi qua reload ở Phase 3 | Tiếp tục loại khỏi phương án identity |

Mẫu 28 ngày có 14 mục lịch; 4 giá trị `IDLICHHOC` được dùng ở nhiều ngày khác nhau. Ví dụ giả định: nếu cùng mã X xuất hiện vào thứ Hai của hai tuần, lưu cả hai vào một `ClassSession` theo X sẽ làm buổi sau ghi đè buổi trước. Đây là lỗi dữ liệu ngay cả khi phòng và giờ chưa thay đổi.

Do đó, không nâng `identityScope` lên `PROVISIONAL_OCCURRENCE`. Điều kiện để dùng mức này là phải chứng minh scope một buổi; bằng chứng mới chưa đáp ứng điều kiện đó. Giữ `UNVERIFIED` và giữ riêng các mục trong observation, kể cả khi chúng có cùng mã nguồn. Một regression test với dữ liệu tổng hợp kiểm tra trường hợp lặp ID qua hai ngày.

Lịch chi tiết lớp trả 7 hàng với ngày/range và ID môn nhưng không có `IDLICHHOC`, nên nguồn này chưa giải quyết được identity. Cũng không ghép ID với ngày/giờ/phòng để làm khóa lâu dài: khi một buổi chuyển ngày, cách ghép đó lại tạo bản ghi mới. Chưa có bằng chứng tự nhiên về một buổi bị đổi giờ/phòng; không chủ động sửa lịch trường để thử.

### Lịch thi: nguồn riêng đã được kiểm chứng

Danh sách kỳ thi trả 17 lựa chọn. Trong lượt nghiên cứu ban đầu, 4 kỳ đầu đã kiểm tra đều có hai bảng rỗng; kỳ thứ năm có 6 bản ghi cá nhân và bảng kế hoạch chung rỗng. Đây là các kỳ do chính tài khoản được đăng nhập nhìn thấy, không phải thử ID của người khác.

Luồng đã triển khai:

1. `fetchExamPeriods` lấy các kỳ có sẵn cho kết nối của user hiện tại.
2. `fetchExams` đọc lại danh sách đó, kiểm tra mã kỳ yêu cầu thuộc danh sách và dùng nhãn từ nguồn.
3. Gọi API lịch thi với người học lấy từ phiên mã hóa, mã kỳ đã kiểm tra và bộ lọc môn rỗng như request thật.
4. Kiểm tra toàn bộ bảng cá nhân, rồi mới trả `ExamObservation`.

API trả object gồm `rsLichThiCaNhan` và `rsKeHoachThiChung`, không phải một mảng lịch duy nhất. Bảng chung mới chỉ quan sát được khi rỗng. Nếu bảng này có phần tử, client hiện trả `UNEXPECTED_SCHEMA` cho cả lượt đọc: không đoán cấu trúc, không âm thầm bỏ bảng rồi báo đã đọc hết. Cần một mẫu hợp lệ của chính tài khoản trước khi bổ sung bộ đọc kế hoạch chung.

| Trường trong mẫu cá nhân | Cách dùng | Điều không được suy ra |
| --- | --- | --- |
| `QLSV_NGUOIHOC_ID` | Phải khớp người học của phiên | Không nhận owner do phía gọi tự chọn |
| `IDLICHHOC` | Giữ làm candidate ID, scope UNVERIFIED | Không kế thừa kết luận identity của lịch học hoặc coi ổn định khi đổi giờ |
| `MAHOCPHAN`, `TENHOCPHAN` | Mã và tên môn trong observation | Chưa có ID môn nguồn và tín chỉ để tạo Course hoàn chỉnh |
| `LANTHI` | Số nguyên dương, giữ riêng là lần thi | Không phải `StudentCourse.attemptNumber`, tức lần học |
| `CATHI` | Chuỗi mô tả ca thi từ nguồn | Không dùng làm thời lượng hoặc lần học |
| `NGAYHOC` | Ngày theo `dd/MM/uuuu`, parse nghiêm ngặt | Không chấp nhận ngày không tồn tại hoặc đổi thứ tự ngày/tháng |
| `GIOBATDAU`, `PHUTBATDAU` | Giờ bắt đầu bắt buộc, số nguyên hợp lệ | Không đổi giờ địa phương thành UTC bằng cách giữ nguyên con số |
| `GIOKETTHUC`, `PHUTKETTHUC` | Cùng có hoặc cùng thiếu; nếu có phải sau giờ bắt đầu | Không tự đặt thời lượng khi thiếu |
| `PHONGHOC_TEN` | Phòng, được phép trống | Không đưa vào khóa định danh |
| `IDLOPHOCPHAN`, `DANGKY_LOPHOCPHAN_ID` | Đều null trong mẫu thi đã đọc | Không tạo lớp/lần học từ nhãn `DANGKY_LOPHOCPHAN_TEN` |

Các giờ trong mẫu được nguồn ghi dưới dạng số thập phân nguyên, chẳng hạn `7.0`; mapper chấp nhận giá trị nguyên chính xác, từ chối `7.5` hoặc chuỗi `"7"`. Múi giờ luôn là `Asia/Ho_Chi_Minh`; observation chứa `Instant` đã chuyển đổi. Không lấy tên/ID sinh viên, số báo danh, người tạo hoặc thời điểm cập nhật vào observation vì use case hiện không cần.

Chưa xác minh trường riêng cho hình thức thi hoặc trạng thái hủy, nên không suy từ nhãn lớp và không tạo trạng thái hủy giả. Mã học kỳ chỉ nằm trong request, chưa có trường semester đã hiểu semantics trên item. Có mã môn và lần thi vẫn chưa đủ nối đến `StudentCourse`: một người có thể học lại cùng môn, và một lần học có thể có nhiều lần thi. Phase 4C không tạo lần học thay thế để vượt ràng buộc `Exam → StudentCourse`.

### Tham số và nguồn hỗ trợ để tra cứu tiếp

Các đường dẫn dưới đây được quan sát từ request thật hoặc đọc từ mã chức năng tương ứng, không tìm bằng cách thử đoán endpoint. Tất cả nằm trên host portal đã khóa ở tầng HTTP.

| Mục đích | Đường dẫn POST |
| --- | --- |
| Kỳ thi | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikVKSgP` |
| Lịch thi riêng | `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpFSkoHgokCS4gIikVKSgP` |
| Kỳ đăng ký của tài khoản | `/dangkyhocapi3/api/DKH_ThongTin_MH/DSA4FSkuKAYoIC8FIC8mCjgCIA8pIC8P` |
| Kế hoạch đăng ký của tài khoản | `/dangkyhocapi3/api/DKH_ThongTin_MH/DSA4BRIKJAkuICIpBSAvJgo4AiAPKSAv` |
| Kết quả đăng ký | `/dangkyhocapi3/api/DKH_Chung_MH/DSA4CiQ1EDQgBSAvJgo4DS4xCS4iESkgLwPP` |
| Lịch chi tiết lớp đã đăng ký | `/dangkyhocapi3/api/DKH_Chung_MH/DSA4DSgiKRU0IC8VKSQuDS4xCS4iESkgLwPP` |

Hai request thi được đưa vào production client; các request đăng ký chỉ dùng nghiên cứu correlation trong phase này, chưa thành capability/import mới.

Request kỳ thi có `action`, `func=pkg_congthongtin_hssv_thongtin.LayDSThoiGianLichThi`, `iM`, `strNguoiThucHien_Id`, `strChucNang_Id`. Request lịch thi dùng `func=pkg_congthongtin_hssv_thongtin.LayDSLichThi_KeHoachThi` và thêm `strQLSV_NguoiHoc_Id`, `strDaoTao_ThoiGianDaoTao_Id`, `strDaoTao_HocPhan_Id`. Không nhận arbitrary URL hoặc function từ application.

Các request đã kiểm tra không có page index/page size, `Pager` trả null. Điều này **chưa chứng minh nguồn trả đủ mọi bản ghi**. Cả lịch học và thi vẫn có `completeness=UNKNOWN`; không thêm vòng phân trang giả. Client giới hạn 256 kỳ, 10.000 mục thi, kích thước response và timeout. Vượt giới hạn hoặc một item sai làm cả lượt đọc thất bại, không cắt bớt rồi trả thành công.

### Tương thích phiên mã hóa và ownership

Mã chức năng lịch thi khác mã lịch học trong request thật. Payload phiên bản 2 thêm ngữ cảnh này; constructor cũ vẫn tạo được phiên không có ngữ cảnh thi. Decoder nhận cả phiên bản 1 và 2, từ chối phiên bản lạ hoặc byte thừa.

Phiên bản payload và phiên bản lớp bảo vệ mã hóa được tách rõ. AAD tiếp tục dùng phiên bản 1 như 4B để ciphertext cũ vẫn giải mã được; `encryption_key_version` và khóa không đổi. Có test dựng ciphertext theo đúng format/AAD cũ rồi đọc bằng code mới. Không có migration dữ liệu phiên, không tự mã hóa lại bản ghi cũ, không triển khai xoay khóa.

Phiên thiếu ngữ cảnh thi trả `CONNECTION_UNAVAILABLE` khi gọi khả năng thi; đọc hồ sơ và lịch vẫn hoạt động. Đây không phải bằng chứng token hết hạn, nên không chuyển kết nối sang `RECONNECTION_REQUIRED`. Muốn bổ sung ngữ cảnh phải cấp lại phiên qua luồng nội bộ có kiểm soát, vẫn kiểm tra ràng buộc cùng tài khoản nguồn.

`AcademicPortalClient` chỉ thêm `fetchExamPeriods` và `fetchExams`, trả các kiểu chuẩn hóa. Adapter kiểm tra user ACTIVE, cặp user/kết nối và trạng thái kết nối trước khi giải mã. Mỗi item thi phải khớp learner của phiên. Không có controller nhận token/cookie, không thay Spring Security/session/CSRF, không mở UI kết nối production.

### Database, kiểm thử và đối chiếu phạm vi

Không thêm migration, source mapping table, `ScheduleImportService` hoặc `ExamImportService`. Schema vẫn ở V6. Các bảng domain đã có không bị sửa để chấp nhận môn/học kỳ/lần học giả. Do chưa persist lịch, các ca import lần đầu/lặp/đồng thời/cập nhật cùng UUID của lịch chưa thể được đánh dấu đạt; test idempotency/concurrency/rollback của hồ sơ 4B vẫn chạy để bảo vệ phần đã hoàn thành.

Kiểm thử mới dùng fixture tổng hợp cho ngày/giờ, owner, cấu trúc hai bảng, kỳ không thuộc danh sách, mã môn thiếu, số bản ghi quá giới hạn và việc không bỏ qua item sai. HTTP loopback kiểm tra request đúng, thành công/rỗng, nghiệp vụ lỗi, 401, redirect login, timeout, oversized và giải mã lỗi. Integration test với PostgreSQL/Redis kiểm tra quyền sở hữu khả năng thi, phiên thiếu ngữ cảnh và việc lỗi nguồn giữ nguyên hồ sơ/ciphertext; chỉ SESSION_EXPIRED yêu cầu kết nối lại.

Lượt Java live ngày 24/09/2026 dùng đúng `PhenikaaHttpClient` và phiên đã đi qua `PhenikaaSessionCipher`, không gọi hàm giải mã của frontend thay cho code Java. Kết quả metadata: `http=true`, `decoded=true`, `mapped=true`, 6 lịch thi khớp số lượng trình duyệt, 4 mục lịch học, `encryptedSessionRoundTrip=true`, `persistenceAttempted=false`. Không có dữ liệu học vụ thật được ghi vào database trong lượt này. Các phản hồi rỗng cũng đã được quan sát trên portal; không suy chúng thành yêu cầu xóa.

Helper kiểm chứng nằm ngoài repository. Phiên được bàn giao một lần qua loopback bằng AES-GCM, khóa bàn giao được bọc bằng RSA-OAEP; fingerprint khóa công khai được đối chiếu trước khi gửi. Không truyền credential qua command line, không ghi request/response thật, HAR hoặc screenshot vào file. Đây chỉ là dụng cụ kiểm chứng local, không phải một phần của ứng dụng.

Lượt `verify` đầu chưa chạy được integration test vì Docker Engine chưa sẵn sàng. Sau khi khởi động Docker và cho tiến trình test truy cập engine, toàn bộ integration test đã chạy thành công; không tắt test hoặc đổi cấu hình để bỏ qua Docker. Kết quả cuối cùng phải đọc cùng commit được bàn giao và CI tương ứng.

Kết quả local cuối ngày 24/09/2026: `mvnw.cmd verify` đạt **119 unit/HTTP test và 72 integration test**, không thất bại, lỗi hoặc bỏ qua. Flyway tạo schema sạch và Hibernate validate thành công. Frontend không thay đổi; workflow CI hiện có vẫn chạy lint, typecheck, unit, build và E2E frontend cùng backend verify khi push nhánh review.

Đã đối chiếu 16 file code/docs thay đổi và 40 file báo cáo/log với các giá trị phiên, mã người học và dữ liệu nguồn đang có trong bộ nhớ, không thấy khớp. Đây là kiểm tra các giá trị đã biết, không thay thế việc review nội dung. Profile Chrome nghiên cứu và helper đã được xóa, bộ nhớ phiên công cụ đã được giải phóng. Không có helper, browser profile, log hoặc test report nào được stage vào Git.

### Việc còn cần bằng chứng trước khi lưu lịch

- Khóa từng buổi học không đổi khi chuyển ngày/giờ; không chỉ một ID dùng cho nhiều ngày.
- Quan hệ giữa kỳ được chọn và thời gian đào tạo trên hàng đăng ký; ý nghĩa năm học/học kỳ/đợt.
- Tín chỉ từng môn từ nguồn phù hợp, không phải tổng tín chỉ của lượt đăng ký.
- Enrollment/lần học thật để liên kết kỳ thi, và scope/stability của candidate ID thi.
- Mẫu kế hoạch thi chung nếu cần hỗ trợ phần đó; phân trang/độ đầy đủ, trạng thái hủy và lifecycle phiên vẫn UNKNOWN.

Chưa bắt đầu import điểm, GPA, toàn bộ curriculum, prerequisites hoặc grading policy. Không có change detection, thông báo thay đổi, Google Calendar, email hoặc đăng nhập production mới. Kết thúc 4C tại các giới hạn trên để review, không tự chuyển sang Phase 5.

## Phase 5A: môn học, học kỳ, lần học và kết quả

### Kết luận trước khi đọc chi tiết

**PHASE_5A_OVERALL = PARTIAL.** Đã có bộ đọc kết quả học tập thật, tín chỉ từng môn và cách nối điểm với đăng ký của tài khoản. Tuy nhiên, một đăng ký nguồn chưa chắc tương đương một lần học AMS. Đây là điều kiện còn thiếu để lưu, không được giải quyết bằng cách tự đánh lại số lần học hoặc bỏ bớt điểm.

| Phần việc | Trạng thái cuối | Ý nghĩa |
| --- | --- | --- |
| ACADEMIC_RECORD_CAPABILITY | PASS | Java đã gọi HTTP, giải mã và chuẩn hóa nguồn thật; giới hạn đọc lặp nêu bên dưới |
| COURSE_MAPPING | BLOCKED_PARTIAL | ID/mã/tên/tín chỉ đã đối chiếu; chưa tạo Course hoặc bảng ánh xạ nguồn |
| SEMESTER_MAPPING | BLOCKED_PARTIAL | Năm học/học kỳ trong điểm đã hiểu; chưa đầy đủ quan hệ với kỳ đăng ký/thi và chưa persist |
| STUDENT_COURSE_MAPPING | BLOCKED_PARTIAL | Nhiều đăng ký cùng môn/cùng số lần học; chưa biết cần gộp hay tách |
| ACADEMIC_RESULT_PERSISTENCE | BLOCKED_PARTIAL | Chưa có importer điểm vì chưa xác định đúng lần học |
| COURSE_CREDITS | PASS | Tín chỉ từng môn có trong điểm và đăng ký, không lấy từ tổng tín chỉ |
| EXAM_TO_STUDENTCOURSE | UNRESOLVED | Thiếu khóa đăng ký và quan hệ kỳ thi với lần học |
| SCHEDULE_CORRELATION | PARTIALLY_RESOLVED | Nối được lịch tới lớp/đăng ký/môn; chưa tới lần học đã xác minh |

Không có migration mới; database vẫn ở V6. Phần importer/migration thử nghiệm chưa commit đã được bỏ sau khi đối chiếu toàn bộ mẫu thật phát hiện xung đột. Không đổi constraint của StudentCourse để làm dữ liệu vừa khuôn. Những kết quả test ở giai đoạn thử nghiệm không được dùng để báo đã import thành công.

### Nguồn đọc và phạm vi của bộ lọc

Đây vẫn là **PORTAL_INTERNAL_API**, không phải API công khai/chính thức được trường cam kết hỗ trợ. Bộ đọc lấy các chương trình mà tài khoản hiện tại được xem, kiểm tra chương trình yêu cầu thuộc danh sách đó, rồi đọc kết quả và dữ liệu đăng ký hỗ trợ.

| Mục đích | Đường dẫn POST dưới `/sinhvienapi3/api/` | Function thuộc `pkg_congthongtin_hssv_thongtin` |
| --- | --- | --- |
| Chương trình của người học | `SV_ThongTin_MH/DSA4FSkuLyYVKC8CKTQuLyYVMygvKQkuIgPP` | `LayThongTinChuongTrinhHoc` |
| Kỳ tra cứu ở màn hình điểm | `SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikJLiIP` | `LayDSThoiGianLichHoc` |
| Kết quả học tập | `SV_ThongTin_MH/CiQ1EDQgCS4iFSAxAiAPKSAv` | `KetQuaHocTapCaNhan` |
| Đăng ký học của tài khoản | `SV_ThongTin_MH/DSA4CiQ1EDQgBSAvJgo4CS4iAiAPKSAv` | `LayKetQuaDangKyHocCaNhan` |

Các request có `action`, `func`, `iM`, mã chức năng, người thực hiện và người học lấy từ phiên. Request điểm thêm `strDaoTao_ChuongTrinh_Id`; giá trị này là `DAOTAO_TOCHUCCHUONGTRINH_ID` được frontend đưa vào lựa chọn chương trình. Bộ đọc không tin nhãn do caller tự gửi.

Điểm được truy vấn theo chương trình, **không có tham số kỳ** trong request đã quan sát. Danh sách 15 kỳ trên cùng màn hình dùng cho bảng đăng ký, không được tự coi là bộ lọc điểm. Khi màn hình khởi tạo, request đăng ký có bộ lọc kỳ rỗng; mẫu trả 56 hàng đăng ký. Chọn một kỳ hợp lệ trả 3 hàng và ID kỳ trên các hàng khớp bộ lọc đó. Client dùng lượt đăng ký không lọc kỳ để đối chiếu các kết quả thuộc nhiều kỳ, không tuyên bố đó là danh sách đầy đủ mọi đăng ký từ trước tới nay.

Envelope vẫn là `Success` và `Data.B`. `Pager` null, request không có page index/page size; chưa chứng minh được hidden limit hoặc độ đầy đủ. Giữ `completeness=UNKNOWN`, không thêm phân trang giả. Giới hạn hiện tại là 256 chương trình/kỳ, 10.000 hàng cho mỗi bảng được dùng, cùng giới hạn byte và timeout của transport. Vượt giới hạn làm cả lượt đọc thất bại, không cắt bớt danh sách.

### Cách đối chiếu môn và đăng ký

Phản hồi điểm có nhiều bảng. Phase này chỉ chuẩn hóa `rsDiemThanhPhan`, `rsDiemKetThucHocPhan` và kiểm tra chủ tài khoản qua `rsThongTinNguoiHoc`. Các bảng GPA tổng hợp, khối kiến thức và môn chưa hoàn thành được quan sát để hiểu nguồn, không dùng để thay điểm từng lần học hoặc import curriculum.

Một khác biệt quan trọng: bảng cuối môn trong mẫu **không có `DAOTAO_HOCPHAN_ID`**, dù bảng thành phần có. Không thể giả định mọi bảng cùng tên cột rồi chép từng hàng vào domain.

```text
Điểm: DIEM_DANHSACHHOC_ID
    → Đăng ký: DANGKY_LOPHOCPHAN_ID
    → Kiểm tra cùng người học/chương trình
    → ID đăng ký + ID môn + mã/tên môn + tín chỉ + kỳ nguồn
```

**VERIFIED trong mẫu:** `DIEM_DANHSACHHOC_ID` trùng ID lớp, không trùng `ID` hàng đăng ký. Cả 39 kết quả cuối và các điểm thành phần đều nối duy nhất tới một hàng đăng ký của tài khoản. ID môn, mã môn, tín chỉ và kỳ trên các bảng được đối chiếu; không ghép chỉ bằng tên môn. Nếu không tìm thấy đăng ký hoặc có nhiều hàng cạnh tranh cùng lớp trong chương trình, client trả `UNEXPECTED_SCHEMA` cho cả lượt đọc.

`DAOTAO_HOCPHAN_HOCTRINH` được frontend hiển thị ở cột “Số tín chỉ” của từng môn và khớp giữa bảng điểm/đăng ký trong mẫu. Vì vậy **COURSE_CREDITS = PASS**. Không dùng `SOTINCHIDADANGKY`, `TONGSOTINCHI`, tín chỉ học phí hoặc tín chỉ tích lũy tổng để thay thế. Tín chỉ đạt của từng lần học vẫn **UNKNOWN**; qua môn không được tự biến thành một giá trị tín chỉ tích lũy khi chưa xác minh quy tắc.

### Học kỳ: đã rõ hơn ở điểm, chưa rõ toàn bộ cây kỳ nguồn

Frontend nhóm bảng cuối môn bằng hai trường riêng `NAMHOC` và `HOCKY`, rồi hiển thị “Năm học … – Học kỳ …”. Đây là bằng chứng ngữ nghĩa, khác với việc tự lấy số cuối của một nhãn kỳ. Trong bảng cuối, `NAMHOC` có dạng năm_năm; trong bảng thành phần, đó là năm bắt đầu dạng số. Mapper kiểm tra hai năm liên tiếp và đối chiếu năm/học kỳ giữa điểm thành phần với kết quả cuối của cùng đăng ký.

Mẫu có 13 ID kỳ trên các điểm, tương ứng 9 cặp năm học–học kỳ. Mỗi ID kỳ trong mẫu chỉ nối tới một cặp; một học kỳ có thể chứa nhiều ID kỳ nguồn. Có thể dùng cặp năm/học kỳ đã kiểm tra làm đầu vào cho `Semester.Identifier`, nhưng **chưa persist** và chưa xác nhận các ID con chính xác thuộc loại đợt nào.

Các bảng điểm tổng có phân loại phạm vi năm/học kỳ và `DOTHOC`, nhưng chỉ một phần ID khớp trực tiếp với kỳ trên điểm chi tiết. Chưa có quan hệ cha–con đầy đủ. Các nhãn bộ lọc không đủ thay thế quan hệ này; ngày bắt đầu/kết thúc học kỳ cũng chưa được xác minh.

Phát hiện 4C không bị xóa: endpoint đăng ký của 4C từng trả kỳ trên hàng khác kỳ được chọn. Endpoint hỗ trợ của 5A là endpoint khác; một mẫu mới có ID khớp không chứng minh hai luồng có cùng semantics. Kỳ thi đã đọc trong 5A không thuộc danh sách kỳ học của màn hình điểm. Không gộp kỳ thi, kỳ đăng ký và kỳ đào tạo chỉ vì nhãn trông giống nhau.

### Vì sao StudentCourse vẫn chưa lưu được?

Bộ đọc chuẩn hóa 49 đăng ký có điểm, gồm 39 đăng ký có kết quả cuối và 10 đăng ký mới có điểm thành phần. Các đăng ký này liên quan tới 37 ID môn. ID đăng ký là khóa tốt để giữ từng hàng nguồn, nhưng không tự chứng minh quan hệ một-một với một lần học.

**VERIFIED:** có 8 nhóm cùng ID môn và cùng `LANHOC` nhưng thuộc các đăng ký/lớp khác nhau. Cả 8 nhóm có sự trùng trong cùng kỳ nguồn và cùng học kỳ. Mỗi nhóm có nhiều nhất một kết quả cuối môn trong mẫu. Vì vậy, thêm học kỳ vào khóa cũng không giải quyết được vấn đề.

Ví dụ tổng hợp: đăng ký A và B cùng môn TEST101, cùng kỳ, đều báo lần học 1; A có kết quả cuối, B chỉ có điểm thành phần. Có thể chúng thuộc nhiều lớp của một lần học, dữ liệu chuyển lớp hoặc dữ liệu quá trình chưa chốt. **Chưa xác minh phương án nào**, nên không chọn hàng có kết quả cuối rồi bỏ hàng kia, cũng không tự đặt B là lần học 2.

Observation giữ `sourceEnrollmentId`, `sourceSectionId` và `reportedLearningAttempt` riêng. Tên “reported” nhắc rằng đây là số nguồn báo, chưa phải `StudentCourse.attemptNumber` đã xác nhận. `hasAmbiguousLearningAttempts()` trả true khi thấy nhiều đăng ký cùng môn/cùng số đó. Nó là cảnh báo quan hệ cần làm rõ; false không phải giấy phép tự động import.

| Trường/khóa | Scope đã quan sát | Stability và confidence |
| --- | --- | --- |
| ID đăng ký | Một hàng đăng ký của người học/chương trình | Khớp giữa lượt không lọc và lượt lọc đã đọc; OBSERVED, chưa có cam kết lifecycle |
| ID lớp/danh sách học | Nối điểm với lớp trong đăng ký | VERIFIED phép nối trong mẫu; không phải enrollment ID |
| ID môn + mã môn | Nhận diện môn giữa đăng ký và điểm | VERIFIED trong mẫu; chưa tạo UUID/mapping database |
| `LANHOC` | Số lần học nguồn hiển thị | Có mẫu học lại, nhưng không duy nhất giữa các đăng ký; scope toàn cục UNKNOWN |
| `LANTHI` | Lần thi của điểm/kết quả | Giữ riêng; tuyệt đối không dùng làm số lần học |

Các ID điểm cuối/thành phần giữ nguyên giữa hai lượt đọc trình duyệt. Điều này chỉ kiểm chứng đọc lặp trong mẫu, không chứng minh chúng bất biến khi trường chuyển lớp hoặc điều chỉnh kết quả.

### Điểm và trạng thái được giữ như thế nào?

| Dữ liệu | Cách chuẩn hóa | Không suy thêm |
| --- | --- | --- |
| `DIEM` trong bảng thành phần | Score cùng mã/tên thành phần, lần thi và ID nguồn | Không coi là điểm cuối môn |
| `DIEM` trong bảng cuối | Numeric score của kết quả cuối | Không tính GPA từ điểm này |
| `DIEMQUYDOI`, `DIEMQUYDOI_TEN` | Grade points và điểm chữ nếu có | Không tự quy đổi khi null |
| `DANHGIA_MA` | DAT → PASSED; KHONGDAT → FAILED; HOCLAI → RETAKE_REQUIRED trong observation | Không suy trạng thái từ ngưỡng điểm số; không ép “Học lại” thành enum domain |
| `LANHOC`, `LANTHI` | Hai số nguyên dương riêng | Không dùng lần thi thay lần học |

Các mã trạng thái trên được đối chiếu với nhãn nguồn; đây là từ vựng schema, không phải thông tin kết quả của một người trong tài liệu. Trạng thái lạ, ID thiếu, giá trị sai kiểu, quan hệ không khớp hoặc duplicate ID điểm đều làm cả observation thất bại. Nhiều kết quả cuối cho một đăng ký cũng bị từ chối vì chưa có bằng chứng chọn lần nào; không tự chọn điểm cao nhất hoặc mới nhất.

Điểm số dùng `BigDecimal`, không làm tròn âm thầm. Số nguyên được nguồn ghi dạng `1.0` vẫn được nhận nếu chính xác; `1.5`, chuỗi số hoặc giá trị không hợp lệ bị từ chối. Các bảng ngoài phạm vi không được lưu. Không có thao tác xóa hoặc đánh dấu hàng cũ failed/withdrawn khi nguồn trả rỗng.

### Hai cầu nối với Phase 4

**EXAM_TO_STUDENTCOURSE = UNRESOLVED.** Mẫu đọc lại có 6 lịch thi cá nhân, bảng kế hoạch chung rỗng. Các ID lớp vẫn null; kỳ thi không nằm trong danh sách kỳ học đã lấy. Mã môn và lần thi không xác định được duy nhất đăng ký/lần học. Không lấy ngày thi hoặc tự đổi `LANTHI` thành `LANHOC` để ghép.

**SCHEDULE_CORRELATION = PARTIALLY_RESOLVED.** Bốn mục lịch nối được duy nhất tới 3 lớp/đăng ký và môn. Các lớp đang có lịch này chưa xuất hiện trong bảng điểm thành phần đã đọc, nên chưa có số lần học đã đối chiếu. Không tạo StudentCourse từ đăng ký chỉ vì lịch đã có.

Phát hiện `IDLICHHOC` lặp qua nhiều ngày của 4C vẫn giữ nguyên. Không có `ScheduleImportService`, `ExamImportService`, khóa buổi học bịa từ ngày/phòng hoặc cơ chế phát hiện thay đổi trong 5A.

### Phiên, kiểm thử và kiểm chứng thật

Mã chức năng học tập khác hồ sơ/lịch học trong mẫu. Payload phiên bản 3 thêm ngữ cảnh này và vẫn đọc được payload 1/2. AAD mã hóa vẫn phiên bản 1, không đổi khóa, không rewrite ciphertext cũ. Phiên thiếu ngữ cảnh học tập trả `CONNECTION_UNAVAILABLE` riêng cho capability đó, không bị coi là hết phiên.

Các lớp mới không nhận arbitrary URL hoặc learner ID. Adapter tiếp tục kiểm tra user ACTIVE, cặp user/kết nối và trạng thái kết nối trước khi giải mã. Chỉ lỗi đã nhận diện là SESSION_EXPIRED mới yêu cầu kết nối lại. Không thêm controller, không đổi security/session/CSRF.

Lượt local 24–25/09/2026 dùng Chrome profile riêng ngoài repository; chủ tài khoản tự đăng nhập. Chỉ tra cứu dữ liệu của tài khoản đó. Java nhận phiên trong bộ nhớ qua loopback mã hóa AES-GCM, khóa bàn giao bọc RSA-OAEP và đối chiếu fingerprint trước khi gửi; không truyền credential trong command line hoặc file.

- Trình duyệt: 15 kỳ, 56 hàng đăng ký không lọc, 49 đăng ký có điểm, 154 điểm thành phần, 39 kết quả cuối, 8 nhóm mơ hồ. Không xuất ID, tên môn hoặc điểm thật vào tài liệu.
- Lượt Java đầu dừng ở kiểm tra quan hệ trước khi nhập học vụ. Database tạm lúc đó chỉ được dùng cho kết nối/hồ sơ theo luồng 4B; không nhập Course/StudentCourse/AcademicResult. Container được đóng khi helper kết thúc.
- Sau khi tách observation khỏi điều kiện persistence, Java dùng phiên đã qua `PhenikaaSessionCipher` đọc chương trình, kỳ và `fetchAcademicRecords` thành công. Đây là code Java thật, không thay bằng parser của trình duyệt.
- Một lượt kiểm chứng bị timeout; lượt kế tiếp đọc bản đầu thành công nhưng lần đọc lặp lại timeout. **Chưa xác nhận so sánh hai observation Java đạt.** Không tăng timeout, tắt kiểm tra hoặc thêm retry vô hạn để che lỗi; kết quả đọc lặp ổn định đã nói phía trên là của trình duyệt.
- Không xác nhận import database, idempotency hoặc concurrency của điểm. Các phần đó chưa triển khai trong bản bàn giao. Không ghi dữ liệu thật làm seed/fixture, không có live test trên CI.

Bản cuối chạy `mvnw.cmd verify` ngày 25/09/2026 đạt **152 unit/HTTP test và 82 integration test**, không lỗi hoặc bỏ qua. Test mới có mapper môn/kỳ/đăng ký/điểm, học lại tổng hợp TEST101, nhiều đăng ký cùng số lần học, tách lần thi, optional field, duplicate/missing ID, completeness, request scope và lỗi HTTP. Integration test PostgreSQL/Redis kiểm tra ownership, user bị khóa, phiên thiếu capability và lỗi nguồn giữ Course cũ. Các test schema sạch, Hibernate validate và migration hiện có tiếp tục chạy; không có V7 để kiểm tra nâng cấp.

Chưa có test import điểm lần đầu/lặp/đồng thời/rollback database để báo PASS, vì không giữ importer khi điều kiện lần học chưa đạt. Không tính 90 integration test của bản thử trước đó vào kết quả bàn giao. Frontend không thay đổi; CI hiện có vẫn chạy toàn bộ kiểm tra backend/frontend bằng dữ liệu tổng hợp.

Đã quét 18 file thay đổi và 45 file báo cáo/log bằng các giá trị phiên, định danh và dữ liệu nguồn nhạy cảm đang có trong bộ nhớ, không thấy khớp. Đây là kiểm tra các giá trị đã biết, không thay thế review nội dung. Helper và profile nghiên cứu nằm ngoài repository, không được stage; không giữ raw response, HAR hoặc screenshot làm tài liệu nghiên cứu.

### Cần xác minh gì để tiếp tục lưu dữ liệu?

1. Quan hệ giữa các đăng ký cùng môn/cùng `LANHOC`: lớp chính/phụ, chuyển lớp hay nhiều lần học thật; cần nguồn hoặc hành vi UI xác nhận, không chỉ tên trường.
2. Một lần học có một hay nhiều ID đăng ký. Chỉ sau đó mới chọn unique constraint và thiết kế bảng ánh xạ phù hợp.
3. Quy tắc xử lý nhiều kết quả cuối, trạng thái “Học lại” và tín chỉ đạt riêng cho từng lần học.
4. Cây kỳ nguồn và quan hệ với kỳ thi/đăng ký. Mẫu khớp của một endpoint không phủ định mẫu khác của 4C.

Chưa bắt đầu Phase 5B hoặc Phase 6; không import full curriculum, prerequisites, GPA, lịch, change detection, Google Calendar hoặc email. Kết thúc 5A ở phần đọc/đối chiếu đã kiểm chứng để review các giới hạn trên.

## Phase 5B: chương trình đào tạo, danh mục môn và nhóm môn

### Kết quả chính và cách đọc phần này

Phase 5B đã có bộ đọc Java và dịch vụ nhập những phần curriculum đủ bằng chứng. “Curriculum” ở đây là chương trình đào tạo gồm danh mục môn và yêu cầu nhóm, không phải danh sách các môn sinh viên đã học. Một môn có trong chương trình không đồng nghĩa sinh viên đã đăng ký hoặc hoàn thành môn đó.

Lượt kiểm chứng local ngày 26/09/2026 đọc được một lựa chọn chương trình, 88 môn và 14 nhóm. Có 85 môn nối được tới nhóm: 42 bắt buộc và 43 tự chọn. Nhập hai lần giữ nguyên UUID của chương trình, môn, nhóm, liên kết và các bảng ánh xạ. Ba môn chưa xác minh được nhóm chỉ được giữ trong danh mục Course, không tự gán loại để tạo CurriculumCourse.

Kết quả tổng thể là **PARTIAL**, không phải lỗi insert đã được che bằng dữ liệu giả. Phần nhập đã xác minh hoạt động, nhưng chưa biết phiên bản chương trình, chưa xác nhận lựa chọn hiện hành của hồ sơ và chưa lưu được điều kiện tiên quyết có ngưỡng điểm. Các blocker của điểm/lần học ở Phase 5A vẫn còn nguyên.

Trong phần này, **VERIFIED** là điều đã đối chiếu giữa request, dữ liệu và hành vi UI/code; **OBSERVED** là đã thấy trong mẫu nhưng chưa đủ để khái quát. **UNKNOWN** nghĩa là chưa biết, không phải giá trị rỗng hoặc 0. **BLOCKED** đánh dấu phần chưa được triển khai vì thiếu bằng chứng hoặc model chưa biểu diễn đúng. **PROPOSED** chỉ là hướng cần review, không phải tính năng đã có.

### Chương trình nào thuộc tài khoản?

Selector của màn hình Chương trình học gọi `pkg_dangkyhoc_chung.LayDSChuongTrinh`. **VERIFIED:** mỗi lựa chọn có ID người học khớp tài khoản; giá trị UI dùng để gọi các API tiếp theo là `DAOTAO_TOCHUCCHUONGTRINH_ID`. Trường `ID` của chính hàng selector là một ID khác, không được dùng thay.

Adapter đọc lại selector trước mỗi lần lấy curriculum. ID yêu cầu phải nằm trong danh sách của tài khoản; tên/mã do bên gọi truyền không thay thế metadata vừa đọc từ nguồn. UUID của AMS vẫn được tạo nội bộ, còn ID tổ chức chương trình được lưu trong bảng ánh xạ.

Mẫu có mã khóa đào tạo qua `DAOTAO_KHOADAOTAO_MA`, nên lưu được cohort. Chưa thấy revision, thời điểm hiệu lực hoặc lịch sử phiên bản có ý nghĩa rõ. Không tách năm từ tên hiển thị, không gán revision `v1`, không dùng ID nguồn giả làm số phiên bản. Hai tổ chức chương trình có ID khác vẫn được giữ riêng, kể cả cùng mã và chưa biết revision; test tổng hợp kiểm tra trường hợp này.

**OBSERVED:** ID này khớp lựa chọn `AcademicProgram` của màn hình điểm trong tài khoản đã khảo sát. Điều đó đủ để đối chiếu mẫu hiện tại, nhưng không chứng minh mọi chương trình lọc điểm luôn tương đương một phiên bản curriculum. Vì vậy, application vẫn giữ `AcademicProgram` và `CurriculumOption` riêng.

Quan hệ tài khoản → lựa chọn chương trình đã xác minh. Quan hệ tài khoản → chương trình **đang áp dụng** chưa xác minh: selector không giải thích rõ lịch sử/hiện hành. Importer lưu curriculum ứng viên nhưng không gọi `StudentProfile.selectCurriculum`. Có đúng một lựa chọn trong mẫu cũng không đủ để tự suy trạng thái hiện hành cho mọi tài khoản.

### Môn học và các loại tín chỉ

Danh mục lấy từ `LayDSKS_DaoTao_HocPhan_CT`. Mỗi hàng có ID môn, mã, tên, tín chỉ học tập và ID tổ chức chương trình. ID hàng curriculum không thay cho ID môn. Tên có thể đổi nên không dùng tên làm khóa.

| Nguồn | Ý nghĩa đã đối chiếu | Cách dùng trong AMS |
| --- | --- | --- |
| `DAOTAO_HOCPHAN_ID` + `DAOTAO_HOCPHAN_MA` | Môn và mã môn | Tra mapping theo hồ sơ + ID; kiểm tra mã không xung đột |
| `HOCTRINHAPDUNGHOCTAP` | Tín chỉ học tập của môn, khớp cột/chi tiết trên UI | `Course.credits`; kiểm tra giống giá trị ở thành viên nhóm |
| `HOCTRINHAPDUNGTINHHOCPHI` | Tín chỉ tính học phí | Không dùng cho Course |
| `TONGSOTINCHIQUYDINH` ở selector | Tổng tín chỉ quy định của chương trình | `Curriculum.minimumCredits`, không phải tín chỉ đã đạt |
| `SOTINCHIQUYDINH` ở nhóm tự chọn | Số tín chỉ bắt buộc của nhóm trên UI | `CurriculumGroup.minimumCredits` |
| Tổng tín chỉ thành viên nhóm | Phép cộng danh mục trong nhóm | Dùng đối chiếu response, không biến thành một requirement mới |

**VERIFIED trong mẫu trình duyệt:** 56 hàng đăng ký nối tới 40 môn riêng biệt trong danh mục; không có xung đột ID/mã/tín chỉ. Cùng mã nhưng khác ID chưa xuất hiện trong mẫu, không có nghĩa trường hợp đó bất khả thi. Nếu gặp, importer trả `IDENTITY_CONFLICT` thay vì gộp. Tín chỉ khác giá trị đã lưu trả `CREDIT_CONFLICT`, không âm thầm chọn nguồn mới thắng nguồn cũ.

Typed mapping cho Course dùng chung trong phạm vi hồ sơ, không tách “môn bảng điểm” và “môn curriculum”. Phase sau có thể dùng nó để tìm UUID đã nhập. Việc tìm được Course không giải quyết quan hệ giữa nhiều đăng ký với một StudentCourse.

### Bắt buộc, tự chọn và yêu cầu nhóm

Phân loại dựa vào hai luồng riêng mà UI thực sự gọi: nhóm bắt buộc và nhóm tự chọn đơn, cùng API thành viên tương ứng. Không suy từ màu, vị trí hoặc chữ trong tên nhóm.

Đã mở cả 5 nhóm bắt buộc và 9 nhóm tự chọn trong UI. ID nhóm, ID chương trình, mã môn và tín chỉ ở các thành viên khớp danh mục. Số thành viên và tổng tín chỉ khớp tổng nguồn báo; không có môn thuộc nhiều nhóm trong mẫu.

Tên nhóm được lưu như tên bình thường, không trở thành enum Phenikaa trong core domain. Domain chỉ phân biệt REQUIRED/ELECTIVE. Nhóm bắt buộc vẫn có entity riêng để giữ cấu trúc chương trình; tổng tín chỉ của nhóm này chưa được hiểu thành “minimum credits”, nên trường đó để null. Với nhóm tự chọn, nguồn có yêu cầu tín chỉ; 4 nhóm còn có yêu cầu số môn, 5 nhóm không cung cấp giá trị này.

Ví dụ tổng hợp: một nhóm chứa bốn môn, mỗi môn ba tín chỉ, nhưng yêu cầu chọn sáu tín chỉ. Tổng danh mục là mười hai, requirement là sáu; hai số không được hoán đổi. Nếu nguồn còn yêu cầu hai môn, AMS lưu riêng cả hai điều kiện, chưa tính sinh viên đã thỏa chúng hay chưa.

Các trường hợp chưa được diễn giải sẽ làm lượt đọc dừng, không bị bỏ qua âm thầm:

- Nhóm có cha: mẫu hiện chỉ xác minh nhóm phẳng, chưa có quy tắc cho cây nhóm.
- Một môn thuộc nhiều nhóm: chưa có quy tắc tính đóng góp tín chỉ hoặc tránh đếm đôi.
- `LAHOCPHANBATBUOC` của thành viên nhóm tự chọn có giá trị: tất cả mẫu hiện là null, chưa xác minh ý nghĩa của override này.
- Tổng nhóm không khớp thành viên, ID ngoài curriculum, mã/tín chỉ không khớp catalog hoặc duplicate ID.

Ba môn ngoài các nhóm đã đọc vẫn tồn tại trong Course. Requirement của chúng là UNKNOWN, không phải tự chọn mặc định. Trường học kỳ kế hoạch trong catalog cũng chưa dùng làm học kỳ thực tế của kết quả học tập.

### Điều kiện tiên quyết: đã thấy nguồn nhưng chưa lưu quan hệ

Danh mục có phần tóm tắt quan hệ ở 46 môn. Đã mở chi tiết một môn có tóm tắt và quan sát request `LayDSKS_DaoTao_QuanHeHocPhan`. Phản hồi có môn hiện tại, môn liên quan, loại quan hệ, mức điều kiện, toán tử và giá trị điều kiện. Java cũng đã đọc được điều kiện này.

**OBSERVED:** mẫu là quan hệ tiên quyết với mức điều kiện liên quan đến điểm, toán tử `>=` và threshold không rỗng. Tài liệu không ghi ngưỡng hoặc môn thật của tài khoản. **UNKNOWN:** thang điểm áp dụng, cách ghép nhiều hàng thành AND/OR, biểu thức lồng nhau, song hành và phạm vi đầy đủ của các loại quan hệ.

`CourseRelationObservation` giữ mô tả điều kiện, gồm nhãn quan hệ/mức/toán tử và threshold; không biến chúng thành một biểu thức có thể chạy. Chỉ đọc chi tiết một môn, không tuyên bố đã kiểm tra tất cả 46 phần tóm tắt hoặc toàn bộ tiên quyết của chương trình.

Model `CoursePrerequisite` hiện chỉ biểu diễn cạnh giữa hai môn. Nếu bỏ ngưỡng điểm rồi chỉ lưu cạnh A → B, AMS sẽ làm mất một phần điều kiện. Tương tự, A OR B không được tách thành hai cạnh bắt buộc vì khi đó thành A AND B. Vì vậy **PREREQUISITE_PERSISTENCE = BLOCKED_PARTIAL**; không có cạnh nguồn nào được insert, không đặt ngưỡng giả hoặc tạo grading policy để lấp chỗ thiếu.

Kiểm tra chống tự tham chiếu và chu trình của Phase 2 vẫn giữ, gồm test tổng hợp A → B → A. Đây là invariant của model cũ, không phải kiểm chứng rằng toàn bộ đồ thị nguồn không có chu trình. **PROPOSED:** review model điều kiện sau khi có thêm mẫu/semantics; không triển khai eligibility engine trong 5B.

### Độ đầy đủ và phân trang

UI tải catalog/nhóm/thành viên bằng `pageIndex=1` và pageSize rất lớn. Envelope `Pager` là chuỗi số; trong các phản hồi đã đối chiếu, nó khớp số hàng tổng. Java không sao chép pageSize hàng triệu từ UI mà dùng 100 hàng/trang, với giới hạn rõ:

| Giới hạn | Giá trị |
| --- | --- |
| Số trang mỗi danh sách | 20 |
| Số môn catalog / thành viên một nhóm | 2.000 |
| Số lựa chọn curriculum / tổng nhóm trong một curriculum | 64 |
| Số điều kiện trong một lượt chi tiết môn | 256 |
| Tổng request cho một lần gọi capability | 128 |

Java thật đã đọc thành công với pageSize 100; mẫu chỉ có 88 môn nên chưa kiểm chứng nhiều trang trên portal thật. Test HTTP tổng hợp có 101 môn để buộc đọc trang hai, cũng kiểm tra lặp trang, tổng đổi giữa chừng, trang rỗng sớm và vượt giới hạn. Có đủ số hàng theo Pager vẫn không chứng minh đã có mọi phiên bản, tab ẩn hoặc dữ liệu chưa công bố, nên `completeness` luôn là UNKNOWN.

Timeout và giới hạn byte mỗi response kế thừa transport hiện có. Tổng số request có chặn nhưng chưa có deadline chung cho cả tác vụ. Khi nhiều nhóm/trang, thời gian giữ khóa database có thể dài; phase này chưa có worker nền hay distributed lock.

### Nhập dữ liệu và xử lý lỗi

`CurriculumImportService` yêu cầu user ACTIVE, kết nối thuộc user đó và StudentProfile đã có. Source observation được kiểm tra trước khi thêm/cập nhật Curriculum, Course, CurriculumGroup và CurriculumCourse. Bảng ánh xạ nguồn giữ UUID ổn định qua các lần nhập; chi tiết FK/unique/nullability ở [database model](database-model.md#ánh-xạ-nguồn-và-nhập-curriculum-trong-phase-5b).

Đổi tên hoặc metadata/yêu cầu nhóm được cập nhật cùng entity khi identity giữ nguyên. Cohort/yêu cầu số môn chưa biết không xóa giá trị cũ. Mã xung đột, tín chỉ khác hoặc chuyển nhóm chưa có quy tắc sẽ làm transaction rollback. Không sửa dữ liệu của hồ sơ khác.

Khi nguồn trả rỗng/thiếu nhưng completeness UNKNOWN, chỉ cập nhật những hàng thực sự nhận được: không xóa course, curriculum, group hoặc membership cũ; không xóa prerequisite; không bỏ curriculum đã chọn. Một lượt nhập thất bại không để lại nửa chương trình. Lỗi nguồn trả mã an toàn; chỉ SESSION_EXPIRED mới yêu cầu kết nối lại.

Chưa có API import hoặc giao diện cấp phiên. Tính năng vẫn mặc định tắt. Payload phiên phiên bản 4 thêm ngữ cảnh curriculum riêng và đọc được phiên bản 1–3; phiên cũ thiếu capability mới không làm các capability cũ bị vô hiệu hóa. Không đổi CSRF, cookie, fixed host allowlist, redirect policy hoặc tắt kiểm tra để gọi được portal.

### Tra cứu endpoint đã quan sát

Tất cả dùng POST trên host portal hiện có; đây là `PORTAL_INTERNAL_API`, không phải official/public API. Field `A` chứa request đã chuyển đổi; response `Data.B` được giải mã bằng ngữ cảnh phiên trong bộ nhớ. Không giữ request/response thật làm fixture.

| Mục đích | Path sau host | Function |
| --- | --- | --- |
| Selector | `/dangkyhocapi3/api/DKH_Chung_MH/DSA4BRICKTQuLyYVMygvKQPP` | `pkg_dangkyhoc_chung.LayDSChuongTrinh` |
| Catalog | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCS4iESkgLx4CFQPP` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_HocPhan_CT` |
| Nhóm bắt buộc | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKAMgNQM0LiIP` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_KhoiBatBuoc` |
| Nhóm tự chọn | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCikuKBU0AikuLx4FLi8P` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_KhoiTuChon_Don` |
| Thành viên bắt buộc | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCREeCikuKAMgNQM0LiIP` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_HP_KhoiBatBuoc` |
| Thành viên tự chọn | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eCREeChU0AikuLx4FLi8P` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_HP_KTuChon_Don` |
| Điều kiện môn | `/kehoachchuongtrinhapi/api/KHCT_ThongTin_MH/DSA4BRIKEh4FIC4VIC4eEDQgLwkkCS4iESkgLwPP` | `pkg_kehoach_thongtin.LayDSKS_DaoTao_QuanHeHocPhan` |

Request chung có `action`, `func`, `iM`, mã chức năng và ID người thực hiện. Selector thêm ID người học; catalog dùng `strDaoTao_ChuongTrinh_Id`; nhóm/thành viên/quan hệ dùng `strDaoTao_ToChucCT_Id`. Thành viên thêm ID nhóm đúng loại, chi tiết môn thêm `strDaoTao_HocPhan_Id`. Các bộ lọc keyword, thuộc tính, thời gian kế hoạch/thực tế, phạm vi đảm nhiệm hoặc môn liên quan được để rỗng đúng request UI đã quan sát; không tự đặt bộ lọc làm mất dữ liệu. Reader và test HTTP ghi chính xác tên parameter cho từng endpoint.

### Kiểm chứng thật và kiểm thử tự động

Lượt local ngày 26/09/2026 dùng phiên do chủ tài khoản tự đăng nhập, Chrome profile và helper riêng ngoài repository. Phiên được chuyển trong bộ nhớ qua loopback mã hóa AES-GCM, khóa bàn giao bọc RSA-OAEP và kiểm tra fingerprint trước khi gửi. Không truyền credential qua command line, file cấu hình hoặc log. Java dùng đúng client/service production, không thay bộ đọc bằng dữ liệu browser đã parse.

Database PostgreSQL/Redis tạm chỉ phục vụ lượt kiểm chứng và được đóng sau khi helper kết thúc. Tài khoản AMS trong database này là tài khoản tổng hợp; không nhập vào database phát triển đang dùng. Luồng kết nối vẫn xác minh hồ sơ thật và mã hóa phiên như Phase 4B.

Kết quả đã kiểm tra:

- Selector trả một curriculum; reader trả 88 môn, 14 nhóm và 85 membership, completeness UNKNOWN.
- Lượt nhập đầu và lượt nhập lặp giữ nguyên toàn bộ UUID, không tăng số hàng. `student_profile.curriculum_id` vẫn null vì chưa xác minh lựa chọn hiện hành.
- Có 0 CoursePrerequisite, 0 StudentCourse và 0 AcademicResult được nhập.
- Reader chi tiết trả một điều kiện quan hệ; điều kiện không được chuyển thành cạnh tiên quyết.
- Lượt đầu timeout ở bước đối chiếu học tập sau khi import đã đạt. Thử lại đúng một lần, không đổi timeout: đọc được 49 bản ghi học tập, nối đủ tới 37 Course đã lưu, khớp ID/mã/tín chỉ. Cảnh báo nhiều đăng ký cùng lần học vẫn là true.

Đây là kiểm chứng một tài khoản/một mẫu, không phải kiểm thử tải hoặc chứng minh portal luôn ổn định. Tính ổn định của import đồng thời được kiểm tra bằng nguồn tổng hợp trong integration test, không gọi portal đồng thời để thử tải.

Bộ `mvnw.cmd verify` có **182 unit/HTTP test và 106 integration test**, không lỗi hoặc bỏ qua. Trong đó có 8 test mapper curriculum, 20 HTTP test curriculum và 24 integration test mới cho import/migration. Các test nền tảng về chu trình tiên quyết, tài khoản, mã hóa, ownership và mapping domain vẫn chạy. PostgreSQL/Redis được khởi động thật bằng Testcontainers; schema sạch V1–V7, nâng cấp V6–V7 và Hibernate validate đều được kiểm tra.

HTTP test dùng server loopback và fixture tổng hợp: selector, catalog, nhóm, quan hệ, dữ liệu rỗng, phân trang, schema lạ, business failure, decode failure, unauthorized, redirect, timeout và response quá lớn. Không có live test gọi Phenikaa trong CI. Không sửa frontend, dependency, workflow hoặc migration V1–V6 để làm test pass.

Trước commit đã quét 30 file thay đổi và 56 file báo cáo/log bằng 1.661 giá trị nguồn/phiên đang có trong bộ nhớ. Hai kết quả khớp chỉ là nhãn chung “Học kỳ” và “Điều kiện” trong tài liệu, không phải định danh hoặc credential. Không thấy giá trị nhạy cảm đã biết trong các file được kiểm tra. Scan này bổ sung cho review nội dung và danh sách staged, không phải lời bảo đảm rằng một biểu thức tìm kiếm có thể phát hiện mọi dạng secret. Chỉ dùng fixture tổng hợp; không đưa payload thật, profile, helper, HAR, screenshot hoặc báo cáo runtime vào Git.

### Trạng thái nghiệm thu

| Tiêu chí | Trạng thái | Phạm vi kết luận |
| --- | --- | --- |
| CURRICULUM_CAPABILITY | PASS | Java đọc selector, catalog, nhóm và thành viên thật |
| CURRICULUM_IDENTITY | PASS | ID tổ chức chương trình được đối chiếu; chưa biết revision/hiện hành |
| CURRICULUM_PERSISTENCE | PASS | Chỉ phần đủ bằng chứng; nhập lặp giữ UUID, không tự chọn cho hồ sơ |
| COURSE_CATALOG_MAPPING | PASS | Mapping theo hồ sơ + source ID, có kiểm tra mã và collision |
| COURSE_CREDITS | PASS | Tín chỉ học tập khớp catalog, thành viên nhóm và Course đối chiếu từ điểm |
| REQUIRED_ELECTIVE_MAPPING | BLOCKED_PARTIAL | Đã xác minh 85/88 môn; ba môn chưa được gán loại |
| CURRICULUM_GROUP_MAPPING | PASS | 14 nhóm phẳng và requirement đã đối chiếu; chưa hỗ trợ cây nhóm |
| PREREQUISITE_SOURCE | PARTIAL | Có nguồn thật và một điều kiện đã đọc; chưa phủ toàn bộ quan hệ |
| PREREQUISITE_PERSISTENCE | BLOCKED_PARTIAL | Model thiếu điều kiện điểm tối thiểu và biểu thức AND/OR |
| PHASE_5B_OVERALL | PARTIAL | Phần nhập đã hoạt động; giữ nguyên các giới hạn ở trên để review |

### Những giới hạn phải giữ khi review phase sau

- **COURSE_MAPPING_5A_AFTER_5B = PARTIALLY_RESOLVED:** đã có UUID/mapping và đối chiếu môn trùng nguồn trong mẫu; chưa khái quát lifecycle ID hoặc catalog cho mọi chương trình.
- **STUDENT_COURSE_MAPPING = BLOCKED_PARTIAL:** vẫn có 8 nhóm đăng ký cùng môn/cùng LANHOC. Không chọn hàng mới nhất/có điểm cuối, không tự gộp, tăng attempt hoặc bỏ hàng.
- **SEMESTER_MAPPING = BLOCKED_PARTIAL:** kỳ kế hoạch trong curriculum không thay cho học kỳ thực tế; chưa giải quyết cây kỳ nguồn/kỳ thi.
- Điểm vẫn thiếu quy tắc nhiều kết quả cuối, trạng thái học lại và tín chỉ đạt riêng từng lần học. Không có AcademicResult importer.
- **EXAM_TO_STUDENTCOURSE = UNRESOLVED**, **SCHEDULE_CORRELATION = PARTIALLY_RESOLVED**. ID lịch lặp qua nhiều ngày vẫn không phải occurrence key. Không có import lịch/thi.

Chưa bắt đầu Phase 5C, 4D hoặc 6. Chưa tính GPA, điều kiện đăng ký, môn còn thiếu, change detection, Google Calendar hoặc email.

## Phase 5C: đối chiếu lần học và chi tiết kết quả

### Kết quả chính

**PHASE_5C_OVERALL = PARTIAL.** Đã tìm được quan hệ nguồn giải thích vì sao không thể biến từng đăng ký thành một StudentCourse: ở cả tám nhóm đang mơ hồ, chi tiết của một kết quả tổng kết chứa điểm thành phần từ hai lớp khác nhau. Quan hệ này được nguồn trả về theo ID kết quả, không phải do AMS tự gom các hàng có cùng môn và số lần học.

Tuy vậy, “cùng góp điểm vào một kết quả tổng kết” chưa đủ để xác định một lần học tồn tại lâu dài. Chưa có quy tắc cho lúc chưa có điểm tổng kết, khi chuyển lớp hoặc khi tổng kết được sửa/tạo lại. Cũng chưa rõ tín chỉ đạt và việc tính GPA của từng lần học. Vì vậy, phase này bổ sung bộ đọc chi tiết và chuẩn hóa định danh học kỳ, **chưa có AcademicRecordImportService, chưa ghi Semester/StudentCourse/AcademicResult từ Phenikaa và chưa thêm V8**.

Baseline là `3600f2c89991d2b45fbe8f80018120b0482e2a4c`, commit hợp nhất Phase 5B. CI của baseline đã thành công trước khi bắt đầu; `AMS-Solution` được fast-forward tới commit đó. Repository vẫn PUBLIC. Không sửa migration V1–V7, dependency hoặc workflow.

### Tám nhóm đăng ký đã rõ thêm điều gì?

Lượt nghiên cứu ngày 29/09/2026 đọc lại 56 đăng ký, 154 điểm thành phần và 39 kết quả tổng kết. Bộ đọc 5A chuẩn hóa chúng thành 49 đăng ký có điểm. Vẫn có tám nhóm cùng môn, cùng số `LANHOC`, nhưng mỗi nhóm gồm hai đăng ký/lớp.

Nút **Chi tiết** cạnh một dòng tổng kết gọi `LayDSDiemThanhPhanTheoTKHP`, truyền ID của chính dòng đó. Đã mở đủ tám dòng tổng kết tương ứng. ID từng điểm thành phần trả về đều tìm được trong bảng điểm thành phần ban đầu; từ đó nối tới đăng ký đã kiểm tra quyền sở hữu. Cả tám lượt đều bao phủ hai lớp trong nhóm và khớp số lần học/lần thi.

Ví dụ tổng hợp, không phải dữ liệu của tài khoản:

```text
Kết quả tổng kết R
  ├─ điểm thành phần P1 → đăng ký A → lớp X
  └─ điểm thành phần P2 → đăng ký B → lớp Y
```

Điều đã xác minh là các cạnh R → P1/P2 và P1/P2 → A/B. **Chưa có cạnh R → định danh lần học ổn định**. Không dùng R làm khóa StudentCourse: R là định danh kết quả tổng kết, có thể có vòng đời khác lần học.

Trang tra cứu đăng ký bổ sung ngữ cảnh: đã duyệt mười lựa chọn kỳ của chính tài khoản và đối chiếu đủ 56 ID đăng ký. UI hiển thị `THUOCTINHLOP_TEN` như loại lớp, chẳng hạn nhãn lý thuyết, thực hành hoặc học trực tuyến. Bảy nhóm có hai loại lớp khác nhau; một nhóm có cùng loại. Điều này cho thấy không thể giải thích mọi nhóm bằng một quy tắc “một lớp lý thuyết + một lớp thực hành”.

`MANHOMLOP` chỉ được frontend dùng làm tên lớp CSS trong đoạn hiển thị đã đọc, chưa thấy nó được dùng làm khóa lần học. `LOPHOCPHANCHINH` có giá trị 0/1 trong dữ liệu nhưng đoạn UI này không giải thích vòng đời của cờ. Không chọn lớp có cờ 1 làm lần học hoặc gộp chỉ vì mã nhóm giống nhau. Lịch sử đăng ký có 93 hàng, nhưng không cung cấp quan hệ định danh đăng ký cũ → đăng ký thay thế đủ rõ để quyết định trường hợp chuyển/hủy lớp.

**Kết luận cho tám nhóm:** đã xác minh hai đăng ký cùng đóng góp vào một tổng kết; chưa xác minh đầy đủ nguyên nhân nghiệp vụ tạo ra từng cặp, hay một quy tắc định danh lần học dùng được qua mọi trạng thái. Hai nhóm chỉ có điểm thành phần, chưa có tổng kết, cũng không thể dùng endpoint chi tiết này để tìm khóa cha. Không loại chúng khỏi lượt nhập rồi báo phần còn lại thành công.

### Bộ đọc mới lưu giữ bằng chứng gì?

`fetchAcademicResultDetail` là capability đọc, chưa có REST endpoint hoặc giao diện AMS cho thao tác này. Caller cung cấp chương trình và ID kết quả; adapter kiểm tra user ACTIVE và kết nối thuộc user trước khi đọc. HTTP client đọc lại chương trình, điểm và đăng ký của tài khoản. ID kết quả phải có trong observation vừa xác minh, không được lấy một ID bất kỳ rồi gọi endpoint chi tiết.

Mỗi hàng chi tiết phải nối bằng ID tới đúng một điểm thành phần đã đọc. Mapper đối chiếu môn, mã môn, tín chỉ, năm học, học kỳ, kỳ nguồn, lần học, lần thi, loại điểm và giá trị điểm. Các đăng ký có thể khác nhau, nhưng không được thuộc môn/lần học/học kỳ khác với tổng kết đang xem. Không ghép theo tên môn hoặc giá trị điểm giống nhau.

Kết quả trả về là `AcademicResultDetail`: ID tổng kết và các liên kết tới ID điểm thành phần/đăng ký. Nó **không phải** AcademicResult đã persist, cũng không phải danh sách StudentCourse đã được hòa giải. Các `toString` chỉ trả nhãn redacted, không in ID hoặc điểm.

Duplicate ID, ID chưa có trong observation hoặc giá trị thay đổi giữa hai request làm lượt đọc thất bại với `UNEXPECTED_SCHEMA`. Khi nguồn thay đổi trong lúc đọc, dừng an toàn tốt hơn việc nối hai phiên bản dữ liệu không khớp. Chi tiết rỗng được giữ là danh sách rỗng; không suy rằng lần học bị hủy hoặc dữ liệu trước đó phải bị xóa. Độ đầy đủ vẫn chưa được nguồn cam kết.

### Học kỳ: đã có cách chuẩn hóa, chưa có bảng ánh xạ mới

Frontend nhóm tổng kết bằng `NAMHOC` và `HOCKY`, hiển thị rõ năm học và học kỳ. Phase 5C kiểm tra lại logic này: 13 ID kỳ trên điểm tương ứng chín cặp năm–học kỳ; mỗi ID kỳ trong mẫu chỉ thuộc một cặp. Vì vậy nhiều kỳ nguồn có thể cùng thuộc một học kỳ AMS.

`AcademicRecordObservation.Entry.semesterIdentifier()` dùng quy ước `T` + số học kỳ, cùng `academicYearStart`. Ví dụ giả định: năm học 2026–2027, học kỳ 1 → `2026-2027:T1`. `T1` là mã nội bộ, không phải ID hoặc tên kỳ Phenikaa. Bộ đọc kiểm tra năm hợp lệ, hai năm liên tiếp và số học kỳ nguyên dương; không tách học kỳ từ ngày học, tên lớp hoặc nhãn bộ lọc.

Mapper dùng định danh này để phát hiện một ID kỳ điểm bị gắn với nhiều học kỳ khác nhau. Test có hai ID kỳ khác nhau cùng cho `2026-2027:T1`, nhưng vẫn giữ riêng hai đăng ký. Chuẩn hóa học kỳ không tự giải quyết việc gộp lần học.

Chưa tạo bảng mapping vì chưa có luồng ghi học vụ đủ điều kiện. Nếu triển khai sau khi gate đạt, khóa nguồn cần có loại kỳ rõ ràng và cho phép nhiều kỳ điểm → một Semester; không đặt unique trên riêng `(profile_id, semester_id)`. Kỳ đăng ký, kỳ thi, kỳ đào tạo và học kỳ kế hoạch của curriculum vẫn là các phạm vi khác nhau. Ngày bắt đầu/kết thúc chưa biết phải để null, không dùng null để xóa ngày đã biết.

### Vì sao vẫn chưa lưu StudentCourse và AcademicResult?

| Phần cần biết | Bằng chứng hiện có | Điều còn thiếu |
| --- | --- | --- |
| Course | V7 đã có mapping theo hồ sơ + source ID; đối chiếu mã/tín chỉ ở 5B | Không tạo danh mục môn riêng cho điểm; phase này không nhập lại catalog |
| Lần học | UI tách “Lần học” và “Lần thi”; tám tổng kết liên kết điểm từ nhiều đăng ký | Khóa lần học ổn định khi chưa có tổng kết, chuyển lớp hoặc sửa kết quả |
| Kết quả hiện hành | UI duyệt từng hàng tổng kết theo học kỳ | Không có quy tắc chọn một hàng khi có nhiều kết quả của cùng lần học |
| Tín chỉ đăng ký học | Có tín chỉ của môn trên đăng ký/điểm | Chưa xác minh tín chỉ ở mức lần học, đặc biệt với nhiều đăng ký góp vào một tổng kết |
| Tín chỉ đạt | Có tổng tín chỉ tích lũy và trạng thái hoàn thành môn trong khối | Không có tín chỉ đạt riêng gắn với từng lần học đã nhận diện |
| GPA | Nguồn có điểm quy đổi, điểm chữ và các bảng tổng hợp | Không có cờ tính/không tính GPA của từng lần học đã xác minh |

`LANHOC` là số UI gọi “Lần học”; `LANTHI` là “Lần thi”. Quan hệ mới không cho phép đổi số lần học, dùng lần thi làm lần học hoặc đánh lại 1, 2, 3 để thỏa unique constraint. Cũng không lấy một lớp bất kỳ làm `StudentCourse.sectionId` khi một tổng kết có điểm từ nhiều lớp.

Đoạn tạo bảng tổng kết hiển thị các hàng nguồn, không có bước chọn điểm cao nhất, ngày mới nhất hoặc lần thi lớn nhất. Bảng `rsDiemMoiNhat` được dùng cho phần điểm mới, không chứng minh một quy tắc chọn current AcademicResult cho mỗi lần học. Guard từ 5A vẫn từ chối nhiều kết quả cuối trong cùng đăng ký; chưa thay bằng quy tắc phỏng đoán.

Nguồn tích lũy theo khối có `KETQUA`, được UI hiển thị thành “Hoàn thành”, nhưng hàng đó không có ID lần học/lần thi để nối chắc chắn về từng kết quả. `SODATICHLUY` thuộc bảng tổng hợp khối; không chia ngược tổng này để dựng tín chỉ đạt cho từng lần học. “Đạt” cũng chưa tự động được chuyển thành toàn bộ tín chỉ môn, và “Không đạt” chưa được dùng để tự điền 0.

`DAT`, `KHONGDAT`, `HOCLAI` tiếp tục được giữ như outcome của observation. Chưa ép `RETAKE_REQUIRED` thành `AcademicResult.Status.FAILED`. Điểm số, điểm chữ và điểm quy đổi được giữ đúng nguồn; không tính lại bằng thang 10/4 hoặc tạo GradingPolicy mặc định.

Domain hiện bắt buộc `creditsEarned` và boolean `includedInGpa`. Boolean false có nghĩa “không tính GPA”, không có nghĩa “chưa biết”. Có thể cần sửa representation sau này, nhưng chỉ nới hai field đó vẫn không giải quyết identity và lựa chọn kết quả. Phase này không sửa schema chỉ để ghi được dữ liệu chưa đủ nghĩa.

### Transaction, lỗi và các điều kiện ghi chưa thực hiện

Không có write transaction mới cho học vụ, không có partial import, không xóa dữ liệu vắng mặt. Capability mới đi qua adapter kết nối hiện có; adapter vẫn cập nhật trạng thái/lần truy cập kết nối và giữ transaction qua HTTP. Đây là giới hạn sẵn có của lớp kết nối, **chưa được sửa trong 5C**. Các test lỗi nguồn kiểm tra dữ liệu học vụ không đổi; chỉ `SESSION_EXPIRED` yêu cầu kết nối lại.

Khi có đủ bằng chứng để xây importer, cần tách đọc/kiểm tra nguồn khỏi transaction ghi ngắn, đồng thời kiểm tra lại quyền sở hữu và trạng thái kết nối trước khi ghi. Không sao chép cách giữ khóa user qua toàn bộ HTTP của importer curriculum. Đây là thiết kế dự kiến, không phải một bảo đảm concurrency đã được triển khai.

Không có test import điểm lần đầu/lặp/đồng thời để báo PASS. UUID ổn định qua import, retake persisted, nhiều đăng ký → một StudentCourse persisted và rollback của importer điểm đều chưa được kiểm chứng vì importer chưa tồn tại. Các test domain/persistence tổng hợp cũ vẫn chạy, nhưng không được dùng thay cho việc kiểm chứng semantics Phenikaa.

### Kiểm chứng thật và kiểm thử

Chủ tài khoản tự đăng nhập vào Chrome profile tạm ngoài repository. Chỉ tra cứu dữ liệu thuộc tài khoản; không xác nhận, sửa hoặc hủy đăng ký. Request/response được đối chiếu trong bộ nhớ, không ghi HAR, screenshot hay payload thật thành fixture.

Trình duyệt đọc điểm/đăng ký hai lần: identity của môn, đăng ký, kỳ, thành phần và tổng kết giữ nguyên. Tám chi tiết tổng kết cũng được đọc hai lần, giữ nguyên tập ID thành phần. Đây là độ ổn định của hai lượt đọc cùng mẫu, chưa chứng minh định danh không đổi khi nhà trường chỉnh dữ liệu.

Helper Java tạm dùng đúng `PhenikaaHttpClient` production. Phiên được chuyển một lần qua loopback bằng AES-GCM; khóa AES được bọc RSA-OAEP và fingerprint khóa công khai được đối chiếu trước khi gửi. Không truyền credential trong command line hoặc file.

| Metadata từ lượt Java | Kết quả |
| --- | --- |
| recordCount | 49 |
| semesterCount | 9 |
| stable | true, hai observation bằng nhau |
| ambiguous | true |
| detailComponentCount | 4 |
| detailRegistrationCount | 2 |
| success | true |
| persistencePerformed | false |

Java kiểm chứng một chi tiết tổng kết; tám chi tiết được đối chiếu bằng trình duyệt. Không nói Java đã đọc đủ tám. Không dùng database thật cho lượt đọc này, không có dữ liệu cá nhân làm seed. Integration test PostgreSQL/Redis dùng dữ liệu tổng hợp, không gọi Phenikaa.

Test mới bao gồm chuẩn hóa học kỳ, năm sai, nhiều kỳ nguồn cùng học kỳ, giữ cảnh báo khi cùng môn/lần học/kỳ, liên kết thành phần qua nhiều đăng ký, từ chối khác lần học, duplicate/missing/changed component và `toString` redacted. HTTP test kiểm tra endpoint chi tiết, quyền sở hữu kết quả, rỗng, malformed, duplicate, business failure, unauthorized, redirect, decode, timeout và giới hạn byte. Test kết nối kiểm tra owner, user bị khóa, phiên thiếu capability và lỗi nguồn không tạo/sửa học vụ.

Lượt `mvnw.cmd verify` cuối ngày 29/09/2026 đạt **205 unit/HTTP test và 114 integration test**, không lỗi, thất bại hoặc bỏ qua. Docker chạy PostgreSQL/Redis thật qua Testcontainers; test schema sạch V1–V7 và Hibernate validate vẫn đạt. Không có V8 để kiểm tra nâng cấp V7–V8. Frontend không thay đổi; CI vẫn dùng workflow hiện có, không gọi portal thật.

Trước commit đã đối chiếu 13 file thay đổi và 53 file báo cáo/log với 2.075 chuỗi nguồn/phiên đang có trong bộ nhớ. Các kết quả khớp thuộc host/path HTTP, kiểu nội dung và từ vựng schema/trạng thái đã mô tả, không phải credential hoặc nội dung học tập riêng. Đây là kiểm tra giá trị đã biết kết hợp review nội dung, không phải bảo đảm một phép tìm chuỗi có thể phát hiện mọi secret. Profile/helper tạm nằm ngoài repository; không stage payload, profile, helper hoặc báo cáo runtime.

### Tra cứu endpoint chi tiết

Endpoint mới được quan sát trên portal, vẫn là **PORTAL_INTERNAL_API**, không phải API công khai được trường cam kết hỗ trợ:

- POST `/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIFKCQsFSkgLykRKSAvFSkkLhUKCREP`.
- Function: `pkg_congthongtin_hssv_thongtin.LayDSDiemThanhPhanTheoTKHP`.
- ID cha: `strDiem_NguoiHoc_TongKet_Id`, lấy từ tổng kết thuộc tài khoản vừa đọc.
- Envelope: `Success`, `Data.B`; giải mã bằng ngữ cảnh phiên đang có.
- Không thấy phân trang trong lượt UI. Client chặn 10.000 hàng và dùng giới hạn byte/timeout transport hiện có, không cắt bớt danh sách.

Frontend hiện gọi endpoint này khi bấm chi tiết. Hàm popover cũ có đoạn lọc theo môn/lần học/lần thi nhưng không phải handler đang dùng; không lấy code cũ đó làm bằng chứng gộp.

### Trạng thái nghiệm thu và phần còn lại

| Gate | Trạng thái | Phạm vi |
| --- | --- | --- |
| SEMESTER_MAPPING | BLOCKED_PARTIAL | Chuẩn hóa identity đã kiểm chứng; chưa persist Semester/mapping |
| LEARNING_ATTEMPT_MODEL | BLOCKED_PARTIAL | Có quan hệ tổng kết–thành phần, chưa có identity xuyên vòng đời |
| REGISTRATION_TO_STUDENTCOURSE | BLOCKED_PARTIAL | Không áp đặt một-một, chưa tạo StudentCourse |
| STUDENT_COURSE_PERSISTENCE | BLOCKED_PARTIAL | Thiếu identity và semantics tín chỉ lần học |
| FINAL_RESULT_SELECTION | BLOCKED_PARTIAL | Chưa có quy tắc current/effective khi nhiều kết quả |
| CREDITS_EARNED | BLOCKED_PARTIAL | Chưa có fact/rule ở mức từng lần học |
| GPA_INCLUSION | BLOCKED_PARTIAL | UNKNOWN không bị biến thành false |
| ACADEMIC_RESULT_PERSISTENCE | BLOCKED_PARTIAL | Chưa đủ gate, không importer/migration |
| EXAM_TO_STUDENTCOURSE | UNRESOLVED | Chưa có StudentCourse đúng để nối; không ghép bằng ngày thi |
| SCHEDULE_TO_STUDENTCOURSE | PARTIALLY_RESOLVED | Quan hệ tới lớp/đăng ký từ 4C còn giá trị, chưa tới StudentCourse đã xác minh |
| PHASE_5C_OVERALL | PARTIAL | Bộ đọc mới đã kiểm chứng; chưa đạt mục tiêu persistence |
| PHASE_5_OVERALL | PARTIAL | Curriculum/catalog đã lưu trong phạm vi 5B, điểm/lần học chưa lưu |

Không đọc lại lịch/thi thật trong lượt 5C này; hai bridge giữ giới hạn đã ghi ở 4C/5A, không được nâng hạng nhờ đọc thêm chi tiết điểm. `IDLICHHOC` lặp qua nhiều ngày vẫn chưa thể làm khóa buổi học. Không tạo Exam/ClassSession hoặc ghép ngày/giờ/phòng thành khóa.

Để mở gate còn cần nguồn giải thích identity lần học qua các trạng thái, quan hệ khi chưa có tổng kết, cách chọn tổng kết hiện hành và fact/rule tín chỉ đạt/GPA. Không cần người dùng gửi thêm credential; không cần đổi constraint bằng một quyết định tùy ý để vượt những chỗ thiếu bằng chứng.

Giữ nguyên `PREREQUISITE_SOURCE = PARTIAL`, `PREREQUISITE_PERSISTENCE = BLOCKED_PARTIAL`; không nghiên cứu tiếp tiên quyết, không ép phân loại ba môn chưa rõ nhóm, không tự chọn curriculum hiện hành. Chưa bắt đầu 4D, Phase 6, GPA engine, worker, change detection, Google Calendar hoặc email.

## Phase 5D: kết luận nghiên cứu ngữ nghĩa kết quả học tập

### Kết luận để người phát triển dùng ngay

**PHASE_5D_OVERALL = PASS cho đợt nghiên cứu; RESEARCH_CONCLUSION = SOURCE_LIMIT_REACHED.** PASS ở đây nghĩa là đã đi hết những đường tra cứu hợp lệ đang có và ghi lại ranh giới hỗ trợ, **không** có nghĩa đã đủ điều kiện nhập điểm. `STUDENT_COURSE_PERSISTENCE_READY = NO` và `ACADEMIC_RESULT_PERSISTENCE_READY = NO`. Không có importer điểm, migration hay endpoint mới trong Phase 5D.

Điểm khác với 5C là đã đối chiếu thêm màn hình khối kiến thức, tra cứu đăng ký ở kỳ mới và hai quy chế đào tạo do chính cổng đăng tải. Quy chế giúp hiểu *cách xử lý học vụ*, nhưng không cung cấp khóa để nối từng hàng trên màn hình với một lần học xuyên suốt vòng đời. Không lấy một quy tắc tổng quát của quy chế để lấp trường còn thiếu trong phản hồi của cổng.

### Đã kiểm tra những gì?

Lượt đọc ngày 29–30/09/2026 dùng phiên do chủ tài khoản tự đăng nhập, chỉ mở màn hình tra cứu và tài liệu nhà trường; không xác nhận/hủy đăng ký, không gọi API ghi. Phần điểm hiện có 40 hàng tổng kết trong chín nhóm năm học–học kỳ, một thẻ “Điểm mới”, bốn hàng “Học phần nợ”, 14 hàng tổng hợp khối và 85 hàng môn trong khối. Tra cứu đăng ký ở kỳ `2026_2027_1` hiện có ba thẻ lớp của hai môn. Đây là **số lượng quan sát**, không phải dữ liệu mẫu để đưa vào repository. Hai lượt điều hướng độc lập cho cùng danh sách điểm, khối và đăng ký cho kết quả như nhau. 5C từng thấy 39 hàng tổng kết; không có bản lưu ID cũ để kết luận hàng thứ 40 là thêm mới, sửa hay thay thế.

Đã xem lại luồng frontend đang chạy: bảng tổng kết nhóm theo `NAMHOC` và `HOCKY`, nút **Chi tiết** dùng handler trong `#zone_bangdiem` để đọc thành phần theo ID tổng kết. Đoạn popover cũ không phải handler hiện hành, nên không dùng nó làm quy tắc nghiệp vụ. `rsDiemMoiNhat` được nhận trong phản hồi `KetQuaHocTapCaNhan` và dùng cho thẻ “Điểm mới”; thẻ hiện có một mục khớp với một hàng tổng kết. Không thấy frontend dùng tập đó để chọn bản ghi hiệu lực cho `AcademicResult`, cũng không chứng minh “mới nhất” có nghĩa theo môn, lần học hay lần thi. Bảng điểm chính hiển thị các hàng nguồn, không chọn cực đại điểm/lần thi/thời gian trước khi vẽ.

Quy chế chính thức đọc từ mục **Văn bản, quy định, biểu mẫu** trên cổng:

- [Quy chế 1062/QĐ-ĐHP-ĐT ngày 20/07/2023](https://qldtbeta.phenikaa-uni.edu.vn/upload/ApisTinTuc/Doc/DBD89AC08B04400D90C60A5BD126FAA9_202309210856337290_20230720qd1062quy-ch.pdf), hiện được cổng ghi “hết hiệu lực”. Điều 35 (trang PDF 33) cho biết áp dụng từ năm học 2023–2024. Điều 12 (trang PDF 13–14) phân biệt điểm thường, P/NP và các trạng thái I, X, R, H, T; Điều 19–20 (trang PDF 21–22) nêu học lại/cải thiện và điểm trung bình có trọng số tín chỉ.
- [Quy chế 1528/QĐ-ĐPH-ĐT ngày 20/04/2026](https://qldtbeta.phenikaa-uni.edu.vn/upload/ApisTinTuc/Doc/D2378F7F8BE744D5ACC6DA9D31DA7902_202609140824428840_20260420qd-1528-quy-.pdf), cổng ghi “đang áp dụng”. Điều 34 khoản 2 (trang PDF 39) cho biết áp dụng từ học kỳ 1 năm học 2026–2027 cho các khóa và thay quy chế 1062. Vì vậy không lấy quy tắc 2026 áp ngược vào các hàng điểm 2023–2026 đang thấy.

Quy chế là bằng chứng về chính sách ở thời điểm nêu trên; chưa phải mô tả API hay cam kết mỗi hàng nguồn đã phản ánh đầy đủ một quyết định học vụ. Nếu cần tính lại một kỳ cũ, phải xét đúng quy chế có hiệu lực khi đó và các quyết định riêng có liên quan, không hard-code ngưỡng/thang điểm từ một văn bản cho mọi thời kỳ.

### Lần học và đăng ký: nối được kết quả, chưa có khóa cha bền vững

Giữ nguyên đối chiếu 5C: tám nhóm cùng môn/cùng `LANHOC` có hai đăng ký/lớp, nhưng điểm thành phần của **một** tổng kết nối tới cả hai. Bảy cặp có loại lớp khác nhau, một cặp cùng loại. `MANHOMLOP` xuất hiện như lớp CSS trong màn hình đã kiểm tra; `LOPHOCPHANCHINH` có cờ nhưng không có quy tắc nguồn chứng minh đó là khóa lần học. Không thể coi một đăng ký, một lớp chính, một mã nhóm hay ID tổng kết là `StudentCourse`.

Ở kỳ tra cứu mới, ba thẻ đăng ký thuộc hai môn và một môn có hai lớp; hai lớp này cũng không có nhãn “lần học cha” hiển thị. Không môn nào trong kỳ đó có hàng tổng kết tương ứng trong bảng điểm hiện đọc. Điều này cho một mẫu đăng ký **chưa có tổng kết ở màn hình hiện tại**, không chứng minh trạng thái “đang học” vĩnh viễn, bởi màn hình có thể thiếu kỳ/dữ liệu. Hai nhóm cũ chỉ có điểm thành phần cũng không dùng được endpoint chi tiết tổng kết để tìm khóa cha.

Quy chế 2026 Điều 8 (trang PDF 9–11) mô tả đăng ký, điều chỉnh/hủy và thông tin nhà trường lưu, nhưng việc *quy chế nói cần lưu* không chứng minh các ID hoặc trạng thái đó có trong request UI hiện tại. Điều 8 còn nói một lớp cho một học phần lúc đăng ký, trong khi tám nhóm lịch sử có hai đăng ký góp vào tổng kết; các thời điểm, loại lớp và biến động có thể khác nhau. Không ép dữ liệu hiện có theo một cách diễn giải điều khoản. `LEARNING_ATTEMPT_IDENTITY` và `REGISTRATION_GROUPING` vẫn `BLOCKED_PARTIAL`.

### Kết quả hiệu lực, tín chỉ và GPA

40 ID tổng kết hiện thấy không trùng nhau và giữ nguyên giữa hai lượt đọc. Không có cặp cùng môn–học kỳ–`LANHOC` với hai tổng kết trong **mẫu hiển thị**; đây là `NO_SAMPLE` cho tình huống chọn giữa nhiều hàng, không phải bằng chứng hệ thống không bao giờ tạo nhiều hàng. Hai lượt đọc không kiểm chứng được ID có giữ nguyên sau khi sửa điểm, phúc khảo hoặc nhập lại hay không. Quy chế 2023 Điều 18 và quy chế 2026 Điều 15–16 có việc điều chỉnh/phúc khảo; chúng không công bố cơ chế version/replacement của hàng API. Không tự chọn điểm cao nhất, `LANTHI` lớn nhất, ID lớn nhất hoặc phần tử cuối mảng.

Quy chế 2023 Điều 19 (trang PDF 21) nêu cải thiện môn đã đạt chọn điểm cao nhất. Quy chế 2026 Điều 10 khoản 7 (trang PDF 18) phân biệt học lại môn chưa đạt dùng kết quả lần học cuối với cải thiện môn đã đạt dùng điểm cao nhất để tính trung bình. Đây là **quy tắc giữa các lần học trong bối cảnh chính sách**, không chứng minh một trong nhiều hàng tổng kết *của cùng lần học* là bản ghi hiện hành. Mẫu hiện tại cũng không có trường active/approved/revision/replaced-by đã xác minh. `CURRENT_RESULT_SELECTION = BLOCKED_PARTIAL`.

Tín chỉ của `Course` là khối lượng môn; tín chỉ trong thẻ đăng ký và hàng điểm là tín chỉ môn. Với hai đăng ký cùng góp một tổng kết, cộng tín chỉ mỗi thẻ sẽ tính đôi, còn lấy một thẻ làm `StudentCourse.creditsAttempted` là một quyết định chưa có căn cứ. Không thấy tín chỉ đăng ký học riêng cho **một lần học đã định danh**. `CREDITS_ATTEMPTED = BLOCKED_PARTIAL`.

Trang khối kiến thức có trạng thái hoàn thành từng môn và tổng tín chỉ tích lũy theo khối, nhưng hàng môn không có ID kết quả, `LANHOC` hay `LANTHI` để nối ngược tới lần học. Quy chế 2026 Điều 11 (trang PDF 19–20) còn xét môn trong chương trình, miễn/công nhận, nhóm tự chọn vượt yêu cầu và một số lựa chọn khi cùng điểm. Một môn đã đạt không tự động cho biết `creditsEarned` của *bản ghi lần học cụ thể*; càng không thể chia tổng tích lũy về từng dòng. `CREDITS_EARNED = BLOCKED_PARTIAL`.

Quy chế 2023 Điều 12 (trang PDF 14) và quy chế 2026 Điều 10 khoản 6 (trang PDF 17–18) loại P/NP và một số trạng thái đặc biệt khỏi điểm trung bình. Quy chế 2026 Điều 11 (trang PDF 19–20) còn giới hạn theo chương trình và cách chọn môn tự chọn; điểm trung bình là tổng có trọng số tín chỉ, không đơn giản là “có điểm hệ 4 thì tính”. UI cho điểm quy đổi/chữ và trung bình do server trả, nhưng không có cờ *hàng kết quả này được tính GPA* đã nối chắc với từng lần học. `GPA_INCLUSION = BLOCKED_PARTIAL`. Không dùng `false` để biểu diễn “chưa biết”.

### Đối chiếu model hiện tại trước khi định viết importer

| Model/field | Hiện có | Vướng mắc với nguồn | Quyết định 5D |
| --- | --- | --- | --- |
| `Semester.Identifier` | Năm bắt đầu + `Tn` | Nhiều kỳ điểm nguồn có thể vào một học kỳ | `SEMESTER_MAPPING = PASS` cho *điểm*; chưa ghép kỳ đăng ký/thi |
| `StudentCourse.courseId`, `semesterId` | Bắt buộc | Môn/học kỳ điểm đã có cách chuẩn hóa | Chưa đủ để định danh một lần học |
| `StudentCourse.sectionId` | Một lớp, có thể null | Một tổng kết có thể dùng nhiều lớp | Không chọn đại một lớp; null cũng không giải quyết khóa lần học |
| `StudentCourse.attemptNumber` | Bắt buộc, số dương | `LANHOC` là số nguồn báo; chưa chứng minh identity xuyên điều chỉnh | Không tự đánh lại số để vừa unique constraint |
| `StudentCourse.creditsAttempted` | Bắt buộc | Chỉ thấy tín chỉ môn/đăng ký | Không lấy tổng của nhiều lớp hoặc mặc định từ `Course` |
| `AcademicResult.studentCourseId` | Bắt buộc | Chưa có StudentCourse đủ tin cậy | Không tạo kết quả mồ côi hay lần học giả |
| `AcademicResult.status` | Bắt buộc | PASSED/FAILED/RETAKE_REQUIRED ở observation chưa khớp hoàn toàn enum | Không ép `RETAKE_REQUIRED` thành `FAILED` |
| `AcademicResult.numericScore`, `letterGrade`, `gradePoints` | Có thể null | UI có giá trị nhưng cần biết bản ghi nào hiệu lực | Giữ giá trị ở observation, không tính lại |
| `AcademicResult.creditsAttempted` | Sao từ lần học | Lần học chưa xác định tín chỉ | Chưa thể ghi đúng |
| `AcademicResult.creditsEarned` | Bắt buộc | Tích lũy/hoàn thành môn không phải tín chỉ từng lần học | Không tự điền bằng 0 hay tín chỉ môn |
| `AcademicResult.includedInGpa` | Boolean bắt buộc | Nguồn chưa cho fact từng kết quả; `false` khác `UNKNOWN` | Chưa lưu; cân nhắc biểu diễn ba trạng thái khi có use case |
| `AcademicResult.gradingPolicyId`, `recordedAt` | Policy tùy chọn, thời điểm bắt buộc | Quy chế có thời hạn áp dụng; thời điểm đọc không phải thời điểm ghi nhận học vụ | Không tạo policy/timestamp giả |

`SEMESTER_MAPPING = PASS` là kết luận hẹp: 13 ID kỳ điểm đã đối chiếu ở 5C ánh xạ vào chín cặp `NAMHOC`–`HOCKY`, và UI nhóm theo chính cặp này. `SEMESTER_PERSISTENCE = NOT_IMPLEMENTED`: 5D không có luồng ghi riêng cho Semester, nên không thêm bảng ánh xạ chỉ để tăng tỷ lệ hoàn thành. Nếu sau này lưu độc lập, phải định kiểu kỳ điểm khác kỳ đăng ký/thi, cho phép nhiều kỳ nguồn về một Semester và không bịa ngày bắt đầu/kết thúc.

### Ranh giới hỗ trợ và việc nên làm tiếp

| Gate | Kết luận 5D | Lý do ngắn |
| --- | --- | --- |
| `LEARNING_ATTEMPT_IDENTITY` | `BLOCKED_PARTIAL` | Có cạnh tổng kết–thành phần–đăng ký, thiếu ID lần học xuyên vòng đời |
| `REGISTRATION_GROUPING` | `BLOCKED_PARTIAL` | Hai đăng ký cùng góp điểm, nhưng không có quy tắc gom khi chưa có tổng kết/chuyển lớp |
| `CURRENT_RESULT_SELECTION` | `BLOCKED_PARTIAL` | Không có mẫu nhiều tổng kết cùng lần học hoặc cờ phiên bản/hiệu lực |
| `CREDITS_ATTEMPTED` | `BLOCKED_PARTIAL` | Tín chỉ môn/đăng ký chưa là tín chỉ của một lần học |
| `CREDITS_EARNED` | `BLOCKED_PARTIAL` | Không có liên kết từ hoàn thành môn/tổng tích lũy về kết quả cụ thể |
| `GPA_INCLUSION` | `BLOCKED_PARTIAL` | Có quy tắc chính sách, thiếu lựa chọn/cờ ở mức bản ghi và đúng thời kỳ |
| `STUDENT_COURSE_PERSISTENCE_READY` | `NO` | Identity và tín chỉ lần học chưa đạt |
| `ACADEMIC_RESULT_PERSISTENCE_READY` | `NO` | Thiếu lần học, kết quả hiện hành, tín chỉ đạt và GPA inclusion |
| `EXAM_TO_STUDENTCOURSE` | `UNRESOLVED` | Lịch thi chưa nối được đúng lần học; 5D không có bằng chứng mới |
| `SCHEDULE_TO_STUDENTCOURSE` | `PARTIALLY_RESOLVED` | Lịch tới lớp/đăng ký, chưa tới lần học; `IDLICHHOC` vẫn lặp |

**Có thể hỗ trợ an toàn:** đọc observation thuộc đúng tài khoản, kiểm tra quan hệ tổng kết–thành phần–đăng ký, giữ các nguồn kỳ/điểm riêng, nhập curriculum/catalog trong phạm vi đã xác minh và trình bày giới hạn cho người phát triển. **Chưa hỗ trợ an toàn:** tự lưu StudentCourse/AcademicResult, chọn kết quả hiệu lực, tính GPA/tín chỉ đạt từ hàng điểm, sinh Exam/ClassSession từ suy đoán, hay đồng bộ xóa dữ liệu khi một phản hồi thiếu hàng. Không có bằng chứng mới để nâng các gate tiên quyết, lựa chọn curriculum hiện hành hoặc tính đầy đủ snapshot.

Đợt nghiên cứu mục tiêu đã hết đường kiểm tra hợp lý trên UI hiện có: đã xem bảng điểm, chi tiết đang chạy, điểm mới, học phần nợ, khối kiến thức, đăng ký kỳ có/chưa có điểm và quy chế liên quan. Để mở lại các gate cần tài liệu/API chính thức giải thích khóa lần học, vòng đời kết quả và quyết định tín chỉ/GPA ở mức bản ghi, hoặc một nguồn hợp lệ khác có các fact đó. Trong lúc chờ, hướng kiến trúc **đề xuất, chưa triển khai** là tạm hoãn persistence kết quả; nếu cần hiển thị, chỉ trình bày observation có ghi rõ nguồn/độ đầy đủ, hoặc yêu cầu đối soát thủ công trước khi ghi. Có thể phát triển các tính năng độc lập với `StudentCourse`, nhưng không tự bắt đầu phase đó ở đây.

Phase 5D chỉ sửa tài liệu. Không tạo endpoint, migration, test hay fixture chứa dữ liệu cá nhân. CI tự động của AMS dùng dữ liệu tổng hợp và không đăng nhập/gọi cổng Phenikaa.

## Phase 5E: API đọc trực tiếp và ranh giới nguồn

### Đọc được gì, chưa lưu gì?

Phase 5E đưa phần đọc kết quả đã kiểm chứng ra một API có xác thực. Luồng là **tài khoản AMS → kết nối của chính tài khoản → bộ đọc Phenikaa → dữ liệu quan sát đã chuẩn hóa → DTO trả về**. DTO là dữ liệu để xem, không phải một `StudentCourse` hoặc `AcademicResult` vừa được tạo. API chỉ hoạt động khi `AMS_PHENIKAA_ENABLED=true` và đã có kết nối được cấp qua quy trình nội bộ; hiện chưa có màn hình để người dùng tự liên kết tài khoản Phenikaa.

| Đường dẫn GET | Ý nghĩa |
| --- | --- |
| `/api/me/academic/source/status` | Trạng thái kết nối và bảng khả năng hỗ trợ, không gọi cổng nguồn |
| `/api/me/academic/source/programs` | Những chương trình tài khoản nguồn được phép tra cứu; trả `programRef` thay ID nguồn |
| `/api/me/academic/source/records?programRef=...` | Điểm thành phần và kết quả cuối đang quan sát, theo từng đăng ký nguồn; trả `detailRef` khi có tổng kết |
| `/api/me/academic/source/records/{detailRef}/detail?programRef=...` | Những điểm thành phần mà nguồn nối với tổng kết đã chọn |

Chưa mở REST cho lịch học/lịch thi trong 5E. Adapter vẫn có khả năng đọc chúng, nhưng thêm hai hợp đồng API nữa chưa cần để chốt bề mặt điểm học tập. Curriculum đã có phần được lưu trong AMS; không gọi lại nguồn mỗi lần chỉ để tạo một API curriculum trùng công dụng. API trạng thái nói rõ các phần này là `ADAPTER_READ_ONLY_NO_API` hoặc `PERSISTED_PARTIAL`, không ngụ ý frontend đã có màn hình tương ứng.

Mỗi hàng `records` vẫn là **một đăng ký quan sát được**, không phải một lần học đã đối soát. Có thể có hai hàng cùng môn, học kỳ và `reportedLearningAttempt`; nếu cùng góp vào một tổng kết, chúng có thể cùng `detailRef`. `course.credits` là tín chỉ môn, không phải `creditsAttempted` của lần học. Học kỳ được hiển thị theo `Semester.Identifier` chỉ cho dữ liệu điểm; không áp mã này sang kỳ lọc lịch/thi. `RETAKE_REQUIRED` giữ nguyên, không đổi thành `FAILED`.

Response có `completeness: UNKNOWN`. Khối `unknownSemantics` ghi riêng `creditsEarned`, `includedInGpa`, `currentResult` đều là `UNKNOWN`; không có `studentCourseId`, không điền 0/false, không gắn cờ “kết quả hiện hành”. Danh sách rỗng cũng giữ `UNKNOWN`, vì chưa chứng minh phản hồi nguồn luôn đầy đủ. Không lưu raw observation, điểm hay lịch vào Redis/PostgreSQL qua API đọc này; database chỉ có thể cập nhật metadata lần truy cập/trạng thái của kết nối như trước.

### Reference và bảo vệ dữ liệu

`programRef`, `registrationRef`, `detailRef` là chuỗi băm phân tách theo loại, user AMS và ID nguồn. Chúng che ID nguồn khỏi hợp đồng JSON, nhưng **không phải** khóa nghiệp vụ bền vững, token đăng nhập hay cơ chế phân quyền tự thân. Khi client dùng một reference, service đọc lại danh sách chương trình/kết quả của chính user, tìm hàng khớp rồi adapter xác minh ID tổng kết còn thuộc observation mới trước khi gọi chi tiết. Reference tự chế, sai loại, thuộc user khác hoặc đã biến mất đều bị từ chối; không có endpoint nhận arbitrary ID Phenikaa để gọi cổng.

Chi tiết được lấy trong một lượt nguồn mới. DTO chỉ trả mã đăng ký băm và tên/mã/điểm thành phần đã chuẩn hóa. Nếu ID hoặc giá trị thành phần khác giữa hai lượt đọc, API trả lỗi dữ liệu chưa nhất quán, không trộn hai phiên bản. Các trường JSON riêng của Phenikaa, ID người học, credential, cookie và phản hồi mã hóa không xuất hiện trong DTO. Không ghi danh sách môn, điểm hoặc reference vào log.

API dùng session và CSRF/CORS hiện có; tất cả endpoint mới là GET và cần tài khoản AMS đã đăng nhập. Mỗi user có khoảng nghỉ mặc định năm giây cho từng loại request chương trình, danh sách điểm và chi tiết. Redis chỉ giữ khóa cooldown gồm UUID nội bộ và loại request, không giữ nội dung học tập. Giá trị có thể cấu hình qua `AMS_PHENIKAA_READ_COOLDOWN` trong khoảng 1–60 giây. Đây là giới hạn tải cơ bản, chưa phải chính sách quota nhiều tầng.

Đường đọc điểm không giữ transaction ghi hoặc khóa user trong lúc gọi HTTP: transaction ngắn đầu kiểm tra account/kết nối và giải mã phiên, transaction ngắn sau cập nhật metadata. Nếu kết nối đã đổi/ngắt trong thời gian gọi nguồn, kết quả cũ không được trả. Điều này khác đường nhập hồ sơ/chương trình đang giữ transaction lâu; 5E chưa refactor hai importer đó.

| Tình huống | HTTP | Mã an toàn trả client |
| --- | --- | --- |
| Chưa có/ngắt kết nối | 409 | `CONNECTION_NOT_FOUND` |
| Phiên nguồn hết hạn | 409 | `RECONNECTION_REQUIRED` |
| Reference không thuộc tập hiện đọc | 404 | `INVALID_SOURCE_REFERENCE` |
| Nguồn chậm quá hạn | 504 | `SOURCE_TIMEOUT` |
| Phản hồi đổi schema/không giải mã được | 502 | `SOURCE_SCHEMA_CHANGED` |
| Dữ liệu hai lượt không khớp/quá giới hạn | 502 | `SOURCE_DATA_INCOMPLETE` |
| Nguồn hoặc kết nối hiện thời không sẵn sàng | 502 | `SOURCE_UNAVAILABLE` |
| Gọi lại quá sớm | 429 | `RATE_LIMITED` |

Lỗi không trả đường dẫn API nội bộ, tên procedure hoặc exception nguồn. Session hết hạn vẫn chuyển kết nối thành `RECONNECTION_REQUIRED`; API không trả 200 với danh sách rỗng để che lỗi. Các giới hạn byte, số hàng và timeout của adapter vẫn áp dụng. Service còn từ chối nếu tổng số hàng để trả vượt 10.000; không cắt bớt rồi báo đã đọc đủ.

### Mốc đóng Phase 5

Phần **đã lưu** vẫn là hồ sơ người học, chương trình/danh mục môn/nhóm trong giới hạn 5B. Phần **đọc trực tiếp** gồm kết quả và chi tiết qua API 5E; lịch học/thi mới có ở adapter, chưa có REST. Phần **bị giới hạn nguồn** vẫn gồm `StudentCourse`, `AcademicResult`, lưu buổi học/lịch thi và điều kiện tiên quyết đầy đủ. Không có V8, cache điểm hay bảng staging. Phase 5 có thể được xem là `CLOSED_WITH_SOURCE_LIMITS` cho những khả năng đã giao; điều đó không biến các phần bị chặn thành hoàn thành.

Test tự động dùng user, điểm và phiên giả định. Test service/API kiểm tra ownership, reference, `UNKNOWN`, lỗi an toàn và dữ liệu rỗng. Integration test dùng PostgreSQL/Redis thật để kiểm tra đường HTTP có xác thực, cooldown, hết phiên và không tạo hàng học vụ. CI không gọi Phenikaa. Việc đọc live qua endpoint mới cần một kết nối AMS đã được cấp hợp lệ cho chính tài khoản; nếu chưa có, không báo test tổng hợp là live verify.

Hướng Phase 6A **chỉ đề xuất**: trạng thái/lượt đồng bộ, worker và khóa cho các dữ liệu đã có thể nhập như hồ sơ/chương trình. Không thiết kế phát hiện thay đổi điểm, buổi học hay kỳ thi dựa trên identity còn chưa xác minh. Không bắt đầu Phase 6 hoặc một Phase 5F tiếp tục đoán khóa lần học.

## Phase 6A: làm mới phần dữ liệu đã có thể lưu

Phase 6A triển khai hạ tầng được đề xuất ở cuối 5E: lượt đồng bộ bền vững, worker và khóa theo user. Worker chỉ gọi importer hồ sơ và chương trình/danh mục đã có; API kết quả học tập vẫn đọc trực tiếp, lịch/thi vẫn chưa vào pipeline. Không có job ghi `StudentCourse`, `AcademicResult`, `ClassSession` hoặc `Exam`, cũng không tạo snapshot/chênh lệch từ các observation này.

Hai importer nay đọc nguồn ngoài transaction ghi, rồi kiểm tra lại tài khoản, kết nối và thế hệ phiên trước khi lưu. Nếu nguồn trả lỗi, dữ liệu đã lưu không bị xóa. Nếu đọc nguồn thành công nhưng bước ghi thất bại, `lastSuccessfulAccessAt` vẫn phản ánh lần đọc nguồn; trạng thái `sync_run` mới là nơi thể hiện kết quả toàn lượt. Điều này tránh hiểu nhầm metadata kết nối là bằng chứng import thành công. [Thiết kế worker, retry và trạng thái](architecture-overview.md#hạ-tầng-đồng-bộ-phase-6a) có chi tiết.
