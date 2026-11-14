import UIKit
import QuickLook
import ComposeApp

class IOSFileViewer: NSObject, FileViewer, QLPreviewControllerDataSource {
    private var previewUrl: URL?

    func viewFile(path: String) {
        // QLPreviewController must be presented on the main thread
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.previewUrl = URL(fileURLWithPath: path)

            let controller = QLPreviewController()
            controller.dataSource = self

            guard let root = UIApplication.shared.connectedScenes
                .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                .first?.rootViewController else { return }

            // walk to the topmost presented VC so this works over the dashboard or fullscreen view
            var top = root
            while let presented = top.presentedViewController { top = presented }
            top.present(controller, animated: true)
        }
    }

    func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
        return previewUrl == nil ? 0 : 1
    }

    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        return previewUrl! as NSURL
    }
}
