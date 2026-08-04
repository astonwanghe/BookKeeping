import SwiftUI

enum LedgerTab: Hashable {
    case dashboard, transactions, entry, statistics, profile

    var title: String {
        switch self {
        case .dashboard: return "首页"
        case .transactions: return "流水"
        case .entry: return "记一笔"
        case .statistics: return "统计"
        case .profile: return "我的"
        }
    }

    var icon: String {
        switch self {
        case .dashboard: return "house.fill"
        case .transactions: return "list.bullet"
        case .entry: return "plus.circle.fill"
        case .statistics: return "chart.line.uptrend.xyaxis"
        case .profile: return "person.fill"
        }
    }
}

final class LedgerNavigation: ObservableObject {
    @Published var selection: LedgerTab = .dashboard
}

struct LedgerTabView: View {
    @StateObject private var navigation = LedgerNavigation()

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch navigation.selection {
                case .dashboard: DashboardView()
                case .transactions: TransactionsView()
                case .entry: EntryView()
                case .statistics: StatisticsView()
                case .profile: ProfileView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .environmentObject(navigation)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            PixelTabBar(selection: $navigation.selection)
        }
        .background(PixelPalette.paper)
    }
}

private struct PixelTabBar: View {
    @Binding var selection: LedgerTab
    private let tabs: [LedgerTab] = [.dashboard, .transactions, .entry, .statistics, .profile]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(tabs, id: \.self) { tab in
                Button { selection = tab } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 16, weight: .bold))
                        Text(tab.title)
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 62)
                    .foregroundStyle(selection == tab ? PixelPalette.ink : Color.white.opacity(0.58))
                    .background(selection == tab ? PixelPalette.orange : PixelPalette.charcoal)
                }
                .buttonStyle(.plain)
            }
        }
        .background(PixelPalette.charcoal)
    }
}

struct DashboardView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var navigation: LedgerNavigation
    @State private var data: Dashboard?
    @State private var transactions = [LedgerTransaction]()
    @State private var error: String?
    @State private var reloadID = UUID()
    private let month = Month.current

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                PixelPalette.charcoal
                    .ignoresSafeArea(edges: .top)
                    .frame(height: 118)
                ScrollView {
                    VStack(spacing: 0) {
                        homeHeader
                        if let data {
                            homeContent(data)
                        } else if let error {
                            loadingError(error)
                        } else {
                            ProgressView("加载账本…")
                                .tint(PixelPalette.moss)
                                .padding(.top, 120)
                        }
                    }
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.always, axes: .vertical)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .background(PixelPalette.paper)
            .toolbar(.hidden, for: .navigationBar)
            .task(id: reloadID) { await load() }
        }
    }

    private var homeHeader: some View {
        HStack(alignment: .center) {
            Text("像素记账")
                .font(.system(.title3, design: .rounded, weight: .black))
                .foregroundStyle(PixelPalette.orange)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 18)
    }

    private func homeContent(_ data: Dashboard) -> some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                OverviewCard(data: data, month: month)
                    .padding(.horizontal, 4)
                    .padding(.top, 4)
                SectionStrip(title: "最近记录", trailing: "查看全部  >") {
                    navigation.selection = .transactions
                }
            }
            .background(PixelPalette.charcoal)
            if recentGroups.isEmpty {
                Text("还没有流水记录")
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(PixelPalette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)
                        .background(PixelPalette.paper)
            } else {
                VStack(spacing: 0) {
                    ForEach(recentGroups) { group in
                        HStack(spacing: 7) {
                            Rectangle()
                                .fill(PixelPalette.orange)
                                .frame(width: 8, height: 8)
                            Text(group.displayDate)
                                .font(.system(.caption, design: .rounded, weight: .black))
                                .foregroundStyle(PixelPalette.ink)
                            Spacer()
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 11)
                        .padding(.bottom, 7)
                        ForEach(group.rows) { row in
                            HomeTransactionRow(row: row)
                            Divider().overlay(PixelPalette.paperDeep)
                        }
                    }
                }
                .background(PixelPalette.paper)
            }
        }
        .background(PixelPalette.paper)
    }

    private var recentGroups: [RecentTransactionGroup] {
        let recentRows = Array(transactions.prefix(12))
        let grouped = Dictionary(grouping: recentRows) { String($0.occurredOn.prefix(10)) }
        return grouped.keys.sorted(by: >).compactMap { date in
            guard let rows = grouped[date], !rows.isEmpty else { return nil }
            return RecentTransactionGroup(date: date, rows: rows)
        }
    }

    private func loadingError(_ message: String) -> some View {
        VStack(spacing: 14) {
            ContentUnavailableView("账本加载失败", systemImage: "wifi.exclamationmark", description: Text(message))
            Button("再试一次") { reloadID = UUID() }
                .buttonStyle(PixelPrimaryButtonStyle(color: PixelPalette.charcoal))
                .padding(.horizontal, 24)
        }
        .padding(.top, 100)
    }

    private func load() async {
        error = nil
        do {
            data = try await APIClient.shared.request("api/dashboard?month=\(month)")
            transactions = try await APIClient.shared.request("api/transactions?month=\(month)")
        } catch {
            if case APIError.unauthorized = error { session.expireSession() }
            self.error = error.localizedDescription
        }
    }
}

