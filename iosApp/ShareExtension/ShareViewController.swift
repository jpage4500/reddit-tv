import UIKit
import UniformTypeIdentifiers

/// the share sheet's way in. it has no UI and doesn't load the Kotlin framework (an extension gets
/// ~120 MB): it pulls the link — or the text around one — out of what was shared, hands it to the
/// app as `sharetotv://share?url=…&title=…` (ShareLink.kt), and closes
class ShareViewController: UIViewController {
    private static let scheme = "sharetotv"

    // RFC 3986 unreserved: everything else in a query value is escaped, '&' and '=' included
    private static let unreserved = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")

    private var isHandled = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if isHandled { return }
        isHandled = true
        Task { await share() }
    }

    private func share() async {
        let items = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        var link: String?
        var text: String?
        for item in items {
            for provider in item.attachments ?? [] {
                if link == nil, provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                   let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) as? URL,
                   !url.isFileURL {
                    link = url.absoluteString
                } else if text == nil, provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                          let value = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) as? String {
                    text = value
                }
            }
        }
        // a page shared as a link usually carries its title as the item's text
        let title = items.first?.attributedContentText?.string.trimmingCharacters(in: .whitespacesAndNewlines)
        // the app finds the url inside shared text itself (ShareManager.extractUrl)
        guard let shared = link ?? text, !shared.isEmpty else {
            extensionContext?.cancelRequest(withError: NSError(domain: "ShareToTV", code: 1))
            return
        }

        var query = "url=" + escape(shared)
        if let title = title, !title.isEmpty, title != shared {
            query += "&title=" + escape(title)
        }
        if let url = URL(string: "\(Self.scheme)://share?\(query)") {
            await MainActor.run { _ = openApp(url) }
        }
        extensionContext?.completeRequest(returningItems: nil)
    }

    private func escape(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: Self.unreserved) ?? value
    }

    /// an extension has no UIApplication.shared; the application is further up the responder chain
    @discardableResult
    private func openApp(_ url: URL) -> Bool {
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return true
            }
            responder = current.next
        }
        return false
    }
}
