# Engine backup payload uses kotlinx.serialization; the plugin generates serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class org.freeperiod.engine.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
