import CoreText
import SwiftUI
import UIKit

/// Paper and ink. The numbers carry the design: big ranks, big scores, thin rules, no colour.
enum Theme {
    private static func color(dark: UInt32, light: UInt32) -> Color {
        func make(_ hex: UInt32) -> UIColor {
            UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        }
        return Color(UIColor { $0.userInterfaceStyle == .dark ? make(dark) : make(light) })
    }

    static let paper = color(dark: 0x121210, light: 0xF1EEE6)
    static let ink = color(dark: 0xF1EEE6, light: 0x111111)
    static let mute = color(dark: 0x96928A, light: 0x6E6A61)
    static let line = color(dark: 0x34332F, light: 0xD2CDC0)
    static let card = color(dark: 0x201F1C, light: 0xE6E2D7)
    static let accent = ink
    static let onAccent = paper

    /// System font. Follows the text size chosen in the phone's settings (accessibility).
    private static func system(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        .system(size: UIFontMetrics.default.scaledValue(for: size), weight: weight)
    }

    static func display(_ size: CGFloat = 38) -> Font { system(size, .heavy) }
    /// The big ranks, dates and scores.
    static func number(_ size: CGFloat = 42) -> Font { system(size, .heavy) }
    static func text(_ size: CGFloat = 16, weight: CGFloat = 400) -> Font {
        let weights: [(CGFloat, Font.Weight)] = [(400, .regular), (500, .medium), (600, .semibold), (700, .bold)]
        return system(size, weights.first { weight <= $0.0 }?.1 ?? .heavy)
    }
}

/// A screen: scrolls, on paper, with the keyboard following the finger.
struct Page<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) { content }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 96)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Theme.paper.ignoresSafeArea())
        .presentationBackground(Theme.paper)
        .foregroundStyle(Theme.ink)
    }
}

struct BigTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(Theme.display())
            .tracking(-1)
            .lineLimit(2)
            .minimumScaleFactor(0.6)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Opens a part of a screen, like a heading on a printed programme.
struct SectionHead<Trailing: View>: View {
    let text: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        VStack(spacing: 10) {
            Rectangle().fill(Theme.ink).frame(height: 2)
            HStack(alignment: .firstTextBaseline) {
                Text(text).font(Theme.text(15, weight: 700)).accessibilityAddTraits(.isHeader)
                Spacer()
                trailing
            }
        }
        .padding(.top, 32)
        .padding(.bottom, 6)
    }
}

extension SectionHead where Trailing == EmptyView {
    init(text: String) {
        self.init(text: text) { EmptyView() }
    }
}

struct Hairline: View {
    var body: some View {
        Rectangle().fill(Theme.line).frame(height: 1)
    }
}

struct Note: View {
    let text: String

    var body: some View {
        Text(text)
            .font(Theme.text(14))
            .foregroundStyle(Theme.mute)
            .fixedSize(horizontal: false, vertical: true)
    }
}

struct ActionButton: View {
    enum Kind { case primary, outline }

    let title: String
    var kind = Kind.primary
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.text(16, weight: 700))
                .frame(maxWidth: .infinity, minHeight: 52)
                .foregroundStyle(kind == .primary ? Theme.onAccent : Theme.ink)
                .background(kind == .primary ? Theme.accent : Color.clear)
                .overlay(Rectangle().stroke(kind == .primary ? Color.clear : Theme.ink, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
    }
}

/// A word in the signal colour, for what is possible but not the main thing.
struct TextAction: View {
    let title: String
    var color = Theme.accent
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.text(15, weight: 600))
                .foregroundStyle(color)
                .multilineTextAlignment(.leading)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A line of a list that does something or leads somewhere.
struct RowLabel: View {
    let title: String
    var detail: String?
    var color = Theme.ink
    var chevron = true

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text(title).font(Theme.text(16, weight: 500)).foregroundStyle(color)
                Spacer(minLength: 8)
                if let detail {
                    Text(detail).font(Theme.text(14)).foregroundStyle(Theme.mute).lineLimit(1)
                }
                if chevron {
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(Theme.mute)
                }
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
            Hairline()
        }
    }
}

struct RowButton: View {
    let title: String
    var detail: String?
    var color = Theme.ink
    var chevron = false
    let action: () -> Void

    var body: some View {
        Button(action: action) { RowLabel(title: title, detail: detail, color: color, chevron: chevron) }
            .buttonStyle(.plain)
    }
}

