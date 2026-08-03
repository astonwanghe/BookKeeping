import SwiftUI

@main
struct PixelLedger: App {
    @StateObject private var session = SessionStore()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(session) }
    }
}
