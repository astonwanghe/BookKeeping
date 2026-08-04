import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var data: Dashboard?
    @State private var error: String?
    @State private var reloadID = UUID()
    private let month = String(Calendar.current.dateComponents([.year, .month], from: .now).year!) + "-" + String(format: "%02d", Calendar.current.component(.month, from: .now))

    var body: some View {
        NavigationStack {
            ScrollView {
                if let data {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("本月账本").font(.largeTitle.bold())
                        HStack {
                            AmountCard(title: "收入", amount: data.income, color: .green)
                            AmountCard(title: "支出", amount: data.expense, color: .red)
                        }
                        AmountCard(title: "结余", amount: data.balance, color: PixelPalette.leaf)
                        Text("支出分类").font(.title2.bold())
                        ForEach(data.expenseBreakdown) { item in
                            HStack {
                                Image(systemName: item.icon)
                                Text(item.name)
                                Spacer()
                                Text(item.amount, format: .currency(code: "CNY"))
                            }
                        }
                    }
                    .padding()
                } else if let error {
                    VStack(spacing: 16) {
                        ContentUnavailableView("概览加载失败", systemImage: "wifi.exclamationmark", description: Text(error))
                        Button("重试") { reloadID = UUID() }
                            .buttonStyle(PixelButtonStyle())
                            .padding(.horizontal)
                    }
                    .padding(.top, 80)
                } else {
                    ProgressView("加载概览…")
                        .padding(.top, 120)
                }
            }
            .background(PixelPalette.paper)
            .navigationTitle("本月账本")
            .task(id: reloadID) { await load() }
        }
    }

    private func load() async {
        error = nil
        do {
            data = try await APIClient.shared.request("api/dashboard?month=\(month)")
        } catch {
            if case APIError.unauthorized = error { session.expireSession() }
            self.error = error.localizedDescription
        }
    }
}
struct AmountCard: View { let title:String;let amount:Decimal;let color:Color; var body: some View { VStack(alignment:.leading){Text(title).font(.caption.bold());Text(amount,format:.currency(code:"CNY")).font(.title2.bold())}.frame(maxWidth:.infinity,alignment:.leading).padding().background(.white).overlay(Rectangle().stroke(color,lineWidth:3)).shadow(color:color.opacity(0.35),radius:0,x:3,y:3) } }
struct TransactionsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var rows = [LedgerTransaction](); @State private var error: String?
    @State private var isLoading = true; @State private var reloadID = UUID()
    private let month = Month.current
    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("加载流水…")
                } else if let error {
                    VStack(spacing: 16) {
                        ContentUnavailableView("流水加载失败", systemImage: "wifi.exclamationmark", description: Text(error))
                        Button("重试") { reloadID = UUID() }
                            .buttonStyle(PixelButtonStyle())
                            .padding(.horizontal)
                    }
                } else if rows.isEmpty {
                    ContentUnavailableView("暂无流水", systemImage: "list.bullet", description: Text("记下第一笔收支吧。"))
                } else {
                    List(rows) { row in
                        HStack {
                            Image(systemName: row.categoryIcon).frame(width: 28)
                            VStack(alignment: .leading) {
                                Text(row.categoryName)
                                Text(row.occurredOn).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(row.amount, format: .currency(code: "CNY"))
                                .foregroundStyle(row.type == .EXPENSE ? .red : .green)
                        }
                    }
                }
            }
            .navigationTitle("\(month) 流水")
            .task(id: reloadID) { await load() }
        }
    }

    private func load() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            rows = try await APIClient.shared.request("api/transactions?month=\(month)")
        } catch {
            if case APIError.unauthorized = error { session.expireSession() }
            self.error = error.localizedDescription
        }
    }
}

