import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    var body: some View { Group { if session.isLoggedIn { LedgerTabView() } else { LoginView() } }.tint(PixelPalette.leaf).background(PixelPalette.paper) }
}

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var phone = ""; @State private var password = ""; @State private var busy = false
    var body: some View { VStack(spacing: 24) {
        Spacer(); Image(systemName: "wallet.pass.fill").font(.system(size: 62, weight: .black)).foregroundStyle(PixelPalette.orange)
        Text("像素账本").font(.system(.largeTitle, design: .rounded, weight: .black)); Text("只记录属于你的生活").foregroundStyle(.secondary)
        TextField("手机号", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber).pixelField()
        SecureField("密码", text: $password).textContentType(.password).pixelField()
        Button("进入账本") { busy=true; Task { await session.login(phone: phone, password: password); busy=false } }.buttonStyle(PixelButtonStyle()).disabled(busy || phone.isEmpty || password.isEmpty)
        if let error=session.error { Text(error).foregroundStyle(.red).font(.footnote) }; Spacer()
    }.padding(28).background(PixelPalette.paper) }
}

struct LedgerTabView: View { var body: some View { TabView { DashboardView().tabItem { Label("概览", systemImage:"house.fill") }; TransactionsView().tabItem { Label("流水", systemImage:"list.bullet") }; EntryView().tabItem { Label("记一笔", systemImage:"plus.circle.fill") }; BudgetView().tabItem { Label("预算", systemImage:"target") }; ProfileView().tabItem { Label("我的", systemImage:"person.fill") } } } }

enum PixelPalette { static let paper=Color(red:0.98,green:0.94,blue:0.82); static let leaf=Color(red:0.16,green:0.35,blue:0.22); static let orange=Color(red:0.82,green:0.35,blue:0.12) }
struct PixelButtonStyle: ButtonStyle { func makeBody(configuration: Configuration) -> some View { configuration.label.fontWeight(.bold).frame(maxWidth:.infinity).padding().background(PixelPalette.orange).foregroundStyle(.white).overlay(Rectangle().stroke(PixelPalette.leaf,lineWidth:3)).shadow(color:PixelPalette.leaf,radius:0,x:4,y:4).offset(y:configuration.isPressed ? 3 : 0) } }
extension View { func pixelField() -> some View { self.padding(14).background(.white).overlay(Rectangle().stroke(PixelPalette.leaf,lineWidth:2)) } }
