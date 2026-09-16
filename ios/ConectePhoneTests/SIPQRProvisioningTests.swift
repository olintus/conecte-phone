import XCTest
@testable import ConectePhone

final class SIPQRProvisioningTests: XCTestCase {
    func testParsesAndroidCompatiblePayload() throws {
        let value = try SIPQRProvisioning.parse(
            "conectephone://provision?v=1&server=fone.conectemax.com.br&port=5061&transport=TLS&username=313&extension=313&displayName=Recep%C3%A7%C3%A3o&password=a%2Bb%26c&expires=1800000000",
            now: Date(timeIntervalSince1970: 1_700_000_000)
        )
        XCTAssertEqual(value.server, "fone.conectemax.com.br")
        XCTAssertEqual(value.transport, .tls)
        XCTAssertEqual(value.displayName, "Recepção")
        XCTAssertEqual(value.password, "a+b&c")
    }

    func testRejectsExpiredPayload() {
        XCTAssertThrowsError(try SIPQRProvisioning.parse(
            "conectephone://provision?v=1&server=pbx.example.com&port=5060&transport=UDP&username=313&password=secret&expires=1600000000",
            now: Date(timeIntervalSince1970: 1_700_000_000)
        ))
    }
}
