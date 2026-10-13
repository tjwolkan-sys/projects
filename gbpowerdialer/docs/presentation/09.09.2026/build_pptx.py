# -*- coding: utf-8 -*-
"""
FCT Kanal Kapasite Gate sunumu (.pptx) uretici.

python-pptx kurulu olmadigi ve repo offline calistigi icin OOXML paketi yalniz
standart kutuphane (zipfile + string XML) ile uretilir. Ek bagimlilik yoktur.

Kullanim:  python build_fct_pptx.py <cikti_yolu.pptx>
"""

import os
import sys
import zipfile
from xml.sax.saxutils import escape

EMU_IN = 914400
SLIDE_W = 12192000          # 13.333 in
SLIDE_H = 6858000           # 7.5 in

INK = "1F2933"
MUTED = "6B7A8F"
ACCENT = "1B6AC9"
ACCENT2 = "0F9D8E"
WARN = "B45309"
DANGER = "B02A1F"
PANEL = "F4F6F8"
PANEL2 = "E8EDF2"
WHITE = "FFFFFF"
LINE = "CBD5DF"

FONT = "Segoe UI"


def inch(v):
    return int(round(v * EMU_IN))


def esc(t):
    return escape(t if t is not None else "")


# --------------------------------------------------------------------------
# Shape builders
# --------------------------------------------------------------------------

def _run(text, sz, color, bold=False, italic=False, font=FONT):
    return (
        '<a:r><a:rPr lang="tr-TR" sz="%d" b="%d" i="%d" dirty="0">'
        '<a:solidFill><a:srgbClr val="%s"/></a:solidFill>'
        '<a:latin typeface="%s"/><a:cs typeface="%s"/></a:rPr>'
        '<a:t>%s</a:t></a:r>'
        % (sz, 1 if bold else 0, 1 if italic else 0, color, font, font, esc(text))
    )


def _para(runs, align="l", indent=0, space_before=0, bullet=None, line_spc=None):
    ppr = '<a:pPr algn="%s"' % align
    if indent:
        ppr += ' marL="%d" indent="%d"' % (indent, -inch(0.20))
    ppr += '>'
    # OOXML sirasi zorunlu: a:lnSpc -> a:spcBef -> a:spcAft -> bullet elemanlari
    if line_spc:
        ppr += '<a:lnSpc><a:spcPct val="%d"/></a:lnSpc>' % (line_spc * 1000)
    if space_before:
        ppr += '<a:spcBef><a:spcPts val="%d"/></a:spcBef>' % space_before
    if bullet:
        ppr += ('<a:buClr><a:srgbClr val="%s"/></a:buClr>'
                '<a:buFont typeface="Arial"/><a:buChar char="%s"/>' % (bullet[1], bullet[0]))
    else:
        ppr += '<a:buNone/>'
    ppr += '</a:pPr>'
    return '<a:p>' + ppr + "".join(runs) + '</a:p>'


def textbox(sid, x, y, w, h, paras, anchor="t", wrap=True):
    return (
        '<p:sp><p:nvSpPr><p:cNvPr id="%d" name="TextBox %d"/>'
        '<p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>'
        '<p:spPr><a:xfrm><a:off x="%d" y="%d"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>'
        '<p:txBody><a:bodyPr wrap="%s" lIns="0" tIns="0" rIns="0" bIns="0" anchor="%s"><a:spAutoFit/></a:bodyPr>'
        '<a:lstStyle/>%s</p:txBody></p:sp>'
        % (sid, sid, x, y, w, h, "square" if wrap else "none", anchor, "".join(paras))
    )


def shape(sid, x, y, w, h, prst, fill, paras=None, line=None, line_w=12700, anchor="ctr"):
    fill_xml = '<a:noFill/>' if fill is None else '<a:solidFill><a:srgbClr val="%s"/></a:solidFill>' % fill
    if line is None:
        ln = '<a:ln><a:noFill/></a:ln>'
    else:
        ln = ('<a:ln w="%d"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:ln>'
              % (line_w, line))
    body = ""
    if paras:
        body = ('<p:txBody><a:bodyPr wrap="square" lIns="%d" tIns="%d" rIns="%d" bIns="%d" anchor="%s"/>'
                '<a:lstStyle/>%s</p:txBody>'
                % (inch(0.10), inch(0.05), inch(0.10), inch(0.05), anchor, "".join(paras)))
    else:
        body = ('<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:buNone/></a:pPr></a:p></p:txBody>')
    return (
        '<p:sp><p:nvSpPr><p:cNvPr id="%d" name="Shape %d"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>'
        '<p:spPr><a:xfrm><a:off x="%d" y="%d"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
        '<a:prstGeom prst="%s"><a:avLst/></a:prstGeom>%s%s</p:spPr>%s</p:sp>'
        % (sid, sid, x, y, w, h, prst, fill_xml, ln, body)
    )


def table(sid, x, y, col_widths, rows, header_fill=ACCENT, sz=1200, row_h=None):
    """rows: list of list of (text, bold, color) or plain str. First row = header."""
    grid = "".join('<a:gridCol w="%d"/>' % w for w in col_widths)
    trs = []
    for ri, row in enumerate(rows):
        tcs = []
        for ci, cell in enumerate(row):
            if isinstance(cell, tuple):
                text, bold, color = cell
            else:
                text, bold, color = cell, False, (WHITE if ri == 0 else INK)
            csz = sz if ri > 0 else sz
            if ri == 0:
                fill = header_fill
            else:
                fill = WHITE if ri % 2 == 1 else PANEL
            lines = text.split("\n")
            paras = [_para([_run(l, csz, color, bold=(bold or ri == 0))], line_spc=90) for l in lines]
            tcs.append(
                '<a:tc><a:txBody><a:bodyPr lIns="%d" tIns="%d" rIns="%d" bIns="%d" anchor="ctr"/>'
                '<a:lstStyle/>%s</a:txBody>'
                '<a:tcPr marL="0" marR="0" marT="0" marB="0" anchor="ctr">'
                '<a:lnL w="6350"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:lnL>'
                '<a:lnR w="6350"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:lnR>'
                '<a:lnT w="6350"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:lnT>'
                '<a:lnB w="6350"><a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:lnB>'
                '<a:solidFill><a:srgbClr val="%s"/></a:solidFill></a:tcPr></a:tc>'
                % (inch(0.08), inch(0.04), inch(0.08), inch(0.04), "".join(paras),
                   LINE, LINE, LINE, LINE, fill)
            )
        h = row_h[ri] if row_h else inch(0.34)
        trs.append('<a:tr h="%d">%s</a:tr>' % (h, "".join(tcs)))
    total_w = sum(col_widths)
    return (
        '<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="%d" name="Table %d"/>'
        '<p:cNvGraphicFramePr><a:graphicFrameLocks noGrp="1"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>'
        '<p:xfrm><a:off x="%d" y="%d"/><a:ext cx="%d" cy="%d"/></p:xfrm>'
        '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">'
        '<a:tbl><a:tblPr firstRow="1"/><a:tblGrid>%s</a:tblGrid>%s</a:tbl>'
        '</a:graphicData></a:graphic></p:graphicFrame>'
        % (sid, sid, x, y, total_w, sum(row_h) if row_h else inch(0.34) * len(rows), grid, "".join(trs))
    )


