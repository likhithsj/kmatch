import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI. Everything on screen -- data loading, the
/// kmatch search, the list -- lives in composeApp/src/commonMain.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose handles the keyboard inset itself
    }
}
