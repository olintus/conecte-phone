import SwiftUI

struct RootView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var model = PhoneViewModel()
    @State private var selectedTab = 0

    var body: some View {
        Group {
            if !container.signedIn {
                LoginView()
            } else if let call = model.activeCall {
                ActiveCallView(call: call, model: model)
                    .transition(.opacity)
            } else {
                TabView(selection: $selectedTab) {
                    HomeView(model: model, onOpenDialer: { selectedTab = 1 })
                        .tabItem { Label("Início", systemImage: "house.fill") }
                        .tag(0)
                    DialerView(model: model, lastDialedNumber: container.callStore.recent.first(where: { $0.direction == .outgoing })?.handle)
                        .tabItem { Label("Teclado", systemImage: "circle.grid.3x3.fill") }
                        .tag(1)
                    SettingsView(model: model)
                        .tabItem { Label("Ajustes", systemImage: "gearshape.fill") }
                        .tag(2)
                }
                .tint(BrandColor.navy)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: model.activeCall?.id)
    }
}
