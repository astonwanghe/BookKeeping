import Foundation

enum APIError: LocalizedError { case badURL, unauthorized, server(String), decoding
    var errorDescription: String? { switch self { case .badURL: return "服务器地址无效"; case .unauthorized: return "登录状态已失效"; case .server(let message): return message; case .decoding: return "服务器返回的数据无法读取" } }
}

actor APIClient {
    static let shared = APIClient()
    private let baseURL: URL
    private var token: String? { Keychain.value(for: "accessToken") }
    init() {
        let raw = Bundle.main.object(forInfoDictionaryKey: "APIBaseURL") as? String ?? "https://api.example.com"
        baseURL = URL(string: raw)!
    }
    func request<T: Decodable>(_ path: String, method: String = "GET", body: (any Encodable)? = nil) async throws -> T { try await perform(path, method: method, body: body, retry: true) }
    private func perform<T: Decodable>(_ path: String, method: String, body: (any Encodable)?, retry: Bool) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path)); request.httpMethod = method; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try JSONEncoder().encode(AnyEncodable(body)) }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.server("网络连接失败") }
        if http.statusCode == 401, retry { try await refresh(); return try await perform(path, method: method, body: body, retry: false) }
        if http.statusCode == 401 { throw APIError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let message = (try? JSONDecoder().decode(ErrorBody.self, from: data).error) ?? "请求失败（\(http.statusCode)）"; throw APIError.server(message)
        }
        guard !data.isEmpty else { return Empty() as! T }
        guard let value = try? JSONDecoder().decode(T.self, from: data) else { throw APIError.decoding }; return value
    }
    func login(phone: String, password: String) async throws -> LoginResponse { let result: LoginResponse = try await request("auth/login", method: "POST", body: LoginBody(phone: phone, password: password)); store(result); return result }
    func logout() async { if let refresh=Keychain.value(for:"refreshToken") { let _: Empty = try? await perform("auth/logout",method:"POST",body:RefreshBody(refreshToken:refresh),retry:false) }; Keychain.remove("accessToken");Keychain.remove("refreshToken") }
    private func refresh() async throws { guard let refresh=Keychain.value(for:"refreshToken") else { throw APIError.unauthorized }; let result: LoginResponse = try await perform("auth/refresh",method:"POST",body:RefreshBody(refreshToken:refresh),retry:false); store(result) }
    private func store(_ response: LoginResponse) { Keychain.set(response.accessToken, for:"accessToken");Keychain.set(response.refreshToken,for:"refreshToken") }
}
private struct LoginBody: Encodable { let phone: String; let password: String }
private struct RefreshBody: Encodable { let refreshToken: String }
private struct ErrorBody: Decodable { let error: String }
private struct Empty: Decodable { }
private struct AnyEncodable: Encodable { private let encodeImpl: (Encoder) throws -> Void; init(_ value: some Encodable) { encodeImpl = value.encode }; func encode(to encoder: Encoder) throws { try encodeImpl(encoder) } }
