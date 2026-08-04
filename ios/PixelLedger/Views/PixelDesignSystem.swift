import SwiftUI

enum PixelPalette {
    static let paper = Color(red: 0.96, green: 0.94, blue: 0.89)
    static let paperDeep = Color(red: 0.90, green: 0.88, blue: 0.82)
    static let charcoal = Color(red: 0.17, green: 0.17, blue: 0.17)
    static let ink = Color(red: 0.15, green: 0.15, blue: 0.15)
    static let moss = Color(red: 0.08, green: 0.43, blue: 0.23)
    static let sage = Color(red: 0.30, green: 0.64, blue: 0.32)
    static let orange = Color(red: 1.00, green: 0.86, blue: 0.31)
    static let coral = Color(red: 0.79, green: 0.08, blue: 0.22)
    static let mint = Color(red: 0.70, green: 0.84, blue: 0.96)
    static let purple = Color(red: 0.65, green: 0.52, blue: 0.91)
    static let blue = Color(red: 0.38, green: 0.62, blue: 0.93)
    static let line = Color(red: 0.16, green: 0.16, blue: 0.16)
    static let muted = Color(red: 0.62, green: 0.62, blue: 0.62)
    static let income = moss
    static let expense = coral
}

enum PixelCategoryStyle {
    static func color(for name: String, type: EntryType? = nil) -> Color {
        switch name {
        case "餐饮": return PixelPalette.coral
        case "交通": return PixelPalette.blue
        case "购物": return PixelPalette.purple
        case "居住": return PixelPalette.sage
        case "娱乐": return PixelPalette.orange
        case "医疗": return PixelPalette.coral
        case "工资": return PixelPalette.moss
        case "奖金": return PixelPalette.orange
        case "其他收入": return PixelPalette.mint
        default: return type == .INCOME ? PixelPalette.mint : PixelPalette.paperDeep
        }
    }

    static func icon(for name: String, fallback: String? = nil) -> String {
        switch name {
        case "餐饮": return "fork.knife"
        case "交通": return "car.fill"
        case "购物": return "bag.fill"
        case "居住": return "house.fill"
        case "娱乐": return "gamecontroller.fill"
        case "医疗": return "cross.case.fill"
        case "工资": return "banknote.fill"
        case "奖金": return "star.fill"
        case "其他收入": return "plus.circle.fill"
        default: return fallback ?? "square.grid.2x2.fill"
        }
    }
}

struct PixelCardModifier: ViewModifier {
    var padding: CGFloat = 16
    var fill: Color = .white
    var stroke: Color = PixelPalette.line
    var shadow: Color = PixelPalette.moss

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(fill)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(stroke, lineWidth: 2)
            }
            .shadow(color: shadow.opacity(0.12), radius: 0, x: 2, y: 2)
    }
}

extension View {
    func pixelCard(
        padding: CGFloat = 18,
        fill: Color = .white,
        stroke: Color = PixelPalette.line,
        shadow: Color = PixelPalette.moss
    ) -> some View {
        modifier(PixelCardModifier(padding: padding, fill: fill, stroke: stroke, shadow: shadow))
    }

    func pixelField() -> some View {
        self
            .padding(14)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(PixelPalette.line, lineWidth: 2)
            }
    }
}

struct PixelPrimaryButtonStyle: ButtonStyle {
    var color: Color = PixelPalette.orange
    var foreground: Color = .white

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.headline, design: .rounded, weight: .black))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(color)
            .foregroundStyle(foreground)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(PixelPalette.line, lineWidth: 2)
            }
            .shadow(color: PixelPalette.line.opacity(0.7), radius: 0, x: 4, y: configuration.isPressed ? 2 : 5)
            .offset(y: configuration.isPressed ? 3 : 0)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct PixelCompactButtonStyle: ButtonStyle {
    var color: Color = PixelPalette.orange
    var foreground: Color = PixelPalette.ink
    var fullWidth = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(.headline, design: .rounded, weight: .black))
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .padding(.horizontal, 28)
            .padding(.vertical, 13)
            .background(color)
            .foregroundStyle(foreground)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(PixelPalette.line, lineWidth: 2)
            }
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct PixelSectionTitle: View {
    let eyebrow: String
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(eyebrow.uppercased())
                .font(.system(size: 11, weight: .black, design: .rounded))
                .tracking(1.3)
                .foregroundStyle(PixelPalette.orange)
            Text(title)
                .font(.system(.title3, design: .rounded, weight: .black))
                .foregroundStyle(PixelPalette.ink)
        }
    }
}

struct PixelIconTile: View {
    let icon: String
    let color: Color
    var size: CGFloat = 44

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.42, weight: .bold))
            .foregroundStyle(PixelPalette.ink)
            .frame(width: size, height: size)
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(PixelPalette.line, lineWidth: 2)
            }
    }
}

struct PixelAmountText: View {
    let amount: Decimal
    var size: CGFloat = 24
    var color: Color = PixelPalette.ink
    var prefix: String = ""

    var body: some View {
        HStack(spacing: 1) {
            if !prefix.isEmpty {
                Text(prefix)
            }
            Text(amount, format: .currency(code: "CNY"))
        }
            .font(.system(size: size, weight: .black, design: .rounded))
            .foregroundStyle(color)
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.7)
    }
}

struct PixelProgressBar: View {
    let progress: CGFloat
    var color: Color = PixelPalette.orange

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(PixelPalette.paperDeep)
                Capsule()
                    .fill(color)
                    .frame(width: proxy.size.width * min(max(progress, 0), 1))
            }
        }
        .frame(height: 10)
    }
}

struct PixelChoice: View {
    let title: String
    let icon: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .bold))
                    .frame(height: 24)
                Text(title)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .foregroundStyle(isSelected ? PixelPalette.ink : PixelPalette.muted)
            .background(isSelected ? PixelPalette.orange : .white)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(isSelected ? PixelPalette.line : PixelPalette.paperDeep, lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
    }
}
