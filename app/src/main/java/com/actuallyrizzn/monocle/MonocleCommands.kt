package com.actuallyrizzn.monocle

import java.util.Locale

object MonocleCommands {
    const val HTTP_PORT = 8767

    fun showText(
        text: String,
        subject: String = LensText.DEFAULT_SUBJECT,
        sender: String = LensText.DEFAULT_FROM,
    ): String {
        val subj = LensText.clip(
            subject.ifBlank { LensText.DEFAULT_SUBJECT },
            LensText.SUBJ_COLS,
        )
        val who = LensText.clip(
            sender.ifBlank { LensText.DEFAULT_FROM },
            LensText.COLS,
        )
        val body = LensText.wrap(text)
        val lit = body.joinToString(",") { "'${LensText.escapePy(it)}'" }
        return """
import device,display,time
h=getattr(device,'hibernate',None)
if h and h():
 h(False)
S='${LensText.escapePy(subj)}'
W='${LensText.escapePy(who)}'
B=[$lit]
H=${LensText.LINE_H}
Y=${LensText.BODY_Y}
N=${LensText.BODY_ROWS}
def chrome():
 b=str(device.battery_level())+'%'
 if len(b)>${LensText.BAT_COLS}:
  b=b[:${LensText.BAT_COLS}]
 bx=640-len(b)*24
 return [display.Text(S,0,0,${LensText.SUBJECT_COLOR}),display.Text(b,bx,0,${LensText.BAT_COLOR}),display.Text(W,0,${LensText.LINE_H},${LensText.FROM_COLOR})]
def fr(s):
 o=chrome()
 i=0
 while i<N:
  if s+i>=len(B):
   break
  o.append(display.Text(B[s+i],0,Y+i*H,${LensText.BODY_COLOR}))
  i+=1
 display.show(o)
display.brightness(${LensText.BRIGHTNESS})
if len(B)<=N:
 fr(0)
else:
 w=len(B)-N+1
 s=0
 while s<w:
  fr(s)
  time.sleep(1.8)
  s+=1
 hh=getattr(device,'hibernate',None)
 if hh:
  hh(True)
        """.trimIndent()
    }

    /** Connect splash: product name, app version, glass version, battery chrome. */
    fun bootSplash(appVersion: String): String {
        val subj = LensText.clip(LensText.PRODUCT_SUBJECT, LensText.SUBJ_COLS)
        val who = LensText.clip(LensText.PRODUCT_FROM, LensText.COLS)
        val app = LensText.clip(appVersion.ifBlank { " " }, LensText.COLS)
        return """
import device,display
h=getattr(device,'hibernate',None)
if h and h():
 h(False)
S='${LensText.escapePy(subj)}'
W='${LensText.escapePy(who)}'
A='${LensText.escapePy(app)}'
V=str(device.VERSION)
if len(V)>${LensText.COLS}:
 V=V[:${LensText.COLS}]
H=${LensText.LINE_H}
Y=${LensText.BODY_Y}
def chrome():
 b=str(device.battery_level())+'%'
 if len(b)>${LensText.BAT_COLS}:
  b=b[:${LensText.BAT_COLS}]
 bx=640-len(b)*24
 return [display.Text(S,0,0,${LensText.SUBJECT_COLOR}),display.Text(b,bx,0,${LensText.BAT_COLOR}),display.Text(W,0,${LensText.LINE_H},${LensText.FROM_COLOR})]
o=chrome()
o.append(display.Text(A,0,Y,${LensText.BODY_COLOR}))
o.append(display.Text(V,0,Y+H,${LensText.BODY_COLOR}))
display.brightness(${LensText.BRIGHTNESS})
display.show(o)
        """.trimIndent()
    }

    fun clearDisplay(): String =
        "import display\ndisplay.clear()\nprint('CLEARED')"

    /**
     * Jump straight into the Nordic bootloader. Skip `update.micropython()`:
     * that path no-ops when REPL TX notify looks down, then sleeps 3s.
     */
    fun enterDfu(): String =
        "import _update\n_update.nrf52()"

    /**
     * Analog 10V OLED boost off. FPGA, flash, and BLE stay.
     * Next paint calls hibernate(False) then shows the card.
     */
    fun blankDisplay(): String =
        """
import device
h=getattr(device,'hibernate',None)
if h:
 h(True)
 print('HIB')
else:
 import display
 display.brightness(0)
 display.clear()
 print('BLANK')
        """.trimIndent()

    fun brightness(level: Int): String {
        val n = level.coerceIn(0, 4)
        return "import display\ndisplay.brightness($n)\nprint('BRIGHT',$n)"
    }

    fun sensors(): String =
        "import device,touch,fpga\n" +
            "print('BAT',device.battery_level())\n" +
            "print('CHG',device.is_charging())\n" +
            "print('VER',device.VERSION)\n" +
            "print('MAC',device.mac_address())\n" +
            "print('TOUCH',touch.state())\n" +
            "print('FPGA',fpga.run())\n" +
            "print('RST',device.reset_cause())"

    fun led(color: String, on: Boolean): String {
        val c = if (color.equals("RED", true)) "RED" else "GREEN"
        val op = if (on) "on" else "off"
        return "import led\nled.$op(led.$c)\nprint('LED','$c','${if (on) "ON" else "OFF"}')"
    }

    fun touch(): String =
        "import touch\nprint('TOUCH',touch.state())"

    fun micRecord(seconds: Double, sampleRate: Int, bitDepth: Int): String {
        val sec = RecordLimit.clamp(seconds)
        val rate = if (sampleRate >= 12000) 16000 else 8000
        val depth = if (bitDepth >= 12) 16 else 8
        val secLit = String.format(Locale.US, "%.1f", sec)
        val sleepLit = String.format(Locale.US, "%.1f", sec + 0.6)
        return """
import microphone,bluetooth,time
microphone.record(seconds=$secLit,sample_rate=$rate,bit_depth=$depth)
time.sleep($sleepLit)
n=min(bluetooth.max_length(),127)
sent=0
if bluetooth.connected():
    while True:
        d=microphone.read(n)
        if d is None:
            break
        bluetooth.send(d)
        sent+=len(d)
else:
    print('RAW_OFF')
print('SENT',sent)
print('RATE',$rate)
print('DEPTH',$depth)
        """.trimIndent()
    }

    /**
     * Official Noa photo path ([photo.py] + [main.py] `bluetooth_send_message`).
     * `camera.capture()` then `ist:` / `idt:` / `ien:` on the raw data service,
     * retrying `OSError` while the radio is busy. Caps stop an endless `read()`.
     */
    fun cameraCapture(): String =
        """
import bluetooth,time,device
h=getattr(device,'hibernate',None)
if h and h():
 h(False)
import camera
def snd(m):
 while True:
  try:
   bluetooth.send(m)
   break
  except OSError:
   pass
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
 print('SENT',sent,'CHUNKS',i)
finally:
 hh=getattr(device,'hibernate',None)
 if hh:
  hh(True)
  print('HIB',hh())
        """.trimIndent()
}
