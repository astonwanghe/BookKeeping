import Foundation

enum EntryType: String, Decodable { case INCOME, EXPENSE }

struct Category: Decodable, Identifiable {
    let id: Int64
    let name: String
    let type: EntryType
}

struct LedgerTransaction: Decodable, Identifiable {
    let id: Int64
    let type: EntryType
    let amount: Decimal
    let occurredOn: String
    let categoryName: String
    let categoryIcon: String
}

struct Dashboard: Decodable {
    let income: Decimal
    let expense: Decimal
    let balance: Decimal
    let expenseBreakdown: [CategoryTotal]
    let budgets: [Budget]
}

struct CategoryTotal: Decodable, Identifiable { var id: String { name }; let name: String; let icon: String; let amount: Decimal }
struct Budget: Decodable { let categoryId: Int64?; let amount: Decimal }
struct User: Decodable { let phone: String }
struct LoginResponse: Decodable { let accessToken: String; let refreshToken: String; let user: User }