private struct OverviewCard: View {
    let data: Dashboard
    let month: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("本月总览")
                    .font(.system(.subheadline, design: .rounded, weight: .black))
                Spacer()
                MonthBadge(month: month, numberColor: PixelPalette.ink, unitColor: PixelPalette.ink)
            }
            HStack(alignment: .top, spacing: 0) {
                OverviewAmount(title: "收入", amount: data.income, color: PixelPalette.moss)
                OverviewAmount(title: "支出", amount: data.expense, color: PixelPalette.coral)
                OverviewAmount(title: "结余", amount: data.balance, color: PixelPalette.ink)
            }
            HStack(spacing: 0) {
                Rectangle().fill(PixelPalette.moss).frame(maxWidth: .infinity)
                Rectangle().fill(PixelPalette.coral).frame(maxWidth: .infinity)
                Rectangle().fill(PixelPalette.ink).frame(maxWidth: .infinity)
            }
            .frame(height: 10)
        }
        .pixelCard(padding: 16, fill: PixelPalette.orange, stroke: PixelPalette.charcoal, shadow: PixelPalette.charcoal)
    }
}

private struct OverviewAmount: View {
    let title: String
    let amount: Decimal
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.system(size: 12, weight: .bold, design: .rounded))
            PixelAmountText(amount: amount, size: 23, color: color, prefix: title == "收入" ? "+" : title == "支出" ? "-" : "")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct MonthBadge: View {
    let month: String
    var numberColor: Color = PixelPalette.orange
    var unitColor: Color = Color.white.opacity(0.68)

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 2) {
            Text(monthNumber)
                .font(.system(size: 20, weight: .black, design: .rounded))
                .foregroundStyle(numberColor)
            Text("月")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(unitColor)
        }
        .accessibilityLabel("\(monthNumber)月")
    }

    private var monthNumber: String {
        guard let value = Int(month.suffix(2)) else { return month }
        return String(value)
    }
}

private struct SectionStrip: View {
    let title: String
    let trailing: String
    let action: () -> Void

    var body: some View {
        HStack {
            HStack(spacing: 7) {
                Rectangle()
                    .fill(PixelPalette.orange)
                    .frame(width: 10, height: 10)
                Text(title)
                    .font(.system(.subheadline, design: .rounded, weight: .black))
                    .foregroundStyle(PixelPalette.orange)
            }
            Spacer()
            Button(action: action) {
                Text(trailing)
                    .font(.system(size: 11, weight: .medium, design: .rounded))
                    .foregroundStyle(PixelPalette.orange)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 11)
        .background(PixelPalette.charcoal)
    }
}

private struct HomeTransactionRow: View {
    let row: LedgerTransaction

    var body: some View {
        HStack(spacing: 12) {
            PixelIconTile(icon: PixelCategoryStyle.icon(for: row.categoryName, fallback: row.categoryIcon), color: categoryColor, size: 36)
            VStack(alignment: .leading, spacing: 3) {
                Text(row.categoryName)
                    .font(.system(.subheadline, design: .rounded, weight: .bold))
                Text(row.type == .EXPENSE ? "支出" : "收入")
                    .font(.system(size: 11, design: .rounded))
                    .foregroundStyle(PixelPalette.muted)
            }
            Spacer()
            PixelAmountText(
                amount: row.amount,
                size: 17,
                color: row.type == .EXPENSE ? PixelPalette.coral : PixelPalette.moss,
                prefix: row.type == .EXPENSE ? "-" : "+"
            )
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }

    private var categoryColor: Color {
        PixelCategoryStyle.color(for: row.categoryName, type: row.type)
    }
}

private struct RecentTransactionGroup: Identifiable {
    let date: String
    let rows: [LedgerTransaction]
    var id: String { date }

