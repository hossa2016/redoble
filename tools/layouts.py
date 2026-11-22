import os
# Genera los layouts del widget. Uso: python3 tools/layouts.py (desde la raíz del repo)
RES=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','app','src','main','res')
F={
 'rock':dict(time='barlow_condensed_extralight',colon='barlow_condensed_light',body='barlow_condensed_regular',med='barlow_condensed_medium',semi='barlow_condensed_semibold',title='anton_regular',label='barlow_condensed_regular',
   tsize=64,dsize=17,wsize=16,tomsize=14,lsize=13,head='#FFEDE6DC',dim='#FFB5AA9F',pad='@drawable/bg_pad',holder='@drawable/bg_icon_holder',hs=34,isz=18,padh=64,
   card='@drawable/bg_player',hair='@drawable/hairline_row',shadow=True,caps=False,center=False,tls='0.06',lls='0',glow=None,tspace='0'),
 'cristal':dict(time='jakarta_light',colon='jakarta_light',body='jakarta_regular',med='jakarta_semibold',semi='jakarta_bold',title='jakarta_bold',label='jakarta_semibold',
   tsize=76,dsize=15,wsize=15,tomsize=13,lsize=12,head='#FFFFFFFF',dim='#CCFFFFFF',pad='@android:color/transparent',holder='@drawable/bg_glass_tile',hs=60,isz=24,padh=88,
   card='@drawable/bg_glass_card',hair='@drawable/hairline_glass',shadow=False,caps=False,center=False,tls='0.04',lls='0',glow=None,tspace='-0.03'),
 'claro':dict(time='intertight_extralight',colon='intertight_extralight',body='inter_regular',med='inter_medium',semi='inter_semibold',title='inter_semibold',label='inter_medium',
   tsize=76,dsize=14,wsize=15,tomsize=13,lsize=12,head='#FF1C1A17',dim='#FF4F4A43',pad='@android:color/transparent',holder='@drawable/bg_claro_holder',hs=48,isz=22,padh=72,
   card='@drawable/bg_claro_card',hair='@drawable/hairline_claro',shadow=False,caps=False,center=False,tls='0.2',lls='0',glow=None,tspace='-0.04'),
 'neon':dict(time='orbitron_medium',colon='orbitron_medium',body='rajdhani_medium',med='rajdhani_semibold',semi='rajdhani_semibold',title='orbitron_bold',label='rajdhani_semibold',
   tsize=54,dsize=15,wsize=16,tomsize=14,lsize=12,head='#FFEFFFFF',dim='#FFA3A5CC',pad='@android:color/transparent',holder='@drawable/bg_neon_tile',hs=60,isz=24,padh=88,
   card='@drawable/bg_neon_card',hair='@drawable/hairline_neon',shadow=False,caps=True,center=False,tls='0.25',lls='0.1',glow='#FF3DF2FF',tspace='0'),
 'lujo':dict(time='cormorant_light',colon='cormorant_light',body='cormorant_italic',med='montserrat_medium',semi='cormorant_medium',title='montserrat_medium',label='montserrat_medium',
   tsize=80,dsize=12,wsize=18,tomsize=11,lsize=10,head='#FFEBD9AE',dim='#FFA8987A',pad='@android:color/transparent',holder='@drawable/bg_lujo_holder',hs=48,isz=20,padh=74,
   card='@drawable/bg_lujo_card',hair='@drawable/hairline_lujo',shadow=False,caps=True,center=True,tls='0.3',lls='0.15',glow=None,tspace='0'),
}
def sh(p,strong=False):
    if not p['shadow']: return ''
    return 'android:shadowColor="#CC000000" android:shadowDy="2" android:shadowRadius="6"'
def tv(id,font,size,color,extra='',w='wrap_content',text=None):
    t=f' android:text="{text}"' if text else ''
    return f'<TextView android:id="@+id/{id}" android:layout_width="{w}" android:layout_height="wrap_content" android:fontFamily="@font/{font}" android:textSize="{size}sp" android:textColor="{color}" android:maxLines="1" android:includeFontPadding="false"{t} {extra} />'
