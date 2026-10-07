# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes InnerClasses

# kotlinx.serialization
-keepclassmembers class **$Companion {
    *;
}
-keep,includedescriptorclasses class com.academy.mapainkluzyvnosti.**$$serializer { *; }
-keepclassmembers class com.academy.mapainkluzyvnosti.** {
    *** Companion;
}
-keepclasseswithmembers class com.academy.mapainkluzyvnosti.** {
    kotlinx.serialization.KSerializer serializer(...);
}
