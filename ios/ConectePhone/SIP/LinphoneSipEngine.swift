#if canImport(linphonesw)
import Combine
import Foundation
import linphonesw

@MainActor
final class LinphoneSipEngine: SipEngine {
    private let core: Core
    private let delegate: CoreDelegateStub
    private let subject = PassthroughSubject<Void, Never>()
    private let callStore: CallStore
    private var currentCall: Call?
    private var pendingID: UUID?
    private var direction: CallDirection?
    private var occurredAt = Date()
    private var connectedAt: Date?
    private var registeredDomain = ""

    var changes: AnyPublisher<Void, Never> { subject.eraseToAnyPublisher() }
    private(set) var registration: RegistrationState = .offline
    private(set) var activeCall: ActiveCall?

    init(callStore: CallStore) throws {
        self.callStore = callStore
        self.core = try Factory.Instance.createCore(configPath: "", factoryConfigPath: "", systemContext: nil)
        self.delegate = CoreDelegateStub()
        delegate.onAccountRegistrationStateChanged = { [weak self] _, _, state, _ in
            Task { @MainActor in
                guard let self else { return }
                switch state {
                case .Ok: self.registration = .online
                case .Progress, .Refreshing: self.registration = .connecting
                case .Failed: self.registration = .failed
                default: self.registration = .offline
                }
                self.subject.send()
            }
        }
        delegate.onCallStateChanged = { [weak self] _, call, state, _ in
            Task { @MainActor in self?.update(call: call, state: state) }
        }
        core.addDelegate(delegate: delegate)
        core.isAutoIterateEnabled = true
        core.isPushNotificationEnabled = true
        core.setUserAgent(name: "ConectePhone", version: "0.6.4;id=\(InstallationIdentity.current.uuidString.lowercased())")
        try core.start()
    }

    func register(account: SIPAccount, username: String, password: String) async throws {
        core.accountList.forEach { core.removeAccount(account: $0) }
        core.authInfoList.forEach { core.removeAuthInfo(info: $0) }
        registeredDomain = account.domain
        registration = .connecting; subject.send()
        let identity = try Factory.Instance.createAddress(addr: "sip:\(account.extensionNumber)@\(account.domain)")
        identity.displayName = account.displayName
        let server = try Factory.Instance.createAddress(addr: "sip:\(account.domain):\(account.port)")
        switch account.transport {
        case .tls: server.transport = .Tls
        case .tcp: server.transport = .Tcp
        case .udp: server.transport = .Udp
        }
        let auth = try Factory.Instance.createAuthInfo(username: username, userid: "", passwd: password,
                                                       ha1: "", realm: "", domain: account.domain)
        let parameters = try core.createAccountParams()
        parameters.identityAddress = identity
        parameters.serverAddress = server
        parameters.registerEnabled = true
        parameters.pushNotificationAllowed = true
        let linphoneAccount = try core.createAccount(params: parameters)
        core.addAuthInfo(info: auth)
        core.addAccount(account: linphoneAccount)
        core.defaultAccount = linphoneAccount
        linphoneAccount.refreshRegister()
        for _ in 0..<200 {
            if registration == .online { return }
            if registration == .failed { throw SIPError.registrationFailed }
            try await Task.sleep(for: .milliseconds(100))
        }
        throw SIPError.registrationTimedOut
    }

    func unregister() async {
        currentCall?.terminate()
        core.accountList.forEach { core.removeAccount(account: $0) }
        core.authInfoList.forEach { core.removeAuthInfo(info: $0) }
        registration = .offline
        activeCall = nil
        subject.send()
    }

    func call(_ handle: String) async throws {
        guard registration == .online else { throw SIPError.notRegistered }
        let target = handle.hasPrefix("sip:") ? handle : "sip:\(handle)@\(registeredDomain)"
        let address = try Factory.Instance.createAddress(addr: target)
        pendingID = UUID(); direction = .outgoing; occurredAt = Date(); connectedAt = nil
        currentCall = try core.inviteAddress(addr: address)
    }

    func prepareIncoming(id: UUID, displayName: String, handle: String) {
        pendingID = id; direction = .incoming; occurredAt = Date(); connectedAt = nil
        activeCall = ActiveCall(id: id, displayName: displayName, handle: handle, phase: .ringing,
                                startedAt: nil, isMuted: false, isSpeakerEnabled: false)
        subject.send()
    }

    func answer(id: UUID) async throws {
        for _ in 0..<100 where currentCall == nil { try await Task.sleep(for: .milliseconds(100)) }
        guard let call = currentCall, activeCall?.id == id else { throw SIPError.callUnavailable }
        try call.accept()
    }

    func reject(id: UUID) async { currentCall?.terminate(); finish(missed: connectedAt == nil) }
    func end() async { currentCall?.terminate() }
    func setMuted(_ value: Bool) { core.isMicEnabled = !value; activeCall?.isMuted = value; subject.send() }
    func setSpeaker(_ value: Bool) {
        let wanted: AudioDevice.Type = value ? .Speaker : .Earpiece
        if let device = core.audioDevices.first(where: { $0.type == wanted }) { currentCall?.outputAudioDevice = device }
        activeCall?.isSpeakerEnabled = value; subject.send()
    }
    func sendDTMF(_ digit: Character) { currentCall?.sendDtmf(dtmf: Int(digit.asciiValue ?? 0)) }

    private func update(call: Call, state: Call.State) {
        currentCall = call
        let address = call.remoteAddress
        let handle = address.username.nilIfEmpty ?? address.asStringUriOnly()
        let name = address.displayName.nilIfEmpty ?? handle
        let id = pendingID ?? UUID()
        pendingID = id
        let phase: CallPhase
        switch state {
        case .IncomingReceived, .PushIncomingReceived: phase = .ringing; direction = .incoming
        case .OutgoingInit, .OutgoingProgress, .OutgoingRinging, .OutgoingEarlyMedia: phase = .dialing; direction = .outgoing
        case .Connected, .StreamsRunning: phase = .active; connectedAt = connectedAt ?? Date()
        case .End, .Error, .Released: finish(missed: direction == .incoming && connectedAt == nil); return
        default: phase = .connecting
        }
        activeCall = ActiveCall(id: id, displayName: name, handle: handle, phase: phase,
                                startedAt: phase == .active ? connectedAt : nil,
                                isMuted: activeCall?.isMuted ?? false,
                                isSpeakerEnabled: activeCall?.isSpeakerEnabled ?? false)
        subject.send()
    }

    private func finish(missed: Bool) {
        if let call = activeCall, let direction {
            callStore.add(CallRecord(id: call.id, displayName: call.displayName, handle: call.handle,
                                     direction: missed ? .missed : direction, occurredAt: occurredAt,
                                     duration: connectedAt.map { Date().timeIntervalSince($0) } ?? 0))
        }
        currentCall = nil; activeCall = nil; pendingID = nil; direction = nil; connectedAt = nil
        subject.send()
    }

    enum SIPError: Error { case notRegistered, registrationFailed, registrationTimedOut, callUnavailable }
}

private extension String { var nilIfEmpty: String? { isEmpty ? nil : self } }
#endif
