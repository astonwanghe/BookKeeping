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

    func restoreUserIfNeeded() async {
        guard isLoggedIn, user == nil else { return }
        do {
            user = try await APIClient.shared.restoreSession().user
        } catch APIError.unauthorized {
            expireSession()
        } catch {
            // 保留当前登录状态，页面可在网络恢复后再次加载用户资料。
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