# --------------------------------------------------------------------------
# Slide chrome
# --------------------------------------------------------------------------

class Deck(object):
    def __init__(self):
        self.slides = []

    def add(self, shapes_xml):
        self.slides.append(shapes_xml)

    # ---- layouts ----------------------------------------------------------

    def chrome(self, sid, title, kicker=None):
        """Return (shapes, next_id, content_top)."""
        out = []
        out.append(shape(sid, 0, 0, SLIDE_W, SLIDE_H, "rect", WHITE))
        sid += 1
        out.append(shape(sid, 0, 0, inch(0.09), SLIDE_H, "rect", ACCENT))
        sid += 1
        top = inch(0.42)
        if kicker:
            out.append(textbox(sid, inch(0.62), top, inch(11.9), inch(0.28),
                               [_para([_run(kicker.upper(), 1100, ACCENT2, bold=True)])]))
            sid += 1
            top += inch(0.33)
        out.append(textbox(sid, inch(0.62), top, inch(11.9), inch(0.55),
                           [_para([_run(title, 2600, INK, bold=True)])]))
        sid += 1
        top += inch(0.72)
        out.append(shape(sid, inch(0.62), top, inch(1.5), inch(0.045), "rect", ACCENT2))
        sid += 1
        return out, sid, top + inch(0.34)

    def footer(self, sid, note, color=MUTED):
        return textbox(sid, inch(0.62), inch(6.72), inch(11.9), inch(0.4),
                       [_para([_run(note, 1050, color, italic=True)])]), sid + 1

    def title_slide(self, title, subtitle, meta_lines):
        sid = 2
        out = [shape(sid, 0, 0, SLIDE_W, SLIDE_H, "rect", "132C4A")]
        sid += 1
        out.append(shape(sid, 0, inch(6.9), SLIDE_W, inch(0.6), "rect", ACCENT2))
        sid += 1
        out.append(shape(sid, inch(0.9), inch(1.55), inch(1.7), inch(0.055), "rect", ACCENT2))
        sid += 1
        out.append(textbox(sid, inch(0.9), inch(1.05), inch(10.5), inch(0.35),
                           [_para([_run("GBPOWERDIALER  ·  FCT ROUTING", 1200, "8FB8E6", bold=True)])]))
        sid += 1
        out.append(textbox(sid, inch(0.9), inch(1.95), inch(11.0), inch(1.5),
                           [_para([_run(title, 4400, WHITE, bold=True)], line_spc=95)]))
        sid += 1
        out.append(textbox(sid, inch(0.9), inch(3.55), inch(10.2), inch(0.6),
                           [_para([_run(subtitle, 1700, "C9DCF2")], line_spc=100)]))
        sid += 1
        paras = [_para([_run(l, 1200, "9FB6D0")], space_before=500) for l in meta_lines]
        out.append(textbox(sid, inch(0.9), inch(4.75), inch(10.2), inch(1.5), paras))
        sid += 1
        self.add("".join(out))

    def section_slide(self, no, title, subtitle):
        sid = 2
        out = [shape(sid, 0, 0, SLIDE_W, SLIDE_H, "rect", PANEL)]
        sid += 1
        out.append(shape(sid, 0, 0, inch(0.09), SLIDE_H, "rect", ACCENT))
        sid += 1
        out.append(textbox(sid, inch(0.9), inch(2.6), inch(2.0), inch(1.2),
                           [_para([_run(no, 6600, "D4DEE8", bold=True)])]))
        sid += 1
        out.append(textbox(sid, inch(2.55), inch(2.85), inch(9.4), inch(0.8),
                           [_para([_run(title, 3200, INK, bold=True)])]))
        sid += 1
        out.append(textbox(sid, inch(2.6), inch(3.75), inch(9.2), inch(0.6),
                           [_para([_run(subtitle, 1500, MUTED)], line_spc=100)]))
        sid += 1
        self.add("".join(out))

    def bullet_slide(self, title, bullets, kicker=None, note=None):
        out, sid, top = self.chrome(2, title, kicker)
        y = top
        for b in bullets:
            if isinstance(b, tuple):
                level, text = b[0], b[1]
                color = b[2] if len(b) > 2 else INK
            else:
                level, text, color = 0, b, INK
            if level == 0:
                out.append(shape(sid, inch(0.66), y + inch(0.10), inch(0.10), inch(0.10),
                                 "ellipse", ACCENT))
                sid += 1
                out.append(textbox(sid, inch(0.98), y, inch(11.4), inch(0.4),
                                   [_para([_run(text, 1550, color, bold=True)], line_spc=100)]))
                sid += 1
                y += inch(0.42)
            elif level == 1:
                out.append(textbox(sid, inch(1.12), y, inch(11.2), inch(0.36),
                                   [_para([_run("– " + text, 1300, color)], line_spc=100)]))
                sid += 1
                y += inch(0.36)
            else:
                out.append(textbox(sid, inch(1.52), y, inch(10.8), inch(0.34),
                                   [_para([_run("· " + text, 1150, MUTED)], line_spc=100)]))
                sid += 1
                y += inch(0.32)
            y += inch(0.05)
        if note:
            f, sid = self.footer(sid, note)
            out.append(f)
        self.add("".join(out))

    def table_slide(self, title, headers, rows, col_widths, kicker=None, note=None, sz=1150):
        out, sid, top = self.chrome(2, title, kicker)
        data = [headers] + rows
        row_h = [inch(0.40)] + [inch(0.40)] * len(rows)
        out.append(table(sid, inch(0.62), top, col_widths, data, sz=sz, row_h=row_h))
        sid += 1
        if note:
            f, sid = self.footer(sid, note)
            out.append(f)
        self.add("".join(out))

    def flow_slide(self, title, steps, kicker=None, note=None):
        """steps: list of (label, detail, color). Vertical chain with down arrows."""
        out, sid, top = self.chrome(2, title, kicker)
        n = len(steps)
        avail = inch(6.15) - top + inch(0.30)
        box_h = int((avail - (n - 1) * inch(0.16)) / n)
        box_h = max(box_h, inch(0.45))
        y = top
        for i, (label, detail, color) in enumerate(steps):
            out.append(shape(sid, inch(0.62), y, inch(0.85), box_h, "roundRect", color,
                             [_para([_run(str(i + 1), 1700, WHITE, bold=True)], align="ctr")]))
            sid += 1
            out.append(shape(sid, inch(1.60), y, inch(10.95), box_h, "roundRect", PANEL,
                             [_para([_run(label, 1400, INK, bold=True)], line_spc=100),
                              _para([_run(detail, 1100, MUTED)], space_before=200, line_spc=100)],
                             line=LINE, line_w=6350, anchor="ctr"))
            sid += 1
            if i < n - 1:
                out.append(shape(sid, inch(0.94), y + box_h + inch(0.015), inch(0.20), inch(0.13),
                                 "downArrow", "9AA9B8"))
                sid += 1
            y += box_h + inch(0.16)
        if note:
            f, sid = self.footer(sid, note)
            out.append(f)
        self.add("".join(out))

    def two_panel_slide(self, title, left, right, kicker=None, note=None):
        """left/right: (heading, color, [lines])"""
        out, sid, top = self.chrome(2, title, kicker)
        w = inch(5.82)
        h = inch(4.55)
        for idx, (heading, color, lines) in enumerate((left, right)):
            x = inch(0.62) + idx * (w + inch(0.30))
            out.append(shape(sid, x, top, w, h, "roundRect", PANEL, line=LINE, line_w=6350))
            sid += 1
            out.append(shape(sid, x, top, w, inch(0.055), "rect", color))
            sid += 1
            out.append(textbox(sid, x + inch(0.28), top + inch(0.26), w - inch(0.56), inch(0.4),
                               [_para([_run(heading, 1600, color, bold=True)], line_spc=100)]))
            sid += 1
            yy = top + inch(0.78)
            for ln in lines:
                bold = ln.startswith("*")
                txt = ln[1:] if bold else ln
                out.append(textbox(sid, x + inch(0.28), yy, w - inch(0.56), inch(0.34),
                                   [_para([_run(txt, 1200 if not bold else 1250,
                                                INK if bold else MUTED, bold=bold)], line_spc=100)]))
                sid += 1
                yy += inch(0.36) if not bold else inch(0.40)
        if note:
            f, sid = self.footer(sid, note)
            out.append(f)
        self.add("".join(out))

    def metric_slide(self, title, cards, bullets, kicker=None, note=None):
        """cards: list of (big, label, color)"""
        out, sid, top = self.chrome(2, title, kicker)
        n = len(cards)
        gap = inch(0.26)
        w = int((inch(11.94) - gap * (n - 1)) / n)
        for i, (big, label, color) in enumerate(cards):
            x = inch(0.62) + i * (w + gap)
            out.append(shape(sid, x, top, w, inch(1.55), "roundRect", PANEL, line=LINE, line_w=6350))
            sid += 1
            out.append(textbox(sid, x + inch(0.22), top + inch(0.24), w - inch(0.44), inch(0.7),
                               [_para([_run(big, 2800, color, bold=True)], align="ctr")]))
            sid += 1
            out.append(textbox(sid, x + inch(0.18), top + inch(0.95), w - inch(0.36), inch(0.5),
                               [_para([_run(label, 1100, MUTED)], align="ctr", line_spc=95)]))
            sid += 1
        y = top + inch(1.90)
        for b in bullets:
            lvl, text = (b if isinstance(b, tuple) else (0, b))
            if lvl == 0:
                out.append(shape(sid, inch(0.66), y + inch(0.10), inch(0.10), inch(0.10), "ellipse", ACCENT))
                sid += 1
                out.append(textbox(sid, inch(0.98), y, inch(11.4), inch(0.4),
                                   [_para([_run(text, 1450, INK, bold=True)], line_spc=100)]))
                sid += 1
                y += inch(0.42)
            else:
                out.append(textbox(sid, inch(1.12), y, inch(11.2), inch(0.36),
                                   [_para([_run("– " + text, 1250, MUTED)], line_spc=100)]))
                sid += 1
                y += inch(0.36)
        if note:
            f, sid = self.footer(sid, note)
            out.append(f)
        self.add("".join(out))