    var displayDate: String {
        guard let date = DateFormatter.pixelDate.date(from: date) else { return date }
        return DateFormatter.pixelLongDate.string(from: date)
    }
}

struct TransactionsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var rows = [LedgerTransaction]()
    @State private var error: String?
    @State private var isLoading = true
    @State private var reloadID = UUID()
    @State private var period: LedgerPeriod = .day
    @State private var selectedDate = Date.now
    private let calendar = Calendar.current

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                PixelPalette.charcoal
                    .ignoresSafeArea(edges: .top)
                    .frame(height: 152)
                ScrollView {
                    VStack(spacing: 0) {
                        transactionHeader
                        periodPicker
                        dateNavigator
                        if isLoading {
                            ProgressView("加载流水…")
                                .padding(.top, 100)
                        } else if let error {
                            ContentUnavailableView("流水加载失败", systemImage: "wifi.exclamationmark", description: Text(error))
                                .padding(.top, 80)
                        } else {
                            transactionSummary
                            transactionList
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }
            .background(PixelPalette.paper)
            .toolbar(.hidden, for: .navigationBar)
            .task(id: reloadID) { await load() }
        }
    }

    private var transactionHeader: some View {
        HStack {
            Text("流水账单")
                .font(.system(.title3, design: .rounded, weight: .black))
                .foregroundStyle(PixelPalette.orange)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 14)
    }

