import SwiftUI
import HarnessUI

@main
struct HarnessSampleApp: App {
    var body: some Scene {
        WindowGroup { ComposeHost().ignoresSafeArea() }
    }
}

struct ComposeHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
