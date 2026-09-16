import AVFAudio
import CallKit
import Foundation
import PushKit
import Combine

@MainActor
final class CallCoordinator: NSObject, @preconcurrency CXProviderDelegate, @preconcurrency PKPushRegistryDelegate {
    private let engine: any SipEngine
    private let provider: CXProvider
    private let controller = CXCallController()
    private var registry: PKPushRegistry?
    private var currentCallID: UUID?
    private var pendingOutgoing: [UUID: String] = [:]
    private var engineObserver: AnyCancellable?

    init(engine: any SipEngine) {
        self.engine = engine
        let configuration = CXProviderConfiguration(localizedName: "Conecte Phone")
        configuration.supportsVideo = false
        configuration.maximumCallsPerCallGroup = 1
        configuration.maximumCallGroups = 1
        configuration.supportedHandleTypes = [.phoneNumber, .generic]
        configuration.includesCallsInRecents = true
        self.provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: .main)
        engineObserver = engine.changes.sink { [weak self] in
            Task { @MainActor in
                guard let self, self.engine.activeCall == nil, let id = self.currentCallID else { return }
                self.provider.reportCall(with: id, endedAt: Date(), reason: .remoteEnded)
                self.currentCallID = nil
            }
        }
    }

    func start() {
        let registry = PKPushRegistry(queue: .main)
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
        self.registry = registry
    }

    func startOutgoing(handle: String) async throws {
        let id = UUID()
        pendingOutgoing[id] = handle
        currentCallID = id
        let action = CXStartCallAction(call: id, handle: CXHandle(type: .phoneNumber, value: handle))
        try await request(CXTransaction(action: action))
    }

    func endCurrent() async {
        guard let id = currentCallID else { return }
        try? await request(CXTransaction(action: CXEndCallAction(call: id)))
    }

    private func request(_ transaction: CXTransaction) async throws {
        try await withCheckedThrowingContinuation { continuation in
            controller.request(transaction) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        guard type == .voIP else { return }
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        VoIPPushTokenStore.current = token
        Task { _ = await PushDeviceRegistrar.register(configuration: AppContainer.shared.configuration) }
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        guard type == .voIP else { return }
        VoIPPushTokenStore.current = nil
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else { completion(); return }
        let data = payload.dictionaryPayload
        let id = (data["callId"] as? String).flatMap(UUID.init(uuidString:)) ?? UUID()
        let name = data["displayName"] as? String ?? "Ligação recebida"
        let handle = data["handle"] as? String ?? "privado"

        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: handle)
        update.localizedCallerName = name
        update.hasVideo = false

        currentCallID = id
        engine.prepareIncoming(id: id, displayName: name, handle: handle)
        provider.reportNewIncomingCall(with: id, update: update) { error in
            if error != nil { Task { @MainActor in await self.engine.reject(id: id) } }
            else { Task { @MainActor in await AppContainer.shared.restoreSession() } }
            completion()
        }
    }

    func providerDidReset(_ provider: CXProvider) {
        Task { await engine.end() }
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        guard let handle = pendingOutgoing.removeValue(forKey: action.callUUID) else { action.fail(); return }
        Task {
            do {
                try await engine.call(handle)
                provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())
                action.fulfill()
            } catch {
                currentCallID = nil
                action.fail()
            }
        }
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        Task {
            do {
                try await engine.answer(id: action.callUUID)
                action.fulfill()
            } catch {
                action.fail()
                provider.reportCall(with: action.callUUID, endedAt: Date(), reason: .failed)
            }
        }
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        Task { currentCallID = nil; await engine.end(); action.fulfill() }
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        engine.setMuted(action.isMuted)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        // The production SIP engine attaches its audio device only after this callback.
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        // The production SIP engine releases its audio device here.
    }
}