    private var periodPicker: some View {
        HStack(spacing: 0) {
            ForEach(LedgerPeriod.allCases, id: \.self) { item in
                Button {
                    period = item
                    reloadID = UUID()
                } label: {
                    Text(item.title)
                        .font(.system(.subheadline, design: .rounded, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .foregroundStyle(period == item ? PixelPalette.ink : Color.white.opacity(0.55))
                        .background(period == item ? PixelPalette.orange : PixelPalette.charcoal)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var dateNavigator: some View {
        HStack {
            Button { moveDate(by: -1) } label: {
                Image(systemName: "chevron.left")
                    .frame(width: 42, height: 36)
            }
            .buttonStyle(.plain)
            Spacer()
            Text(dateTitle)
                .font(.system(.subheadline, design: .rounded, weight: .bold))
            Spacer()
            Button { moveDate(by: 1) } label: {
                Image(systemName: "chevron.right")
                    .frame(width: 42, height: 36)
            }
            .buttonStyle(.plain)
        }
        .foregroundStyle(PixelPalette.ink)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.white)
    }

    private var transactionSummary: some View {
        HStack {
            SummaryMetric(title: "收入", amount: periodIncome, color: PixelPalette.moss)
            SummaryMetric(title: "支出", amount: periodExpense, color: PixelPalette.coral)
            SummaryMetric(title: "净额", amount: periodBalance, color: PixelPalette.ink)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(PixelPalette.orange)
    }

    private var transactionList: some View {
        LazyVStack(spacing: 0) {
            if rows.isEmpty {
                Text("暂无流水")
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(PixelPalette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 50)
                    .background(PixelPalette.paper)
            } else {
                ForEach(rows) { row in
                    DetailedTransactionRow(row: row)
                    Divider().overlay(PixelPalette.paperDeep)
                }
            }
        }
        .background(PixelPalette.paper)
    }

    private var dateTitle: String {
        switch period {
        case .day:
            return DateFormatter.pixelLongDate.string(from: selectedDate)
        case .week:
            guard let interval = calendar.dateInterval(of: .weekOfYear, for: selectedDate) else { return "本周" }
            let end = calendar.date(byAdding: .day, value: -1, to: interval.end) ?? interval.end
            return "\(DateFormatter.pixelMonthDay.string(from: interval.start)) - \(DateFormatter.pixelMonthDay.string(from: end))"
        case .month:
            return DateFormatter.pixelMonthTitle.string(from: selectedDate)
        case .year:
            return "\(calendar.component(.year, from: selectedDate))年"
        }
    }

    private var periodIncome: Decimal {
        rows.filter { $0.type == .INCOME }.reduce(0) { $0 + $1.amount }
    }

    private var periodExpense: Decimal {
        rows.filter { $0.type == .EXPENSE }.reduce(0) { $0 + $1.amount }
    }

    private var periodBalance: Decimal { periodIncome - periodExpense }

    private func moveDate(by value: Int) {
        let component: Calendar.Component
        switch period {
        case .day: component = .day
        case .week: component = .weekOfYear
        case .month: component = .month
        case .year: component = .year
        }
        selectedDate = calendar.date(byAdding: component, value: value, to: selectedDate) ?? selectedDate
        reloadID = UUID()
    }

    private func load() async {
        isLoading = true
        error = nil
        defer { isLoading = false }
        do {
            let sourceRows = try await loadSourceRows()
            rows = filterRows(sourceRows)
        } catch {
            if case APIError.unauthorized = error { session.expireSession() }
            self.error = error.localizedDescription
        }
    }

    private func loadSourceRows() async throws -> [LedgerTransaction] {
        if period != .year {
            let month = DateFormatter.pixelMonth.string(from: selectedDate)
            return try await APIClient.shared.request("api/transactions?month=\(month)")
        }

        let year = calendar.component(.year, from: selectedDate)
        var result = [LedgerTransaction]()
        for month in 1...12 {
            guard let date = calendar.date(from: DateComponents(year: year, month: month, day: 1)) else { continue }
            let monthValue = DateFormatter.pixelMonth.string(from: date)
            let monthRows: [LedgerTransaction] = try await APIClient.shared.request("api/transactions?month=\(monthValue)")
            result.append(contentsOf: monthRows)
        }
        return result
    }

    private func filterRows(_ sourceRows: [LedgerTransaction]) -> [LedgerTransaction] {
        switch period {
        case .day:
            let date = DateFormatter.pixelDate.string(from: selectedDate)
            return sourceRows.filter { $0.occurredOn.hasPrefix(date) }
        case .week:
            guard let interval = calendar.dateInterval(of: .weekOfYear, for: selectedDate) else { return sourceRows }
            return sourceRows.filter { row in
                guard let date = DateFormatter.pixelDate.date(from: String(row.occurredOn.prefix(10))) else { return false }
                return date >= interval.start && date < interval.end
            }
        case .month:
            let month = DateFormatter.pixelMonth.string(from: selectedDate)
            return sourceRows.filter { $0.occurredOn.hasPrefix(month) }
        case .year:
            let year = String(calendar.component(.year, from: selectedDate))
            return sourceRows.filter { $0.occurredOn.hasPrefix(year) }
        }
    }
}

private enum LedgerPeriod: CaseIterable {
    case day, week, month, year
    var title: String {
        switch self { case .day: return "日"; case .week: return "周"; case .month: return "月"; case .year: return "年" }
    }
}

private struct SummaryMetric: View {
    let title: String
    let amount: Decimal
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(title).font(.system(size: 11, weight: .bold, design: .rounded))
            PixelAmountText(
                amount: amount,
                size: 17,
                color: color,
                prefix: title == "收入" ? "+" : title == "支出" ? "-" : ""
            )
        }
        .frame(maxWidth: .infinity)
    }
}

private struct DetailedTransactionRow: View {
    let row: LedgerTransaction

    var body: some View {
        HStack(spacing: 12) {
            PixelIconTile(icon: PixelCategoryStyle.icon(for: row.categoryName, fallback: row.categoryIcon), color: categoryColor, size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(row.categoryName)
                    .font(.system(.subheadline, design: .rounded, weight: .bold))
                Text("\(String(row.occurredOn.suffix(5)))  ·  \(row.categoryName)")
                    .font(.system(size: 11, design: .rounded))
                    .foregroundStyle(PixelPalette.muted)
            }
            Spacer()
            PixelAmountText(
                amount: row.amount,
                size: 17,
                color: row.type == .EXPENSE ? PixelPalette.coral : PixelPalette.moss,
                prefix: row.type == .EXPENSE ? "-" : "+"
            )
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }

    private var categoryColor: Color {
        PixelCategoryStyle.color(for: row.categoryName, type: row.type)
    }
}

struct StatisticsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var dashboard: Dashboard?
    @State private var monthlySummaries = [MonthlySummary]()
    @State private var incomeBreakdown = [CategoryTotal]()
    @State private var error: String?
    @State private var reloadID = UUID()
    @State private var selectedMonth = Month.current
    @State private var rankingType: EntryType = .EXPENSE

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                PixelPalette.charcoal
                    .ignoresSafeArea(edges: .top)
                    .frame(height: 104)
                ScrollView {
                    VStack(spacing: 0) {
                        statisticsHeader
                        if let dashboard {
                            statisticsContent(dashboard)
                        } else if let error {
                            ContentUnavailableView("统计加载失败", systemImage: "wifi.exclamationmark", description: Text(error))
                                .padding(.top, 100)
                        } else {
                            ProgressView("整理统计数据…")
                                .padding(.top, 120)
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }
            .background(PixelPalette.paper)
            .toolbar(.hidden, for: .navigationBar)
            .task(id: reloadID) { await load() }
        }
    }

    private var statisticsHeader: some View {
        HStack {
            Text("数据统计")
                .font(.system(.title3, design: .rounded, weight: .black))
                .foregroundStyle(PixelPalette.orange)
            Spacer()
            Menu {
                ForEach(monthOptions, id: \.self) { month in
                    Button(monthDisplay(month)) {
                        selectedMonth = month
                        reloadID = UUID()
                    }
                }
            } label: {
                Text(monthDisplay(selectedMonth) + "⌄")
                    .font(.system(size: 12, weight: .black, design: .rounded))
                    .foregroundStyle(PixelPalette.orange)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 14)
    }

    private func statisticsContent(_ data: Dashboard) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                SummaryTile(title: "总收入", amount: data.income, color: PixelPalette.moss)
                SummaryTile(title: "总支出", amount: data.expense, color: PixelPalette.coral)
                SummaryTile(title: "结余", amount: data.balance, color: PixelPalette.orange)
            }
            .foregroundStyle(.white)
            categorySection
            trendSection
        }
    }

    private var categorySection: some View {
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: 7) {
                    Rectangle()
                        .fill(PixelPalette.orange)
                        .frame(width: 10, height: 10)
                    Text("分类排行")
                        .font(.system(.subheadline, design: .rounded, weight: .black))
                        .foregroundStyle(PixelPalette.orange)
                }
                Spacer()
                HStack(spacing: 3) {
                    rankingTab(.EXPENSE, title: "支出")
                    rankingTab(.INCOME, title: "收入")
                }
                .padding(3)
                .background(PixelPalette.ink.opacity(0.65))
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(PixelPalette.orange, lineWidth: 1.5)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(PixelPalette.charcoal)
            VStack(alignment: .leading, spacing: 8) {
                if rankingItems.isEmpty {
                    Text(rankingType == .EXPENSE ? "暂无支出分类数据" : "暂无收入分类数据")
                        .foregroundStyle(PixelPalette.muted)
                } else {
                    ForEach(rankingItems) { item in
                        CategoryRankRow(item: item, total: rankingTotal)
                    }
                }
            }
            .padding(20)
            .background(PixelPalette.paper)
        }
        .background(PixelPalette.paper)
    }

