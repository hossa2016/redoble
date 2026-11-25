#!/usr/bin/env python3
"""Parte cada diseño del widget Completo en piezas para poder ordenarlas (16.0).

widget_<tema>.xml  -> cáscara: root con top_space y una pila vacía (stack)
widget_<tema>_p_header.xml  (header con col vacía)
widget_<tema>_p_middle.xml / _p_middle_top.xml (reproductor abajo / arriba)
widget_<tema>_p_setlist.xml
widget_<tema>_p_<dia|hora|fecha|clima|bateria>.xml (partes de la columna)
Se corre una sola vez sobre los diseños originales.
"""
import sys, copy, os
import xml.etree.ElementTree as ET
A = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', A)
def aid(e): return (e.get('{%s}id' % A) or '').replace('@+id/', '')
def ids(e): return {aid(x) for x in e.iter() if aid(x)}
PIECE = [('dia', {'day_txt', 'day_txt_bmp', 'date_row'}), ('hora', {'clock', 'clock_bmp'}),
         ('fecha', {'date', 'date_bmp'}), ('clima', {'hairline', 'status_row', 'wx_now', 'wx_tomorrow', 'wx_tomorrow_bmp'}),
         ('bateria', {'batt_row'})]
def piece_of(e):
    s = ids(e)
    for name, keys in PIECE:
        if s & keys: return name
    raise SystemExit('pieza desconocida: %s' % s)
def write(el, path):
    el.set('xmlns:android', A) if False else None
    data = ET.tostring(el, encoding='unicode')
    open(path, 'w').write('<?xml version="1.0" encoding="utf-8"?>\n' + data + '\n')
def wrap(children):
    if len(children) == 1: return children[0]
    w = ET.Element('LinearLayout', {'{%s}layout_width' % A: 'match_parent', '{%s}layout_height' % A: 'wrap_content',
                                    '{%s}orientation' % A: 'vertical'})
    w.extend(children); return w
d = sys.argv[1]
for t in ['rock', 'claro', 'cristal', 'neon', 'lujo']:
    base = ET.parse(os.path.join(d, 'widget_%s.xml' % t)).getroot()
    up = ET.parse(os.path.join(d, 'widget_%s_up.xml' % t)).getroot()
    kids = {aid(c): c for c in base}
    assert list(kids) == ['top_space', 'header', 'middle', 'setlist'], (t, list(kids))
    header = kids['header']; col = header.find(".//*[@{%s}id='@+id/col']" % A)
    groups = []
    for c in list(col):
        n = piece_of(c)
        if groups and groups[-1][0] == n: groups[-1][1].append(c)
        else: groups.append((n, [c]))
        col.remove(c)
    names = [g[0] for g in groups]
    assert len(set(names)) == len(names), (t, names)
    print(t, names)
    for n, cs in groups: write(wrap(cs), os.path.join(d, 'widget_%s_p_%s.xml' % (t, n)))
    write(header, os.path.join(d, 'widget_%s_p_header.xml' % t))
    write(kids['middle'], os.path.join(d, 'widget_%s_p_middle.xml' % t))
    write({aid(c): c for c in up}['middle'], os.path.join(d, 'widget_%s_p_middle_top.xml' % t))
    write(kids['setlist'], os.path.join(d, 'widget_%s_p_setlist.xml' % t))
    for c in list(base): 
        if aid(c) != 'top_space': base.remove(c)
    ET.SubElement(base, 'LinearLayout', {'{%s}id' % A: '@+id/stack', '{%s}layout_width' % A: 'match_parent',
        '{%s}layout_height' % A: '0dp', '{%s}layout_weight' % A: '1', '{%s}orientation' % A: 'vertical'})
    write(base, os.path.join(d, 'widget_%s.xml' % t))
    write(copy.deepcopy(base), os.path.join(d, 'widget_%s_up.xml' % t))