/// Text input on a rule, its name above it.
struct LineField: View {
    let label: String
    @Binding var text: String
    var keyboard = UIKeyboardType.default
    var content: UITextContentType?
    var capitalization = TextInputAutocapitalization.never
    var secure = false
    var multiline = false
    @FocusState private var focused: Bool
    @State private var revealed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(Theme.text(13, weight: 600)).foregroundStyle(Theme.mute)
            HStack(spacing: 8) {
                input
                    .labelsHidden()
                    .font(Theme.text(17))
                    .keyboardType(keyboard)
                    .textContentType(content)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled()
                    .focused($focused)
                    .frame(minHeight: 36)
                    .accessibilityIdentifier(label)
                if secure {
                    Button {
                        revealed.toggle()
                    } label: {
                        Image(systemName: revealed ? "eye.slash" : "eye")
                            .foregroundStyle(Theme.mute)
                            .frame(width: 44, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(revealed ? "Masquer le mot de passe" : "Afficher le mot de passe")
                }
            }
            Rectangle().fill(focused ? Theme.accent : Theme.line).frame(height: focused ? 2 : 1)
        }
        .padding(.vertical, 10)
    }

    @ViewBuilder
    private var input: some View {
        if secure && !revealed {
            SecureField(label, text: $text)
        } else if multiline {
            TextField(label, text: $text, axis: .vertical).lineLimit(1...5)
        } else {
            TextField(label, text: $text)
        }
    }
}

/// Search input, with what to look for written in it.
struct SearchLine: View {
    let prompt: String
    @Binding var text: String
    var capitalization = TextInputAutocapitalization.words

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Theme.mute)
            TextField(prompt, text: $text)
                .font(Theme.text(16))
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .frame(minHeight: 44)
            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.mute).frame(width: 36, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Effacer")
            }
        }
        .padding(.horizontal, 12)
        .background(Theme.line.opacity(0.45))
    }
}

/// A set's note: five big digits, the one chosen in ink.
struct NumberRate: View {
    let level: Int
    let onSet: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(1...5, id: \.self) { digit in
                Button {
                    onSet(digit)
                } label: {
                    Text("\(digit)")
                        .font(Theme.number(34))
                        .foregroundStyle(digit == level ? Theme.ink : Theme.line)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Note \(digit) sur 5")
                .accessibilityAddTraits(digit == level ? .isSelected : [])
            }
        }
    }
}

/// A score as a big number, "4,5", or a dash when nothing is rated.
struct ScoreText: View {
    let value: Double?
    var size: CGFloat = 26

    static func format(_ value: Double) -> String {
        String(format: "%.1f", value).replacingOccurrences(of: ".", with: ",")
    }

    var body: some View {
        Text(value.map(Self.format) ?? "–")
            .font(Theme.number(size))
            .tracking(-0.5)
            .foregroundStyle(value == nil ? Theme.mute : Theme.ink)
            .accessibilityLabel(value.map { "Note \(Self.format($0)) sur 5" } ?? "Pas de note")
    }
}

/// Between two views of the same screen.
struct Segments<Item: Hashable>: View {
    let items: [Item]
    @Binding var selection: Item
    let label: (Item) -> String

    var body: some View {
        HStack(spacing: 2) {
            ForEach(items, id: \.self) { item in
                Button {
                    selection = item
                } label: {
                    Text(label(item))
                        .font(Theme.text(14, weight: 600))
                        .foregroundStyle(item == selection ? Theme.ink : Theme.mute)
                        .frame(maxWidth: .infinity, minHeight: 36)
                        .background(item == selection ? Theme.paper : Color.clear, in: RoundedRectangle(cornerRadius: 7))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(item == selection ? .isSelected : [])
            }
        }
        .padding(3)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 9))
    }
}

/// A small rounded choice, filled in ink when on.
struct Chip: View {
    let text: String
    var on = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Theme.text(14, weight: 600))
                .padding(.horizontal, 13)
                .frame(minHeight: 34)
                .foregroundStyle(on ? Theme.paper : Theme.ink)
                .background(on ? Theme.ink : Theme.card, in: Capsule())
                .frame(minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// The hint shown while there is nothing noted yet.
struct TipCard: View {
    let text: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Astuce").font(Theme.text(15, weight: 800))
            Text(text).font(Theme.text(15)).fixedSize(horizontal: false, vertical: true)
            TextAction(title: "Créer une note", action: action)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 4)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
    }
}

/// Choice among a few words: the one selected is underlined in the signal colour.
struct WordTabs<Item: Hashable>: View {
    let items: [Item]
    @Binding var selection: Item
    let label: (Item) -> String
    var size: CGFloat = 20

