import kotlinx.coroutines.flow.MutableStateFlow

private const val translations = """
Thinking
vi: Đang suy nghĩ

Edited
vi: Đã chỉnh sửa

Shouted
vi: Đã hét

[Ctrl] Move to top
vi: [Ctrl] Di chuyển lên đầu

[Ctrl] Move to bottom
vi: [Ctrl] Di chuyển xuống cuối

Click to edit
vi: Nhấn để chỉnh sửa

On
vi: Bật

Off
vi: Tắt

Health
vi: Sức khỏe

Mind
vi: Tâm trí

Spirit
vi: Tinh thần

Outdoors
vi: Ngoài trời

Background color
vi: Màu nền

Light color
vi: Màu sáng

Fog density
vi: Độ dày mây mù

Add
vi: Thêm

Just now
vi: Vừa xong

minutes ago
vi: phút trước

hours ago
vi: giờ trước

days ago
vi: ngày trước

Long time ago 
vi: Lâu lắm rồi

Remove image
vi: Xóa hình ảnh

Add image
vi: Thêm hình ảnh

Add audio
vi: Thêm âm thanh

Player image input
vi: Hình ảnh của người chơi

Player audio input
vi: Âm thanh của người chơi

Allow multiple
vi: Cho phép nhiều hình ảnh

Confirm label
vi: Nhãn nút xác nhận

No images
vi: Không có hình ảnh

No audio
vi: Không có âm thanh

My Worlds
vi: Thế giới của tôi

All types
vi: Tất cả loại

Search assets
vi: Tìm kiếm tài sản

Close
vi: Đóng

Download
vi: Tải xuống

Send to chat
vi: Gửi vào chat

Settings
vi: Cài đặt

Always show QR code
vi: Luôn hiển thị mã QR

Scan to join!
vi: Quét để tham gia!

Change Password
vi: Đổi mật khẩu

Change
vi: Thay đổi

Current Password
vi: Mật khẩu hiện tại

New Password
vi: Mật khẩu mới

Repeat New Password
vi: Nhập lại mật khẩu mới

Passwords do not match
vi: Mật khẩu không khớp

Current password is incorrect
vi: Mật khẩu hiện tại không đúng

Search objects
vi: Tìm kiếm đối tượng

collected
vi: đã thu thập

Open
vi: Mở

Customize character
vi: Tùy chỉnh nhân vật

Status
vi: Trạng thái

Player Account
vi: Tài khoản người chơi

Player
vi: Người chơi

Language
vi: Ngôn ngữ

Low Graphics Mode
vi: Chế độ đồ họa thấp

Pretty Graphics
vi: Đồ họa đẹp

Music
vi: Âm nhạc

Music volume
vi: Âm lượng nhạc

Night Music
vi: Nhạc ban đêm

Set music
vi: Chọn nhạc

Change music
vi: Đổi nhạc

Set night music
vi: Chọn nhạc ban đêm

Change night music
vi: Đổi nhạc ban đêm

Log Out
vi: Đăng xuất

Are you sure you want to log out?
vi: Bạn có chắc chắn muốn đăng xuất không?

My profile
vi: Trang cá nhân của tôi

Set your status
vi: Đặt trạng thái của bạn

World chat
vi: Trò chuyện thế giới

Pinned messages
vi: Tin nhắn được ghim

Pin
vi: Ghim

Unpin
vi: Bỏ ghim

Pin to top
vi: Ghim lên đầu

Unpin message
vi: Bỏ ghim tin nhắn

No pinned messages yet
vi: Chưa có tin nhắn được ghim

© 2025 Saigon.Quest v0.1
vi: © 2025 Saigon.Quest v0.1

Claim this World
vi: Yêu cầu thế giới này

Claim this world
vi: Yêu cầu thế giới này

Login and claim this world
vi: Đăng nhập và yêu cầu thế giới này

Go home
vi: Về trang chủ

Retry
vi: Thử lại

Loading Saigon.Quest world...
vi: Đang tải thế giới Saigon.Quest...

Loading...
vi: Đang tải...

Loading…
vi: Đang tải…

Error loading world
vi: Lỗi tải thế giới

No world found
vi: Không tìm thấy thế giới

No world found at
vi: Không tìm thấy thế giới tại

World not found or server error
vi: Không tìm thấy thế giới hoặc lỗi máy chủ

Network error: Unable to connect to the server. Please check your internet connection.
vi: Lỗi mạng: Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối Internet của bạn.

Server error: Something went wrong on our end. Please try again later.
vi: Lỗi máy chủ: Đã có sự cố từ phía chúng tôi. Vui lòng thử lại sau.

Unknown error
vi: Lỗi không xác định

Untitled World
vi: Thế giới không tên

Confirm
vi: Xác nhận

No
vi: Không

Yes
vi: Có

Ok
vi: Ok

Drop quantity
vi: Số lượng thả

Invalid quantity
vi: Số lượng không hợp lệ

Saigon.Quest
vi: Saigon.Quest

Virtual World
vi: Thế giới ảo

Share this world
vi: Chia sẻ thế giới này

🖨️ Print
vi: 🖨️ In

Print This Location
vi: In vị trí này

📥 Download image
vi: 📥 Tải xuống hình ảnh

Log In
vi: Đăng nhập

Create Account
vi: Tạo tài khoản

Username
vi: Tên người dùng

Password
vi: Mật khẩu

Enter username
vi: Nhập tên người dùng

Enter password
vi: Nhập mật khẩu

Password must be at least 8 characters
vi: Mật khẩu phải có ít nhất 8 ký tự

Year of Birth
vi: Năm sinh

Select year
vi: Chọn năm

Sex
vi: Giới tính

Boy
vi: Nam

Girl
vi: Nữ

Username cannot be empty
vi: Tên người dùng không được để trống

Password cannot be empty
vi: Mật khẩu không được để trống

Year of birth is required
vi: Năm sinh là bắt buộc

Sex is required
vi: Giới tính là bắt buộc

Body
vi: Cơ thể

Skin Tone
vi: Màu da

Eyes
vi: Mắt

Hair
vi: Tóc

Skinny
vi: Gầy

Normal
vi: Bình thường

Fat
vi: Mập

Black
vi: Đen

Blue
vi: Xanh dương

Brown
vi: Nâu

Green
vi: Xanh lá

Gray
vi: Xám

Short
vi: Ngắn

Medium
vi: Trung bình

Long
vi: Dài

Cancel
vi: Hủy

Discard recording
vi: Hủy bản ghi

Save
vi: Lưu

You haven't made any friends yet!
vi: Bạn vẫn chưa kết bạn nào!

Mark all as read
vi: Đánh dấu tất cả là đã đọc

Loading notifications...
vi: Đang tải thông báo...

No notifications
vi: Không có thông báo

Failed to load notifications
vi: Không thể tải thông báo

An error occurred
vi: Đã xảy ra lỗi

Loading friends...
vi: Đang tải danh sách bạn bè...

Error
vi: Lỗi

Last seen in:
vi: Lần cuối xuất hiện ở:

Currently at
vi: Hiện tại ở

Jump to Friend
vi: Đến bạn bè

Remove
vi: Xóa

Are you sure you want to remove
vi: Bạn có chắc chắn muốn xóa

from your friends list?
vi: khỏi danh sách bạn bè không?

Message
vi: Tin nhắn

Message
vi: Tin nhắn

Message was deleted
vi: Tin nhắn đã bị xóa

Edit message
vi: Chỉnh sửa tin nhắn

Delete message
vi: Xóa tin nhắn

Message options
vi: Tùy chọn tin nhắn

Edit your message
vi: Chỉnh sửa tin nhắn của bạn

Are you sure you want to delete this message?
vi: Bạn có chắc chắn muốn xóa tin nhắn này không?

Delete
vi: Xóa

Edit
vi: Chỉnh sửa

Update
vi: Cập nhật

Create new item
vi: Tạo vật phẩm mới

Title
vi: Tiêu đề

Subtitle
vi: Tóm tắt

Enter item title
vi: Nhập tiêu đề vật phẩm

Description
vi: Mô tả

Enter item description
vi: Nhập mô tả vật phẩm

Enter file path
vi: Nhập đường dẫn tệp

Asset index (0-99)
vi: Chỉ số tài nguyên (0-99)

Enter asset index
vi: Nhập chỉ số tài nguyên

Is this item wearable?
vi: Vật phẩm này có thể mặc được không?

Wearable asset
vi: Vật phẩm có thể mặc

Enter wearable file path
vi: Nhập đường dẫn tệp vật phẩm có thể mặc

Note: creating an item will add 10 copies of it to your inventory.
vi: Lưu ý: tạo một vật phẩm sẽ thêm 10 bản sao vào kho của bạn.

Create
vi: Tạo

Creating...
vi: Đang tạo...

Title is required
vi: Tiêu đề là bắt buộc

File path is required
vi: Đường dẫn tệp là bắt buộc

Asset index must be a positive number
vi: Chỉ số tài nguyên phải là số không âm

Asset index must be a number
vi: Chỉ số tài nguyên phải là một số

Wearable file path is required
vi: Đường dẫn tệp vật phẩm có thể mặc là bắt buộc

Failed to create item
vi: Không thể tạo vật phẩm

You must be logged in to wear items
vi: Bạn phải đăng nhập để mặc vật phẩm

Removed
vi: Đã gỡ

Couldn't remove
vi: Không thể gỡ

Wearing
vi: Đang mặc

Couldn't wear
vi: Không thể mặc

Used
vi: Đã dùng

Objects
vi: Đối tượng

Create object
vi: Tạo đối tượng

Loading objects...
vi: Đang tải đối tượng...

No objects in this world yet.
vi: Chưa có đối tượng nào trong thế giới này.

Untitled
vi: Không có tiêu đề

Locate
vi: Định vị

Failed to load objects
vi: Không thể tải đối tượng

Failed to locate
vi: Không thể định vị

Object manager not available
vi: Trình quản lý đối tượng không khả dụng

Cannot locate object
vi: Không thể định vị đối tượng

Object not found
vi: Không tìm thấy đối tượng

Cannot edit object
vi: Không thể chỉnh sửa đối tượng

Object
vi: Đối tượng

Visitor Account
vi: Tài khoản khách

Checking friendship status...
vi: Đang kiểm tra trạng thái kết bạn...

Sending request...
vi: Đang gửi yêu cầu...

Add friend
vi: Kết bạn

Friend request sent
vi: Đã gửi lời mời kết bạn

Accepting request...
vi: Đang chấp nhận yêu cầu...

Accept friend request
vi: Chấp nhận lời mời kết bạn

Removing...
vi: Đang xóa...

Removing friend...
vi: Đang xóa bạn bè...

Remove friend
vi: Xóa bạn bè

Loading friendship...
vi: Đang tải trạng thái kết bạn...

Not friends.
vi: Chưa là bạn.

Friend request sent.
vi: Đã gửi lời mời kết bạn.

Friend request received.
vi: Đã nhận lời mời kết bạn.

You're friends!
vi: Hai bạn là bạn bè!

Ignore
vi: Bỏ qua

Friends
vi: Bạn bè

Notifications
vi: Thông báo

Admin panel
vi: Bảng quản trị

Register new asset
vi: Đăng ký tài nguyên mới

Asset type
vi: Loại tài nguyên

File name (without extension)
vi: Tên tệp (không có phần mở rộng)

Register asset
vi: Đăng ký tài nguyên

Edit object
vi: Chỉnh sửa đối tượng

Object title
vi: Tiêu đề đối tượng

File (without .glb)
vi: Tệp (không có .glb)

Collection
vi: Bộ sưu tập

e.g. chair_collection
vi: ví dụ: chair_collection

Text displayed above object
vi: Văn bản hiển thị phía trên đối tượng

Rotation
vi: Xoay

Logic editor
vi: Trình chỉnh sửa logic

Object logic
vi: Logic đối tượng

nodes configured
vi: nút đã cấu hình

Logic data available
vi: Có dữ liệu logic

Object has no logic yet.
vi: Đối tượng chưa có logic.

Create the object to add logic.
vi: Tạo đối tượng để thêm logic.

Saving...
vi: Đang lưu...

Open URL?
vi: Mở URL?

Open URL
vi: Mở URL

Just now
vi: Vừa xong

minutes ago
vi: phút trước

hours ago
vi: giờ trước

days ago
vi: ngày trước

Long time ago
vi: Lâu lắm rồi

Edit world
vi: Chỉnh sửa thế giới

Create world
vi: Tạo thế giới

Create your player
vi: Tạo người chơi của bạn

Open world chat
vi: Mở trò chuyện thế giới

Open chat
vi: Mở trò chuyện

Inventory
vi: Kho đồ

Refresh
vi: Làm mới

New
vi: Mới

Bought
vi: Đã mua

Give
vi: Cho

Worn items
vi: Vật phẩm đang mặc

Loading inventory...
vi: Đang tải kho đồ...

Your character
vi: Nhân vật của bạn

Drop to wear item
vi: Thả để mặc vật phẩm

Drag wearable items here to equip them
vi: Kéo vật phẩm có thể mặc vào đây để mặc

Worn Items
vi: Vật phẩm đang mặc

No items worn. Drag wearable items to your character to wear them.
vi: Chưa mặc vật phẩm nào. Kéo vật phẩm có thể mặc vào nhân vật của bạn để mặc.

Click to remove
vi: Nhấp để gỡ

Inventory items
vi: Vật phẩm trong kho

Double click to move to top
vi: Nhấn đúp để chuyển lên đầu

Item moved to top
vi: Đã chuyển vật phẩm lên đầu

Failed to move to top
vi: Chuyển lên đầu thất bại

Your inventory is empty. Click '+ New' to create an item.
vi: Kho đồ của bạn trống. Nhấp '+ Mới' để tạo vật phẩm.

Collect item
vi: Lượm vật phẩm

Wearable
vi: Có thể mặc

Use
vi: Dùng

Wear
vi: Mặc

Take off
vi: Cời

Inventory refreshed
vi: Kho đồ đã được làm mới

Failed to refresh inventory
vi: Không thể làm mới kho đồ

Loading node types...
vi: Đang tải loại nút...

Error loading node types
vi: Lỗi tải loại nút

Actions
vi: Hành động

Dialogues
vi: Hội thoại

Events
vi: Sự kiện

Inputs
vi: Đầu vào

Node properties
vi: Thuộc tính nút

Type
vi: Loại

Action
vi: Hành động

Event
vi: Sự kiện

Dialogue
vi: Hội thoại

Input
vi: Đầu vào

Label
vi: Nhãn

Drag items from the left panel or double-click to create nodes
vi: Kéo mục từ bảng bên trái hoặc nhấp đúp để tạo nút

Buttons
vi: Nút

New button text
vi: Văn bản nút mới

Add
vi: Thêm

Enter text
vi: Nhập văn bản

x,y,z
vi: x,y,z

Object
vi: Đối tượng

Logic
vi: Logic

Bye
vi: Tạm biệt

Please close this window
vi: Vui lòng đóng cửa sổ này

Nothing to pick up
vi: Không có gì để nhặt

Not enough quantity
vi: Không đủ số lượng

Failed to drop item
vi: Không thể thả vật phẩm

Failed to pick up item
vi: Không thể nhặt vật phẩm

Resume
vi: Tiếp tục

Resume to
vi: Tiếp tục vào

Paused.
vi: Đã tạm dừng.

Reconnecting
vi: Đang kết nối lại

Take these items?
vi: Nhận những vật phẩm này?

Decline
vi: Từ chối

Take items
vi: Nhận vật phẩm

Give these items?
vi: Tặng những vật phẩm này?

Take these items?
vi: Nhận những vật phẩm này?

Give items
vi: Tặng vật phẩm

Pending requests
vi: Yêu cầu đang chờ

Note
vi: Ghi chú

Upload proof of payment / VNQR screenshot
vi: Tải ảnh xác minh thanh toán / ảnh chụp màn hình VNQR

Amount must be positive
vi: Số tiền phải là số dương

Attachment image is required
vi: Cần có hình ảnh đính kèm

Insufficient funds
vi: Bạn chưa có đủ tiền

Balance
vi: Số dư

Current balance
vi: Số dư hiện tại

View all
vi: Xem tất cả

Recent events
vi: Sự kiện gần đây

Balance history
vi: Lịch sử số dư

History
vi: Lịch sử

Last 5 events
vi: 5 sự kiện gần đây

View history
vi: Xem lịch sử

Withdraw
vi: Rút tiền

Deposit
vi: Nạp tiền

Deposit / Withdraw
vi: Nạp tiền / Rút tiền

Amount (VND)
vi: Số tiền (VND)

Notes
vi: Ghi chú

Upload proof of payment
vi: Tải ảnh xác minh thanh toán

Upload your VNQR
vi: Tải ảnh chụp màn hình VNQR của bạn

Submit
vi: Gửi

Submitting...
vi:Đang gửi...

Pending Withdraws
vi: Rút tiền đang chờ

Pending Deposits
vi: Nạp tiền đang chờ

Status
vi: Trạng thái

Complete
vi: Hoàn thành

Denied
vi: Bị từ chối

Cancelled
vi: Đã hủy

Cancel request
vi: Hủy yêu cầu

Approved
vi: Đã chấp thuận

Approve
vi: Chấp thuận

Deny
vi: Từ chối

Cost
vi: Giá

Cost (VND)
vi: Giá (đ)

VND
vi: đ

Customize wind weights
vi: Tùy chỉnh trọng số gió

Wind weights (5x5)
vi: Trọng số gió (5x5)

Reset to defaults
vi: Đặt lại về mặc định

Wind weights are invalid
vi: Trọng số gió không hợp lệ

Pending balance requests
vi: Yêu cầu số dư đang chờ

Pending user requests
vi: Yêu cầu người dùng đang chờ

Send system notification
vi: Gửi thông báo hệ thống

System
vi: Hệ thống

Assets
vi: Tài nguyên

Manage assets
vi: Quản lý tài nguyên

No assets found
vi: Không tìm thấy tài nguyên

File
vi: Tệp

(untitled)
vi: (không tiêu đề)

Transfer
vi: Chuyển tiền

Jan
vi: Thg 1

Feb
vi: Thg 2

Mar
vi: Thg 3

Apr
vi: Thg 4

May
vi: Thg 5

Jun
vi: Thg 6

Jul
vi: Thg 7

Aug
vi: Thg 8

Sep
vi: Thg 9

Oct
vi: Thg 10

Nov
vi: Thg 11

Dec
vi: Thg 12

Edit asset
vi: Chỉnh sửa tài sản

Are you sure you want to delete this asset?
vi: Bạn có chắc muốn xóa tài sản này không?

Scene preview for
vi: Xem trước cảnh cho

Items asset editor (sheet 320x320): click a 32x32 cell to edit title and category (coming soon)
vi: Trình chỉnh sửa tài sản vật phẩm (bảng 320x320): bấm vào ô 32x32 để sửa tiêu đề và danh mục (sắp ra mắt)

Spritesheet editor (256x256): wind weights toggle/list, title, category (coming soon)
vi: Trình chỉnh sửa spritesheet (256x256): bật/tắt/trình bày trọng số gió, tiêu đề, danh mục (sắp ra mắt)

World preview
vi: Xem trước thế giới

Title and category editors (coming soon)
vi: Trình chỉnh sửa tiêu đề và danh mục (sắp ra mắt)

Objects preview
vi: Xem trước đối tượng

Collections list editor (coming soon)
vi: Trình chỉnh sửa danh sách bộ sưu tập (sắp ra mắt)

Add all remaining collections
vi: Thêm tất cả các bộ sưu tập còn lại

Category
vi: Danh mục

Collection
vi: Bộ sưu tập

Weights CSV (25 numbers, row-major)
vi: CSV trọng số (25 số, theo hàng)


Asset browser
vi: Trình duyệt tài sản

Search by title
vi: Tìm theo tiêu đề

All categories
vi: Tất cả danh mục

No results
vi: Không có kết quả

Browse asset
vi: Duyệt tài sản

Browse assets
vi: Duyệt tài sản

Asset
vi: Tài sản

Select
vi: Chọn

Invalid weights CSV
vi: CSV trọng số không hợp lệ

Expected 25 numbers between 0 and 1
vi: Yêu cầu 25 số trong khoảng từ 0 đến 1

Values are clamped to [0, 1]
vi: Giá trị được giới hạn trong [0, 1]

Preview
vi: Xem trước

Access control
vi: Kiểm soát truy cập

All visitors
vi: Tất cả khách truy cập

All players
vi: Tất cả người chơi

Friends of owners
vi: Bạn của chủ sở hữu

Holders of an item (access card)
vi: Người sở hữu vật phẩm (thẻ truy cập)

Select item
vi: Chọn vật phẩm

No item selected
vi: Chưa chọn vật phẩm

Selected item
vi: Vật phẩm đã chọn

Clear
vi: Xóa

Select items
vi: Chọn nhiều vật phẩm

Quantity
vi: Số lượng

Chat history
vi: Lịch sử trò chuyện

Chat
vi: Trò chuyện

Expire messages
vi: Hết hạn tin nhắn

Keep messages for (days)
vi: Giữ tin nhắn trong (ngày)

Please enter a positive number of days
vi: Vui lòng nhập số ngày dương

Kept forever
vi: Giữ vĩnh viễn

Forever
vi: mãi mãi

Kept for
vi: Giữ trong

day
vi: ngày

days
vi: ngày

Friend requests
vi: Lời mời kết bạn

Incoming
vi: Đến

Outgoing
vi: Đi

No incoming requests
vi: Không có lời mời đến

No outgoing requests
vi: Không có lời mời đi

Accept
vi: Chấp nhận

Decline
vi: Từ chối

sent you a friend request
vi: đã gửi lời mời kết bạn cho bạn

Friend request to
vi: Lời mời kết bạn tới

Unable to jump to your friend right now
vi: Không thể nhảy đến bạn của bạn ngay bây giờ

Unable to determine your friend's exact location
vi: Không thể xác định vị trí chính xác của bạn của bạn

New world
vi: Thế giới mới

Enter world URL
vi: Nhập URL thế giới

Market
vi: Chợ

Go to market
vi: Đi chợ

Your balance
vi: Số dư của bạn

Search items
vi: Tìm kiếm vật phẩm

Buy item
vi: Mua vật phẩm

Per item
vi: Mỗi vật phẩm

Total
vi: Tổng

Buy
vi: Mua

or
vi: hoặc

Paste
vi: Dán

for
vi: với

Click or drop media to upload
vi: Nhấp hoặc thả phương tiện để tải lên

Click or drop image to upload
vi: Nhấp hoặc thả hình ảnh để tải lên

Record audio
vi: Ghi âm

Stop recording
vi: Dừng ghi âm

Click or drop audio to upload
vi: Nhấp hoặc thả âm thanh để tải lên

Uploading...
vi: Đang tải lên...

Upload or choose media
vi: Tải lên hoặc chọn phương tiện

Upload or choose image
vi: Tải lên hoặc chọn hình ảnh

Upload or choose audio
vi: Tải lên hoặc chọn âm thanh

Media Library
vi: Thư viện phương tiện

Delete
vi: Xóa

Loading media...
vi: Đang tải phương tiện...

No media yet. Upload some media above.
vi: Chưa có phương tiện. Hãy tải lên phương tiện phía trên.

Are you sure you want to delete this media?
vi: Bạn có chắc muốn xóa phương tiện này không?

Rename
vi: Đổi tên

New name
vi: Tên mới

Rename media
vi: Đổi tên phương tiện

Print your location
vi: In vị trí của bạn

Shout
vi: Hét

Send
vi: Gửi

Send VND
vi: Gửi tiền

Sending...
vi: Đang gửi...

Send VND to
vi: Gửi tiền đến

Note (optional)
vi: Ghi chú (tùy chọn)

Amount exceeds your balance
vi: Số tiền vượt quá số dư của bạn

Successfully sent VND
vi: Gửi tiền thành công

Okay
vi: Đồng ý

Sent
vi: Đã gửi

Received
vi: Đã nhận

Manage items
vi: Quản lý vật phẩm

Are you sure you want to remove this object?
vi: Bạn có chắc chắn muốn xóa đối tượng này không?

Sit
vi: Ngồi

Stand
vi: Đứng

Are you sure you want to delete this world? This cannot be undone.
vi: Bạn có chắc chắn muốn xóa thế giới này không? Điều này không thể hoàn tác.

Delete world
vi: Xóa thế giới

Attachment (optional)
vi: Tệp đính kèm (tùy chọn)

World price
vi: Giá thế giới

Confirm purchase
vi: Xác nhận mua

You are about to purchase a world for
vi: Bạn sắp mua một thế giới với giá

Continue?
vi: Tiếp tục?

Start your first world!
vi: Bắt đầu thế giới đầu tiên của bạn!

Saigon.Quest is created by Jacob Ferrero
vi: Saigon.Quest được tạo bởi Jacob Ferrero

Saigon.Quest allows anyone to create mini worlds that can be virtual versions of real locations, or simply extra virtual rooms for your house or coffeeshop.
vi: Saigon.Quest cho phép mọi người tạo các thế giới nhỏ có thể là phiên bản ảo của những địa điểm thực, hoặc đơn giản là những phòng ảo bổ sung cho ngôi nhà hoặc quán cà phê của bạn.

Creating your own world
vi: Tạo thế giới của riêng bạn

Hosted worlds: Simply navigate to the URL of your choice
vi: Thế giới được lưu trữ: Chỉ cần truy cập URL bạn muốn

Self-hosted worlds: Please contact Zalo 0708739214
vi: Tự lưu trữ thế giới: Vui lòng liên hệ Zalo 0708739214

About
vi: Giới thiệu

Credits
vi: Tác giả

Hide UI
vi: Ẩn UI

Show UI
vi: Hiển thị UI

Subscribe
vi: Đăng ký

Unsubscribe
vi: Hủy đăng ký

Subscribed
vi: Đã đăng ký

Not subscribed
vi: Chưa đăng ký

Subscribe to this world?
vi: Đăng ký nhận thông báo cho thế giới này?

Unsubscribe from this world?
vi: Hủy đăng ký nhận thông báo cho thế giới này?

Claim your first world!
vi: Nhận thế giới đầu tiên của bạn!

Messages are kept for
vi: Tin nhắn được lưu trữ

Visited Worlds
vi: Thế giới đã ghé thăm

Visited worlds
vi: Thế giới đã ghé thăm

No visited worlds yet
vi: Chưa có thế giới đã ghé thăm

Last visited
vi: Lần cuối ghé thăm

Remove visited world
vi: Xóa thế giới đã ghé thăm

Are you sure you want to remove this visited world?
vi: Bạn có chắc chắn muốn xóa thế giới đã ghé thăm này không?

Observer mode
vi: Chế độ quan sát

Wide angle
vi: Chế độ rộng

World screenshot
vi: Ảnh chụp thế giới

Set screenshot from scene
vi: Đặt ảnh chụp từ cảnh hiện tại

a
vi: Thay đổi ảnh chụp

Search nodes
vi: Tìm kiếm node

Copy link
vi: Sao chép liên kết

Copy to chat
vi: Sao chép vào tin nhắn

Copied!
vi: Đã sao chép!

Write chat message
vi: Viết tin nhắn

Bold
vi: Đậm

Italic
vi: Nghiêng

Underline
vi: Gạch dưới

Strikethrough
vi: Gạch ngang

Insert audio
vi: Chèn âm thanh

Insert image
vi: Chèn hình ảnh

Insert link
vi: Chèn liên kết

Quote
vi: Trích dẫn

Code
vi: Mã

Enter URL
vi: Nhập URL

H1
vi: H1

H2
vi: H2

H3
vi: H3

H4
vi: H4

Rich editor
vi: Trình soạn thảo

World docs
vi: Tài liệu thế giới

Docs
vi: Tài liệu

New doc
vi: Tài liệu mới

Create doc
vi: Tạo tài liệu

Search docs
vi: Tìm kiếm tài liệu

Select doc
vi: Chọn tài liệu

World has no docs.
vi: Thế giới chưa có tài liệu.

Saving…
vi: Đang lưu…

Saved
vi: Đã lưu

Version conflict
vi: Xung đột phiên bản

Cannot edit
vi: Không thể chỉnh sửa

Empty doc
vi: Tài liệu trống

Are you sure you want to delete this doc?
vi: Bạn có chắc chắn muốn xóa tài liệu này không?

Cannot delete: this doc has children
vi: Không thể xóa: tài liệu này có tài liệu con

Move up
vi: Di chuyển lên

Move down
vi: Di chuyển xuống

Title
vi: Tiêu đề

Online
vi: Trực tuyến

Reconnecting…
vi: Đang kết nối lại…

You don't own this world.
vi: Bạn không sở hữu thế giới này.

Owners
vi: Chủ sở hữu

Owner user ID
vi: ID người dùng chủ sở hữu

Add owner
vi: Thêm chủ sở hữu

Remove owner
vi: Xóa chủ sở hữu

No owners
vi: Không có chủ sở hữu

Select friend
vi: Chọn bạn

Search friends
vi: Tìm kiếm bạn bè

Doc history
vi: Lịch sử tài liệu

No history yet
vi: Chưa có lịch sử

Users
vi: Người dùng

Unknown
vi: Không rõ

Restore
vi: Khôi phục

Are you sure you want to restore this version?
vi: Bạn có chắc chắn muốn khôi phục phiên bản này không?

Failed to load history
vi: Tải lịch sử không thành công

Fullscreen
vi: Toàn màn hình

Take a photo
vi: Chụp ảnh

Use real-time sun lighting based on latitude and longitude
vi: Sử dụng ánh sáng mặt trời thực thời gian dựa trên kinh độ và vĩ độ

Latitude and longitude
vi: Kinh độ và vĩ độ

AI
vi: AI

AI prompt
vi: Nhắc AI

AI prompt history
vi: Lịch sử nhắc AI

History
vi: Lịch sử

No items
vi: Không có mục

Prompt
vi: Lời nhắc

Response
vi: Phản hồi

AI request failed or timed out
vi: Yêu cầu AI thất bại hoặc hết thời gian

Send to doc
vi: Gửi vào tài liệu

User requests
vi: Yêu cầu của người dùng

User requests (admin)
vi: Yêu cầu của người dùng (quản trị)

Request new item type
vi: Yêu cầu loại vật phẩm mới

Request new object type
vi: Yêu cầu loại đối tượng mới

Request new node type
vi: Yêu cầu loại nút mới
                
Request new wearable type
vi: Yêu cầu loại trang phục mới

Show MD
vi: Hiện MD

Hide MD
vi: Ẩn MD

Request new wearable item type
vi: Yêu cầu loại vật phẩm có thể mặc mới

Fee
vi: Phí

Coming soon
vi: Sắp ra mắt

New request
vi: Yêu cầu mới

Submit request
vi: Gửi yêu cầu

My requests
vi: Yêu cầu của tôi

Wearable item
vi: Vật phẩm có thể mặc

You are about to purchase a request for
vi: Bạn sắp mua một yêu cầu với giá

Title must be between 3 and 80 characters
vi: Tiêu đề phải từ 3 đến 80 ký tự

Description must be between 10 and 2000 characters
vi: Mô tả phải từ 10 đến 2000 ký tự

In review
vi: Đang xem xét

Refunded
vi: Đã hoàn tiền

Mark in review
vi: Đánh dấu đang xem xét

Mark completed
vi: Đánh dấu hoàn thành

Add note
vi: Thêm ghi chú

All
vi: Tất cả

Is the item wearable?
vi: Vật phẩm có thể mặc không?

Character tool
vi: Công cụ nhân vật

Sprite sheet settings
vi: Cài đặt sprite sheet

Cell width (px)
vi: Chiều rộng ô (px)

Cell height (px)
vi: Chiều cao ô (px)

Columns
vi: Cột

Frames per second
vi: Khung hình mỗi giây

Base sprite sheet
vi: Sprite sheet cơ bản

Additional sprite sheets
vi: Sprite sheet bổ sung

Overlay
vi: Lớp phủ

Start walking
vi: Bắt đầu đi bộ

Stop walking
vi: Dừng đi bộ

AI chat
vi: Chat AI

Send
vi: Gửi

Type a message…
vi: Nhập tin nhắn…

It doesn't fit.
vi: Nó không vừa.

Sex
vi: Giới tính

Location
vi: Vị trí

Boy
vi: Nam

Girl
vi: Nữ

Hair
vi: Tóc

Eyes
vi: Mắt

Ears
vi: Tai

Neck
vi: Cổ

Body
vi: Thân

Legs
vi: Chân

Feet
vi: Bàn chân

Wearable?
vi: Có thể mặc không?

Add a note about this friend...
vi: Thêm ghi chú về người bạn này...

Failed to update note
vi: Cập nhật ghi chú thất bại

Error updating note
vi: Lỗi khi cập nhật ghi chú

Play sound
vi: Phát âm thanh

Sound
vi: Âm thanh

Choose sound
vi: Chọn âm thanh

Change sound
vi: Đổi âm thanh

Remove sound
vi: Xóa âm thanh

Play for everyone
vi: Phát cho mọi người

Plays a sound
vi: Phát âm thanh

Join list
vi: Ghép danh sách

List
vi: Danh sách

Template
vi: Mẫu

Separator
vi: Phân cách

Images (Markdown)
vi: Hình ảnh (Markdown)

Format and join a comma-separated list into a single string
vi: Định dạng và ghép danh sách phân tách bằng dấu phẩy thành một chuỗi duy nhất

String template
vi: Mẫu chuỗi

Format a template string by replacing placeholders like {name} with values
vi: Định dạng chuỗi mẫu bằng cách thay thế các trình giữ chỗ như {name} bằng các giá trị

Prompt player with a title, preview areas and confirm button
vi: Nhắc người chơi bằng tiêu đề, khu vực xem trước và nút xác nhận
"""

expect fun logMissingTranslation(key: String, language: String?)

object Translations {
    private const val DEFAULT_LANGUAGE = "en"

    private val _translations =
        translations.trim().split("""\n\s*\n""".toRegex()).associate {
            val lines = it.split("\n")
            lines[0] to lines.drop(1).associate { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
        }

    val language = MutableStateFlow(DEFAULT_LANGUAGE)

    fun setLanguage(lang: String?) {
        language.value = lang ?: DEFAULT_LANGUAGE
    }

    fun str(key: String) = _translations[key]?.let { it[language.value] } ?: run {
        if (language.value != DEFAULT_LANGUAGE) {
            logMissingTranslation(key, language.value)
        }
        key
    }
}


