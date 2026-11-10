# Keep kotlinx.serialization generated serializers for our model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hubdeck.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.hubdeck.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
