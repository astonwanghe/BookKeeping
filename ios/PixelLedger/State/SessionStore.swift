import Foundation
import SwiftUI

@MainActor final class SessionStore: ObservableObject {
    @Published var user: User?
    @Published var error: String?
    @Published private(set) var isLoggedIn: Bool

    init() {
        isLoggedIn = Keychain.value(for: "accessToken") != nil
    }

    func login(phone: String, password: String) async {
        error = nil
        do {
            let response = try await APIClient.shared.login(phone: phone, password: password)
            user = response.user
            isLoggedIn = true
        } catch {
            self.error = error.localizedDescription
        }
    }

    func logout() {
        user = nil
        isLoggedIn = false
        Task { await APIClient.shared.logout() }
    }

    func expireSession() {
        user = nil
        isLoggedIn = false
        error = APIError.unauthorized.localizedDescription
        Task { await APIClient.shared.clearTokens() }
    }
}
