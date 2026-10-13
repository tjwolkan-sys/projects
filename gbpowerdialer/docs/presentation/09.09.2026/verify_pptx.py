# -*- coding: utf-8 -*-
"""pptx paketini dogrular: XML well-formed mi, rels hedefleri var mi, content-types tam mi."""
import sys
import zipfile
import posixpath
import xml.etree.ElementTree as ET

REL_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"
CT_NS = "{http://schemas.openxmlformats.org/package/2006/content-types}"

path = sys.argv[1]
z = zipfile.ZipFile(path)
names = set(z.namelist())
errors = []

# 1. well-formed
for n in sorted(names):
    if not n.endswith(".xml") and not n.endswith(".rels"):
        continue
    try:
        ET.fromstring(z.read(n))
    except Exception as e:
        errors.append("XML PARSE %s -> %s" % (n, e))

# 2. rels targets
for n in sorted(names):
    if not n.endswith(".rels"):
        continue
    base = posixpath.dirname(posixpath.dirname(n)) or ""
    root = ET.fromstring(z.read(n))
    for rel in root.findall(REL_NS + "Relationship"):
        if rel.get("TargetMode") == "External":
            continue
        target = rel.get("Target")
        resolved = posixpath.normpath(posixpath.join(base, target)).lstrip("/")
        if resolved not in names:
            errors.append("REL MISSING %s -> %s (resolved %s)" % (n, target, resolved))

# 3. content types
ct = ET.fromstring(z.read("[Content_Types].xml"))
defaults = {d.get("Extension").lower() for d in ct.findall(CT_NS + "Default")}
overrides = {o.get("PartName").lstrip("/") for o in ct.findall(CT_NS + "Override")}
for n in sorted(names):
    if n == "[Content_Types].xml":
        continue
    ext = n.rsplit(".", 1)[-1].lower()
    if n in overrides or ext in defaults:
        continue
    errors.append("NO CONTENT TYPE %s" % n)
for o in sorted(overrides):
    if o not in names:
        errors.append("OVERRIDE FOR MISSING PART %s" % o)

# 4. slide count consistency
pres = ET.fromstring(z.read("ppt/presentation.xml"))
P = "{http://schemas.openxmlformats.org/presentationml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
sld_nodes = list(pres.find(P + "sldIdLst"))
sld_rids = [s.get(R + "id") for s in sld_nodes]
prels = ET.fromstring(z.read("ppt/_rels/presentation.xml.rels"))
rid_map = {r.get("Id"): (r.get("Type"), r.get("Target")) for r in prels.findall(REL_NS + "Relationship")}
SLIDE_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"
for rid in sld_rids:
    if rid not in rid_map:
        errors.append("SLIDE RID NOT IN RELS %s" % rid)
    elif rid_map[rid][0] != SLIDE_TYPE:
        # klasik hata: sldIdLst rId1'i gosterir ama rId1 slideMaster'dir
        errors.append("SLIDE RID WRONG TYPE %s -> %s (%s)" % (rid, rid_map[rid][1], rid_map[rid][0]))

# ST_SlideId gecerli aralik: 256 <= id < 2147483648 (dis aralik PowerPoint'te dosya reddi)
seen_ids = set()
for s in sld_nodes:
    try:
        sid_val = int(s.get("id"))
    except (TypeError, ValueError):
        errors.append("SLIDE ID PARSE %r" % s.get("id"))
        continue
    if not (256 <= sid_val < 2147483648):
        errors.append("SLIDE ID OUT OF RANGE %d (gecerli: 256..2147483647)" % sid_val)
    if sid_val in seen_ids:
        errors.append("SLIDE ID DUPLICATE %d" % sid_val)
    seen_ids.add(sid_val)

# Override PartName tekrari (set kullanimi bunu gizler)
ov_list = [o.get("PartName").lstrip("/") for o in ct.findall(CT_NS + "Override")]
for part in sorted({o for o in ov_list if ov_list.count(o) > 1}):
    errors.append("DUPLICATE CONTENT TYPE OVERRIDE %s" % part)

slide_files = sorted(n for n in names if n.startswith("ppt/slides/slide") and n.endswith(".xml"))
print("parts=%d slides_in_zip=%d slides_in_sldIdLst=%d" % (len(names), len(slide_files), len(sld_rids)))
if len(slide_files) != len(sld_rids):
    errors.append("SLIDE COUNT MISMATCH")

# 5. shape id uniqueness per slide + non-empty text
A = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
for n in slide_files:
    root = ET.fromstring(z.read(n))
    ids = [c.get("id") for c in root.iter(P + "cNvPr")]
    if len(ids) != len(set(ids)):
        dupes = sorted({i for i in ids if ids.count(i) > 1})
        errors.append("DUPLICATE SHAPE ID %s -> %s" % (n, dupes))
    texts = [t.text or "" for t in root.iter(A + "t")]
    if not any(x.strip() for x in texts):
        errors.append("EMPTY SLIDE %s" % n)

# 6. gorsel tasma: hicbir sekil slayt sinirlarini asmasin
SLIDE_W, SLIDE_H = 12192000, 6858000
EMU = 914400.0
for n in slide_files:
    root = ET.fromstring(z.read(n))
    for xfrm in root.iter(A + "xfrm"):
        off = xfrm.find(A + "off")
        ext = xfrm.find(A + "ext")
        if off is None or ext is None:
            continue
        x, y = int(off.get("x")), int(off.get("y"))
        cx, cy = int(ext.get("cx")), int(ext.get("cy"))
        if cx == 0 and cy == 0:
            continue
        if x + cx > SLIDE_W + 1 or y + cy > SLIDE_H + 1:
            errors.append("OVERFLOW %s shape right=%.2fin bottom=%.2fin (slayt %.2f x %.2f in)"
                          % (n, (x + cx) / EMU, (y + cy) / EMU, SLIDE_W / EMU, SLIDE_H / EMU))

if errors:
    print("\nHATA (%d):" % len(errors))
    for e in errors:
        print("  " + e)
    sys.exit(1)
print("PPTX PAKET DOGRULAMASI: TEMIZ")
