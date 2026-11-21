# Keep kotlinx.serialization generated serializers for our model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.** {
    *** Companion;
}
-keepclasseswithmembers class app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
