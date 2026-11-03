"""Generate Android icon vectors and the README header from shared SVG artwork. Standard library only."""
from copy import deepcopy
from pathlib import Path
import re
import json
import math
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "src/main/res"
SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"


def number(value):
    return f"{value:.6f}".rstrip("0").rstrip(".")


def vector(width, height, body):
    return (
        f'<vector xmlns:android="{ANDROID}" xmlns:aapt="{AAPT}" '
        f'android:width="{width}dp" android:height="{height}dp" '
        f'android:viewportWidth="{width}" android:viewportHeight="{height}">\n'
        + body + "\n</vector>\n"
    )


def generate():
    source = ET.parse(HERE / "frame-luma.svg").getroot()
    _, _, width, height = [float(n) for n in source.attrib["viewBox"].split()]
    spec = json.loads(source.find(SVG + "metadata").text)
    cx, cy = spec["center"]
    radius, stroke_width = spec["radius"], spec["stroke"]
    colors = spec["colors"]
    mark = source.find(SVG + "g")
    rays = [p for p in mark if p.attrib.get("data-role") == "ray"]
    if len(colors) != 8 or len(rays) != len(colors):
        raise ValueError("The approved sun requires eight spectrum anchors and eight rays")
    for ray, color in zip(rays, colors):
        if ray.attrib.get("fill") != color or "stroke" in ray.attrib or len(ray) != 0 or not re.fullmatch(r"#[0-9A-Fa-f]{6}", color):
            raise ValueError("Each capsule must use exactly its local pure spectrum color")

    def point(angle, r):
        return cx + r * math.cos(angle), cy + r * math.sin(angle)

    def xy(p):
        return number(p[0]) + " " + number(p[1])

    ring_path = "M" + xy(point(0, radius))
    for i in range(len(colors)):
        a, b = i * math.tau / len(colors), (i + 1) * math.tau / len(colors)
        first, last = point(a, radius), point(b, radius)
        k = 4 / 3 * math.tan((b - a) / 4) * radius
        before = first[0] - k * math.sin(a), first[1] + k * math.cos(a)
        after = last[0] + k * math.sin(b), last[1] - k * math.cos(b)
        ring_path += "C" + xy(before) + " " + xy(after) + " " + xy(last)
    ring_path += "Z"

    # SVG has no portable sweep paint. A dense angular color field is clipped ONCE
    # by a single annulus; no overlapping stroked arcs or round end caps are used.
    defs = source.find(SVG + "defs")
    defs.clear()
    clip = ET.SubElement(defs, SVG + "clipPath", {"id": "ring-outline", "clipPathUnits": "userSpaceOnUse"})
    outer, inner = radius + stroke_width / 2, radius - stroke_width / 2
    outline = ""
    for r in (outer, inner):
        outline += ("M" + xy((cx + r, cy)) + "A" + number(r) + " " + number(r) + " 0 1 0 " + xy((cx - r, cy))
                    + "A" + number(r) + " " + number(r) + " 0 1 0 " + xy((cx + r, cy)) + "Z")
    ET.SubElement(clip, SVG + "path", {"d": outline, "clip-rule": "evenodd", "fill-rule": "evenodd"})
    for child in list(mark):
        if child.attrib.get("data-role") == "ring":
            mark.remove(child)
    ring = ET.Element(SVG + "g", {"data-role": "ring", "clip-path": "url(#ring-outline)"})
    field = ET.SubElement(ring, SVG + "g", {"shape-rendering": "crispEdges"})

    def spectrum(t):
        position = (t % 1) * len(colors)
        index = int(position)
        first, last = colors[index], colors[(index + 1) % len(colors)]
        f = position - index
        channels = [round(int(first[j:j+2], 16) * (1-f) + int(last[j:j+2], 16) * f) for j in (1, 3, 5)]
        return "#" + "".join(f"{channel:02X}" for channel in channels)

    divisions = 256
    delta = math.tau / divisions
    for i in range(divisions):
        a, b = i * delta, (i + 1) * delta
        gradient = ET.SubElement(defs, SVG + "linearGradient", {
            "id": f"spectrum-{i}", "gradientUnits": "userSpaceOnUse",
            "x1": number(point(a, radius)[0]), "y1": number(point(a, radius)[1]),
            "x2": number(point(b, radius)[0]), "y2": number(point(b, radius)[1]),
        })
        ET.SubElement(gradient, SVG + "stop", {"offset": "0", "stop-color": spectrum(i/divisions)})
        ET.SubElement(gradient, SVG + "stop", {"offset": "1", "stop-color": spectrum((i+1)/divisions)})
        extent = outer * 2
        d = "M" + xy((cx, cy)) + "L" + xy(point(a - delta/20, extent)) + "L" + xy(point(b + delta/20, extent)) + "Z"
        ET.SubElement(field, SVG + "path", {"d": d, "fill": f"url(#spectrum-{i})"})
    mark.insert(0, ring)
    ET.register_namespace("", SVG[1:-1])
    ET.indent(source, space="  ")
    (HERE / "frame-luma.svg").write_text(ET.tostring(source, encoding="unicode") + "\n", encoding="utf-8")

    def paths(monochrome=False):
        stroke = number(stroke_width)
        attrs = (
            f'android:pathData="{ring_path}" android:fillColor="#00000000" '
            f'android:strokeWidth="{stroke}" android:strokeLineJoin="round"'
        )
        if monochrome:
            body = [f'  <path {attrs} android:strokeColor="#000000"/>']
        else:
            body = [f'  <path {attrs}>', '    <aapt:attr name="android:strokeColor">',
                    f'      <gradient android:type="sweep" android:centerX="{number(width/2)}" android:centerY="{number(height/2)}">']
            for index, color in enumerate(colors + [colors[0]]):
                body.append(f'        <item android:offset="{number(index/len(colors))}" android:color="{color}"/>')
            body += ['      </gradient>', '    </aapt:attr>', '  </path>']
        for ray in rays:
            a = ray.attrib
            color = "#000000" if monochrome else a["fill"]
            body.append(f'  <path android:pathData="{a["d"]}" android:fillColor="{color}"/>')
        return "\n".join(body)

    def save(relative, text):
        path = RES / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    w, h = number(width), number(height)
    adaptive_scale = source.attrib["data-adaptive-scale"]
    def launcher_paths(monochrome=False):
        return (f'  <group android:pivotX="{number(width/2)}" android:pivotY="{number(height/2)}" android:scaleX="{adaptive_scale}" android:scaleY="{adaptive_scale}">\n'
                + paths(monochrome) + "\n  </group>")
    save("drawable/ic_launcher_foreground.xml", vector(w, h, launcher_paths()))
    save("drawable/ic_launcher_monochrome.xml", vector(w, h, launcher_paths(True)))
    background = f'  <path android:fillColor="@color/launcher_background" android:pathData="M0 0H{w}V{h}H0Z"/>'
    save("mipmap-anydpi/ic_launcher.xml", vector(w, h, background + "\n" + launcher_paths()))
    for api in (26, 33):
        monochrome = '\n  <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>' if api >= 33 else ""
        save(f"mipmap-anydpi-v{api}/ic_launcher.xml", f'<adaptive-icon xmlns:android="{ANDROID}">\n  <background android:drawable="@color/launcher_background"/>\n  <foreground android:drawable="@drawable/ic_launcher_foreground"/>{monochrome}\n</adaptive-icon>\n')
    save("values/launcher_colors.xml", '<resources>\n  <color name="launcher_background">#FFFFFF</color>\n</resources>\n')

    banner = spec["tv_banner"]
    bw, bh = banner["size"]
    tx, ty = map(number, banner["translate"])
    scale = number(banner["scale"])
    body = [f'  <path android:fillColor="@color/launcher_background" android:pathData="M0 0H{number(bw)}V{number(bh)}H0Z"/>',
            f'  <group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{scale}" android:scaleY="{scale}">', paths(), '  </group>']
    save("drawable/tv_banner.xml", vector(number(bw), number(bh), "\n".join(body)))

    header = ET.Element(SVG + "svg", {
        "width": "1200", "height": "220", "viewBox": "0 0 1200 220",
        "fill": "none", "role": "img", "aria-labelledby": "brand-title",
    })
    ET.SubElement(header, SVG + "title", {"id": "brand-title"}).text = "FrameLuma · 映光"
    ET.SubElement(header, SVG + "rect", {"width": "1200", "height": "220", "fill": "#FCFBF8"})
    header.append(deepcopy(source.find(SVG + "defs")))
    symbol = ET.SubElement(header, SVG + "g", {"transform": "translate(505.5 -18.5) scale(1.75)"})
    symbol.append(deepcopy(mark))
    ET.SubElement(header, SVG + "text", {
        "x": "600", "y": "192", "text-anchor": "middle", "fill": "#252420",
        "font-family": "Segoe UI, Arial, Noto Sans CJK SC, Microsoft YaHei, sans-serif",
        "font-size": "48", "font-weight": "600", "letter-spacing": "-.5",
    }).text = "FrameLuma · 映光"
    ET.indent(header, space="  ")
    header_path = HERE.parents[1] / "docs/images/brand-header.svg"
    header_path.parent.mkdir(parents=True, exist_ok=True)
    header_path.write_text(ET.tostring(header, encoding="unicode") + "\n", encoding="utf-8")
    print("Generated Android icon vectors and the shared bilingual README header.")


if __name__ == "__main__":
    generate()
