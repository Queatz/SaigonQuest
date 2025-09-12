package serialization

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import models.chat.ImageAttachment

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = ImageAttachment::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ImageAttachment::class, name = "Image"),
)
interface AttachmentMixin
