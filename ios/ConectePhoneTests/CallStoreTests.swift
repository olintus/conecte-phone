import XCTest
@testable import ConectePhone

@MainActor
final class CallStoreTests: XCTestCase {
    func testPersistentHistoryIsNewestFirst() {
        let store = CallStore()
        store.clear()
        store.add(.init(id: UUID(), displayName: "Antiga", handle: "300", direction: .outgoing,
                        occurredAt: Date().addingTimeInterval(-60), duration: 10))
        store.add(.init(id: UUID(), displayName: "Nova", handle: "301", direction: .incoming,
                        occurredAt: Date(), duration: 5))
        XCTAssertEqual(store.recent.count, 2)
        XCTAssertGreaterThan(store.recent[0].occurredAt, store.recent[1].occurredAt)
        store.clear()
    }
}
