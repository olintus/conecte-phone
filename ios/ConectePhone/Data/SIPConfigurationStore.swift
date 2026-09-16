import Foundation
import Security

final class SIPConfigurationStore {
    private let defaults = UserDefaults.standard
    private let configurationKey = "sip_configuration_v1"
    private let passwordAccount = "sip_password"
    private let service = "br.com.conectemax.phone"

    func load() -> SIPConfiguration {
        var configuration = defaults.data(forKey: configurationKey)
            .flatMap { try? JSONDecoder().decode(SIPConfiguration.self, from: $0) }
            ?? SIPConfiguration()
        configuration.password = keychainPassword() ?? ""
        return configuration
    }

    func save(_ configuration: SIPConfiguration) {
        var metadata = configuration
        metadata.password = ""
        if let data = try? JSONEncoder().encode(metadata) { defaults.set(data, forKey: configurationKey) }
        savePassword(configuration.password)
    }

    func clear() {
        defaults.removeObject(forKey: configurationKey)
        savePassword("")
    }

    private func keychainPassword() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func savePassword(_ password: String) {
        SecItemDelete(baseQuery as CFDictionary)
        guard !password.isEmpty else { return }
        var item = baseQuery
        item[kSecValueData as String] = Data(password.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }

    private var baseQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: passwordAccount]
    }
}

enum SessionStore {
    private static let key = "signed_in"
    static var isSignedIn: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}

enum InstallationIdentity {
    private static let key = "installation_id"
    static var current: UUID {
        if let raw = UserDefaults.standard.string(forKey: key), let id = UUID(uuidString: raw) { return id }
        let id = UUID()
        UserDefaults.standard.set(id.uuidString.lowercased(), forKey: key)
        return id
    }
}