struct EntryView: View {
    @State private var type: EntryType = .EXPENSE; @State private var categories = [Category](); @State private var categoryId: Int64?; @State private var amount = ""; @State private var note = ""; @State private var saved = false; @State private var error: String?
    var filtered: [Category] { categories.filter { $0.type == type } }
    var body: some View { NavigationStack { Form { Section { Picker("类型",selection:$type) { Text("支出").tag(EntryType.EXPENSE);Text("收入").tag(EntryType.INCOME) }.pickerStyle(.segmented).onChange(of:type) { categoryId = filtered.first?.id }
                TextField("金额",text:$amount).keyboardType(.decimalPad).font(.system(size:36,weight:.bold,design:.rounded))
                Picker("分类",selection:$categoryId) { ForEach(filtered) { Text($0.name).tag(Optional($0.id)) } }
                TextField("备注（可选）",text:$note)
            } header: { Text("快速记账") }
            Section { Button("保存这一笔") { save() }.buttonStyle(PixelButtonStyle()).disabled(Decimal(string:amount)==nil || categoryId==nil) }
            if saved { Text("已保存").foregroundStyle(.green) }; if let error { Text(error).foregroundStyle(.red) }
        }.scrollContentBackground(.hidden).background(PixelPalette.paper).navigationTitle("记一笔").task { do { categories = try await APIClient.shared.request("api/categories"); categoryId = filtered.first?.id } catch { self.error=error.localizedDescription } } } }
    private func save() { guard let value=Decimal(string:amount),let categoryId else{return}; Task { do { let _: EmptyResponse = try await APIClient.shared.request("api/transactions",method:"POST",body:NewTransaction(categoryId:categoryId,amount:value,occurredOn:ISO8601DateFormatter().string(from:.now).prefix(10).description,note:note.isEmpty ? nil : note)); amount="";note="";saved=true } catch { self.error=error.localizedDescription } } }
}

struct BudgetView: View {
    @State private var amount=""; @State private var dashboard: Dashboard?; @State private var categories=[Category](); @State private var categoryId: Int64?; @State private var error: String?; private let month=Month.current
    var body: some View { NavigationStack { Form { Section("预算设置") { Picker("预算类型",selection:$categoryId) { Text("本月总预算").tag(Int64?.none); ForEach(categories.filter { $0.type == .EXPENSE }) { Text($0.name).tag(Optional($0.id)) } }.onChange(of: categoryId) { updateAmount() }; TextField("金额",text:$amount).keyboardType(.decimalPad); Button("保存预算") { save() }.disabled(Decimal(string:amount)==nil) }
        if let dashboard { Section("本月支出") { Text(dashboard.expense,format:.currency(code:"CNY")); ForEach(dashboard.expenseBreakdown) { item in HStack { Text(item.name); Spacer(); Text(item.amount,format:.currency(code:"CNY")) } } } }
        if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("\(month) 预算").task { await load() } } }
    private func load() async { do { dashboard = try await APIClient.shared.request("api/dashboard?month=\(month)"); categories = try await APIClient.shared.request("api/categories"); updateAmount() } catch { self.error=error.localizedDescription } }
    private func updateAmount() { amount = dashboard?.budgets.first(where:{$0.categoryId==categoryId})?.amount.description ?? "" }
    private func save() { guard let value=Decimal(string:amount) else{return};Task { do { let _: EmptyResponse=try await APIClient.shared.request("api/budgets/\(month)",method:"PUT",body:BudgetPayload(categoryId:categoryId,amount:value));await load() } catch { self.error=error.localizedDescription } } }
}
struct ProfileView: View { @EnvironmentObject private var session: SessionStore; var body: some View { NavigationStack { List { Section("账户") { Text(session.user?.phone ?? "已登录"); Button("退出登录",role:.destructive) { session.logout() } } }.navigationTitle("我的") } } }

private enum Month { static var current: String { let formatter=DateFormatter();formatter.dateFormat="yyyy-MM";return formatter.string(from:.now) } }
private struct NewTransaction: Encodable { let categoryId:Int64;let amount:Decimal;let occurredOn:String;let note:String? }
private struct BudgetPayload: Encodable { let categoryId:Int64?;let amount:Decimal }
private struct EmptyResponse: Decodable { }
