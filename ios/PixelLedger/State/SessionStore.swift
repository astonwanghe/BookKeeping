import Foundation
import SwiftUI

@MainActor final class SessionStore: ObservableObject {
    @Published var user: User?
    @Published var error: String?
    var isLoggedIn: Bool { Keychain.value(for: "accessToken") != nil }
    func login(phone: String, password: String) async { do { user = try await APIClient.shared.login(phone: phone, password: password) } catch { self.error = error.localizedDescription } }
    func logout() { user = nil; Task { await APIClient.shared.logout() } }
}