    private func rankingTab(_ type: EntryType, title: String) -> some View {
        let color = type == .EXPENSE ? PixelPalette.coral : PixelPalette.moss
        return Button {
            rankingType = type
        } label: {
            Text(title)
                .font(.system(size: 11, weight: .black, design: .rounded))
                .foregroundStyle(rankingType == type ? .white : color)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(rankingType == type ? color : .clear)
                .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var rankingItems: [CategoryTotal] {
        rankingType == .EXPENSE ? dashboard?.expenseBreakdown ?? [] : incomeBreakdown
    }

    private var rankingTotal: Decimal {
        rankingItems.reduce(0) { $0 + $1.amount }
    }

    private var trendSection: some View {
        VStack(spacing: 0) {
            StatisticsSectionHeader(title: "近6月趋势")
            MonthlyTrendChart(values: monthlySummaries)
                .padding(20)
                .background(PixelPalette.paper)
        }
        .background(PixelPalette.paper)
    }

    private var monthOptions: [String] {
        let calendar = Calendar.current
        return (0..<6).compactMap { offset in
            guard let date = calendar.date(byAdding: .month, value: -offset, to: .now) else { return nil }
            return DateFormatter.pixelMonth.string(from: date)
        }
    }

    private func monthDisplay(_ month: String) -> String {
        guard let year = Int(month.prefix(4)), let number = Int(month.suffix(2)) else { return month }
        return "\(year)年\(number)月"
    }

    private func load() async {
        error = nil
        do {
            dashboard = try await APIClient.shared.request("api/dashboard?month=\(selectedMonth)")
            incomeBreakdown = try await loadIncomeBreakdown()
            monthlySummaries = await loadMonthlySummaries()
        } catch {
            if case APIError.unauthorized = error { session.expireSession() }
            self.error = error.localizedDescription
        }
    }

    private func loadIncomeBreakdown() async throws -> [CategoryTotal] {
        let rows: [LedgerTransaction] = try await APIClient.shared.request("api/transactions?month=\(selectedMonth)")
        var amounts = [String: Decimal]()
        var icons = [String: String]()
        for row in rows where row.type == .INCOME {
            amounts[row.categoryName, default: 0] += row.amount
            icons[row.categoryName] = row.categoryIcon
        }
        return amounts.map { name, amount in
            CategoryTotal(
                name: name,
                icon: PixelCategoryStyle.icon(for: name, fallback: icons[name]),
                amount: amount
            )
        }
        .sorted { $0.amount > $1.amount }
    }

    private func loadMonthlySummaries() async -> [MonthlySummary] {
        var result = [MonthlySummary]()
        guard let baseDate = monthDate(from: selectedMonth) else { return result }
        for offset in stride(from: 0, through: 5, by: 1) {
            guard let date = Calendar.current.date(byAdding: .month, value: -offset, to: baseDate) else { continue }
            let monthValue = DateFormatter.pixelMonth.string(from: date)
            if let historical: Dashboard = try? await APIClient.shared.request("api/dashboard?month=\(monthValue)") {
                result.append(MonthlySummary(month: monthValue, income: historical.income, expense: historical.expense))
            }
        }
        return result
    }

    private func monthDate(from month: String) -> Date? {
        guard let year = Int(month.prefix(4)), let number = Int(month.suffix(2)) else { return nil }
        return Calendar.current.date(from: DateComponents(year: year, month: number, day: 1))
    }
}

private struct SummaryTile: View {
    let title: String
    let amount: Decimal
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(title).font(.system(size: 11, weight: .bold, design: .rounded))
            PixelAmountText(amount: amount, size: 20, color: color == PixelPalette.orange ? PixelPalette.ink : .white)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 76)
        .background(color)
    }
}

private struct StatisticsSectionHeader: View {
    let title: String

