package com.actuallyrizzn.monocle

/**
 * Resident operator on the glasses. Display, camera, mic, sensors, LEDs, touch.
 * Phone sends 4-byte tags on the raw data service (Noa photo.py / audio.py / main.py).
 */
object HudScript {
    const val VERSION = "op-v2"
    const val FILE = "op.py"
    /** Binary paste size. Multiple of 3 so base64 stays padded. This firmware has no unhexlify. */
    const val CHUNK_BYTES: Int = 120

    const val SOURCE = """
# op-v2
import bluetooth,device,time
COLS=26
BAT_COLS=4
SUBJ_COLS=21
LINE_H=50
BODY_Y=100
BODY_ROWS=6
BRIGHT=0
SEP=b'\x1e'
acc=b''
def snd(m):
 while True:
  try:
   bluetooth.send(m)
   break
  except OSError:
   pass
def wake():
 h=getattr(device,'hibernate',None)
 if h and h():
  h(False)
def nap():
 h=getattr(device,'hibernate',None)
 if h:
  h(True)
def clip(s,n):
 s=s.replace('\r',' ').replace('\n',' ').strip() or ' '
 if len(s)<=n:
  return s
 return s[:n]
def wrap(t):
 t=t.replace('\r',' ').replace('\n',' ')
 words=[w for w in t.split(' ') if w]
 if not words:
  return [' ']
 lines=[]
 cur=''
 for w in words:
  while len(w)>COLS:
   if cur:
    lines.append(cur)
    cur=''
   lines.append(w[:COLS])
   w=w[COLS:]
   if len(lines)>=48:
    return lines[:48]
  trial=w if not cur else cur+' '+w
  if len(trial)<=COLS:
   cur=trial
  else:
   lines.append(cur)
   cur=w
  if len(lines)>=48:
   return lines[:48]
 if cur:
  lines.append(cur)
 return lines or [' ']
def blank():
 nap()
def paint(subj,who,text):
 wake()
 import display
 display.brightness(BRIGHT)
 S=clip(subj,SUBJ_COLS)
 W=clip(who,COLS)
 B=wrap(text)
 b=str(device.battery_level())+'%'
 if len(b)>BAT_COLS:
  b=b[:BAT_COLS]
 bx=640-len(b)*24
 def chrome():
  return [display.Text(S,0,0,0x29D0D0),display.Text(b,bx,0,0xFFEE33),display.Text(W,0,LINE_H,0xFFEE33)]
 def fr(s):
  o=chrome()
  i=0
  while i<BODY_ROWS:
   if s+i>=len(B):
    break
   o.append(display.Text(B[s+i],0,BODY_Y+i*LINE_H,0xFFFFFF))
   i+=1
  display.show(o)
 if len(B)<=BODY_ROWS:
  fr(0)
 else:
  w=len(B)-BODY_ROWS+1
  s=0
  while s<w:
   fr(s)
   time.sleep(1.8)
   s+=1
  nap()
def dec(x):
 try:
  return x.decode('utf-8')
 except:
  return ' '
def parse(raw):
 parts=raw.split(SEP)
 if len(parts)<3:
  paint('Monocle','-',dec(raw))
  return
 paint(dec(parts[0]),dec(parts[1]),dec(SEP.join(parts[2:])))
def do_cam():
 wake()
 import camera
 try:
  camera.capture()
  snd(b'ist:')
  time.sleep(0.25)
  n=bluetooth.max_length()-4
  sent=0
  i=0
  while i<2000 and sent<200000:
   d=camera.read(n)
   if d is None:
    break
   snd(b'idt:'+d)
   sent+=len(d)
   i+=1
  snd(b'ien:')
 finally:
  nap()
def do_mic(rest):
 wake()
 import microphone
 try:
  sec=float(dec(rest) or '2')
 except:
  sec=2.0
 if sec<0.5:
  sec=0.5
 if sec>4:
  sec=4
 microphone.record(seconds=sec,sample_rate=8000,bit_depth=8)
 time.sleep(sec+0.6)
 snd(b'ast:')
 n=bluetooth.max_length()-4
 while True:
  d=microphone.read(n)
  if d is None:
   break
  snd(b'dat:'+d)
 snd(b'aen:')
def do_sns():
 import fpga,touch
 msg='BAT %d\nCHG %s\nVER %s\nMAC %s\nTOUCH %s\nFPGA %s\nRST %s'%(device.battery_level(),device.is_charging(),device.VERSION,device.mac_address(),touch.state(),fpga.run(),device.reset_cause())
 snd(b'sns:'+msg.encode())
def do_led(rest):
 import led
 s=dec(rest).upper()
 col=led.RED if s[:1]=='R' else led.GREEN
 if ('0' in s) or s.endswith('OFF'):
  led.off(col)
 else:
  led.on(col)
def do_tqs():
 import touch
 snd(b'tch:'+str(touch.state()).encode())
def on_raw(m):
 global acc
 if not m:
  return
 tag=m[:4]
 rest=m[4:]
 if tag==b'crd:':
  parse(rest)
 elif tag==b'dat:':
  acc+=rest
 elif tag==b'end:':
  acc+=rest
  parse(acc)
  acc=b''
 elif tag==b'clr:':
  blank()
 elif tag==b'brt:' and rest:
  wake()
  import display
  try:
   display.brightness(int(rest.decode()))
  except:
   pass
 elif tag==b'png:':
  snd(b'pon:')
 elif tag==b'cam:':
  do_cam()
 elif tag==b'mic:':
  do_mic(rest)
 elif tag==b'sns:':
  do_sns()
 elif tag==b'led:':
  do_led(rest)
 elif tag==b'tqs:':
  do_tqs()
def arm():
 bluetooth.receive_callback(on_raw)
"""