def build(name,p):
    H,D=p['head'],p['dim']; center=p['center']
    grav='center_horizontal' if center else 'start'
    timeglow=f'android:shadowColor="{p["glow"]}" android:shadowRadius="18"' if p['glow'] else sh(p)
    caps='android:textAllCaps="true"' if p['caps'] else ''
    rock = name=='rock'
    dayimg=f'<ImageView android:id="@+id/day_img" android:layout_width="72dp" android:layout_height="176dp" android:contentDescription="@null" android:scaleType="fitXY" android:visibility="{"visible" if rock else "gone"}" />'
    dfmt={'cristal':"EEEE, d 'de' MMMM",'neon':"EEEE '//' dd.MM",'lujo':"EEEE · d 'de' MMMM"}.get(name,'EEEE')
    daytxt=f'<TextClock android:id="@+id/day_txt" android:layout_width="{"0dp" if name=="claro" else "wrap_content"}" {"android:layout_weight=\"1\"" if name=="claro" else ""} android:layout_height="wrap_content" android:format12Hour="{dfmt}" android:format24Hour="{dfmt}" android:textAllCaps="true" android:letterSpacing="{p["tls"]}" android:fontFamily="@font/{p["title"]}" android:textSize="13sp" android:textColor="{H}" android:maxLines="1" android:visibility="{"gone" if rock else "visible"}" />'
    clock=f'''<LinearLayout android:id="@+id/clock" android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:layout_marginTop="{0 if rock else 2}dp">
 <TextClock android:id="@+id/hh" android:layout_width="wrap_content" android:layout_height="wrap_content" android:fontFamily="@font/{p["time"]}" android:format12Hour="{'hh' if name=='neon' else 'h'}" android:format24Hour="{'HH' if name=='neon' else 'H'}" android:includeFontPadding="false" android:maxLines="1" android:letterSpacing="{p["tspace"]}" {timeglow} android:textColor="{H}" android:textSize="{p["tsize"]}sp" />
 <TextView android:id="@+id/colon" android:layout_width="wrap_content" android:layout_height="wrap_content" android:fontFamily="@font/{p["colon"]}" android:includeFontPadding="false" android:maxLines="1" android:text=":" {timeglow} android:textColor="{H}" android:textSize="{p["tsize"]}sp" />
 <TextClock android:id="@+id/mm" android:layout_width="wrap_content" android:layout_height="wrap_content" android:fontFamily="@font/{p["time"]}" android:format12Hour="mm" android:format24Hour="mm" android:includeFontPadding="false" android:maxLines="1" android:letterSpacing="{p["tspace"]}" {timeglow} android:textColor="{H}" android:textSize="{p["tsize"]}sp" />
</LinearLayout>'''
    datefmt="d 'de' MMMM"
    date=f'<TextClock android:id="@+id/date" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="2dp" android:fontFamily="@font/{p["med"] if name=="lujo" else p["body"]}" android:format12Hour="{datefmt}" android:format24Hour="{datefmt}" android:maxLines="1" {caps} android:letterSpacing="{p["lls"]}" {sh(p)} android:textColor="{H if name!="lujo" else D}" android:textSize="{p["dsize"]}sp" />'
    hair=f'<FrameLayout android:id="@+id/hairline" android:layout_width="{"170dp" if center else "match_parent"}" android:layout_height="{"9dp" if name=="lujo" else "1dp"}" android:layout_marginTop="8dp" android:layout_marginBottom="8dp" android:background="{p["hair"]}" />'
    status=f'''<LinearLayout android:id="@+id/status_row" android:layout_width="wrap_content" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
 {tv("wx_now",p["body"] if name=="lujo" else p["med"],p["wsize"],H,sh(p)+' android:ellipsize="end" '+(caps if name=="neon" else ''))}
 </LinearLayout>
 <LinearLayout android:id="@+id/batt_row" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginTop="8dp" android:gravity="center_vertical" android:orientation="horizontal">
  <ImageView android:id="@+id/leds_img" android:layout_width="84dp" android:layout_height="14dp" android:contentDescription="@null" android:scaleType="fitXY" />
  <ImageView android:id="@+id/batt_bolt" android:layout_width="9dp" android:layout_height="14dp" android:layout_marginStart="6dp" android:contentDescription="Cargando" android:scaleType="fitCenter" android:visibility="gone" />
  {tv("batt_txt",p["med"],max(p["wsize"]-2,11),H,'android:layout_marginStart="6dp" '+sh(p))}
 </LinearLayout>'''
    status_only, batt = status.split(' </LinearLayout>\n <LinearLayout android:id="@+id/batt_row"',1)
    status_only = status_only + ' </LinearLayout>'
    batt = ' <LinearLayout android:id="@+id/batt_row"' + batt
    tom=tv('wx_tomorrow',p['body'] if name!='lujo' else p['med'],p['tomsize'],D,'android:layout_marginTop="3dp" android:ellipsize="end" '+(caps if name in ('neon','lujo') else '')+' android:letterSpacing="'+p['lls']+'" '+sh(p),w='wrap_content')
    col=f'''<LinearLayout android:id="@+id/col" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:gravity="{grav}" android:orientation="vertical" android:paddingStart="{8 if rock else 4}dp">
{('<LinearLayout android:id="@+id/date_row" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">'+daytxt+date+'</LinearLayout>') if name=="claro" else daytxt}
 {clock}
 {"" if name=="claro" else date}
 {hair}
 {status_only}
 {tom}
 {batt}
</LinearLayout>'''
    hcard = ' android:background="@drawable/bg_glass_card" android:paddingStart="16dp" android:paddingTop="16dp" android:paddingEnd="16dp" android:paddingBottom="16dp"' if name=='cristal' else ''
    header=f'''<LinearLayout android:id="@+id/header" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal"{hcard}>
 {dayimg}
 {col}
</LinearLayout>'''
    # players
    large=f'''<LinearLayout android:id="@+id/player" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="bottom" android:layout_marginStart="2dp" android:layout_marginEnd="2dp" android:layout_marginBottom="12dp" android:background="{p["card"]}" android:orientation="vertical" android:paddingStart="14dp" android:paddingTop="12dp" android:paddingEnd="14dp" android:paddingBottom="8dp" android:visibility="gone">
 <LinearLayout android:id="@+id/player_head" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal">
  <ImageView android:id="@+id/player_eq" android:layout_width="16dp" android:layout_height="14dp" android:layout_marginEnd="8dp" android:scaleType="fitCenter" android:contentDescription="@null" />
  {tv("player_src",p["title"],12 if name!="lujo" else 10,H,'android:letterSpacing="'+p["tls"]+'"',text="ROCKPLAY")}
  {tv("player_state",p["body"] if name!="lujo" else p["med"],13 if name!="lujo" else 10,D,'android:layout_weight="1" '+(caps if name in ("neon","lujo") else ''),w="0dp")}
  <ImageView android:id="@+id/player_close" android:layout_width="36dp" android:layout_height="28dp" android:layout_marginEnd="-8dp" android:contentDescription="Cerrar reproductor" android:paddingStart="10dp" android:paddingTop="6dp" android:paddingEnd="10dp" android:paddingBottom="6dp" android:scaleType="fitCenter" />
 </LinearLayout>
 <LinearLayout android:id="@+id/player_info" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:gravity="center_vertical" android:orientation="horizontal">
  <ImageView android:id="@+id/player_art" android:layout_width="56dp" android:layout_height="56dp" android:scaleType="fitCenter" android:contentDescription="Carátula" />
  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_marginStart="12dp" android:layout_weight="1" android:orientation="vertical">
   {tv("player_title",p["semi"],20 if name not in ("neon","claro","cristal") else 17,H,'android:ellipsize="end" '+(caps if name=="neon" else ''),w="match_parent")}
   {tv("player_artist",p["body"],15 if name!="lujo" else 16,D,'android:layout_marginTop="3dp" android:ellipsize="end" '+(caps if name=="neon" else ''),w="match_parent")}
  </LinearLayout>
 </LinearLayout>
 <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:gravity="center" android:orientation="horizontal">
  <ImageView android:id="@+id/player_prev" android:layout_width="48dp" android:layout_height="48dp" android:contentDescription="Anterior" android:padding="13dp" android:scaleType="fitCenter" />
  <ImageView android:id="@+id/player_play" android:layout_width="52dp" android:layout_height="52dp" android:layout_marginStart="36dp" android:layout_marginEnd="36dp" android:scaleType="fitCenter" android:contentDescription="Reproducir" />
  <ImageView android:id="@+id/player_next" android:layout_width="48dp" android:layout_height="48dp" android:contentDescription="Siguiente" android:padding="13dp" android:scaleType="fitCenter" />
 </LinearLayout>
</LinearLayout>'''
    compact=f'''<LinearLayout android:id="@+id/player_c" android:layout_width="match_parent" android:layout_height="64dp" android:layout_gravity="bottom" android:layout_marginStart="2dp" android:layout_marginEnd="2dp" android:layout_marginBottom="12dp" android:background="{p["card"]}" android:gravity="center_vertical" android:orientation="horizontal" android:paddingStart="10dp" android:paddingEnd="6dp" android:visibility="gone">
 <LinearLayout android:id="@+id/pc_info" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:gravity="center_vertical" android:orientation="horizontal">
  <ImageView android:id="@+id/pc_art" android:layout_width="46dp" android:layout_height="46dp" android:scaleType="fitCenter" android:contentDescription="Carátula" />
  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_marginStart="12dp" android:layout_weight="1" android:orientation="vertical">
   {tv("pc_title",p["semi"],16 if name not in ("lujo",) else 18,H,'android:ellipsize="end" '+(caps if name=="neon" else ''),w="match_parent")}
   {tv("pc_artist",p["body"],13 if name!="lujo" else 14,D,'android:layout_marginTop="2dp" android:ellipsize="end" '+(caps if name=="neon" else ''),w="match_parent")}
  </LinearLayout>
 </LinearLayout>
 <ImageView android:id="@+id/pc_play" android:layout_width="44dp" android:layout_height="44dp" android:padding="2dp" android:scaleType="fitCenter" android:contentDescription="Reproducir" />
 <ImageView android:id="@+id/pc_next" android:layout_width="44dp" android:layout_height="44dp" android:padding="12dp" android:scaleType="fitCenter" android:contentDescription="Siguiente" />
</LinearLayout>'''
    def pad(i):
        ms='' if i%3==0 else 'android:layout_marginStart="8dp"'
        return f'''<FrameLayout android:id="@+id/pad{i}" android:layout_width="0dp" android:layout_height="{p["padh"]}dp" android:layout_weight="1" {ms} android:background="{p["pad"]}">
 {tv(f"num{i}",p["med"],10 if name=="lujo" else 11,H,'android:layout_gravity="top|start" android:layout_marginStart="8dp" android:layout_marginTop="4dp"',text=f"0{i+1}")}
 <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_gravity="center" android:gravity="center_horizontal" android:orientation="vertical" android:paddingStart="4dp" android:paddingEnd="4dp" android:paddingTop="{4 if p["pad"].startswith("@drawable") else 0}dp">
  <FrameLayout android:layout_width="{p["hs"]}dp" android:layout_height="{p["hs"]}dp" android:background="{p["holder"]}">
   <ImageView android:id="@+id/icon{i}" android:layout_width="{p["isz"]}dp" android:layout_height="{p["isz"]}dp" android:layout_gravity="center" android:contentDescription="@null" />
  </FrameLayout>
  {tv(f"label{i}",p["label"],p["lsize"],H,'android:layout_marginTop="5dp" android:ellipsize="end" android:gravity="center" android:letterSpacing="'+p["lls"]+'" '+caps+' '+sh(p))}
 </LinearLayout>
</FrameLayout>'''
    setlist=f'''<LinearLayout android:id="@+id/setlist" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
 <LinearLayout android:id="@+id/setlist_head" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical" android:orientation="horizontal" android:paddingStart="4dp" android:paddingEnd="4dp" android:paddingBottom="8dp">
  {tv("setlist_title",p["title"],15 if name in ("rock",) else 12,H,'android:layout_weight="1" android:letterSpacing="'+p["tls"]+'" '+sh(p),w="0dp",text="SETLIST" if name=="rock" else "APPS")}
  {tv("count",p["body"] if name!="lujo" else p["med"],12 if name!="lujo" else 10,D,caps)}
 </LinearLayout>
 <LinearLayout android:id="@+id/row1" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal">{pad(0)}{pad(1)}{pad(2)}</LinearLayout>
 <LinearLayout android:id="@+id/row2" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:orientation="horizontal">{pad(3)}{pad(4)}{pad(5)}</LinearLayout>
 <LinearLayout android:id="@+id/empty" android:layout_width="match_parent" android:layout_height="64dp" android:background="{p["card"]}" android:gravity="center" android:visibility="gone">
  {tv("empty_txt",p["body"],15,D,'',text="Toca para elegir tus apps")}
 </LinearLayout>
</LinearLayout>'''
    xml=f'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/root" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
