import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let composeViewController = MainViewControllerKt.MainViewController()
        // wrap in custom controller that manages status bar
        return FullScreenAwareViewController(childViewController: composeViewController)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// custom view controller wrapper that manages status bar visibility
class FullScreenAwareViewController: UIViewController {
    private let childViewController: UIViewController

    init(childViewController: UIViewController) {
        self.childViewController = childViewController
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        // ensure this controller's view extends edge-to-edge
        view.insetsLayoutMarginsFromSafeArea = false

        addChild(childViewController)
        view.addSubview(childViewController.view)
        childViewController.view.translatesAutoresizingMaskIntoConstraints = false

        // extend to screen edges, not safe area
        NSLayoutConstraint.activate([
            childViewController.view.topAnchor.constraint(equalTo: view.topAnchor),
            childViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            childViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            childViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        // ensure child view also ignores safe area
        childViewController.view.insetsLayoutMarginsFromSafeArea = false

        childViewController.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // force child to use full bounds
        childViewController.view.frame = view.bounds
    }

    override var prefersStatusBarHidden: Bool {
        return PlatformUtils.shared.shouldHideStatusBar()
    }

    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation {
        return .fade
    }

    override var additionalSafeAreaInsets: UIEdgeInsets {
        get {
            // only cancel out safe area insets when full screen is enabled
            if PlatformUtils.shared.shouldHideStatusBar() {
                return UIEdgeInsets(
                    top: -view.safeAreaInsets.top,
                    left: -view.safeAreaInsets.left,
                    bottom: -view.safeAreaInsets.bottom,
                    right: -view.safeAreaInsets.right
                )
            } else {
                return UIEdgeInsets.zero
            }
        }
        set {
            super.additionalSafeAreaInsets = newValue
        }
    }
}