    var body: some View {
        HStack(spacing: 20) {
            ForEach(items, id: \.self) { item in
                Button {
                    selection = item
                } label: {
                    VStack(spacing: 6) {
                        Text(label(item))
                            .font(Theme.text(size, weight: 700))
                            .foregroundStyle(item == selection ? Theme.ink : Theme.mute)
                        Rectangle().fill(item == selection ? Theme.accent : Color.clear).frame(height: 2)
                    }
                    .fixedSize()
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(item == selection ? .isSelected : [])
            }
        }
    }
}

/// A short word in a thin frame: a style, a filter.
struct Tag: View {
    let text: String
    var selected = false
    var systemImage: String?

    var body: some View {
        HStack(spacing: 5) {
            Text(text).font(Theme.text(14, weight: 600))
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: 10, weight: .bold))
            }
        }
        .padding(.horizontal, 10)
        .frame(minHeight: 32)
        .foregroundStyle(selected ? Theme.paper : Theme.ink)
        .background(selected ? Theme.ink : Color.clear)
        .overlay(Rectangle().stroke(selected ? Theme.ink : Theme.line, lineWidth: 1))
    }
}

/// Wraps tags onto as many lines as needed.
struct TagFlow: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(subviews, width: proposal.width ?? .infinity).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let frames = arrange(subviews, width: bounds.width).frames
        for (subview, frame) in zip(subviews, frames) {
            subview.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY), proposal: ProposedViewSize(frame.size))
        }
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> (frames: [CGRect], size: CGSize) {
        var frames: [CGRect] = []
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0, widest: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += lineHeight + spacing
                lineHeight = 0
            }
            frames.append(CGRect(origin: CGPoint(x: x, y: y), size: size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return (frames, CGSize(width: widest, height: y + lineHeight))
    }
}

/// "Rouille (Caisson Noir)": the artist stands out, the soundsystem follows.
struct SetName: View {
    let artist: String?
    let soundsystem: String?
    var fallback = ""
    var size: CGFloat = 17

    var body: some View {
        if let artist, let soundsystem {
            (Text(artist).font(Theme.text(size, weight: 700)) + Text(" (\(soundsystem))").font(Theme.text(size)).foregroundColor(Theme.mute))
        } else {
            Text(artist ?? soundsystem ?? fallback).font(Theme.text(size, weight: 700))
        }
    }
}

enum NightDate {
    private static func formatter(_ format: String, locale: String = "fr_FR") -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: locale)
        formatter.dateFormat = format
        return formatter
    }

    private static let iso = formatter("yyyy-MM-dd", locale: "en_US_POSIX")
    private static let short = formatter("d MMM yyyy")
    private static let long = formatter("EEEE d MMMM")
    private static let month = formatter("MMMM yyyy")
    private static let day = formatter("d")
    private static let weekday = formatter("EEE")
    private static let year = formatter("yyyy")

    private static func render(_ isoDate: String, _ output: DateFormatter) -> String {
        guard let date = iso.date(from: isoDate) else { return isoDate }
        return output.string(from: date)
    }

    /// "31 oct. 2026".
    static func format(_ isoDate: String) -> String { render(isoDate, short) }
    /// "Samedi 31 octobre".
    static func title(_ isoDate: String) -> String { render(isoDate, long).capitalizedFirst }
    /// "Octobre 2026".
    static func monthTitle(_ isoDate: String) -> String { render(isoDate, month).capitalizedFirst }
    static func dayNumber(_ isoDate: String) -> String { render(isoDate, day) }
    static func weekday(_ isoDate: String) -> String { render(isoDate, weekday) }
    static func yearOf(_ isoDate: String) -> String { render(isoDate, year) }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}

/// Closes the keyboard on a tap anywhere outside a text field. Attached to the window, so it
/// also covers sheets and doesn't get in the way of the buttons underneath.
struct KeyboardDismisser: UIViewRepresentable {
    func makeUIView(context: Context) -> Anchor { Anchor() }

    func updateUIView(_ view: Anchor, context: Context) {}

    final class Anchor: UIView, UIGestureRecognizerDelegate {
        private var attached = false

        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard let window, !attached else { return }
            attached = true
            let tap = UITapGestureRecognizer(target: self, action: #selector(tapped))
            tap.cancelsTouchesInView = false
            tap.delegate = self
            window.addGestureRecognizer(tap)
        }

        @objc private func tapped() {
            window?.endEditing(true)
        }

        func gestureRecognizer(_ gesture: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            !(touch.view is UITextField || touch.view is UITextView)
        }

        func gestureRecognizer(_ gesture: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }
    }
}
