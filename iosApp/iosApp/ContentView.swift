import SwiftUI
import UIKit
import shared

/// Compose Multiplatform 入口（shared 模块导出的 MainViewController）
struct ComposeContainer: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeContainer()
            .ignoresSafeArea(.all)
    }
}
