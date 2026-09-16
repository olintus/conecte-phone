import Foundation

enum VoIPPushTokenStore {
    private static let key = "apns_voip_token"
    static var current: String? {
        get { UserDefaults.standard.string(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}

enum PushDeviceRegistrar {
    static let endpoint = URL(string: "https://fone.conectemax.com.br/v1/mobile/devices")!

    static func register(configuration: SIPConfiguration) async -> Bool {
        guard let token = VoIPPushTokenStore.current, !configuration.effectiveExtension.isEmpty else { return false }
        return await request(method: "PUT", extension: configuration.effectiveExtension, token: token) == 204
    }

    static func unbind(configuration: SIPConfiguration) async {
        guard let token = VoIPPushTokenStore.current, !configuration.effectiveExtension.isEmpty else { return }
        _ = await request(method: "DELETE", extension: configuration.effectiveExtension, token: token)
    }

    private static func request(method: String, extension: String, token: String) async -> Int {
        var request = URLRequest(url: endpoint)
        request.httpMethod = method
        request.timeoutInterval = 8
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: [
            "installationId": InstallationIdentity.current.uuidString.lowercased(),
            "extension": extension,
            "platform": "ios",
            "pushType": "apns_voip",
            "pushToken": token,
        ])
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode ?? -1
        } catch {
            return -1
        }
    }
}
