# Rally Tester — Android App

Test een **Logitech Rally** systeem via USB-C vanaf een Android 14 tablet of telefoon.

## Functies
| Functie | Implementatie |
|---|---|
| 📷 USB UVC camera preview | AndroidUSBCamera library (libuvc via JNI) |
| 🎚 VU-meters L+R + peak hold | Custom `VuMeterView`, 40 segmenten |
| 🔊 Testtoon 80/440/1000 Hz | `AudioTrack` sinus, gerouteerd naar USB speaker |
| 🔁 Echo loopback latentiemeting | Burst afspelen → mic luisteren → RMS-drempel |
| 🔌 Expliciete USB audio routing | `AudioRecord/AudioTrack.setPreferredDevice()` |
| 🔆 Scherm altijd aan | `FLAG_KEEP_SCREEN_ON` |

## Project openen in Android Studio

1. Pak het zip-bestand uit
2. **File → Open** → selecteer de map `RallyTester`
3. Wacht op Gradle sync (eerste keer download ~2 min)
4. **Als de Gradle wrapper JAR mist:**
   - `File → Project Structure → Project`
   - Klik op "Gradle Settings" → Android Studio genereert de wrapper automatisch
   - Of via terminal: `gradle wrapper --gradle-version 8.4`
5. Sluit je Android apparaat aan (USB debugging aan in developer options)
6. **Run ▶**

## Vereisten
- Android Studio **Hedgehog 2023.1.1** of nieuwer
- JDK 17
- Android **14+** (API 34) apparaat met **USB OTG/host** ondersteuning
- USB-C naar USB-A adapter indien nodig voor de Rally kabel

## Rechten
De app vraagt bij eerste start om:
- `CAMERA` — voor de UVC camera stream
- `RECORD_AUDIO` — voor de VU-meters en loopback test

## USB auto-launch
De app start automatisch als een Logitech apparaat (VendorID `0x046D`) wordt aangesloten.
Zie `res/xml/device_filter.xml`.

## Architectuur
```
MainActivity.kt       ← UI coordinatie, lifecycle
├── UvcCameraHelper.kt  ← AndroidUSBCamera wrapper (UVC/USB camera)
├── UsbAudioRouter.kt   ← Zoekt Rally USB audio device, routes AudioRecord/AudioTrack
├── AudioMeter.kt       ← AudioRecord → stereo RMS + peak dB
├── ToneGenerator.kt    ← AudioTrack → sinus wave naar USB speaker
├── EchoTester.kt       ← Burst afspelen + echo opvangen → latentie meting
└── VuMeterView.kt      ← Custom View: 40 segmenten + peak hold naald
```

## Bekende beperkingen
- De **UVC camera** werkt alleen op apparaten met echte USB Host mode.
  Niet alle Android tablets ondersteunen UVC zonder root.
- De **echo loopback** meting is een software-schatting via drempeldetectie.
  Kamerakoestiek beïnvloedt de uitkomst. Zet de Rally mic dicht bij de speaker.
- De **gradle-wrapper.jar** is niet meegeleverd (kan niet worden gedownload vanuit
  de build-omgeving). Android Studio genereert dit automatisch bij het openen.
