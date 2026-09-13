import XCTest

final class PaymentSmokeTests: XCTestCase {
    @MainActor
    func testVisiblePaymentFlow() {
        let app = XCUIApplication()
        app.launch()
        let next = app.buttons["contract.continue"]
        XCTAssertTrue(next.waitForExistence(timeout: 15))
        capture("ios-contract")
        next.tap()
        let submit = app.buttons["payment.submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 5))
        XCTAssertTrue(submit.isHittable)
        capture("ios-confirm")
        submit.tap()
        let success = app.descendants(matching: .any)["payment.success"].firstMatch
        XCTAssertTrue(success.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["payment.submit"].exists)
        capture("ios-success")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
