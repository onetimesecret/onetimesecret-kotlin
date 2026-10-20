# Consumer R8/ProGuard rules for onetimesecret-kotlin.
# R8 picks this file up automatically from the jar; nothing to copy.
#
# kotlinx.serialization ships its own rules for the runtime. These keep the
# generated serializers of this SDK's models when R8 runs in full mode.
-keepclassmembers @kotlinx.serialization.Serializable class com.onetimesecret.sdk.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.onetimesecret.sdk.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.onetimesecret.sdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
