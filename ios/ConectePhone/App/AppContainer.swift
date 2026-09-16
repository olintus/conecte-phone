import Combine
import Foundation

@MainActor
final class AppContainer: ObservableObject {
    static let shared = AppContainer()

    let sipEngine: any SipEngine
    let callStore: CallStore
    let callCoordinator: CallCoordinator
    let configurationStore = SIPConfigurationStore()
    @Published var configuration: SIPConfiguration
    @Published var signedIn: Bool

    private init() {
        let store = CallStore()
        self.callStore = store
        #if canImport(linphonesw)
        let engine: any SipEngine
        if let productionEngine = try? LinphoneSipEngine(callStore: store) {
            engine = productionEngine
        } else {
            engine = DemoSipEngine()
        }
        #else
        let engine: any SipEngine = DemoSipEngine()
        #endif
        self.sipEngine = engine
        self.callCoordinator = CallCoordinator(engine: engine)
        self.configuration = configurationStore.load()
        self.signedIn = SessionStore.isSignedIn
    }

    func restoreSession() async {
        guard signedIn, configuration.isConfigured else { return }
        guard sipEngine.registration == .offline || sipEngine.registration == .failed else { return }
        try? await register(configuration)
    }

    func register(_ value: SIPConfiguration) async throws {
        let previous = configuration
        try await sipEngine.register(
            account: SIPAccount(extensionNumber: value.effectiveExtension, domain: value.server,
                                displayName: value.displayName.isEmpty ? value.effectiveExtension : value.displayName,
                                port: value.port, transport: value.transport),
            username: value.username,
            password: value.password
        )
        if previous.isConfigured && previous.effectiveExtension != value.effectiveExtension {
            await PushDeviceRegistrar.unbind(configuration: previous)
        }
        configurationStore.save(value)
        configuration = value
        signedIn = true
        SessionStore.isSignedIn = true
        _ = await PushDeviceRegistrar.register(configuration: value)
    }

    func logout() async {
        await PushDeviceRegistrar.unbind(configuration: configuration)
        await sipEngine.unregister()
        callStore.clear()
        configurationStore.clear()
        configuration = SIPConfiguration()
        signedIn = false
        SessionStore.isSignedIn = false
    }
}