    var body: some View {
        HStack(spacing: 7) {
            Rectangle()
                .fill(PixelPalette.orange)
                .frame(width: 10, height: 10)
            Text(title)
                .font(.system(.subheadline, design: .rounded, weight: .black))
                .foregroundStyle(PixelPalette.orange)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 11)
        .background(PixelPalette.charcoal)
    }
}

private struct CategoryRankRow: View {
    let item: CategoryTotal
    let total: Decimal
    private var color: Color { PixelCategoryStyle.color(for: item.name) }

    var body: some View {
        HStack(spacing: 10) {
            PixelIconTile(icon: PixelCategoryStyle.icon(for: item.name, fallback: item.icon), color: color, size: 34)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(item.name)
                        .font(.system(.subheadline, design: .rounded, weight: .black))
                    Spacer()
                    PixelAmountText(amount: item.amount, size: 15, color: PixelPalette.coral)
                }
                HStack(spacing: 8) {
                    PixelProgressBar(progress: progress, color: color)
                        .frame(maxWidth: .infinity)
                    Text(percentageText)
                        .font(.system(size: 11, weight: .black, design: .rounded))
                        .foregroundStyle(PixelPalette.muted)
                        .frame(width: 34, alignment: .trailing)
                }
            }
        }
        .padding(.vertical, 6)
    }

    private var progress: CGFloat {
        let totalValue = NSDecimalNumber(decimal: total).doubleValue
        guard totalValue > 0 else { return 0 }
        return CGFloat(NSDecimalNumber(decimal: item.amount).doubleValue / totalValue)
    }

    private var percentageText: String { "\(Int(progress * 100))%" }
}

private struct MonthlySummary: Identifiable {
    let month: String
    let income: Decimal
    let expense: Decimal
    var id: String { month }
}

private struct MonthlyTrendChart: View {
    let values: [MonthlySummary]

    var body: some View {
        Group {
            if values.isEmpty {
                Text("暂无近 6 月数据")
                    .font(.system(.subheadline, design: .rounded))
                    .foregroundStyle(PixelPalette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
            } else {
                VStack(spacing: 8) {
                    HStack(spacing: 12) {
                        Label("收入", systemImage: "circle.fill")
                            .foregroundStyle(PixelPalette.moss)
                        Label("支出", systemImage: "circle.fill")
                            .foregroundStyle(PixelPalette.coral)
                        Spacer()
                    }
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    VStack(spacing: 8) {
                        ForEach(values) { item in
                            MonthlyHorizontalRow(item: item, maxValue: maxValue)
                        }
                    }
                }
                .padding(12)
                .background(PixelPalette.paper)
            }
        }
    }

    private var maxValue: Double {
        values.flatMap {
            [NSDecimalNumber(decimal: $0.income).doubleValue, NSDecimalNumber(decimal: $0.expense).doubleValue]
        }.max() ?? 1
    }

}

private struct MonthlyHorizontalRow: View {
    let item: MonthlySummary
    let maxValue: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(monthTitle)
                .font(.system(size: 10, weight: .black, design: .rounded))
                .foregroundStyle(PixelPalette.ink)
            comparisonLine(title: "收入", amount: item.income, color: PixelPalette.moss)
            comparisonLine(title: "支出", amount: item.expense, color: PixelPalette.coral)
        }
        .padding(.vertical, 3)
    }

    private func comparisonLine(title: String, amount: Decimal, color: Color) -> some View {
        HStack(spacing: 6) {
            Text(title)
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundStyle(color)
                .frame(width: 25, alignment: .leading)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(PixelPalette.paperDeep)
                    Capsule()
                        .fill(color)
                        .frame(width: proxy.size.width * ratio(for: amount))
                }
            }
            .frame(height: 9)
            PixelAmountText(amount: amount, size: 11, color: color)
                .frame(width: 76, alignment: .trailing)
        }
    }

    private var monthTitle: String {
        guard let year = Int(item.month.prefix(4)), let number = Int(item.month.suffix(2)) else { return item.month }
        return "\(year)年\(number)月"
    }

    private func ratio(for amount: Decimal) -> CGFloat {
        guard maxValue > 0 else { return 0 }
        return CGFloat(NSDecimalNumber(decimal: amount).doubleValue / maxValue)
    }
}

