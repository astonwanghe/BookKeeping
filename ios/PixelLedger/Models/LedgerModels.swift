import Foundation

enum EntryType: String, Codable, CaseIterable { case INCOME, EXPENSE }

struct Category: Codable, Identifiable, Hashable {
    let id: Int64
    var name: String
    let type: EntryType
    var icon: String
    var sortOrder: Int
    var active: Bool
}

struct LedgerTransaction: Codable, Identifiable {
    let id: Int64
    let type: EntryType
    let amount: Decimal
    let occurredOn: String
    let note: String?
    let categoryId: Int64
    let categoryName: String
    let categoryIcon: String
}

struct Dashboard: Codable {
    let month: String
    let income: Decimal
    let expense: Decimal
    let balance: Decimal
    let expenseBreakdown: [CategoryTotal]
    let budgets: [Budget]
}

struct CategoryTotal: Codable, Identifiable { var id: String { name }; let name: String; let icon: String; let amount: Decimal }
struct Budget: Codable, Identifiable { let id: Int64; let categoryId: Int64?; let amount: Decimal }
struct User: Codable { let id: Int64; let phone: String; let email: String; let emailVerified: Bool }
struct LoginResponse: Codable { let accessToken: String; let refreshToken: String; let user: User }
