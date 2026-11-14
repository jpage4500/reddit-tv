import ComposeApp
import Foundation
import SwiftUI

@main
struct iOSApp: App {

    init() {
        // inject the Swift implementations into Kotlin
        FileViewerKt.globalFileViewer = IOSFileViewer()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // sharetotv:// links, from the Share Extension. qualified: SwiftUI has a ShareLink too
                .onOpenURL { url in
                    _ = ComposeApp.ShareLink.shared.handle(link: url.absoluteString)
                }
        }
    }
}
