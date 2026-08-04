import Foundation

enum APIError: LocalizedError { case badURL, unauthorized, server(String), decoding
    var errorDescription: String? { switch self { case .badURL: return "服务器地址无效"; case .unauthorized: return "登录状态已失效"; case .server(let message): return message; case .decoding: return "服务器返回的数据无法读取" } }
}

actor APIClient {
    static let shared = APIClient()
    private let baseURL: URL
    // 当前进程内优先使用刚刚登录或刷新的令牌，避免模拟器 Keychain 返回旧值。
    private var accessToken: String?
    private var refreshToken: String?

    init() {
        let raw = Bundle.main.object(forInfoDictionaryKey: "APIBaseURL") as? String ?? "https://api.example.com"
        baseURL = URL(string: raw)!
        accessToken = Keychain.value(for: "accessToken")
        refreshToken = Keychain.value(for: "refreshToken")
    }
    func request<T: Decodable>(_ path: String, method: String = "GET", body: (any Encodable)? = nil) async throws -> T { try await perform(path, method: method, body: body, retry: true) }
    private func perform<T: Decodable>(_ path: String, method: String, body: (any Encodable)?, retry: Bool) async throws -> T {
        var request = URLRequest(url: makeURL(path)); request.httpMethod = method; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try JSONEncoder().encode(AnyEncodable(body)) }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.server("网络连接失败") }
        if http.statusCode == 401, retry { try await refresh(); return try await perform(path, method: method, body: body, retry: false) }
        if http.statusCode == 401 {
            // 登录请求失败时直接展示服务端返回的原因，不能再尝试刷新旧会话。
            if path == "auth/login" {
                let message = (try? JSONDecoder().decode(ErrorBody.self, from: data).error)
                    ?? APIError.unauthorized.localizedDescription
                throw APIError.server(message)
            }
            throw APIError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            let message = (try? JSONDecoder().decode(ErrorBody.self, from: data).error) ?? "请求失败（\(http.statusCode)）"; throw APIError.server(message)
        }
        guard !data.isEmpty else { return Empty() as! T }
        guard let value = try? JSONDecoder().decode(T.self, from: data) else { throw APIError.decoding }; return value
    }
    func login(phone: String, password: String) async throws -> LoginResponse {
        // 登录是建立会话的入口，不应携带旧会话的自动刷新逻辑。
        let result: LoginResponse = try await perform(
            "auth/login",
            method: "POST",
            body: LoginBody(phone: phone, password: password),
            retry: false
        )
        store(result)
        return result
    }
    func logout() async {
        if let refreshToken {
            do {
                let _: Empty = try await perform("auth/logout", method: "POST", body: RefreshBody(refreshToken: refreshToken), retry: false)
            } catch {
                // 即使服务端退出请求失败，也要清理本地令牌。
            }
        }
        clearTokens()
    }

    func clearTokens() {
        accessToken = nil
        refreshToken = nil
        Keychain.remove("accessToken")
        Keychain.remove("refreshToken")
    }
    private func refresh() async throws {
        guard let refreshToken else { throw APIError.unauthorized }
        let result: LoginResponse = try await perform("auth/refresh", method: "POST", body: RefreshBody(refreshToken: refreshToken), retry: false)
        store(result)
    }

    private func store(_ response: LoginResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        Keychain.set(response.accessToken, for: "accessToken")
        Keychain.set(response.refreshToken, for: "refreshToken")
    }

    private func makeURL(_ path: String) -> URL {
        let parts = path.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        var url = baseURL.appendingPathComponent(String(parts[0]))
        if parts.count == 2, var components = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            components.percentEncodedQuery = String(parts[1])
            url = components.url ?? url
        }
        return url
    }
}
private struct LoginBody: Encodable { let phone: String; let password: String }
private struct RefreshBody: Encodable { let refreshToken: String }
private struct ErrorBody: Decodable { let error: String }
private struct Empty: Decodable { }
private struct AnyEncodable: Encodable { private let encodeImpl: (Encoder) throws -> Void; init(_ value: some Encodable) { encodeImpl = value.encode }; func encode(to encoder: Encoder) throws { try encodeImpl(encoder) } }
