import SwiftUI
import UIKit

struct DialerView: View {
    @ObservedObject var model: PhoneViewModel
    let lastDialedNumber: String?
    @State private var number = ""
    private let keys = [("1", ""), ("2", "ABC"), ("3", "DEF"), ("4", "GHI"), ("5", "JKL"), ("6", "MNO"), ("7", "PQRS"), ("8", "TUV"), ("9", "WXYZ"), ("*", ""), ("0", "+"), ("#", "")]

    var body: some View {
        VStack {
            BrandMark().frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 24).padding(.top, 16)
            Spacer()
            Text(number.isEmpty ? "Digite um número" : number)
                .font(number.isEmpty ? .title3 : .system(size: 34, weight: .medium))
                .foregroundStyle(number.isEmpty ? BrandColor.muted.opacity(0.65) : BrandColor.navy)
                .lineLimit(1).minimumScaleFactor(0.65)
                .padding(.horizontal)
            Spacer().frame(height: 28)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 16), count: 3), spacing: 13) {
                ForEach(keys, id: \.0) { digit, letters in
                    Button { number.append(digit); UIImpactFeedbackGenerator(style: .light).impactOccurred() } label: {
                        VStack(spacing: 0) {
                            Text(digit).font(.system(size: 27, weight: .medium))
                            Text(letters).font(.system(size: 9, weight: .medium)).tracking(2).frame(height: 11)
                        }
                        .foregroundStyle(BrandColor.navy)
                        .frame(width: 76, height: 76)
                        .background(BrandColor.navy.opacity(0.055), in: Circle())
                    }
                }
            }
            .padding(.horizontal, 30)
            HStack(spacing: 22) {
                Color.clear.frame(width: 68, height: 68)
                Button {
                    if number.isEmpty { number = lastDialedNumber ?? "" }
                    else { model.call(number) }
                } label: {
                    Image(systemName: "phone.fill").font(.system(size: 29, weight: .semibold)).foregroundStyle(BrandColor.navy)
                        .frame(width: 72, height: 72).background(BrandColor.lime, in: Circle())
                }
                Button { if !number.isEmpty { number.removeLast() } } label: {
                    Image(systemName: "delete.left.fill").font(.title3).foregroundStyle(number.isEmpty ? .clear : BrandColor.muted)
                        .frame(width: 68, height: 68)
                }
            }
            .padding(.top, 8)
            Spacer()
        }
        .background(Color.white)
    }
}
