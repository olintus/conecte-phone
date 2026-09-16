import Foundation

enum SIPQRProvisioning {
    static func parse(_ raw: String, now: Date = Date()) throws -> SIPConfiguration {
        guard raw.count <= 4096, let components = URLComponents(string: raw),
              components.scheme?.lowercased() == "conectephone",
              components.host?.lowercased() == "provision" else {
            throw ProvisioningError.message("Este QR Code não pertence ao Conecte Phone.")
        }
        var values: [String: String] = [:]
        for item in components.queryItems ?? [] {
            guard let value = item.value, values[item.name] == nil else {
                throw ProvisioningError.message("QR Code inválido.")
            }
            values[item.name] = value
        }
        guard values["v"] == "1" else { throw ProvisioningError.message("Versão do QR Code não suportada.") }
        if let expires = values["expires"].flatMap(TimeInterval.init), now.timeIntervalSince1970 > expires {
            throw ProvisioningError.message("Este QR Code expirou. Gere outro no portal.")
        }
        guard let server = values["server"], !server.isEmpty,
              !server.contains(where: { $0.isWhitespace || "/?#@".contains($0) }),
              let portRaw = values["port"], let port = Int(portRaw), (1...65535).contains(port),
              let transportRaw = values["transport"]?.uppercased(),
              let transport = SIPTransport(rawValue: transportRaw),
              let username = values["username"], !username.isEmpty,
              let password = values["password"], !password.isEmpty else {
            throw ProvisioningError.message("Dados SIP inválidos no QR Code.")
        }
        return SIPConfiguration(server: server, port: port, transport: transport,
                                username: username, extensionNumber: values["extension"] ?? username,
                                displayName: values["displayName"] ?? "", password: password)
    }

    enum ProvisioningError: LocalizedError {
        case message(String)
        var errorDescription: String? { if case let .message(value) = self { value } else { nil } }
    }
}
