import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    var body: some View {
        Group {
            if session.isLoggedIn { LedgerTabView() } else { LoginView() }
        }
        .tint(PixelPalette.moss)
        .background(PixelPalette.paper)
    }
}

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var phone = ""; @State private var password = ""; @State private var busy = false
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            VStack(spacing: 14) {
                PixelIconTile(icon: "wallet.pass.fill", color: PixelPalette.mint, size: 72)
                Text("像素账本")
                    .font(.system(.largeTitle, design: .rounded, weight: .black))
                Text("只记录属于你的生活")
                    .font(.system(.subheadline, design: .rounded, weight: .medium))
                    .foregroundStyle(PixelPalette.muted)
            }
            .padding(.bottom, 12)

            VStack(spacing: 12) {
                TextField("手机号", text: $phone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .pixelField()
                SecureField("密码", text: $password)
                    .textContentType(.password)
                    .pixelField()
            }
            Button("进入账本") {
                busy = true
                Task {
                    await session.login(phone: phone, password: password)
                    busy = false
                }
            }
            .buttonStyle(PixelPrimaryButtonStyle())
            .disabled(busy || phone.isEmpty || password.isEmpty)
            if let error = session.error {
                Text(error)
                    .foregroundStyle(PixelPalette.coral)
                    .font(.footnote)
            }
            Spacer()
        }
        .padding(28)
        .background(PixelPalette.paper)
    }
}
