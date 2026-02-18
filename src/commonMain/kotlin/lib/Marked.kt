package lib

@JsModule("marked")
@JsNonModule
external object marked {
    class Renderer {
        var link: (token: RendererLinkToken) -> String = definedExternally
        var html: (token: RendererHtmlToken) -> String = definedExternally
        var text: (token: RendererTextToken) -> String = definedExternally
    }

    fun parse(
        text: String,
        options: dynamic = definedExternally
    ): String

    fun use(options: dynamic): Unit

    interface RendererLinkToken {
        var href: String
        var raw: String
        var title: String?
        var text: String?
    }

    interface RendererHtmlToken {
        var raw: String
        var text: String
    }

    interface RendererTextToken {
        var raw: String
        var text: String
    }
}
