import Foundation

@MainActor
final class CallStore: ObservableObject {
    @Published private(set) var recent: [CallRecord] = []
    private let defaults = UserDefaults.standard
    private let key = "conecte_call_history_v1"

    init() { load() }

    func add(_ record: CallRecord) {
        recent.removeAll { $0.id == record.id }
        recent.append(record)
        recent = Array(recent.sorted { $0.occurredAt > $1.occurredAt }.prefix(100))
        save()
    }

    func clear() { recent = []; defaults.removeObject(forKey: key) }

    private struct Stored: Codable {
        let id: UUID
        let displayName: String
        let handle: String
        let direction: String
        let occurredAt: Date
        let duration: TimeInterval
    }

    private func load() {
        guard let data = defaults.data(forKey: key),
              let records = try? JSONDecoder().decode([Stored].self, from: data) else { return }
        recent = records.compactMap { item in
            let direction: CallDirection
            switch item.direction {
            case "incoming": direction = .incoming
            case "outgoing": direction = .outgoing
            case "missed": direction = .missed
            default: return nil
            }
            return CallRecord(id: item.id, displayName: item.displayName, handle: item.handle,
                              direction: direction, occurredAt: item.occurredAt, duration: item.duration)
        }
    }

    private func save() {
        let records = recent.map { item in
            Stored(id: item.id, displayName: item.displayName, handle: item.handle,
                   direction: String(describing: item.direction), occurredAt: item.occurredAt, duration: item.duration)
        }
        if let data = try? JSONEncoder().encode(records) { defaults.set(data, forKey: key) }
    }
}
