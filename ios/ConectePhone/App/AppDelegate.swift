import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        AppContainer.shared.callCoordinator.start()
        Task { @MainActor in await AppContainer.shared.restoreSession() }
        return true
    }
}
