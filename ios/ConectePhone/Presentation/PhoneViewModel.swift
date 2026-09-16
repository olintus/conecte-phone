import Combine
import Foundation

@MainActor
final class PhoneViewModel: ObservableObject {
    @Published private(set) var registration: RegistrationState
    @Published private(set) var activeCall: ActiveCall?

    private let engine: any SipEngine
    private let coordinator: CallCoordinator
    private var cancellable: AnyCancellable?

    init(engine: any SipEngine = AppContainer.shared.sipEngine,
         coordinator: CallCoordinator = AppContainer.shared.callCoordinator) {
        self.engine = engine
        self.coordinator = coordinator
        self.registration = engine.registration
        self.activeCall = engine.activeCall
        self.cancellable = engine.changes.sink { [weak self] in
            Task { @MainActor in self?.refresh() }
        }
    }

    func call(_ handle: String) { Task { try? await coordinator.startOutgoing(handle: handle) } }
    func end() { Task { await coordinator.endCurrent() } }
    func mute(_ value: Bool) { engine.setMuted(value) }
    func speaker(_ value: Bool) { engine.setSpeaker(value) }
    func dtmf(_ digit: Character) { engine.sendDTMF(digit) }

    private func refresh() {
        registration = engine.registration
        activeCall = engine.activeCall
    }
}