# --------------------------------------------------------------------------
# Package scaffolding
# --------------------------------------------------------------------------

SLIDE_TMPL = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">'
    '<p:cSld><p:spTree>'
    '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
    '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/>'
    '<a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>'
    '%s'
    '</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>'
)

THEME = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="GBPD">'
    '<a:themeElements>'
    '<a:clrScheme name="GBPD"><a:dk1><a:srgbClr val="1F2933"/></a:dk1>'
    '<a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="132C4A"/></a:dk2>'
    '<a:lt2><a:srgbClr val="F4F6F8"/></a:lt2><a:accent1><a:srgbClr val="1B6AC9"/></a:accent1>'
    '<a:accent2><a:srgbClr val="0F9D8E"/></a:accent2><a:accent3><a:srgbClr val="B45309"/></a:accent3>'
    '<a:accent4><a:srgbClr val="B02A1F"/></a:accent4><a:accent5><a:srgbClr val="6B7A8F"/></a:accent5>'
    '<a:accent6><a:srgbClr val="8FB8E6"/></a:accent6>'
    '<a:hlink><a:srgbClr val="1B6AC9"/></a:hlink><a:folHlink><a:srgbClr val="6B7A8F"/></a:folHlink></a:clrScheme>'
    '<a:fontScheme name="GBPD">'
    '<a:majorFont><a:latin typeface="Segoe UI"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>'
    '<a:minorFont><a:latin typeface="Segoe UI"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>'
    '</a:fontScheme>'
    '<a:fmtScheme name="GBPD">'
    '<a:fillStyleLst>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '</a:fillStyleLst>'
    '<a:lnStyleLst>'
    '<a:ln w="6350" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>'
    '<a:ln w="12700" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>'
    '<a:ln w="19050" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>'
    '</a:lnStyleLst>'
    '<a:effectStyleLst>'
    '<a:effectStyle><a:effectLst/></a:effectStyle>'
    '<a:effectStyle><a:effectLst/></a:effectStyle>'
    '<a:effectStyle><a:effectLst/></a:effectStyle>'
    '</a:effectStyleLst>'
    '<a:bgFillStyleLst>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'
    '</a:bgFillStyleLst>'
    '</a:fmtScheme></a:themeElements></a:theme>'
)

