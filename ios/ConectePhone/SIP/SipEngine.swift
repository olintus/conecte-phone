import Combine
import Foundation

@MainActor
protocol SipEngine: AnyObject {
    var registration: RegistrationState { get }
    var activeCall: ActiveCall? { get }
    var changes: AnyPublisher<Void, Never> { get }

    func register(account: SIPAccount, username: String, password: String) async throws
    func unregister() async
    func call(_ handle: String) async throws
    func prepareIncoming(id: UUID, displayName: String, handle: String)
    func answer(id: UUID) async throws
    func reject(id: UUID) async
    func end() async
    func setMuted(_ value: Bool)
    func setSpeaker(_ value: Bool)
    func sendDTMF(_ digit: Character)
}