    val ARM = """
import sys
if 'op' in sys.modules:
 del sys.modules['op']
import op
op.arm()
print('OP_OK')
""".trimIndent()

    val CHECK = """
try:
 p=open('op.py')
 line=''
 while line=='':
  x=p.readline()
  if not x:
   break
  line=x.strip()
 print('OPVER', line)
except:
 print('OPVER missing')
""".trimIndent()

    /** Tiny write so a bad REPL session fails before we touch op.py. */
    val PROBE = """
f=open('_t.py','wb')
f.write(b'ok')
f.close()
p=open('_t.py')
d=p.read()
p.close()
try:
 import os
 os.remove('_t.py')
except:
 pass
print('PROBE', d)
""".trimIndent()

    /** Bytes of the resident operator file. */
    fun sourceBytes(): ByteArray = SOURCE.trimIndent().toByteArray(Charsets.UTF_8)

    /**
     * Small raw-REPL pastes. One interrupt to enter raw, then these with
     * interrupt=false. A single giant paste (old writeAndArm) reboots this firmware.
     * Stacked Ctrl-C on /v1/repl also reboots. Keep each paste tiny.
     */
    fun writePlan(chunkBytes: Int = CHUNK_BYTES): List<String> {
        val bytes = sourceBytes()
        val width = (chunkBytes.coerceAtLeast(12)).let { it - (it % 3) }
        val out = ArrayList<String>(2 + (bytes.size + width - 1) / width)
        out.add("import ubinascii\nf=open('op.py','wb')\nprint('OPEN')")
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + width, bytes.size)
            val piece = bytes.copyOfRange(i, end)
            val b64 = java.util.Base64.getEncoder().encodeToString(piece)
            out.add("f.write(ubinascii.a2b_base64('$b64'))\nprint('W',${piece.size})")
            i = end
        }
        out.add("f.close()\nprint('WROTE',${bytes.size})")
        return out
    }

    fun replFailed(out: String): Boolean {
        val t = out
        if (t.startsWith("ERROR:")) return true
        if (t.contains("Traceback")) return true
        if (t.contains("MemoryError")) return true
        if (t.contains("SyntaxError")) return true
        if (t.contains("OSError")) return true
        return false
    }
}
