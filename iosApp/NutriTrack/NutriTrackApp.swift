import SwiftUI
import Shared

@main
struct NutriTrackApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and handles the safe area and keyboard itself.
            ComposeView().ignoresSafeArea()
        }
    }
}

/// Hosts the shared Compose UI (the same screens as the Android app).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(scanner: CameraBarcodeScanner())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
