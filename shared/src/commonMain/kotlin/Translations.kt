import kotlinx.coroutines.flow.MutableStateFlow

private const val translations = """
Close
vi: Đóng

collected
vi: đã thu thập

Open
vi: Mở

Customize Character
vi: Tùy chỉnh nhân vật

Status
vi: Trạng thái

Player Account
vi: Tài khoản người chơi

Language
vi: Ngôn ngữ

Low Graphics Mode
vi: Chế độ đồ họa thấp

Pretty Graphics
vi: Đồ họa đẹp

Log Out
vi: Đăng xuất

My Profile
vi: Trang cá nhân của tôi

Set your status
vi: Đặt trạng thái của bạn

World Chat
vi: Trò chuyện thế giới

© 2025 Saigon.Quest v0.1
vi: © 2025 Saigon.Quest v0.1

Claim this World
vi: Yêu cầu thế giới này

Login and Claim this World
vi: Đăng nhập và yêu cầu thế giới này

Go home
vi: Về trang chủ

Retry
vi: Thử lại

Loading Saigon.Quest world...
vi: Đang tải thế giới Saigon.Quest...

Loading...
vi: Đang tải...

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

📥 Download Image
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

Error:
vi: Lỗi:

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

Enter item title
vi: Nhập tiêu đề vật phẩm

Description
vi: Mô tả

Enter item description
vi: Nhập mô tả vật phẩm

File path (URL to sprite sheet)
vi: Đường dẫn tệp (URL tới sprite sheet)

Enter file path
vi: Nhập đường dẫn tệp

Asset index (0-99)
vi: Chỉ số tài nguyên (0-99)

Enter asset index
vi: Nhập chỉ số tài nguyên

Is this item wearable?
vi: Vật phẩm này có thể mặc được không?

Wearable file path (URL to sprite sheet)
vi: Đường dẫn tệp vật phẩm có thể mặc (URL tới sprite sheet)

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

Failed to remove
vi: Không thể gỡ

Wearing
vi: Đang mặc

Failed to wear
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

e.g. world1
vi: ví dụ: world1

Collection
vi: Bộ sưu tập

e.g. chair_collection
vi: ví dụ: chair_collection

Status text (displayed above object)
vi: Văn bản trạng thái (hiển thị phía trên đối tượng)

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

Edit World
vi: Chỉnh sửa thế giới

Create World
vi: Tạo thế giới

Create your player
vi: Tạo người chơi của bạn

Open world chat
vi: Mở trò chuyện thế giới

Inventory
vi: Kho đồ

Refresh
vi: Làm mới

New
vi: Mới

Loading inventory...
vi: Đang tải kho đồ...

Your Character
vi: Nhân vật của bạn

Drop to wear item
vi: Thả để mặc vật phẩm

Drag wearable items here to equip them
vi: Kéo vật phẩm có thể mặc vào đây để trang bị

Worn Items
vi: Vật phẩm đang mặc

No items worn. Drag wearable items to your character to wear them.
vi: Chưa mặc vật phẩm nào. Kéo vật phẩm có thể mặc vào nhân vật của bạn để mặc.

(Click to remove)
vi: (Nhấp để gỡ)

Inventory Items
vi: Vật phẩm trong kho

Your inventory is empty. Click '+ New' to create an item.
vi: Kho đồ của bạn trống. Nhấp '+ Mới' để tạo vật phẩm.

Wearable
vi: Có thể mặc

Use
vi: Dùng

Wear
vi: Mặc

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

Drag items from the left panel to create nodes
vi: Kéo mục từ bảng bên trái để tạo nút

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

Resume to
vi: Tiếp tục vào

Paused.
vi: Đã tạm dừng.

"""

expect fun logMissingTranslation(key: String, language: String?)

object Translations {
    private const val DEFAULT_LANGUAGE = "en"

    private val _translations =
        translations.trim().split("\n\n").associate {
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
