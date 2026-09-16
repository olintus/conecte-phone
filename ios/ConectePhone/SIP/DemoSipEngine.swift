import Combine
import Foundation

@MainActor
final class DemoSipEngine: SipEngine {
    private let subject = PassthroughSubject<Void, Never>()
    var changes: AnyPublisher<Void, Never> { subject.eraseToAnyPublisher() }
    private(set) var registration: RegistrationState = .online
    private(set) var activeCall: ActiveCall?

    func register(account: SIPAccount, username: String, password: String) async throws {
        registration = .connecting; subject.send()
        try await Task.sleep(for: .milliseconds(350))
        registration = .online; subject.send()
    }

    func unregister() async { registration = .offline; subject.send() }

    func call(_ handle: String) async throws {
        guard !handle.isEmpty else { return }
        activeCall = ActiveCall(id: UUID(), displayName: handle, handle: handle, phase: .dialing, startedAt: nil, isMuted: false, isSpeakerEnabled: false)
        subject.send()
        try await Task.sleep(for: .milliseconds(650))
        activeCall?.phase = .active
        activeCall?.startedAt = Date()
        subject.send()
    }

    func prepareIncoming(id: UUID, displayName: String, handle: String) {
        activeCall = ActiveCall(id: id, displayName: displayName, handle: handle, phase: .ringing, startedAt: nil, isMuted: false, isSpeakerEnabled: false)
        subject.send()
    }

    func answer(id: UUID) async throws {
        guard activeCall?.id == id else { return }
        activeCall?.phase = .active
        activeCall?.startedAt = Date()
        subject.send()
    }

    func reject(id: UUID) async { activeCall = nil; subject.send() }
    func end() async { activeCall = nil; subject.send() }
    func setMuted(_ value: Bool) { activeCall?.isMuted = value; subject.send() }
    func setSpeaker(_ value: Bool) { activeCall?.isSpeakerEnabled = value; subject.send() }
    func sendDTMF(_ digit: Character) {}
}

