# kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.rzh.valo.data.** {
    *** Companion;
}
-keepclasseswithmembers,includedescriptorclasses class com.rzh.valo.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
