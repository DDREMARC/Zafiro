# Release-only shrinking rules. Debug builds do not run R8 (isMinifyEnabled = false).
# kotlinx.serialization and Room ship their own consumer rules; keep the
# generated serializers of the small @Serializable models used for Room columns.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.dremarc.grokbot.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.dremarc.grokbot.data.local.**$$serializer { *; }
-keepclassmembers class com.dremarc.grokbot.data.local.** {
    *** Companion;
}
-keepclasseswithmembers class com.dremarc.grokbot.data.local.** {
    kotlinx.serialization.KSerializer serializer(...);
}
