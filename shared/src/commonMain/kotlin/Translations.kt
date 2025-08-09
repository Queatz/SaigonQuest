import kotlinx.coroutines.flow.MutableStateFlow

private const val translations = """
Close
vi: Đóng

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

Loading Saigon.Quest world...
vi: Đang tải thế giới Saigon.Quest...

Loading...
vi: Đang tải...

Error loading world
vi: Lỗi tải thế giới

Untitled World
vi: Thế giới không tên

Confirm
vi: Xác nhận

No
vi: Không

Yes
vi: Có

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