SLIDE_MASTER = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">'
    '<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>'
    '<a:effectLst/></p:bgPr></p:bg><p:spTree>'
    '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
    '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/>'
    '<a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>'
    '</p:spTree></p:cSld>'
    '<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2"'
    ' accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6"'
    ' hlink="hlink" folHlink="folHlink"/>'
    '<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>'
    '<p:txStyles><p:titleStyle><a:lvl1pPr><a:defRPr sz="2600"/></a:lvl1pPr></p:titleStyle>'
    '<p:bodyStyle><a:lvl1pPr><a:defRPr sz="1400"/></a:lvl1pPr></p:bodyStyle>'
    '<p:otherStyle><a:lvl1pPr><a:defRPr sz="1400"/></a:lvl1pPr></p:otherStyle></p:txStyles>'
    '</p:sldMaster>'
)

SLIDE_LAYOUT = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
    '<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"'
    ' type="blank" preserve="1">'
    '<p:cSld name="Bos"><p:spTree>'
    '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
    '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/>'
    '<a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>'
    '</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>'
)


def build(deck, path, title, author):
    n = len(deck.slides)
    z = zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED)

    ct = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
          '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
          '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
          '<Default Extension="xml" ContentType="application/xml"/>'
          '<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>'
          '<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>'
          '<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>'
          '<Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>'
          '<Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>'
          '<Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>'
          '<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>'
          '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
          '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>']
    for i in range(1, n + 1):
        ct.append('<Override PartName="/ppt/slides/slide%d.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>' % i)
    ct.append('</Types>')
    z.writestr("[Content_Types].xml", "".join(ct))

    z.writestr("_rels/.rels",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
               '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>'
               '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
               '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>'
               '</Relationships>')

    # ST_SlideId gecerli aralik: 256 <= id < 2147483648. 256'nin altina dusmek
    # (ornegin 255) dosyanin tamami icin PowerPoint tarafindan reddedilmeye yol acar.
    sld_ids = "".join('<p:sldId id="%d" r:id="rId%d"/>' % (256 + i, i + 2) for i in range(n))
    z.writestr("ppt/presentation.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
               ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
               ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" saveSubsetFonts="1">'
               '<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>'
               '<p:sldIdLst>%s</p:sldIdLst>'
               '<p:sldSz cx="%d" cy="%d"/><p:notesSz cx="6858000" cy="9144000"/>'
               '</p:presentation>' % (sld_ids, SLIDE_W, SLIDE_H))

    prels = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
             '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
             '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>']
    for i in range(1, n + 1):
        prels.append('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide%d.xml"/>' % (i + 1, i))
    prels.append('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>' % (n + 2))
    prels.append('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/>' % (n + 3))
    prels.append('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/>' % (n + 4))
    prels.append('<Relationship Id="rId%d" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles" Target="tableStyles.xml"/>' % (n + 5))
    prels.append('</Relationships>')
    z.writestr("ppt/_rels/presentation.xml.rels", "".join(prels))

    NS = ('xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
          ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
          ' xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"')
    z.writestr("ppt/presProps.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<p:presentationPr %s/>' % NS)
    z.writestr("ppt/viewProps.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<p:viewPr %s/>' % NS)
    z.writestr("ppt/tableStyles.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
               ' def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>')

    z.writestr("ppt/slideMasters/slideMaster1.xml", SLIDE_MASTER)
    z.writestr("ppt/slideMasters/_rels/slideMaster1.xml.rels",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
               '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>'
               '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>'
               '</Relationships>')
    z.writestr("ppt/slideLayouts/slideLayout1.xml", SLIDE_LAYOUT)
    z.writestr("ppt/slideLayouts/_rels/slideLayout1.xml.rels",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
               '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>'
               '</Relationships>')
    z.writestr("ppt/theme/theme1.xml", THEME)

    for i, shapes in enumerate(deck.slides, start=1):
        z.writestr("ppt/slides/slide%d.xml" % i, SLIDE_TMPL % shapes)
        z.writestr("ppt/slides/_rels/slide%d.xml.rels" % i,
                   '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
                   '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>'
                   '</Relationships>')

    z.writestr("docProps/core.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"'
               ' xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"'
               ' xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
               '<dc:title>%s</dc:title><dc:creator>%s</dc:creator>'
               '<cp:lastModifiedBy>%s</cp:lastModifiedBy>'
               '<dcterms:created xsi:type="dcterms:W3CDTF">2026-09-09T09:00:00Z</dcterms:created>'
               '<dcterms:modified xsi:type="dcterms:W3CDTF">2026-09-09T09:00:00Z</dcterms:modified>'
               '</cp:coreProperties>' % (esc(title), esc(author), esc(author)))

    z.writestr("docProps/app.xml",
               '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
               '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"'
               ' xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">'
               '<Application>Microsoft Office PowerPoint</Application>'
               '<Slides>%d</Slides><Company>Global Bilgi</Company>'
               '</Properties>' % n)

    z.close()


# --------------------------------------------------------------------------
# Content
# --------------------------------------------------------------------------

def content(d):
    d.title_slide(
        "FCT Kanal Kapasite Gate",
        "FCT basina es zamanli kanal limitini PowerDialer'da sayan, dolu FCT'ye hic gitmeyen admission control",
        ["Branch: feature/GLB90114534-fct-kanal-kapasite-gate   ·   Commit: 5cfa5bfd",
         "Kapsam: FctChannelRegistry · FctChannelGate · RouteDetailsFilter · FCT Channel Monitor",
         "Hazirlayan: GLB90114534   ·   09.09.2026"])

    # ---- 1. Problem -------------------------------------------------------
    d.section_slide("01", "Problem", "Bugun FCT kapasitesini kimse saymiyor; dolulugu ancak hatadan ogreniyoruz.")

    d.metric_slide(
        "Bugunku davranis: dolulugu hatadan ogreniyoruz",
        [("34", "Asterisk hangup cause\nNo circuit / channel available", DANGER),
         ("TRC 25", "Dialer sonuc kodu\nCongestion", DANGER),
         ("TRC 79 / 907", "Originate action error\nring timeout artisi sonrasi", WARN),
         ("~%17", "Trunk zamaninin bosa giden\npayi (uretim log analizi)", WARN)],
        [(0, "FCT'ler Asterisk tarafinda yonetiliyor; PowerDialer FCT basina kac kanal oldugunu bilmiyor."),
         (1, "Kapasitesi dolmus bir FCT'ye originate atiliyor, cause 34 ile geri donuyor."),
         (1, "Cagri kanal-zamani harcaniyor ama hicbir zaman baglanmiyor: kesin maliyet, sifir fayda."),
         (0, "Sonuc: dolu FCT'yi ancak deneyip basarisiz olduktan sonra ogreniyoruz."),
         (1, "Reaktif dongu: dene -> cause 34 -> tekrar dene. Kaynak tuketimi sabit, basari orani dusuk.")],
        kicker="Problem",
        note="Kaynak: docs/analyse/07.08.2026 ve 08.09.2026 uretim log analizleri (dl-e9e937bb: 290.308 congestion / 361.253 originate).")

    # ---- 2. Cozum ---------------------------------------------------------
    d.section_slide("02", "Cozum", "Kapasite tanimi QSS'te, sayim dialer'da. Dolu FCT'ye hic gitme.")

    d.two_panel_slide(
        "Sorumluluk ayrimi: tanim QSS'te, sayim dialer'da",
        ("QSS — kapasiteyi TANIMLAR", ACCENT,
         ["*getRouteDetails yaniti",
          "RouteDetailEntry.capacity (field 4)",
          "qss_route.capacity NULL ise 0 doner",
          "*QSS limiti UYGULAMAZ",
          "Cagri yapildi mi, cevap ne oldu bilmez",
          "Bu yuzden sayim QSS'te olamaz"]),
        ("PowerDialer — kapasiteyi SAYAR", ACCENT2,
         ["*Cagri yasam dongusunun sahibi",
          "Originate ne zaman atildi bilir",
          "Hangup / expire / hata ne zaman oldu bilir",
          "*FctChannelRegistry",
          "Numara basina in-flight callId seti",
          "Rezervasyon = originate, birakma = hangup"]),
        kicker="Cozum",
        note="Temel kural: bir numara = bir FCT. Kapasite anahtari dogrudan phoneNumber'dir; ayri bir FCT kimligi tutulmaz.")

    d.bullet_slide(
        "Dort bilesen",
        [(0, "FctChannelRegistry — tek @Component, uygulama geneli durum"),
         (1, "Numara -> in-flight callId seti, cooldown, bounded olay log'u, 5 dakikalik KPI penceresi"),
         (1, "Rezervasyon SET'tir, sayac degildir: sayisal drift yapisal olarak olusamaz"),
         (0, "FctChannelGate — cagri akisi ile registry arasindaki TEK karar noktasi"),
         (1, "reserveForOrigination · reserveNextAfterNoCircuit · release · isListRetryAllowed"),
         (0, "RouteDetailsFilter — QSS listesini cagri tipine ve deneme sayisina gore suzer"),
         (1, "Eski obtainCallerId yolunda QSS'e giden CT_PD_* / AT_* context'ini tuketen tarafta telafi eder"),
         (0, "FCT Channel Monitor — Options menusu altinda canli izleme ekrani"),
         (1, "GET /fctChannel/snapshot · 1 sn cache · DialerAdministration yetkisi")],
        kicker="Mimari",
        note="Kalip olarak AgentDialLockRegistry'nin kapasitesi N olan halidir; yeni bir esyapi getirmez.")

    # ---- 3. Akis ----------------------------------------------------------
    d.section_slide("03", "Ana akis", "Originate'e en yakin noktada rezervasyon, dolu ise cagriyi yakmadan oteleme.")

    d.flow_slide(
        "Originate akisi: adim adim",
        [("CallOriginator tick — cagri kuyruktan alinir", "isFctRoutingPath: fctRoutingEnabled + callerIdBehaviour=POOL", ACCENT),
         ("QSS getRouteDetails(routingGroupId, calledNumber)", "Yanit: phoneNumber + type + attempts + capacity listesi", ACCENT),
         ("RouteDetailsFilter.filterForCall(entries, callType, attempts)", "FRESH->Dialer First · RETRY->Retry · CALLBACK->Callback · uc katmanli fail-open", ACCENT2),
         ("RouteDetailsCursor kurulur, ilk entry caller ID olur", "Cursor cagriya baglanir: cause 34 iterasyonu da bu cursor uzerinden yurur", ACCENT2),
         ("processOrigination: pause kontrolu -> KAPASITE GATE", "reserveForOrigination: cursor uzerinde dolu/cooldown entry'leri atlar", WARN),
         ("Rezervasyon alindi -> AMI OriginateAction", "callQueue.add + addToOriginatedCalls + CDR + Redis dial event", ACCENT2)],
        kicker="Akis",
        note="Rezervasyon NEDEN burada: LSC async islemi ve preview timeout suresince FCT kanali bosa tutulmasin diye AMI send'e en yakin noktada alinir.")

    d.two_panel_slide(
        "Kapasite hesabi ve headroom",
        ("effectiveCapacity(capacity)", ACCENT,
         ["*headroom = %10 (varsayilan)",
          "effective = floor(capacity * 90 / 100)",
          "en az 1 (Math.max(1, effective))",
          "*Ornek: capacity = 10",
          "effective = 9 -> 10. cagri REJECT",
          "*Neden headroom var?",
          "PowerDialer disi tuketiciler ve restart",
          "sonrasi bilinmeyen doluluk icin pay"]),
        ("Gate ne zaman PASIF?", MUTED,
         ["*capacity <= 0 (QSS'te NULL)",
          "isGateActive false -> her zaman true doner",
          "*fct.channel.gate.enabled = false",
          "Kill switch: 04.05 davranisi birebir geri gelir",
          "*Ikisinde de rezervasyon YINE KAYDEDILIR",
          "Monitor ekrani gercek dolulugu gate",
          "acilmadan once de gosterir (fail-open)"]),
        kicker="Kapasite",
        note="Kapasite kontrolu ve rezervasyon ayni per-numara synchronized blogunda yapilir: iki originator thread'i son kanali ikiye satamaz.")

    d.flow_slide(
        "Tum FCT'ler dolu ise: cagri YAKILMAZ",
        [("reserveForOrigination false doner", "Cursor'daki her entry dolu veya cooldown'da -> WARN + originate atlanir", WARN),
         ("deferOriginationUntilFctChannelAvailable", "Kampanya pause defer'i ile AYNI primitive kullanilir", WARN),
         ("cleanupCallBeforeDeferredOrigination", "attempt geri alinir · agent idle havuzuna doner · callUpToDateKeeper unregister", ACCENT2),
         ("Cagri kuyrugun BASINA konur (offerFirst)", "Bir sonraki tick'te yeniden denenir; sira kaybi olmaz", ACCENT2),
         ("Defer limiti (10) asilirsa", "TELEPHONE_RC_NO_CALLERID_AVAILABLE akisina dusulur — cagri sonsuza kadar donmez", DANGER)],
        kicker="Doluluk",
        note="Kritik davranis: kapasite yoklugu bir HATA degil, bir BEKLEME durumudur. Cagri kaydi ve attempt sayaci korunur.")

    d.flow_slide(
        "cause 34 geldiyse: bilgiyi sakla, cagriyi zorlamadan devret",
        [("DialerHangupEvent, hangupCause = 34", "HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE -> tryFctRetryWithNextCallerId", DANGER),
         ("isListRetryAllowed(fctListRetryCount)?", "fct.channel.list.retry.max = 0 -> gate acikken in-call retry YOK", WARN),
         ("Sinir asilsa da releaseAndMarkFull calisir", "Rezervasyon birakilir + numara 60 sn cooldown'a alinir", ACCENT2),
         ("Standart processCallResult zinciri", "TRC 25 -> retry strategy. Cagri kaybolmaz, normal retry politikasina girer", ACCENT2)],
        kicker="cause 34",
        note="Gerekce: gate zaten dolu/cooldown FCT'ye gitmiyor. Buna ragmen cause 34 geliyorsa kitlik gate'in gormedigi seviyededir (trunk/SBC) ve siradaki FCT'nin de basarisiz olma olasiligi yuksek, kanal-zamani maliyeti ise kesin.")

    # ---- 4. Dayaniklilik --------------------------------------------------
    d.section_slide("04", "Dayaniklilik", "Kacan release, restart, kampanya aksiyonlari ve QSS kesintisi.")

    d.bullet_slide(
        "Release noktalari — kanal nerede geri verilir?",
        [(0, "Normal yol"),
         (1, "releaseTelephonySlotIfNeeded — telefonluk slotla ayni anda"),
         (1, "CallCleaner.completeCall — cagri tamamlandiginda"),
         (0, "Istisnai yollar"),
         (1, "CallCleaner.clearExpiredCall — CALL_EXPIRE_TIMEOUT sonrasi"),
         (1, "handlePreviewTimeout · handleExceptionDuringOrigination — Originate atilamadiysa kanal hemen geri verilir"),
         (1, "cause 34 releaseAndMarkFull · CampaignStopJob.lastCleanUpChecks (kampanya bazli safety net)"),
         (0, "Son savunma hatti: TTL prune"),
         (1, "fct.channel.ttl.ms = 3.900.000 ms = CALL_EXPIRE_TIMEOUT (1 sa) + 5 dk"),
         (1, "TTL bilincli olarak cagri omrunun UZERINDE: aksi halde konusma halindeki uzun cagrinin kanali erken birakilir"),
         (1, "fct.channel.stale.warn.ms = 600.000 -> 10 dk hareketsiz kayit monitor'de isaretlenir")],
        kicker="Release",
        note="Release owner kontrollu (numara, callId) ve idempotenttir: yasam dongusunun birden fazla noktasindan cagrilmasi guvenlidir.")

    d.table_slide(
        "Kampanya yasam dongusu ve restart davranisi",
        [("Aksiyon", True, WHITE), ("Registry'ye etkisi", True, WHITE), ("Gerekce", True, WHITE)],
        [["Campaign START", "Etki yok", "Rezervasyonun sahibi CAGRIDIR, kampanya degil"],
         ["Campaign PAUSE / PAUSING", "Etki yok", "Ucustaki cagrilar kanalini tutmaya devam eder"],
         ["Campaign RESET / RELOAD", "Etki yok", "Registry bir indekstir, ikinci bir dogruluk kaynagi degil"],
         ["Campaign STOP (graceful)", "Cagri bazli release", "Her cagri normal hangup yolundan birakir"],
         ["Campaign STOP (forceful)", "releaseAllForCampaign", "CampaignStopJob.lastCleanUpChecks icinde safety net"],
         ["Scheduler otomatik start/stop", "Manuel ile ayni", "Ayri bir kod yolu yok, ayni primitive"],
         ["Uygulama RESTART / DEPLOY", "Registry BOSALIR", "JVM ici state; kisa sure kapasite bos gorunur"],
         ["Restart sonrasi ilk dakika", "Cold start WARN + cooldown", "headroom %10 ve cooldown bu pencereyi absorbe eder"]],
        [inch(3.0), inch(3.4), inch(5.54)],
        kicker="Yasam dongusu",
        note="Preview kampanyalar gate kapsami disindadir (originate'i webphone atar); FCT routing liste iterasyonu preview'da korunur.")

    d.two_panel_slide(
        "QSS erisilemezse: gecici hata ile kalici yokluk ayrildi",
        ("GECICI — erisilemedi", DANGER,
         ["*RouteDetailsUnavailableException",
          "gRPC channel yok veya cagri hata atti",
          "*cancelCall(..., allowRetries = TRUE)",
          "Cagri retry'a izin verilerek iptal edilir",
          "*Onceden ne oluyordu?",
          "Hata da bos liste donuyordu -> retry'siz",
          "iptal -> kisa bir QSS/Eureka kesintisinde",
          "o an originate edilen TUM cagrilar TRC 910",
          "ile KALICI olarak kaybediliyordu"]),
        ("KALICI — route yok", WARN,
         ["*Bos liste (grup bulunamadi)",
          "QSS sozlesmesine gore bu bir HATA DEGIL",
          "*NoAvailablePoolCallerIdException",
          "cancelCall(..., allowRetries = FALSE)",
          "*Neden retry'siz?",
          "Konfigurasyon eksikligi tekrar denemekle",
          "duzelmez; cagriyi kuyrukta dondurmek",
          "yalnizca kaynak harcar"]),
        kicker="Hata ayrimi",
        note="Ayrica: her FCT originate'inde basilan 'Got route details' INFO satiri guard'li DEBUG'a indirildi (gunluk ~180.000 satir).")

    # ---- 5. QSS sozlesme --------------------------------------------------
    d.table_slide(
        "QSS getRouteDetails sozlesmesi ve RouteDetailsFilter suzmesi",
        [("Alan", True, WHITE), ("QSS davranisi", True, WHITE), ("PowerDialer kullanimi", True, WHITE)],
        [["phoneNumber", "FCT numarasi", "Kapasite anahtari + caller ID (bir numara = bir FCT)"],
         ["type", "Virgulle birlesik: Manual, Retry,\nCallback, Dialer First", "callType eslesmesi: FRESH->Dialer First,\nRETRY->Retry, CALLBACK->Callback"],
         ["attempts", "Virgulle birlesik; AT_ yoksa\n0..99 fallback doner", "call.getAttempts() ile token bazli eslesme"],
         ["capacity", "Numara basina es zamanli cikis limiti;\nqss_route.capacity NULL ise 0", "effectiveCapacity(capacity) -> gate esigi;\n0 ise gate pasif"],
         ["(grup yok)", "Bos liste doner — hata degil", "NoAvailablePoolCallerId -> retry'siz iptal"]],
        [inch(1.9), inch(4.5), inch(5.54)],
        kicker="Sozlesme",
        note="SINIR: bu suzme retryCallerIdBehavior (UNIQUE/SAME) semantigini geri KAZANDIRMAZ. O semantik QSS provision history'sini gerektirir; tam parite icin RouteDetailsRequest'e context alanlari eklenmelidir (kalem 1b, QSS gundeminde).")

    d.bullet_slide(
        "RouteDetailsFilter: uc katmanli fail-open",
        [(0, "Katman 1 — bos alan degeri entry'yi ELEMEZ"),
         (1, "type veya attempts bos geldiyse matchesType/matchesAttempt true doner"),
         (0, "Katman 2 — taninmayan QSS sozlugu entry'yi ELEMEZ"),
         (1, "Degerde hic BILINEN etiket yoksa (QSS sozlugu degismis olabilir) entry korunur"),
         (1, "Ayni mantik attempts icin: hic sayisal token yoksa entry korunur"),
         (0, "Katman 3 — suzme tum listeyi elerse filtresiz listeye DONULUR"),
         (1, "WARN basilir: 'Route details filter emptied the list, falling back to unfiltered'"),
         (0, "Netice: suzme hicbir kosulda cagriyi caller ID'siz birakmaz"),
         (1, "QSS siralamasi korunur — oncelik mantigi QSS'te kalir")],
        kicker="Suzme",
        note="Neden gerekli: obtainCallerId QSS'e Uniqueness + AT_ + CT_PD_ + UT_CAMPAIGN_ gonderirken obtainRouteDetailsCursor yalniz (routingGroupId, calledNumber) gonderiyordu; caller ID secim is kurali sessizce yok sayiliyordu.")

    # ---- 6. Monitor + config ---------------------------------------------
    d.section_slide("05", "Izleme ve konfigurasyon", "Canli registry durumu, KPI'lar ve tek property'lik geri donus yolu.")

    d.bullet_slide(
        "FCT Channel Monitor ekrani",
        [(0, "Options menusu altinda, DialerAdministration yetkisiyle"),
         (1, "GET /fctChannel/snapshot — 1 sn sunucu tarafi cache (fct.channel.snapshot.cache.ms)"),
         (0, "Numara bazli tablo"),
         (1, "phoneNumber · type · attempts · capacity · effectiveCapacity · in-flight · doluluk %"),
         (1, "cooldown kalan sure · son olay zamani · stale isareti (10 dk hareketsiz)"),
         (0, "Olay log'u — bounded ArrayDeque, son 200 kayit"),
         (1, "ACQUIRE · RELEASE · REJECT (kapasite dolu) · SKIP (cooldown) · MARK_FULL · PRUNE"),
         (0, "KPI — kayan 5 dakikalik pencere"),
         (1, "pruned (kacan release gostergesi) · skipped (cooldown) · rejected (kapasite dolu)"),
         (1, "pruned sayisi surekli artiyorsa bir release yolu eksik demektir — teshis sinyali")],
        kicker="Monitor",
        note="Ekran salt-okunur bir gozlem yuzeyidir; registry'ye yazmaz. Tema: acik/flat (FctChannelRegistry.png gorseline gore).")

    d.table_slide(
        "Konfigurasyon — application.properties",
        [("Property", True, WHITE), ("Varsayilan", True, WHITE), ("Anlami", True, WHITE)],
        [["fct.channel.gate.enabled", "true", "KILL SWITCH. false -> 04.05 FCT routing davranisi birebir geri gelir"],
         ["fct.channel.ttl.ms", "3.900.000", "Rezervasyon TTL'i (CALL_EXPIRE_TIMEOUT + 5 dk). Kacan release'i temizler"],
         ["fct.channel.stale.warn.ms", "600.000", "Bu suredir hareketsiz kayit monitor'de stale isaretlenir"],
         ["fct.channel.cooldown.ms", "60.000", "cause 34 sonrasi numaranin dinlenme suresi"],
         ["fct.channel.headroom.percent", "10", "Kapasitenin sayilmayan trafige ayrilan payi"],
         ["fct.channel.defer.limit", "10", "Kapasite bekleyen cagrinin kuyrukta kac tick donebilecegi"],
         ["fct.channel.list.retry.max", "0", "cause 34 sonrasi AYNI cagri icinde in-call retry sayisi"],
         ["fct.channel.event.log.size", "200", "Monitor olay log'u kapasitesi (bounded)"],
         ["fct.channel.snapshot.cache.ms", "1.000", "Snapshot endpoint cache suresi"]],
        [inch(3.9), inch(1.5), inch(6.54)],
        kicker="Konfigurasyon",
        sz=1050,
        note="Geri donus yolu TEK property: fct.channel.gate.enabled = false. Kapatildiginda list-retry siniri da uygulanmaz.")

    # ---- 7. Test ----------------------------------------------------------
    d.section_slide("06", "Test", "10 kritik senaryo ve mevcut otomatik kapsam.")

    d.table_slide(
        "10 kritik test senaryosu (1-5)",
        [("#", True, WHITE), ("Senaryo", True, WHITE), ("Beklenen", True, WHITE), ("Kapsam", True, WHITE)],
        [["1", "Kapasite dolu -> cagri yakilmaz", "Originate atlanir, attempt geri alinir,\ncagri kuyrugun basina doner", "Otomatik\nCallOriginatorFctGateTest"],
         ["2", "Es zamanlilik: son kanal", "Iki thread ayni son kanali alamaz;\nyalniz biri true doner", "Otomatik\nFctChannelRegistryConcurrencyTest"],
         ["3", "Headroom hesabi", "capacity 10 -> effective 9;\n10. cagri REJECT", "Otomatik\nFctChannelRegistryTest"],
         ["4", "capacity <= 0 / kill switch", "Gate pasif, cagri normal gider,\nregistry YINE sayar", "Otomatik\nFctRoutingGateDisabled...Test"],
         ["5", "cause 34 -> in-call retry yok", "release + cooldown yapilir,\nTRC 25 -> standart retry", "Otomatik\nManagerConnection...ListRetryCapTest"]],
        [inch(0.5), inch(3.2), inch(4.6), inch(3.64)],
        kicker="Test",
        sz=1000,
        note="Tam set: docs/test-cases/FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE.md")

    d.table_slide(
        "10 kritik test senaryosu (6-10)",
        [("#", True, WHITE), ("Senaryo", True, WHITE), ("Beklenen", True, WHITE), ("Kapsam", True, WHITE)],
        [["6", "Release butunlugu", "Tum cikis yollarindan birakilir;\nidempotent ve owner kontrollu", "Otomatik\nFctChannelGateTest"],
         ["7", "TTL self-healing", "Kacan release prune edilir;\nuzun konusma ERKEN prune EDILMEZ", "Otomatik\nFctChannelRegistryTest"],
         ["8", "Kampanya yasam dongusu", "start/pause/reset registry'ye dokunmaz;\nforceful stop supurur", "Otomatik\nFctChannelRegistryCampaignCleanupTest"],
         ["9", "QSS gecici hata vs kalici yokluk", "Erisilemedi -> retry ACIK;\nbos liste -> retry KAPALI", "Otomatik\nCallerIdManagerRouteDetailsTest"],
         ["10", "Suzme + fail-open", "callType/attempt suzulur;\nliste bosalirsa filtresiz devam", "Otomatik\nRouteDetailsFilterTest"]],
        [inch(0.5), inch(3.2), inch(4.6), inch(3.64)],
        kicker="Test",
        sz=1000,
        note="Ek kontroller (10'un disinda, manuel): fctRoutingEnabled kaydetme round-trip, Monitor ekrani TR/EN lokalizasyon, monitor yetki kontrolu.")

    d.metric_slide(
        "Mevcut otomatik kapsam",
        [("118", "test / 0 failure / 0 error\nFCT + regresyon birlesik set", ACCENT2),
         ("29", "test\nuc kalemin hedefli seti", ACCENT2),
         ("26", "Karma spec\nyazildi, node_modules blocker'i", WARN),
         ("BUILD OK", "mvn -o -DskipTests package\ngbpowerdialer.war", ACCENT2)],
        [(0, "Test sinifi envanteri"),
         (1, "Registry: FctChannelRegistryTest (11) · Concurrency (1) · CampaignCleanup (4)"),
         (1, "Gate ve akis: FctChannelGateTest (9) · CallOriginatorFctGateTest (3) · FctRoutingGateDisabledCharacterizationTest (3)"),
         (1, "cause 34: ManagerConnectionDecoratorFctCause34CooldownTest (2) · FctListRetryCapTest (5)"),
         (1, "Suzme/QSS: RouteDetailsFilterTest (12) · CallerIdManagerRouteDetailsTest (5)"),
         (1, "Monitor/DTO: FctChannelMonitorControllerTest (5) · ContractTest (10) · CampaignFctRoutingFormMappingRegressionTest (5)")],
        kicker="Kapsam",
        note="Tum Maven komutlari offline (-o) kosuldu. Karma icin gelistirici tarafinda npm install gerekiyor.")

    d.bullet_slide(
        "Devreye alma ve takip",
        [(0, "Devreye alma sirasi"),
         (1, "1. gate.enabled = false ile deploy: registry sayar, akisa dokunmaz -> gercek doluluk gozlemlenir"),
         (1, "2. Monitor'de capacity degerlerinin QSS ile tutarliligi dogrulanir"),
         (1, "3. gate.enabled = true: dolu FCT'ye originate kesilir"),
         (1, "4. KPI'da pruned artisi izlenir; artiyorsa eksik bir release yolu var demektir"),
         (0, "Kapsam disi / takip edilecekler"),
         (1, "Kalem 1b — QSS RouteDetailsRequest context alanlari (gercek UNIQUE/SAME paritesi)"),
         (1, "TRC 25 raporlama artisinin PO ile paylasilmasi: dolu FCT'ye gitmeyen cagrilar artik TRC 25 yerine defer/retry olarak gorunur"),
         (1, "Prod loglarinda 'FCT routing list retry' satirinin varliginin teyidi"),
         (1, "Karma kosumu (npm install sonrasi 26 spec)")],
        kicker="Sonraki adimlar",
        note="Riskli bir davranis gorulurse tek adim geri donus: fct.channel.gate.enabled = false.")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "FCT-KANAL-KAPASITE-GATE.pptx"
    d = Deck()
    content(d)
    folder = os.path.dirname(os.path.abspath(out))
    if folder and not os.path.isdir(folder):
        os.makedirs(folder)
    build(d, out, "FCT Kanal Kapasite Gate", "GLB90114534")
    print("OK %s slides=%d bytes=%d" % (out, len(d.slides), os.path.getsize(out)))


if __name__ == "__main__":
    main()
