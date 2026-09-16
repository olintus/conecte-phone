import SwiftUI

enum BrandColor {
    static let navy = Color(red: 7/255, green: 27/255, blue: 54/255)
    static let navySoft = Color(red: 16/255, green: 41/255, blue: 69/255)
    static let lime = Color(red: 183/255, green: 242/255, blue: 58/255)
    static let ice = Color(red: 244/255, green: 247/255, blue: 249/255)
    static let muted = Color(red: 113/255, green: 128/255, blue: 150/255)
    static let danger = Color(red: 232/255, green: 75/255, blue: 85/255)
}

struct BrandMark: View {
    var light = false
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "phone.fill")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(BrandColor.navy)
                .frame(width: 38, height: 38)
                .background(BrandColor.lime, in: Circle())
            VStack(alignment: .leading, spacing: -1) {
                Text("CONECTE").font(.system(size: 17, weight: .black)).tracking(1.5)
                Text("PHONE").font(.system(size: 8, weight: .bold)).tracking(3).foregroundStyle(BrandColor.lime)
            }
            .foregroundStyle(light ? .white : BrandColor.navy)
        }
    }
}

