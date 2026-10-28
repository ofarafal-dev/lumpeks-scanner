# --- Lumpeks Scanner: reguły R8 ---

# Czytelniejsze stack trace'y z wersji release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit Text Recognition: biblioteka dostarcza własne reguły (consumer rules),
# poniższe są zabezpieczeniem — model i rejestracja komponentów korzystają z refleksji.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.odml.image.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_text_common.**

# Parsowanie JSON: org.json jest częścią Androida, a modele parsujemy ręcznie
# (bez refleksji), więc nie wymagają reguł. Zachowujemy je dla czytelności crash logów.
-keep class dev.ofarafal.lumpeksscanner.data.** { *; }
