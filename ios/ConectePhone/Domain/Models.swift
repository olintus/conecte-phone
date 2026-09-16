import Foundation

enum RegistrationState: Sendable, Equatable { case online, connecting, offline, failed }
enum CallDirection: Sendable, Equatable { case incoming, outgoing, missed }
enum CallPhase: Sendable, Equatable { case idle, dialing, ringing, connecting, active, ended }

enum SIPTransport: String, Codable, CaseIterable, Sendable { case tls = "TLS", tcp = "TCP", udp = "UDP" }

struct SIPConfiguration: Codable, Equatable, Sendable {
    var server = ""
    var port = 5061
    var transport: SIPTransport = .tls
    var username = ""
    var extensionNumber = ""
    var displayName = ""
    var password = ""

    var effectiveExtension: String { extensionNumber.isEmpty ? username : extensionNumber }
    var isConfigured: Bool { !server.isEmpty && !username.isEmpty && !password.isEmpty }
}

struct SIPAccount: Sendable {
    let extensionNumber: String
    let domain: String
    let displayName: String
    let port: Int = 5061
    let transport: SIPTransport = .tls
}

struct CallRecord: Identifiable, Sendable {
    let id: UUID
    let displayName: String
    let handle: String
    let direction: CallDirection
    let occurredAt: Date
    let duration: TimeInterval
}

struct ActiveCall: Identifiable, Sendable {
    let id: UUID
    var displayName: String
    var handle: String
    var phase: CallPhase
    var startedAt: Date?
    var isMuted: Bool
    var isSpeakerEnabled: Bool
}