<FrameLayout android:id="@+id/top_space" android:layout_width="match_parent" android:layout_height="12dp" />
{header}
<FrameLayout android:id="@+id/middle" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:minHeight="10dp">
{large}
{compact}
</FrameLayout>
{setlist}
</LinearLayout>
'''
    xml=add_bmps(xml, name)
    # Efecto de toque de Android: onda en los botones del reproductor y en las apps
    tone='light' if name=='claro' else 'dark'   # onda oscura sobre fondo claro, clara sobre oscuro
    for bid in ('player_close','player_prev','player_play','player_next','pc_play','pc_next'):
        xml=xml.replace(f'<ImageView android:id="@+id/{bid}" ', f'<ImageView android:id="@+id/{bid}" android:foreground="@drawable/ripple_round_{tone}" ',1)
    for i in range(6):
        xml=xml.replace(f'<FrameLayout android:id="@+id/pad{i}" ', f'<FrameLayout android:id="@+id/pad{i}" android:foreground="@drawable/ripple_box_{tone}" ',1)
    for bid in ('player_info','pc_info'):
        xml=xml.replace(f'android:id="@+id/{bid}" ', f'android:id="@+id/{bid}" android:foreground="@drawable/ripple_box_{tone}" ',1)
    open(f'{RES}/layout/widget_{name}.xml','w',encoding='utf-8').write(xml)
    # Variante con el reproductor justo debajo del clima (opción "Debajo del clima")
    old='android:layout_gravity="bottom" android:layout_marginStart="2dp" android:layout_marginEnd="2dp" android:layout_marginBottom="12dp"'
    assert xml.count(old)==2, name
    up=xml.replace(old,'android:layout_gravity="top" android:layout_marginStart="2dp" android:layout_marginEnd="2dp" android:layout_marginTop="16dp"')
    open(f'{RES}/layout/widget_{name}_up.xml','w',encoding='utf-8').write(up)

import re as _re
BMP_IDS=['day_txt','date','wx_now','wx_tomorrow','batt_txt','setlist_title','player_src','player_state','player_title','player_artist','pc_title','pc_artist']+[f'label{i}' for i in range(6)]
def add_bmps(xml, name):
    """Agrega, junto a cada texto, una imagen gemela (<id>_bmp) para dibujar el
    texto con la letra del tema (HyperOS ignora las fuentes en los widgets)."""
    keep=('layout_width','layout_height','layout_weight','layout_gravity','layout_marginStart','layout_marginEnd','layout_marginTop','layout_marginBottom')
    for i in BMP_IDS:
        m=_re.search(r'<Text(?:View|Clock) android:id="@\+id/'+i+r'"[^>]*/>', xml)
        if not m: raise SystemExit('falta '+i+' en '+name)
        el=m.group(0)
        attrs=' '.join(a for a in _re.findall(r'android:[a-zA-Z_]+="[^"]*"', el) if a.split('=')[0].split(':')[1] in keep)
        if 'layout_width="match_parent"' in attrs or 'layout_width="0dp"' in attrs:
            sc='fitCenter' if name=='lujo' and i not in ('day_txt',) else 'fitStart'
        else:
            sc='fitStart'
        attrs=attrs.replace('layout_height="match_parent"','layout_height="wrap_content"')
        had_weight='layout_weight' in attrs
        attrs=attrs.replace('layout_width="match_parent"','layout_width="wrap_content"').replace('layout_width="0dp"','layout_width="wrap_content"')
        attrs=_re.sub(r'android:layout_weight="[^"]*"\s*','',attrs)
        if name=='claro' and i=='date': attrs+=' android:layout_marginStart="12dp"'
        img=f'<ImageView android:id="@+id/{i}_bmp" {attrs} android:adjustViewBounds="true" android:scaleType="{sc}" android:contentDescription="@null" android:visibility="gone" />'
        # Si el texto ocupaba el espacio sobrante (peso), un relleno lo ocupa para que
        # lo que viene después (por ejemplo la X de cerrar) siga pegado a la derecha
        if had_weight: img+='<FrameLayout android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="0.01" />'
        xml=xml[:m.end()]+img+xml[m.end():]
    # Reloj: imagen gemela después del contenedor de la hora
    k=xml.index('android:id="@+id/clock"'); e=xml.index('</LinearLayout>',k)+len('</LinearLayout>')
    xml=xml[:e]+'\n<ImageView android:id="@+id/clock_bmp" android:layout_width="wrap_content" android:layout_height="wrap_content" android:adjustViewBounds="true" android:contentDescription="@null" android:visibility="gone" />'+xml[e:]
    return xml

for n,p in F.items(): build(n,p)

D={
'bg_neon_tile':'<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><shape android:shape="rectangle"><corners android:radius="6dp"/><solid android:color="#F20B0A16"/><stroke android:width="1dp" android:color="#B33DF2FF"/></shape></item><item android:gravity="bottom|end" android:width="18dp" android:height="2dp" android:right="6dp" android:bottom="1dp"><shape android:shape="rectangle"><solid android:color="#FFFF3DCB"/></shape></item></layer-list>',
'bg_glass_tile':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="20dp"/><solid android:color="#1FFFFFFF"/><stroke android:width="1dp" android:color="#33FFFFFF"/></shape>',
'bg_glass_pad':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="22dp"/><solid android:color="#14FFFFFF"/><stroke android:width="1dp" android:color="#1FFFFFFF"/></shape>',
'bg_glass_card':'<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><shape android:shape="rectangle"><corners android:radius="26dp"/><solid android:color="#1AFFFFFF"/><stroke android:width="1dp" android:color="#24FFFFFF"/></shape></item><item android:gravity="top" android:height="1dp" android:left="24dp" android:right="24dp"><shape android:shape="rectangle"><solid android:color="#26FFFFFF"/></shape></item></layer-list>',
'bg_glass_holder':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval"><solid android:color="#14FFFFFF"/><stroke android:width="1dp" android:color="#29FFFFFF"/></shape>',
'hairline_glass':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><size android:height="1dp"/><solid android:color="#26FFFFFF"/></shape>',
'bg_claro_holder':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval"><solid android:color="#FFFFFFFF"/><stroke android:width="1dp" android:color="#141C1A17"/></shape>',
'bg_claro_card':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="24dp"/><solid android:color="#FFFFFFFF"/><stroke android:width="1dp" android:color="#0F1C1A17"/></shape>',
'hairline_claro':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><size android:height="1dp"/><solid android:color="#1F1C1A17"/></shape>',
'bg_neon_pad':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="6dp"/><solid android:color="#E60B0A16"/><stroke android:width="1dp" android:color="#8C3DF2FF"/></shape>',
'bg_neon_card':'<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><shape android:shape="rectangle"><corners android:radius="8dp"/><solid android:color="#F20B0A16"/><stroke android:width="1dp" android:color="#CC3DF2FF"/></shape></item><item android:gravity="bottom|end" android:width="60dp" android:height="2dp" android:right="14dp"><shape android:shape="rectangle"><solid android:color="#FFFF3DCB"/></shape></item></layer-list>',
'bg_neon_holder':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="4dp"/><solid android:color="#00000000"/><stroke android:width="1dp" android:color="#99FF3DCB"/></shape>',
'hairline_neon':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><size android:height="1dp"/><solid android:color="#663DF2FF"/></shape>',
'bg_lujo_holder':'<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><shape android:shape="oval"><solid android:color="#00000000"/><stroke android:width="1dp" android:color="#8CD4B06A"/></shape></item><item android:left="5dp" android:top="5dp" android:right="5dp" android:bottom="5dp"><shape android:shape="oval"><solid android:color="#00000000"/><stroke android:width="1dp" android:color="#33D4B06A"/></shape></item></layer-list>',
'bg_lujo_card':'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><corners android:radius="22dp"/><gradient android:angle="270" android:startColor="#B32A241A" android:endColor="#A60E0D0B"/><stroke android:width="1dp" android:color="#66D4B06A"/></shape>',
'hairline_lujo':'<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item android:gravity="center_vertical" android:height="1dp" android:right="96dp"><shape android:shape="rectangle"><gradient android:startColor="#00D4B06A" android:endColor="#CCD4B06A"/></shape></item><item android:gravity="center_vertical" android:height="1dp" android:left="96dp"><shape android:shape="rectangle"><gradient android:startColor="#CCD4B06A" android:endColor="#00D4B06A"/></shape></item><item android:gravity="center" android:width="7dp" android:height="7dp"><rotate android:fromDegrees="45" android:pivotX="50%" android:pivotY="50%"><shape android:shape="rectangle"><stroke android:width="1dp" android:color="#FFD4B06A"/></shape></rotate></item></layer-list>',
}
for n,x in D.items(): open(f'{RES}/drawable/{n}.xml','w').write('<?xml version="1.0" encoding="utf-8"?>\n'+x+'\n')
print('ok')
