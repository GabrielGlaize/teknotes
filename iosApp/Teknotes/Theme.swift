import CoreText
import SwiftUI
import UIKit

/// The "Flyer" style of gabrielglaize.dev: black, chrome titles, typewriter labels, pink accent.
enum Theme {
    static let pink = Color(UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 1, green: 0.31, blue: 0.64, alpha: 1)
        : UIColor(red: 0.78, green: 0.12, blue: 0.45, alpha: 1)
    })
    static let background = Color(UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 0.04, green: 0.04, blue: 0.04, alpha: 1)
        : UIColor(red: 0.96, green: 0.95, blue: 0.92, alpha: 1)
    })
    static let mute = Color(UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 0.56, green: 0.55, blue: 0.52, alpha: 1)
        : UIColor(red: 0.42, green: 0.40, blue: 0.38, alpha: 1)
    })

    /// Bundled fonts are registered at launch, so no Info.plist entry is needed.
    static func registerFonts() {
        let urls = Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        for url in urls {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    /// Variable font instance (axes "wght" and "wdth" given as 4-letter tags).
    private static func variable(_ family: String, size: CGFloat, weight: CGFloat, width: CGFloat? = nil) -> Font {
        var axes: [Int: CGFloat] = [tag("wght"): weight]
        if let width { axes[tag("wdth")] = width }
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: family,
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): axes,
        ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }

    private static func tag(_ text: String) -> Int {
        text.unicodeScalars.reduce(0) { ($0 << 8) | Int($1.value) }
    }

    static func title(_ size: CGFloat = 34) -> Font { variable("Archivo", size: size, weight: 800, width: 125) }
    static func body(_ size: CGFloat = 17, weight: CGFloat = 400) -> Font { variable("Manrope", size: size, weight: weight) }
    static let label = Font.custom("Special Elite", size: 12)
}

/// Big title in brushed chrome, like the name on the portfolio.
struct ChromeTitle: View {
    let text: String
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors: [Color] = scheme == .dark
            ? [.white, Color(white: 0.8), Color(white: 0.36), Color(white: 0.92), Color(white: 0.55)]
            : [Color(white: 0.07), Color(white: 0.28), Color(white: 0.6), Color(white: 0.17), Color(white: 0.36)]
        Text(text.uppercased())
            .font(Theme.title())
            .foregroundStyle(LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom))
            .lineLimit(2)
            .minimumScaleFactor(0.6)
    }
}

/// Small typewriter label, upper case.
struct FlyerLabel: View {
    let text: String
    var color: Color = Theme.mute

    var body: some View {
        Text(text.uppercased()).font(Theme.label).tracking(1).foregroundStyle(color)
    }
}

struct ScreenHeader: View {
    let label: String
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FlyerLabel(text: label, color: Theme.pink)
            ChromeTitle(text: title)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }
}

enum NightDate {
    private static let iso: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let display: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "fr_FR")
        formatter.dateFormat = "EEE d MMM yyyy"
        return formatter
    }()

    /// "sam. 31 oct. 2026" from "2026-10-31".
    static func format(_ isoDate: String) -> String {
        guard let date = iso.date(from: isoDate) else { return isoDate }
        return display.string(from: date)
    }
}