struct EntryView: View {
    @State private var type: EntryType = .EXPENSE
    @State private var categories = [Category]()
    @State private var categoryId: Int64?
    @State private var amount = ""
    @State private var note = ""
    @State private var occurredOn = Date.now
    @State private var isDatePickerPresented = false
    @State private var saved = false
    @State private var error: String?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 4)
    private var filtered: [Category] { categories.filter { $0.type == type } }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                PixelPalette.charcoal
                    .ignoresSafeArea(edges: .top)
                    .frame(height: 106)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("记一笔")
                                .font(.system(.title3, design: .rounded, weight: .black))
                                .foregroundStyle(PixelPalette.orange)
                            Spacer()
                            Button {
                                isDatePickerPresented = true
                            } label: {
                                Label(dateLabel, systemImage: "calendar")
                                    .font(.system(size: 12, weight: .bold, design: .rounded))
                                    .foregroundStyle(PixelPalette.orange)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                        .padding(.bottom, 10)
                        VStack(alignment: .leading, spacing: 18) {
                            typePicker
                            amountInput
                            categoryPicker
                            noteInput
                            if let error {
                                Text(error)
                                    .font(.footnote)
                                    .foregroundStyle(PixelPalette.coral)
                                    .padding(.horizontal, 20)
                            }
                            HStack {
                                Button("保存这一笔") { save() }
                                    .buttonStyle(PixelPrimaryButtonStyle(color: PixelPalette.orange, foreground: PixelPalette.ink))
                                    .disabled(parsedAmount == nil || categoryId == nil)
                            }
                            .padding(.horizontal, 20)
                            if saved {
                                Label("已经记下啦", systemImage: "checkmark.seal.fill")
                                    .font(.system(.subheadline, design: .rounded, weight: .bold))
                                    .foregroundStyle(PixelPalette.moss)
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.vertical, 20)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(PixelPalette.paper)
                        .clipShape(
                            UnevenRoundedRectangle(
                                cornerRadii: .init(topLeading: 20, bottomLeading: 0, bottomTrailing: 0, topTrailing: 20),
                                style: .continuous
                            )
                        )
                    }
                    .padding(.bottom, 20)
                }
                .scrollIndicators(.hidden)
            }
            .background(PixelPalette.paper)
            .toolbar(.hidden, for: .navigationBar)
            .task { await loadCategories() }
            .popover(isPresented: $isDatePickerPresented, attachmentAnchor: .rect(.bounds), arrowEdge: .top) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("选择日期")
                            .font(.system(.headline, design: .rounded, weight: .black))
                        Spacer()
                        Button("完成") { isDatePickerPresented = false }
                            .font(.system(.subheadline, design: .rounded, weight: .bold))
                            .buttonStyle(.plain)
                            .foregroundStyle(PixelPalette.moss)
                    }
                    DatePicker("", selection: $occurredOn, displayedComponents: .date)
                        .labelsHidden()
                        .datePickerStyle(.graphical)
                        .tint(PixelPalette.moss)
                }
                .padding(16)
                .frame(width: 330)
                .presentationCompactAdaptation(.popover)
            }
        }
    }

    private var typePicker: some View {
        HStack(spacing: 0) {
            entryTypeButton(.EXPENSE, title: "支出", icon: "arrow.up.right")
            entryTypeButton(.INCOME, title: "收入", icon: "arrow.down.left")
        }
        .padding(4)
        .padding(.horizontal, 20)
    }

    private var amountInput: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("¥")
                .font(.system(size: 24, weight: .black, design: .rounded))
                .foregroundStyle(amountColor)
            TextField("0.00", text: $amount)
                .keyboardType(.decimalPad)
                .font(.system(size: 36, weight: .black, design: .rounded))
                .foregroundStyle(amountColor)
                .multilineTextAlignment(.leading)
                .monospacedDigit()
                .onChange(of: amount) { _, newValue in
                    let formatted = formatAmountInput(newValue)
                    if formatted != newValue { amount = formatted }
                }
            Spacer()
        }
        .pixelCard(padding: 14, fill: .white, stroke: amountColor, shadow: amountColor)
        .padding(.horizontal, 20)
    }

    private var categoryPicker: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("分类")
                .font(.system(.subheadline, design: .rounded, weight: .black))
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(filtered) { category in
                    PixelChoice(title: category.name, icon: categoryIcon(for: category), isSelected: category.id == categoryId) {
                        categoryId = category.id
                    }
                }
            }
        }
        .padding(.horizontal, 20)
    }

    private var noteInput: some View {
        HStack(spacing: 10) {
            Image(systemName: "pencil.line").foregroundStyle(PixelPalette.muted)
            TextField("备注（可选）", text: $note)
                .font(.system(.body, design: .rounded))
        }
        .pixelCard(padding: 12, fill: .white, stroke: PixelPalette.paperDeep, shadow: PixelPalette.paperDeep)
        .padding(.horizontal, 20)
    }

    private func entryTypeButton(_ entryType: EntryType, title: String, icon: String) -> some View {
        let color = entryType == .EXPENSE ? PixelPalette.coral : PixelPalette.moss
        return Button {
            type = entryType
            categoryId = categories.first(where: { $0.type == entryType })?.id
        } label: {
            Label(title, systemImage: icon)
                .font(.system(.subheadline, design: .rounded, weight: .black))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .foregroundStyle(type == entryType ? .white : color)
                .background(type == entryType ? color : .clear)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func categoryIcon(for category: Category) -> String {
        PixelCategoryStyle.icon(for: category.name)
    }

    private func loadCategories() async {
        do {
            categories = try await APIClient.shared.request("api/categories")
            categoryId = filtered.first?.id
        } catch { self.error = error.localizedDescription }
    }

    private func save() {
        guard let value = parsedAmount, let categoryId else { return }
        Task {
            do {
                let _: EmptyResponse = try await APIClient.shared.request(
                    "api/transactions",
                    method: "POST",
                    body: NewTransaction(categoryId: categoryId, amount: value, occurredOn: DateFormatter.pixelDate.string(from: occurredOn), note: note.isEmpty ? nil : note)
                )
                amount = ""
                note = ""
                withAnimation { saved = true }
            } catch { self.error = error.localizedDescription }
        }
    }

    private var amountColor: Color {
        type == .EXPENSE ? PixelPalette.coral : PixelPalette.moss
    }

    private var dateLabel: String {
        Calendar.current.isDateInToday(occurredOn)
            ? "今天"
            : DateFormatter.pixelMonthDay.string(from: occurredOn)
    }

    private var parsedAmount: Decimal? {
        Decimal(string: amount.replacingOccurrences(of: ",", with: ""))
    }

    private func formatAmountInput(_ input: String) -> String {
        var clean = ""
        var hasDecimalSeparator = false
        for character in input {
            if character.isNumber {
                clean.append(character)
            } else if character == "." && !hasDecimalSeparator {
                clean.append(character)
                hasDecimalSeparator = true
            }
        }
        guard !clean.isEmpty else { return "" }

        let parts = clean.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
        let integerPart = String(parts[0])
        let digits = Array(integerPart)
        var grouped = ""
        for (index, digit) in digits.enumerated() {
            if index > 0 && (digits.count - index) % 3 == 0 { grouped.append(",") }
            grouped.append(digit)
        }
        if parts.count == 2 {
            return "\(grouped).\(parts[1].prefix(2))"
        }
        return grouped
    }
}

struct ProfileView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                PixelPalette.charcoal
                    .ignoresSafeArea(edges: .top)
                    .frame(height: 88)
                VStack(spacing: 0) {
                    HStack {
                        Text("我的")
                            .font(.system(.title3, design: .rounded, weight: .black))
                            .foregroundStyle(PixelPalette.orange)
                        Spacer()
                        HStack(spacing: 8) {
                            Text(profileInitial)
                                .font(.system(.headline, design: .rounded, weight: .black))
                                .foregroundStyle(PixelPalette.ink)
                                .frame(width: 36, height: 36)
                                .background(.white)
                                .clipShape(Circle())
                            Text(profileName)
                                .font(.system(.subheadline, design: .rounded, weight: .bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
                    .padding(.bottom, 10)
                    Spacer()
                    VStack(spacing: 24) {
                        Text("其他功能敬请期待~")
                            .font(.system(.subheadline, design: .rounded, weight: .bold))
                            .foregroundStyle(PixelPalette.muted)
                        Button("退出登录", role: .destructive) { session.logout() }
                            .buttonStyle(PixelPrimaryButtonStyle(color: PixelPalette.charcoal))
                            .padding(.horizontal, 20)
                    }
                    Spacer()
                }
            }
            .background(PixelPalette.paper)
            .toolbar(.hidden, for: .navigationBar)
            .task { await session.restoreUserIfNeeded() }
        }
    }

    private var profileName: String {
        let nickname = session.user?.nickname?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !nickname.isEmpty { return nickname }
        return session.user?.phone ?? "用户"
    }

    private var profileInitial: String {
        String(profileName.prefix(1))
    }
}

private struct NewTransaction: Encodable {
    let categoryId: Int64
    let amount: Decimal
    let occurredOn: String
    let note: String?
}

private struct EmptyResponse: Decodable {}

private enum Month {
    static var current: String {
        DateFormatter.pixelMonth.string(from: .now)
    }
}

private extension DateFormatter {
    static let pixelMonth: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        return formatter
    }()

    static let pixelDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    static let pixelLongDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy年M月d日 EEEE"
        return formatter
    }()

    static let pixelMonthDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日"
        return formatter
    }()

    static let pixelMonthTitle: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy年M月"
        return formatter
    }()
}
